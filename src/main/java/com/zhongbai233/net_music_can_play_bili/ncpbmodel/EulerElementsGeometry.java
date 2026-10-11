package com.zhongbai233.net_music_can_play_bili.ncpbmodel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.SimpleUnbakedGeometry;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 带「任意角度 / 三轴」元素支持的模型几何（1.20.1 / Forge 版）。
 *
 * <h2>两条烘焙路径</h2>
 * <ol>
 *   <li><b>普通元素</b>（{@code elements}）：角度都是原版合法的 0/±22.5/±45，
 *       直接交给 {@link UnbakedGeometryHelper#bakeElementFace}（原版路径）。</li>
 *   <li><b>欧拉元素</b>（{@code ncpb_euler_elements}）：角度是连续值，
 *       <b>自己逐顶点烤</b>（见下）。</li>
 * </ol>
 *
 * <h2>为什么欧拉元素不走 {@code ModelState}（这是一个被游戏实测推翻过的设计）</h2>
 * 早先的写法是：把「绕 origin 的欧拉旋转」共轭塞进 {@code ModelState}，交给引擎的
 * {@code applyModelRotation} 施加。数学上它与原版 {@code applyElementRotation} 等价
 * （纯 JOML 逐顶点证明，差 &lt; 1e-7），**但游戏里烤出来的几何是错的**：
 * 六个面各自轴对齐、却互相对不上（"扭箱"）。
 *
 * <p>证据：{@code tools/compare_game_dump.py} 把游戏自己（{@code ModelDump} 钩子）
 * 烤出来的顶点与离线预测逐点比 —— 带欧拉元素的模型偏差约 <b>0.19</b>，
 * 而只用原版单轴旋转的 {@code speaker} 偏差 <b>0.000001</b>。
 * 结论：问题不在数学，而在"交给引擎施加"这条路上。</p>
 *
 * <p>所以现在改成<b>逐顶点复刻原版 {@code FaceBakery} 的两步</b>，
 * 并自己写出 {@code BakedQuad} 的顶点数组（格式与 {@code FaceBakery.bakeVertex} 逐字一致：
 * 位置 3 float + 颜色 -1 + uv 2 float，光照与法线保持 0）：</p>
 * <ol>
 *   <li>{@code applyElementRotation}：绕元素 origin 施加纯旋转（用 {@link EulerElement} 提供的 R）；</li>
 *   <li>{@code applyModelRotation}：绕**固定支点 (0.5,0.5,0.5)** 施加方块状态的矩阵。</li>
 * </ol>
 * 两步都用 {@code rotateVertexBy} 的原始语义 {@code pivot + (M·(v−pivot)) * scale}。
 *
 * <h2>父链元素为什么在同步阶段摊平</h2>
 * Forge 1.20.1 的 {@code BlockModel.getElements()} 对挂了自定义 loader 的模型返回空列表，
 * 而本模组的链里存在"父模型自己也挂 loader"的情形 ⇒ 父模型的普通元素永远取不到。
 * 所以 {@code tools/sync_assets.py} 在同步阶段把父链合并进叶子，模型自包含。
 * 详见 misakanet-pending/L24。
 */
public final class EulerElementsGeometry extends SimpleUnbakedGeometry<EulerElementsGeometry> {

    private static final Vector3f CENTER = new Vector3f(0.5F, 0.5F, 0.5F);
    private static final Vector3f ONE = new Vector3f(1.0F, 1.0F, 1.0F);

    private final List<BlockElement> ownElements;
    private final List<EulerElement> eulerElements;

    public EulerElementsGeometry(List<BlockElement> ownElements, List<EulerElement> eulerElements) {
        this.ownElements = List.copyOf(ownElements);
        this.eulerElements = List.copyOf(eulerElements);
    }

    public List<BlockElement> ownElements() {
        return this.ownElements;
    }

    public List<EulerElement> eulerElements() {
        return this.eulerElements;
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> sprites, ModelState state,
                            ResourceLocation modelLocation) {
        // 普通元素：摊平后已经在自己的 JSON 里，直接走原版路径。
        if (!this.ownElements.isEmpty()) {
            bakeResolvingTextures(context, builder, this.ownElements, sprites, state, modelLocation);
        }
        // 欧拉元素：自己逐顶点烤（理由见类注释）。
        for (EulerElement euler : this.eulerElements) {
            bakeEulerElement(context, builder, euler, sprites, state);
        }
    }

    /**
     * 与 {@link UnbakedGeometryHelper#bakeElements} 同构，但**先解析 {@code #} 贴图引用**。
     *
     * <p>为什么不能直接用 {@code bakeElements}：它对每个面做的是
     * {@code sprites.apply(new Material(LOCATION_BLOCKS, new ResourceLocation(face.texture)))} ——
     * 对 {@code "#0"} 就是拿含 {@code #} 的字符串去构造 {@code ResourceLocation}，而它只接受
     * {@code [a-z0-9/._-]}，于是整个模型烘焙失败。Forge 给 {@code #} 准备的入口是
     * {@link UnbakedGeometryHelper#resolveDirtyMaterial}（它 {@code startsWith("#")} 时改走
     * {@code context.getMaterial(...)}，会沿父链解析，所以 {@code "#0" -> "#projector" -> 叶子贴图} 这条链才通）。</p>
     */
    private static void bakeResolvingTextures(IGeometryBakingContext context, IModelBuilder<?> builder,
                                              List<BlockElement> elements,
                                              Function<Material, TextureAtlasSprite> sprites,
                                              ModelState state, ResourceLocation modelLocation) {
        for (BlockElement element : elements) {
            for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                Direction direction = entry.getKey();
                BlockElementFace face = entry.getValue();
                Material material = UnbakedGeometryHelper.resolveDirtyMaterial(face.texture, context);
                BakedQuad quad = UnbakedGeometryHelper.bakeElementFace(
                        element, face, sprites.apply(material), direction, state, modelLocation);
                addQuad(builder, state, face, quad);
            }
        }
    }

    /** 逐顶点烤一个欧拉元素的所有面。 */
    private static void bakeEulerElement(IGeometryBakingContext context, IModelBuilder<?> builder,
                                         EulerElement euler, Function<Material, TextureAtlasSprite> sprites,
                                         ModelState state) {
        BlockElement element = euler.element();
        Vector3f origin = euler.origin();
        Matrix4f elementRotation = euler.rotationMatrix();
        Vector3f elementScale = euler.scale();
        Matrix4f modelRotation = state.getRotation().getMatrix();

        for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
            Direction direction = entry.getKey();
            BlockElementFace face = entry.getValue();
            Material material = UnbakedGeometryHelper.resolveDirtyMaterial(face.texture, context);
            TextureAtlasSprite sprite = sprites.apply(material);
            BlockFaceUV uv = face.uv;

            int[] data = new int[32];
            float[][] corners = faceCorners(element, direction);
            for (int i = 0; i < 4; i++) {
                Vector3f v = new Vector3f(corners[i][0] / 16.0F, corners[i][1] / 16.0F, corners[i][2] / 16.0F);
                // ① 元素自身的旋转（原版 applyElementRotation 的语义）
                v = rotateVertexBy(v, origin, elementRotation, elementScale);
                // ② 方块状态的旋转（原版 applyModelRotation：固定支点 0.5,0.5,0.5，scale=(1,1,1)）
                v = rotateVertexBy(v, CENTER, modelRotation, ONE);

                int base = i * 8;
                data[base] = Float.floatToRawIntBits(v.x);
                data[base + 1] = Float.floatToRawIntBits(v.y);
                data[base + 2] = Float.floatToRawIntBits(v.z);
                data[base + 3] = -1;                       // 颜色：白（与 FaceBakery.bakeVertex 一致）
                data[base + 4] = Float.floatToRawIntBits(sprite.getU(uv.getU(i)));
                data[base + 5] = Float.floatToRawIntBits(sprite.getV(uv.getV(i)));
                // base+6 光照、base+7 法线：与 FaceBakery.bakeVertex 一样留 0，由渲染器补齐
            }
            BakedQuad quad = new BakedQuad(data, face.tintIndex, direction, sprite, element.shade);
            addQuad(builder, state, face, quad);
        }
    }

    /** 原版 {@code FaceBakery.rotateVertexBy} 的逐字复刻：{@code p + (M·(v−p)) * scale}。 */
    private static Vector3f rotateVertexBy(Vector3f vertex, Vector3f pivot, Matrix4f m, Vector3f scale) {
        Vector4f v = new Vector4f(vertex.x - pivot.x, vertex.y - pivot.y, vertex.z - pivot.z, 1.0F);
        m.transform(v);
        v.mul(new Vector4f(scale, 1.0F));
        return new Vector3f(pivot.x + v.x, pivot.y + v.y, pivot.z + v.z);
    }

    /**
     * 一个面 4 个顶点的取角顺序（与 wiki / FaceInfo 一致）。
     *
     * <p>顺序**必须**对：顶点顺序既决定 uv 的对应，也决定面朝向（背面会被剔除）。
     * 这里的顺序与离线渲染器 {@code t5/mcart/mc_art/ingame.py} 的
     * {@code block_face_corners} 一致，便于用同一套离线图核对。</p>
     */
    private static float[][] faceCorners(BlockElement element, Direction face) {
        float x1 = element.from.x, y1 = element.from.y, z1 = element.from.z;
        float x2 = element.to.x, y2 = element.to.y, z2 = element.to.z;
        switch (face) {
            case DOWN:
                return new float[][]{{x1, y1, z2}, {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}};
            case UP:
                return new float[][]{{x1, y2, z1}, {x1, y2, z2}, {x2, y2, z2}, {x2, y2, z1}};
            case NORTH:
                return new float[][]{{x2, y2, z1}, {x2, y1, z1}, {x1, y1, z1}, {x1, y2, z1}};
            case SOUTH:
                return new float[][]{{x1, y2, z2}, {x1, y1, z2}, {x2, y1, z2}, {x2, y2, z2}};
            case WEST:
                return new float[][]{{x1, y2, z1}, {x1, y1, z1}, {x1, y1, z2}, {x1, y2, z2}};
            default:   // EAST
                return new float[][]{{x2, y2, z2}, {x2, y1, z2}, {x2, y1, z1}, {x2, y2, z1}};
        }
    }

    /** 与原版一致：有 cullface 就交剔除面（方向随方块状态旋转），否则交不剔除面。 */
    private static void addQuad(IModelBuilder<?> builder, ModelState state, BlockElementFace face, BakedQuad quad) {
        Direction cull = face.cullForDirection;
        if (cull == null) {
            builder.addUnculledFace(quad);
        } else {
            builder.addCulledFace(Direction.rotate(state.getRotation().getMatrix(), cull), quad);
        }
    }
}
