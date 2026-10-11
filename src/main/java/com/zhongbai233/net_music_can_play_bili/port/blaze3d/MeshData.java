package com.zhongbai233.net_music_can_play_bili.port.blaze3d;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * <b>移植桩（渲染延后）</b>：1.21 的 `MeshData` 在 1.20.1 上不存在。
 *
 * <p>上游把渲染好的几何包成一个 `MeshData`，再上传到 GPU。本桩**只提供类型与表面**，
 * 让相关的签名/容器写法能编译；它靠 {@link #of(BufferBuilder)} 记住真实的 builder，
 * 由调用点交给 1.20.1 的 `RenderType.end(...)` 去画。</p>
 *
 * <p><b>⚠️ 画面正确性不在本工程验收范围内</b>（MVP-SCOPE 登记渲染为延后）。
 * 本桩的设计取向是"**要崩也只崩在被延后的渲染路径上**"，不牵连 A1–A6 的功能路径。</p>
 *
 * <p><b>🔴 为什么它不在 `com.mojang.blaze3d.vertex` 里（第 91 次实测的启动崩溃）</b>：
 * 它原来是放在**游戏自己的包**里的（为了和原版同名好"借道"）。这在 Forge 上**行不通** ——
 * ModLauncher 把每个 mod 做成 JPMS 自动模块，而 `minecraft` 模块**导出**了
 * `com.mojang.blaze3d.vertex`；我们的 jar 又包含同一个包 ⇒ 模块解析直接拒绝：</p>
 *
 * <pre>java.lang.module.ResolutionException: Module net_music_can_play_bili contains package
 * com.mojang.blaze3d.vertex, module minecraft exports package com.mojang.blaze3d.vertex ...</pre>
 *
 * <p>异常打在线程 "main" 的 **stderr** 上，而 `javaw` 没有控制台 ⇒
 * 表现是"**日志戛然而止、没有 crash-report、没有 hs_err、进程悄悄消失**"。
 * ⇒ 铁律：**mod 的 class 只能待在自己的命名空间里**（本工程 = `com.zhongbai233.*`）。</p>
 */
public final class MeshData implements AutoCloseable {

    /** 1.20.1 里 VertexBuffer 是真实存在的类，可以直接借用它的 bind()/draw() 表面。 */
    public final VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);

    /** 见 {@link #of(BufferBuilder)}；只有"由 builder 造的"桩才有值。 */
    private BufferBuilder pendingBuilder;
    private final DrawState drawState;
    private final ByteBuffer indexBuffer;

    public MeshData(DrawState drawState, ByteBuffer indexBuffer) {
        this.drawState = drawState;
        this.indexBuffer = indexBuffer;
    }

    /** 1.21 的 `BufferBuilder.build()` 的对应物。
     *
     * <p>1.20.1 没有 `MeshData`：上游"build 出 MeshData 再 draw"两步在这里塌成一步 ——
     * **先把 builder 记下来**，到 draw 时交给 1.20.1 唯一的公开出口
     * `RenderType.end(BufferBuilder, VertexSorting)`（见 `VideoBillboardQuadSupport`）。
     * 这样调用点只改一行，且"怎么结束一个 render type"只写在一个地方。</p> */
    public static MeshData of(BufferBuilder builder) {
        MeshData data = new MeshData(new DrawState(VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT, 0, null), null);
        data.pendingBuilder = builder;
        return data;
    }

    /** 见 {@link #of(BufferBuilder)}。 */
    public BufferBuilder builder() {
        return this.pendingBuilder;
    }

    /** 空几何：桩的默认产出。 */
    public static MeshData empty() {
        return new MeshData(new DrawState(VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT, 0, null), null);
    }

    public DrawState drawState() {
        return this.drawState;
    }

    public ByteBuffer indexBuffer() {
        return this.indexBuffer;
    }

    /** 1.21 用它把索引按距离重排；本桩只登记一个状态，不做真实重排。 */
    public SortState sortQuads(ByteBufferBuilder indexStorage, VertexSorting sorting) {
        return new SortState(sorting);
    }

    @Override
    public void close() {
        // 桩：不释放原生资源。
    }

    /** `MeshData.drawState()` 的桩。 */
    public static final class DrawState {
        private final VertexFormat.Mode mode;
        private final VertexFormat.IndexType indexType;
        private final int indexCount;
        private final VertexFormat vertexFormat;

        public DrawState(VertexFormat.Mode mode, VertexFormat.IndexType indexType, int indexCount, VertexFormat vertexFormat) {
            this.mode = mode;
            this.indexType = indexType;
            this.indexCount = indexCount;
            this.vertexFormat = vertexFormat;
        }

        public VertexFormat.Mode mode() {
            return this.mode;
        }

        public VertexFormat.IndexType indexType() {
            return this.indexType;
        }

        public int indexCount() {
            return this.indexCount;
        }

        public VertexFormat vertexFormat() {
            return this.vertexFormat;
        }
    }

    /** `MeshData.SortState` 的桩。 */
    public static final class SortState {
        private final VertexSorting sorting;

        public SortState(VertexSorting sorting) {
            this.sorting = sorting;
        }

        public VertexSorting sorting() {
            return this.sorting;
        }

        /** 1.21 返回重排后的索引缓冲；桩返回一个空的 Result。 */
        public ByteBufferBuilder.Result buildSortedIndexBuffer(ByteBufferBuilder storage, VertexSorting sorting) {
            return new ByteBufferBuilder.Result(ByteBuffer.allocate(0));
        }

        /** 有些调用点用 forEach 形式；保留一个兜底以免签名不匹配。 */
        public void buildSortedIndexBuffer(ByteBufferBuilder storage, VertexSorting sorting, Consumer<ByteBuffer> sink) {
            sink.accept(ByteBuffer.allocate(0));
        }
    }
}
