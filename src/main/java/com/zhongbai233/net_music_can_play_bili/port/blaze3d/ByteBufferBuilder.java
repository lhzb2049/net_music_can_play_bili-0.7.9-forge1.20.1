package com.zhongbai233.net_music_can_play_bili.port.blaze3d;

import java.nio.ByteBuffer;

/**
 * <b>移植桩（渲染延后）</b>：1.21 的 `ByteBufferBuilder` 在 1.20.1 上不存在。
 *
 * <p>上游代码把它当作**签名与容器里的类型**大量使用
 * （`Map&lt;RenderType, ByteBufferBuilder&gt;`、`ByteBufferBuilder[]`、`List&lt;ByteBufferBuilder&gt;`…），
 * 这些用法只要"类型存在"就能编译通过 —— 这就是本桩存在的唯一目的。</p>
 *
 * <p><b>⚠️ 它不工作。</b>渲染在本工程里按 MVP-SCOPE 登记为<b>延后</b>：
 * 这里只保证"能编译、不崩"，**不保证画面正确**。所有依赖它的渲染路径都不应当被当作已实现。</p>
 *
 * <p><b>⚠️ 已知的硬边界</b>：1.20.1 的 `MultiBufferSource.immediate(...)` 形参是
 * `BufferBuilder`，而 `BufferBuilder` 是真实类、构造器需要缓冲大小 ——
 * 所以本桩<b>不可能</b>"是"一个 `BufferBuilder`。那些调用点**必须真改**
 * （判断标准：目标方法的形参是 1.20.1 的真实类型时，桩不适用）。</p>
 *
 * <p><b>🔴 为什么它不在 `com.mojang.blaze3d.vertex` 里</b>：见 {@link MeshData} 的类注释 ——
 * 放在游戏自己的包里会触发 JPMS"分裂包"，ModLauncher 在模块解析阶段就拒绝，
 * 而且异常只打到 `javaw` 丢掉的那条 stderr 上（表现为"日志戛然而止"）。</p>
 */
public final class ByteBufferBuilder implements AutoCloseable {

    private ByteBuffer buffer;

    public ByteBufferBuilder(int capacity) {
        this.buffer = ByteBuffer.allocateDirect(Math.max(1, capacity));
    }

    /** 拷贝构造：1.21 用它把一个已有 builder 的内容作为新 builder 的初始内容。 */
    public ByteBufferBuilder(ByteBufferBuilder other) {
        this(other == null ? 1 : other.buffer.capacity());
        if (other != null) {
            ByteBuffer src = other.buffer.duplicate();
            src.flip();
            this.buffer.put(src);
        }
    }

    /** 让"交给原版 API"的调用点还有救：返回内部缓冲的副本视图。 */
    public ByteBuffer buffer() {
        return this.buffer;
    }

    public int capacity() {
        return this.buffer.capacity();
    }

    /** 兜底写入口：桩不做任何布局，只保证不抛。 */
    public void reserve(int bytes) {
        if (bytes > this.buffer.capacity()) {
            ByteBuffer bigger = ByteBuffer.allocateDirect(bytes);
            ByteBuffer old = this.buffer.duplicate();
            old.flip();
            bigger.put(old);
            this.buffer = bigger;
        }
    }

    @Override
    public void close() {
        // 桩：不释放任何原生资源（用的是堆外分配的 Buffer，交给 GC）。
    }

    /** 1.21 里 `ByteBufferBuilder.build()` 返回的对象；桩只包一个 ByteBuffer。 */
    public static final class Result implements AutoCloseable {
        private final ByteBuffer buffer;

        public Result(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public ByteBuffer buffer() {
            return this.buffer;
        }

        @Override
        public void close() {
            // 桩：不释放原生资源。
        }
    }
}
