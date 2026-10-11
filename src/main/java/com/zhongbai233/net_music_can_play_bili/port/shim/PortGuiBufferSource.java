package com.zhongbai233.net_music_can_play_bili.port.shim;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * <b>按 RenderType 分配缓冲的 MultiBufferSource</b>：1.20.1 上画 GUI 必须用它，
 * <b>不能</b>用 {@code MultiBufferSource.immediate(...)}。
 *
 * <h2>为什么（字节码实证，不是推断）</h2>
 *
 * <p>1.20.1 的 {@code MultiBufferSource.BufferSource.getBuilderRaw} 反汇编出来就是：</p>
 * <pre>getBuilderRaw(RenderType): getfield builder → Map.getOrDefault(type, builder) → checkcast BufferBuilder</pre>
 *
 * <p>而 {@code MultiBufferSource.immediate(builder)} 传进去的 {@code fixedBuffers} 是**空表**
 * ⇒ <b>所有 RenderType 共用同一个 {@code BufferBuilder}</b>。</p>
 *
 * <p>偏偏 GUI 会**混用多种顶点格式**：{@code RenderType.gui()}（positionColor，用于 fill）、
 * {@code RenderType.text(...)}（字体图集，positionColorTexLightmap）、
 * {@code RenderType.guiTextured(tex)}（positionTexColor，用于贴图）……
 * 混进一个 builder ⇒ 顶点格式被按错误的布局解释 ⇒ <b>整块屏幕是黑的</b>
 * （实测症状：手持 Pad/MP4 有屏幕轮廓、内容全黑）。</p>
 *
 * <p>1.21 里 {@code MultiBufferSource.immediate(ByteBufferBuilder)} 是**按类型分配**的，
 * 所以上游代码那样写没问题；本工程当初做 1.21→1.20.1 API 映射时**换了 API 没换语义**
 * （把 {@code immediate(ByteBufferBuilder)} 直译成 {@code immediate(Tesselator.getBuilder())}），
 * 才留下这个黑屏。教训：**API 名一样不代表语义一样**。</p>
 *
 * <h2>做法</h2>
 *
 * <p>每种 {@code RenderType} 各给一个 {@code BufferBuilder}（用该类型自己的
 * {@code mode()}/{@code format()} 开始），收尾时交给
 * {@link RenderType#end(BufferBuilder, com.mojang.blaze3d.vertex.VertexSorting)}
 * —— 这正是 vanilla {@code BufferSource.endBatch(RenderType)} 的那一步，
 * 它内部按顺序做 {@code setupRenderState()} → {@code BufferUploader.drawWithShader(...)} →
 * {@code clearRenderState()}，于是每种类型都用自己的着色器与纹理状态绘制（GUI 是二维 ⇒ 正交排序）。</p>
 *
 * <p><b>收尾时机（第 92 次查清）</b>：1.20.1 的 {@code GuiGraphics.fill(...)} 末尾会调
 * {@code flushIfUnmanaged()}，而 {@code managed} 只在 {@code drawManaged(...)} 期间为 true
 * ⇒ <b>每调一次 {@code g.fill(...)} 就会走一次 {@code bufferSource.endBatch()}</b>。
 * 所以这里的 {@code endBatch} 必须“把已积累的顶点画掉并重置”，而不是攒到帧末（vanilla 的
 * 行为就是如此）。</p>
 */
public final class PortGuiBufferSource extends MultiBufferSource.BufferSource implements AutoCloseable {

    /** 与 vanilla {@code RenderBuffers} 同量级的缓冲大小；GUI 单帧远用不完，会按需增长。 */
    private static final int BUFFER_SIZE = 1536;

    /**
     * 一个 {@code RenderType} 对应的一整套东西：自己的缓冲、自己的顶点计数、包了收尾垫片的消费者。
     *
     * <p><b>为什么消费者要包 {@link AutoEndVertexConsumer}</b>：1.21 的 {@code VertexConsumer}
     * 没有 {@code endVertex()}（隐式收尾）。vanilla 的 GUI 路径自己会显式收尾（不影响），
     * 但本工程从 1.21 移植过来的自绘几何不会 —— 包一层就把两种语义统一了；
     * 顺带用它的计数口把“各类型顶点数”变成**真数**（旧版那个数恒为 0，见下）。</p>
     */
    private static final class TypeBatch {
        private final RenderType type;
        private final BufferBuilder builder = new BufferBuilder(BUFFER_SIZE);
        private final int[] vertices = new int[1];
        private final VertexConsumer consumer;

        private TypeBatch(RenderType type) {
            this.type = type;
            this.builder.begin(type.mode(), type.format());
            this.consumer = new AutoEndVertexConsumer(this.builder, this.vertices);
        }
    }

    /** 本帧已建立的类型批次；{@link LinkedHashMap} = “首次使用顺序” = GUI 的绘制顺序。 */
    private final Map<RenderType, TypeBatch> started = new LinkedHashMap<>();

    // ---- 诊断（第 92 次：把这三个数做成“可信的”，上一版全是占位/恒 0）----
    private final Map<String, Integer> frameVertices = new LinkedHashMap<>();
    private int frameGetBufferCalls;
    private int frameFlushes;
    private String diagTag = "gui";
    /**
     * 是否打印诊断。GUI 侧要（探针），**世界渲染侧不要** ——
     * 世界那条路每帧都会 {@code endBatch()}，开着会把这行日志刷成每帧一条。
     */
    private final boolean diag;

    public PortGuiBufferSource() {
        this(true);
    }

    public PortGuiBufferSource(boolean diag) {
        // 父类的 fallback builder 我们用不到（下面的 getBuffer 全权接管），
        // 但 1.20.1 的 GuiGraphics 构造器要的是 BufferSource 这个**具体类型**，所以必须继承它；
        // 世界渲染（`PortWorldRenderEvents`）也需要它 —— 那里同样必须**按 RenderType 分配缓冲**，
        // 否则就是 §6.23 续 2 记过的那个坑（所有类型挤进同一个 BufferBuilder）。
        super(new BufferBuilder(BUFFER_SIZE), java.util.Map.of());
        this.diag = diag;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        this.frameGetBufferCalls++;
        return this.started.computeIfAbsent(type, TypeBatch::new).consumer;
    }

    /**
     * 收尾并绘制本批顶点。**每次 {@code g.fill(...)} 都会打到这里**（见类注释），
     * 所以它必须是幂等的：没有批次时立刻返回，结批后把 {@link #started} 清干净，
     * 下一次 {@code getBuffer} 会重新 {@code begin(...)}。
     */
    @Override
    public void endBatch() {
        if (this.started.isEmpty()) {
            return;
        }
        this.frameFlushes++;
        for (TypeBatch batch : this.started.values()) {
            if (batch.builder.isCurrentBatchEmpty()) {
                // 只 getBuffer 过、一个顶点都没写 ⇒ 什么都不用画（也不去碰空批次的 end()）。
                batch.builder.discard();
            } else {
                batch.type.end(batch.builder, RenderSystem.getVertexSorting());
            }
            if (this.diag) {
                this.frameVertices.merge(shortName(batch.type), batch.vertices[0], Integer::sum);
            }
        }
        this.started.clear();
    }
    /**
     * 换帧：把上一帧的汇总打出来（<b>这就是“到底画了多少顶点”的判据</b>），
     * 并丢掉任何没结掉的遗留批次 —— 遗留说明上一帧中途出过异常，
     * 那些顶点若留到这一帧会在**错误的绑定/投影**下被画出来。
     */
    public void diagTag(String tag) {
        if (!this.diag) {
            return;      // 世界渲染侧不开诊断（见 `diag` 字段的注释）
        }
        if (this.frameGetBufferCalls > 0 || this.frameFlushes > 0) {
            com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.r(
                    "{} 上一帧：getBuffer={} 次 结批={} 次 各类型顶点数={}",
                    tag, this.frameGetBufferCalls, this.frameFlushes, this.frameVertices);
        }
        if (!this.started.isEmpty()) {
            com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.r(
                    "{} 有 {} 个类型批次没结掉（上一帧中断），已丢弃", tag, this.started.size());
            for (TypeBatch batch : this.started.values()) {
                if (batch.builder.building()) {
                    batch.builder.discard();
                }
            }
            this.started.clear();
        }
        this.frameGetBufferCalls = 0;
        this.frameFlushes = 0;
        this.frameVertices.clear();
        this.diagTag = tag;
    }

    /**
     * 类型短名，只用于诊断打印。刻意不用 {@code Collectors.toMap}：
     * 多个 {@code guiTextured(<不同纹理>)} 会归一化成同一个短名，
     * 那个收集器遇到重复键会**直接抛异常**（把被测量的那一帧打死）。
     */
    private static String shortName(RenderType type) {
        String name = type.toString();
        int colon = name.indexOf(':');
        return (colon < 0 ? name : name.substring(0, colon)).replace("RenderType[", "");
    }

    /**
     * 本帧累计提交的顶点总数（诊断用）。
     *
     * <p>它是"<b>画了但被裁掉</b>"这类缺陷的唯一证词：顶点数 &gt; 0 而回读 FBO 只有 1 种颜色，
     * 说明收尾/上传都发生了，问题在**投影或模型视图**（PORT-STATUS §6.25 实测过这一族）。</p>
     */
    public int frameVertexTotal() {
        int total = 0;
        for (int vertices : this.frameVertices.values()) {
            total += vertices;
        }
        return total;
    }

    @Override
    public void close() {
        this.endBatch();
    }
}
