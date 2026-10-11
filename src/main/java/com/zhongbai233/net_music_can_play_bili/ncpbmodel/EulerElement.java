package com.zhongbai233.net_music_can_play_bili.ncpbmodel;

import net.minecraft.client.renderer.block.model.BlockElement;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 一个允许「任意角度、三轴同时」旋转的元素（1.20.1 / Forge 版）。
 *
 * <h2>为什么需要它</h2>
 * 原版的元素旋转有两条硬限制：
 * <ol>
 *   <li>{@code BlockElementRotation} 只接受 {@code (origin, Direction.Axis, angle, rescale)} —— <b>单轴</b>；</li>
 *   <li>{@code BlockElement$Deserializer} 只放行 {@code 0 / ±22.5 / ±45} 这几个角度，其它值直接抛
 *       {@code JsonParseException} ⇒ <b>整个模型加载失败</b>。</li>
 * </ol>
 * 而本模组的模型用逐轴欧拉角（{@code {origin, x, y, z}}）、角度是连续值
 * （例如投影仪挡板 90°、现代唱片机碟片 −67.5/−90/−112.5、中控台侧板 −20/10/7.5），
 * 一个都写不进原版格式。
 *
 * <h2>怎么绕开（这里曾经走错过一次，记下来）</h2>
 * <b>曾经的做法（错的）</b>：把「绕 origin 的欧拉旋转」共轭塞进 {@code ModelState}，
 * 交给引擎的 {@code applyModelRotation} 去施加。数学上它等价于原版
 * {@code applyElementRotation}（已用纯 JOML 逐顶点证明，差 &lt; 1e-7），
 * <b>但实测游戏里烤出来的几何是错的</b>：6 个面各自是轴对齐矩形，却互相对不上，
 * 整体呈"扭箱"。证据：{@code tools/compare_game_dump.py} 把游戏自己
 * （{@code ModelDump} 钩子）烤出来的顶点与离线预测比，带欧拉元素的模型偏差约
 * 0.19，而**只用原版单轴旋转的 speaker 偏差 0.000001**。
 * 也就是说：问题不在"数学算错"，而在"交给引擎施加"这条路上。
 *
 * <p><b>现在的做法（对的）</b>：不再依赖 {@code ModelState}。
 * 本类只负责**算**（origin / 纯旋转矩阵 / 缩放），
 * 由 {@link EulerElementsGeometry} **逐顶点**复刻原版 {@code FaceBakery} 的两步
 * （{@code applyElementRotation} → {@code applyModelRotation}），并自己写出
 * {@code BakedQuad} 的顶点数据。这样每一步都在本工程的控制之下、可被游戏实测校验。</p>
 *
 * <p>矩阵口径与上游（1.21.1）逐字一致，避免"旋转顺序猜错"：</p>
 * <pre>
 *   new Matrix4f().rotationZYX(z * DEG_TO_RAD, y * DEG_TO_RAD, x * DEG_TO_RAD)
 * </pre>
 */
public final class EulerElement {

    /** 上游用硬编码 0.017453292f（不是 Math.PI/180），这里保持一致。 */
    private static final float DEG_TO_RAD = 0.017453292F;

    private final BlockElement element;
    private final Vector3f origin;
    private final Matrix4f rotation;
    private final Vector3f scale;

    /**
     * @param element 已构造好的元素（其 {@code rotation} 必须为 {@code null}，旋转由本类提供）
     * @param origin  旋转中心，<b>单位为方块</b>（JSON 里的 0~16 值 × 0.0625）
     * @param x       绕 X 轴角度（度）
     * @param y       绕 Y 轴角度（度）
     * @param z       绕 Z 轴角度（度）
     */
    public EulerElement(BlockElement element, Vector3f origin, float x, float y, float z) {
        this(element, origin, x, y, z, new Vector3f(1.0F, 1.0F, 1.0F));
    }

    public EulerElement(BlockElement element, Vector3f origin, float x, float y, float z, Vector3f scale) {
        this.element = element;
        this.origin = new Vector3f(origin);
        this.rotation = new Matrix4f().rotationZYX(z * DEG_TO_RAD, y * DEG_TO_RAD, x * DEG_TO_RAD);
        this.scale = new Vector3f(scale);
    }

    public BlockElement element() {
        return this.element;
    }

    /** 旋转中心，方块单位（0..1）。 */
    public Vector3f origin() {
        return new Vector3f(this.origin);
    }

    /** **纯**旋转矩阵（不含 origin 平移、不含缩放）—— 与原版 {@code applyElementRotation} 里那个矩阵同义。 */
    public Matrix4f rotationMatrix() {
        return new Matrix4f(this.rotation);
    }

    /** 原版 rescale 用的逐轴缩放；本模组暂不支持 rescale，恒为 (1,1,1)。 */
    public Vector3f scale() {
        return new Vector3f(this.scale);
    }
}
