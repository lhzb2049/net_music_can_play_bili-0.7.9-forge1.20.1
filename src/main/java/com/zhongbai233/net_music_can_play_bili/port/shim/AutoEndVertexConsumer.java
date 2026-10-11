package com.zhongbai233.net_music_can_play_bili.port.shim;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * <b>顶点收尾垫片</b>：把 1.21 的"隐式顶点收尾"补回 1.20.1 的"显式 {@code endVertex()}"。
 *
 * <h2>为什么必须有它（第 91 次四续，实测"交互存在但完全没有渲染"的根因）</h2>
 *
 * <p><b>1.21</b> 的 {@code VertexConsumer} <b>没有</b> {@code endVertex()}：顶点在缓冲里隐式收尾
 * （下一次 {@code vertex(...)} 或 {@code build()} 时结算）。<br>
 * <b>1.20.1</b> 的 {@code BufferBuilder} 则要求<b>每个顶点显式 {@code endVertex()}</b> ——
 * 少一次，那个顶点就不算数，{@code end()} 时顶点数为 0，于是</p>
 *
 * <pre>提交了几何、批次也刷了，屏幕上什么都没有</pre>
 *
 * <p>实测证据：本工程全树 {@code .vertex(} 出现 <b>30 处</b>，而 {@code .endVertex()}
 * <b>0 处</b>（上游 1.21 源码里压根没有这个方法）。于是 Pad/MP4 的手持屏幕
 * "每帧提交 568 次，画面上一点东西都没有" —— 连白色的机身四边形都看不见。</p>
 *
 * <h2>做法</h2>
 *
 * <p>不去改 30 条链（既啰嗦又容易漏），而是**在提交口包一层**：</p>
 * <ol>
 *   <li>所有链式方法都**返回垫片自己**，于是整条链都走垫片；</li>
 *   <li>每次 {@code vertex(...)} 之前，先把**上一个还没收尾的顶点** {@code endVertex()} 掉
 *       —— 这正是 1.21 的语义；</li>
 *   <li>调用方（{@link PortSubmitNodeCollector}）在发射器返回后**再收尾一次**，
 *       把最后一个顶点结掉。</li>
 * </ol>
 *
 * <p>接口里的 {@code default} 方法（{@code color(int)}、{@code uv2(int)}、
 * {@code vertex(Matrix4f,...)}、{@code putBulkData(...)} 等）**不用覆写**：
 * 它们内部都会回调到本类实现的抽象方法上，于是自动获得同样的收尾语义。</p>
 */
public final class AutoEndVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final int[] counter;      // 可空：诊断用，统计"发起了几个顶点"
    private boolean pending;

    public AutoEndVertexConsumer(VertexConsumer delegate) {
        this(delegate, null);
    }

    public AutoEndVertexConsumer(VertexConsumer delegate, int[] counter) {
        this.delegate = delegate;
        this.counter = counter;
    }

    /** 把上一个顶点收尾（如果有）。 */
    private void endPending() {
        if (this.pending) {
            this.delegate.endVertex();
            this.pending = false;
        }
    }

    /** 发射器返回后由调用方收尾最后一个顶点。 */
    public void finish() {
        this.endPending();
    }

    // ---- 抽象方法：唯一需要转发的部分 ----

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (this.counter != null) {
            this.counter[0]++;
        }
        this.endPending();
        this.delegate.vertex(x, y, z);
        this.pending = true;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        this.delegate.color(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        this.delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        this.delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        this.delegate.endVertex();
        this.pending = false;
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        this.delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        this.delegate.unsetDefaultColor();
    }

    // ---- 下面两个为了少走 default 的矩阵变换路径，保持链式返回自己 ----

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z) {
        if (this.counter != null) {
            this.counter[0]++;
        }
        this.endPending();
        this.delegate.vertex(matrix, x, y, z);
        this.pending = true;
        return this;
    }

    @Override
    public VertexConsumer normal(Matrix3f matrix, float x, float y, float z) {
        this.delegate.normal(matrix, x, y, z);
        return this;
    }
}
