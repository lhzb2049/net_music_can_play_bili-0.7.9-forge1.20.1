package com.zhongbai233.net_music_can_play_bili.blockentity;

import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElement;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElementPosition;
import com.zhongbai233.net_music_can_play_bili.util.NcpbSystemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * 方块实体的<b>渲染裁剪包围盒</b>：公共（非客户端）侧的**唯一实现**。
 *
 * <h2>为什么要抽到公共侧（第 99 次，采纳参考移植的做法）</h2>
 *
 * <p>1.20.1 里真正被原版裁剪读取的钩子是 <b>{@code IForgeBlockEntity#getRenderBoundingBox()}</b>
 * ——它挂在 <b>BlockEntity</b> 上（1.21 的 Forge 才把它放在 {@code IForgeBlockEntityRenderer} 上，
 * 所以 {@code client/renderer/} 里那些同名方法在 1.20.1 里<b>已经不是覆写</b>、只剩内部调用）。
 * 也就是说：投影幕这类"画在方块外面几格"的东西，必须在 <b>BE</b> 上给出包围盒，
 * 否则一旦方块所在截面离开视锥，渲染器连被调用都不会（用户实测症状：
 * "只要视野中没有投影仪这个方块，画面就消失"）。</p>
 *
 * <p>而 BE 与 BER 两边都需要同一套几何（BER 还要用真实画面宽高比），所以**只能有一份实现**，
 * 放在专用服务端也会加载的公共包里。本类只依赖 {@link BlockPos}/{@link AABB}/{@link Vec3}
 * 与 {@code util.NcpbSystemProperties}，<b>没有任何 {@code net.minecraft.client.*} 依赖</b>。</p>
 *
 * <p>盒子语义逐行对齐渲染器（不引入近似）：
 * {@link #turntable} ← {@code ModernTurntableRenderer}；
 * {@link #lyricProjector} ← {@code LyricProjectorRenderer}；
 * {@link #videoProjector} / {@link #projectorScreen} ← {@code VideoProjectorRenderer}
 * + {@code ProjectorScreenBounds}/{@code ProjectorScreenGeometry}。</p>
 *
 * <p><b>参考对照</b>：GitHub 上另一份已实机验证的 1.20.1 移植
 * （`ChihayaAnonQWQ/NetMusicCanPlayBili-1.20.1`，MIT）里有一个同名的
 * {@code BlockEntityRenderBounds}，结论与钩子位置一致；本类是它的等价实现。</p>
 */
public final class BlockEntityRenderBounds {

    /** 投影面基准高度，对应 {@code ProjectorScreenGeometry.BASE_HEIGHT}。 */
    public static final double PROJECTOR_BASE_HEIGHT = 1.35D;
    /** 没有可用视频帧时的默认画面宽高比（与 BER 的回退值一致）。 */
    public static final double DEFAULT_VIDEO_ASPECT = 16.0D / 9.0D;
    /** BER 对宽高比的下限钳制。 */
    private static final double MIN_VIDEO_ASPECT = 1.0D / 8.0D;

    /** 视频包围盒余量/宽高比上限：与 {@code ProjectorRenderProperties.videoBounds()} **同一批属性键与默认值**。 */
    private static final double VIDEO_MARGIN =
       Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.video.projector.render_margin", 0.5));
    private static final double VIDEO_MAX_ASPECT =
       Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.video.projector.render_max_aspect", 8.0));

    /** 歌词排版常量：与 {@code LyricProjectorRenderer} 的 TEXT_SCALE=0.025 / LINE_STEP=14 / 上 2 下 2 保持一致。 */
    private static final double LYRIC_TEXT_SCALE = 0.025D;
    private static final double LYRIC_LINE_STEP = 14.0D;
    private static final int LYRIC_VISIBLE_LINES_ABOVE = 2;
    private static final int LYRIC_VISIBLE_LINES_BELOW = 2;
    private static final double LYRIC_MARGIN =
       Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.lyric.projector.render_margin", 2.0));
    private static final double LYRIC_MIN_INFLATE =
       Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.lyric.projector.render_min_inflate", 2.5));
    private static final double LYRIC_MAX_TEXT_WIDTH =
       Math.max(0.0D, NcpbSystemProperties.doubleValue("ncpb.lyric.projector.render_max_text_width", 8.0));

    private BlockEntityRenderBounds() {
    }

    /** 现代唱片机：方块周围 1 / 2.5 / 1 格（与 {@code ModernTurntableRenderer} 相同）。 */
    public static AABB turntable(BlockPos pos) {
        return new AABB(pos).inflate(1.0D, 2.5D, 1.0D);
    }

    /**
     * 中控台：方块本身 ∪ **所有已启用元素**的世界包围盒
     * （与 {@code ControlConsoleRenderer#getRenderBoundingBox} 逐行等价）。
     *
     * <p><b>为什么必须有它</b>（第 101 次，用户实测："右键进入中控台后**编辑界面消失**"）：
     * 进入编辑/漫游模式时相机位置会变，方块很容易先离开视锥；而中控台的面板元素画在
     * 方块周围（甚至按元素变换拉到几格之外）。1.20.1 里不覆写 BE 的包围盒，就按
     * "方块所在 16³ 截面是否在视锥里"决定要不要调用渲染器 ⇒ 一进编辑模式面板就整块消失。</p>
     */
    public static AABB controlConsole(BlockPos pos, ControlConsoleDocument document) {
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1.0D;
        double maxY = pos.getY() + 1.0D;
        double maxZ = pos.getZ() + 1.0D;
        for (ControlConsoleElement element : document.elements()) {
            if (!element.enabled()) {
                continue;
            }
            Matrix4f transform = element.editorTransform().matrix();
            if (element.type() == ControlConsoleElement.Type.AUDIO) {
                Vector3d worldPosition = ControlConsoleElementPosition.worldPosition(pos.getX(), pos.getY(), pos.getZ(), element);
                minX = Math.min(minX, worldPosition.x);
                minY = Math.min(minY, worldPosition.y);
                minZ = Math.min(minZ, worldPosition.z);
                maxX = Math.max(maxX, worldPosition.x);
                maxY = Math.max(maxY, worldPosition.y);
                maxZ = Math.max(maxZ, worldPosition.z);
            } else {
                float halfHeight = element.height() * 0.5F;
                float halfWidth = halfHeight * element.aspect();
                for (int ySign : new int[]{-1, 1}) {
                    for (int xSign : new int[]{-1, 1}) {
                        Vector3f corner = transform.transformPosition(new Vector3f(xSign * halfWidth, ySign * halfHeight, 0.0F));
                        double worldX = centerX + corner.x;
                        double worldY = pos.getY() + 1.55D + corner.y;
                        double worldZ = centerZ + corner.z;
                        minX = Math.min(minX, worldX);
                        minY = Math.min(minY, worldY);
                        minZ = Math.min(minZ, worldZ);
                        maxX = Math.max(maxX, worldX);
                        maxY = Math.max(maxY, worldY);
                        maxZ = Math.max(maxZ, worldZ);
                    }
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 歌词投影仪：以方块为中心，按"投影偏移半径 + 文本半径 + 余量"膨胀
     * （与 {@code LyricProjectorRenderer} 的算式逐行等价）。
     */
    public static AABB lyricProjector(BlockPos pos, double projectionScale, double distanceX, double height, double distanceZ) {
        double scale = Math.max(0.25D, Math.abs(projectionScale));
        double textHalfWidth = LYRIC_MAX_TEXT_WIDTH * scale * 0.5D;
        double lineHeight = LYRIC_LINE_STEP * LYRIC_TEXT_SCALE * scale;
        double textHalfHeight = Math.max(lineHeight, lineHeight * (1 + LYRIC_VISIBLE_LINES_ABOVE + LYRIC_VISIBLE_LINES_BELOW) * 0.5D);
        double textRadius = Math.sqrt(textHalfWidth * textHalfWidth + textHalfHeight * textHalfHeight);
        double offsetRadius = Math.sqrt(distanceX * distanceX + height * height + distanceZ * distanceZ);
        double inflate = Math.max(LYRIC_MIN_INFLATE, offsetRadius + textRadius + LYRIC_MARGIN);
        return new AABB(pos).inflate(inflate, inflate, inflate);
    }

    /**
     * 视频投影仪（BE 侧默认宽高比 = 16:9）。
     *
     * <p><b>已知差异（BE 侧无法消除）</b>：BER 会用客户端解码器**当前帧**的 width/height 推导真实宽高比
     * （那是客户端渲染期状态，BE 不能引用），所以播放非 16:9 画面时本盒在水平方向略窄；
     * 客户端渲染时请调用带 {@code aspect} 的重载以完全对齐。</p>
     */
    public static AABB videoProjector(BlockPos pos, double offsetX, double height, double offsetZ,
                                      double yawDegrees, double pitchDegrees, double scale) {
        return videoProjector(pos, offsetX, height, offsetZ, yawDegrees, pitchDegrees, scale, DEFAULT_VIDEO_ASPECT);
    }

    /** 视频投影仪（显式宽高比）：按 BER 的规则钳制到 [1/8, maxAspect] 后套用余量。 */
    public static AABB videoProjector(BlockPos pos, double offsetX, double height, double offsetZ,
                                      double yawDegrees, double pitchDegrees, double scale, double aspect) {
        return projectorScreen(pos, offsetX, height, offsetZ, yawDegrees, pitchDegrees, scale,
           clampVideoAspect(aspect), VIDEO_MARGIN);
    }

    /** 旋转后投影四边形的世界 AABB：与 {@code ProjectorScreenGeometry.aroundCenter} 逐行等价。 */
    public static AABB projectorScreen(BlockPos blockPos, double offsetX, double height, double offsetZ,
                                       double yawDegrees, double pitchDegrees, double scale, double aspect, double margin) {
        double centerX = blockPos.getX() + 0.5D + offsetX;
        double centerY = blockPos.getY() + height;
        double centerZ = blockPos.getZ() + 0.5D + offsetZ;
        double halfHeight = PROJECTOR_BASE_HEIGHT * Math.abs(scale) * 0.5D;
        double halfWidth = halfHeight * Math.max(0.0D, aspect);
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double upX = forwardX * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = forwardZ * Math.sin(pitch);
        double extentX = Math.abs(rightX * halfWidth) + Math.abs(upX * halfHeight);
        double extentY = Math.abs(upY * halfHeight);
        double extentZ = Math.abs(rightZ * halfWidth) + Math.abs(upZ * halfHeight);
        double safeMargin = Math.max(0.0D, margin);
        return new AABB(centerX - extentX - safeMargin, centerY - extentY - safeMargin, centerZ - extentZ - safeMargin,
           centerX + extentX + safeMargin, centerY + extentY + safeMargin, centerZ + extentZ + safeMargin);
    }

    /** 点到 AABB 的最近距离平方（点在盒内为 0）：与 {@code ProjectorScreenBounds.distanceToSqr} 等价，供 BER 的距离门共用。 */
    public static double distanceToSqr(AABB bounds, Vec3 point) {
        double dx = axisDistance(point.x, bounds.minX, bounds.maxX);
        double dy = axisDistance(point.y, bounds.minY, bounds.maxY);
        double dz = axisDistance(point.z, bounds.minZ, bounds.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** 按 BER 的规则钳制画面宽高比：{@code min(maxAspect, max(1/8, aspect))}。 */
    public static double clampVideoAspect(double aspect) {
        return Math.min(VIDEO_MAX_ASPECT, Math.max(MIN_VIDEO_ASPECT, aspect));
    }

    public static double videoMargin() {
        return VIDEO_MARGIN;
    }

    public static double videoMaxAspect() {
        return VIDEO_MAX_ASPECT;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        return value > max ? value - max : 0.0D;
    }
}
