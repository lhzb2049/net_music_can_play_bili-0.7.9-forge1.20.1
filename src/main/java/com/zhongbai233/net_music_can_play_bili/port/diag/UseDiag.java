package com.zhongbai233.net_music_can_play_bili.port.diag;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;

/**
 * <b>临时诊断（第 91 次续）</b>：把"方块右键这条路到底有没有走到、走到了返回什么"写进 {@code latest.log}。
 *
 * <p>为什么需要它：用户报告"右键方块还是没有反应"，而静态证据显示制品里 <b>确实有</b> 1.20.1 的重写
 * （jar 里是重混淆后的 {@code m_6227_} = {@code use}）。剩下两种可能无法再从静态上区分：</p>
 * <ol>
 *   <li>{@code use()} <b>根本没被调用</b>（交互没到达方块：按键/物品/交互被上游吞掉）；</li>
 *   <li>{@code use()} <b>被调用了</b>，但里面"开界面"那一步是空转（dist 判断、客户端钩子、网络包断了）。</li>
 * </ol>
 *
 * <p>一盏灯照出是哪一种。日志同时打印 <b>side</b>（CLIENT/SERVER）——第 3 种可能是
 * "服务端走了、客户端没走"，分开看才分得清。</p>
 *
 * <p><b>现在是 opt-in</b>：默认<b>关</b>（交付件不带 {@code [NCPB/diag]} 噪声），
 * 需要时用 {@code -Dncpb.diag.use=true} 打开；关着时连 Forge 事件探针都不挂，
 * 零开销、零日志。方块右键那条缺陷就是靠它定位的（见 PORT-STATUS §6.21）。</p>
 */
public final class UseDiag {

    /**
     * 诊断总开关。<b>第 105 次起默认关闭</b>（发布卫生）：探针代码全部保留，
     * 但要用 `-Dncpb.diag.use=true`（或 `-Dncpb.diag.use`）显式打开，
     * 免得发布包在正常游玩时把日志刷满 —— 排查时按需打开即可。
     */
    public static final boolean ON = Boolean.parseBoolean(System.getProperty("ncpb.diag.use", "false"));

    /**
     * 心跳计数（第 100 次加）：`tag → [累计次数, 上次打印纳秒, 上次打印时的累计]`。
     *
     * <p>用途：验证"**引擎还在调用这个渲染器吗**"。已有的状态探针只在**载荷变化**时打印，
     * 而"投影幕在方块离开视野后消失"这件事恰恰**不改变载荷** —— 所以需要按时间打的心跳，
     * 把"方块不在视野里时，渲染器每 5 秒仍被调用 N 次"变成一行可读的数字。</p>
     */
    private static final java.util.Map<String, long[]> HEARTBEATS = new java.util.concurrent.ConcurrentHashMap<>();

    private UseDiag() {
    }

    /**
     * 按时间节流的心跳：每 {@code intervalMillis} 毫秒打一行，附带"这一周期内被调用了多少次"。
     *
     * @param tag            心跳标签（同一个渲染器用固定字符串）
     * @param intervalMillis 打印间隔
     * @param payload        附加信息（会被原样拼到行尾）
     */
    public static void heartbeat(String tag, long intervalMillis, String payload) {
        if (!ON) {
            return;
        }
        long[] state = HEARTBEATS.computeIfAbsent(tag, ignored -> new long[]{0L, System.nanoTime(), 0L});
        state[0]++;
        long now = System.nanoTime();
        if (now - state[1] >= java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(1L, intervalMillis))) {
            long inPeriod = state[0] - state[2];
            state[1] = now;
            state[2] = state[0];
            LogManager.getLogger("NCPB/diag").info(
                    "[NCPB/diag] {} 心跳：累计调用={} 次，本周期={} 次 {}",
                    tag, state[0], inPeriod, payload == null ? "" : payload);
        }
    }

    /** 在方块/物品的交互入口第一行调用。 */
    public static void enter(String what, Level level, Player player, InteractionHand hand) {
        if (!ON || player == null || level == null) {
            return;      // Item#useOn 的 context 可能没有玩家
        }
        ItemStack stack = player.getItemInHand(hand);
        LogManager.getLogger("NCPB/diag").info(
                "[NCPB/diag] {} 进来了：side={} hand={} held={} 空手={} 潜行={} 玩家={}",
                what,
                level.isClientSide() ? "CLIENT" : "SERVER",
                hand,
                stack.isEmpty() ? "<空手>" : stack.getItem(),
                stack.isEmpty(),
                player.isShiftKeyDown(),
                player.getName().getString());
    }

    /** 包裹返回值：把交互结果打印出来。 */
    public static InteractionResult exit(String what, InteractionResult result) {
        if (ON) {
            LogManager.getLogger("NCPB/diag").info("[NCPB/diag] {} 返回：{}", what, result);
        }
        return result;
    }

    /** {@code Item#use} 返回的是 {@code InteractionResultHolder}，单独一个重载。 */
    public static <T> net.minecraft.world.InteractionResultHolder<T> exit(
            String what, net.minecraft.world.InteractionResultHolder<T> result) {
        if (ON) {
            LogManager.getLogger("NCPB/diag").info("[NCPB/diag] {} 返回：{}", what, result);
        }
        return result;
    }

    /** 记录"要开界面了"这一步（区分"没走到"与"走到了但开界面失败"）。 */
    public static void opening(String what, boolean clientSide, Object screen) {
        if (ON) {
            LogManager.getLogger("NCPB/diag").info("[NCPB/diag] {} 准备开界面：clientSide={} screen={}",
                    what, clientSide, screen);
        }
    }

    /**
     * 给 VertexConsumer 套一个**计数代理**：只统计 {@code vertex(...)}/ {@code endVertex()} 被调用次数。
     * 用反射代理实现，省得手写十几个转发方法。链式调用里第一次 vertex 命中代理、后续命中真对象，
     * 所以计到的是"**发起了几个顶点**"，这对判"几何有没有被提交"足够。
     */
    public static net.minecraft.client.renderer.MultiBufferSource.BufferSource counting(
            net.minecraft.client.renderer.MultiBufferSource.BufferSource real, int[] counter) {
        return real;
    }

    public static com.mojang.blaze3d.vertex.VertexConsumer counting(
            final com.mojang.blaze3d.vertex.VertexConsumer real, final int[] counter) {
        return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                com.mojang.blaze3d.vertex.VertexConsumer.class.getClassLoader(),
                new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},
                (proxy, method, args) -> {
                    String n = method.getName();
                    if (n.equals("vertex") || n.equals("endVertex")) counter[0]++;
                    try {
                        return method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /**
     * <b>回读当前绑定的 FBO 像素</b>：这是"离屏到底画没画出内容"的**唯一判据**。
     * 采左上角一块（64×64）就够，避免每帧读整张 448×256。只在 {@code ON} 时做，并且节流。
     *
     * @return <b>不同颜色数</b>（{@code =1} 表示"只有清屏色"）；未开启或读取失败时返回 {@code -1}。
     *         返回值让调用方能把它和"本帧提交了多少顶点"对照 —— 那一比就是"画了但被裁掉"的判据
     *         （PORT-STATUS §6.25：提交了 508+456 个顶点、回读却只有 1 种颜色）。
     */
    public static int readbackFbo(String tag) {
        return readbackFbo(tag, 0, 0);
    }

    /**
     * 同上，但可指定采样原点（<b>GL 像素坐标</b>：原点在左下角）。用于“**内容有没有填满整张纹理**”的判据：
     * 左下角有内容、右上角只有清屏色 ⇒ 投影/缩放与 FBO 尺寸不匹配
     * （PORT-STATUS §6.26：FBO 是 SCALE× 的设备像素，而 GUI 用的是逻辑像素）。
     */
    public static int readbackFbo(String tag, int originX, int originY) {
        if (!ON) {
            return -1;
        }
        long now = System.nanoTime();
        if (!LAST_READBACK.containsKey(tag) || now - LAST_READBACK.get(tag) > 1_000_000_000L) {
            LAST_READBACK.put(tag, now);
        } else {
            return -1;
        }
        try {
            int w = 64;
            int h = 64;
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(w * h * 4);
            org.lwjgl.opengl.GL11.glReadPixels(originX, originY, w, h, org.lwjgl.opengl.GL11.GL_RGBA,
                    org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf);
            // 【能分辨的判据】只打"非零像素/maxRGB"是**分不出**"只有清屏色"和"清屏色+GUI"的
            // （有红底时两者都是非零、maxRGB=255）。所以这里统计**不同颜色数**：
            //   只有清屏 ⇒ 1 种颜色；画了东西 ⇒ ≥2 种。这才是"到底画没画"的判据。
            java.util.Map<Integer, Integer> hist = new java.util.HashMap<>();
            int nonZero = 0;
            for (int i = 0; i < w * h; i++) {
                int rgb = ((buf.get(i * 4) & 0xFF) << 16) | ((buf.get(i * 4 + 1) & 0xFF) << 8) | (buf.get(i * 4 + 2) & 0xFF);
                int a = buf.get(i * 4 + 3) & 0xFF;
                if ((rgb | a) != 0) {
                    nonZero++;
                }
                hist.merge((a << 24) | rgb, 1, Integer::sum);
            }
            int top = 0;
            int topKey = 0;
            for (java.util.Map.Entry<Integer, Integer> e : hist.entrySet()) {
                if (e.getValue() > top) {
                    top = e.getValue();
                    topKey = e.getKey();
                }
            }
            r("{} 回读FBO {}x{}: 不同颜色数={} 最常见色=#{}({:d}/{}像素) 非零={}",
                    tag, w, h, hist.size(), String.format("%08X", topKey), top, w * h, nonZero);
            return hist.size();

        } catch (Throwable t) {
            LogManager.getLogger("NCPB/diag").warn("[NCPB/diag] 回读失败 " + tag, t);
            return -1;
        }
    }

    private static final java.util.Map<String, Long> LAST_READBACK = new java.util.concurrent.ConcurrentHashMap<>();

    /** 通用探针：临时打到渲染路径上，用来判"哪一层断了"。 */
    public static void r(String what, Object... args) {
        if (ON) {
            LogManager.getLogger("NCPB/diag").info("[NCPB/diag] " + what, args);
        }
    }

    /**
     * <b>照全部的灯</b>：Forge 的 {@code RightClickBlock} 在**所有物品/方块逻辑之前**触发，
     * 而且能看到**被点的方块 id**。所以"点击到底有没有到方块""点到的是不是我们的方块"
     * 这两个问题一次就能回答 —— 之前只插方块自己的 {@code use()} 时，
     * "物品把交互吃掉"和"点到的不是我们的方块"在日志里长得一模一样（都是**什么都没有**）。
     *
     * <p>挂载方式与工程里其它 Forge 事件一致（{@code MinecraftForge.EVENT_BUS.addListener}），
     * 在 mod 构造期调用。单机（集成服务器）下两侧都会触发，正好能一起看。</p>
     */
    public static void register() {
        if (!ON) {
            return;
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(UseDiag::onRightClickBlock);
        LogManager.getLogger("NCPB/diag").info("[NCPB/diag] RightClickBlock 探针已挂载");
    }

    private static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        try {
            net.minecraft.world.item.ItemStack held = e.getEntity().getItemInHand(e.getHand());
            net.minecraft.world.level.block.state.BlockState st = e.getLevel().getBlockState(e.getPos());
            Object be = e.getLevel().getBlockEntity(e.getPos());
            LogManager.getLogger("NCPB/diag").info(
                    "[NCPB/diag] RightClickBlock：side={} hand={} held={} 空手={} 潜行={} 方块={} 方块类={} 方块实体={} pos={}",
                    e.getLevel().isClientSide() ? "CLIENT" : "SERVER",
                    e.getHand(),
                    held.isEmpty() ? "<空手>" : held.getItem(),
                    held.isEmpty(),
                    e.getEntity().isShiftKeyDown(),
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()),
                    st.getBlock().getClass().getSimpleName(),
                    be == null ? "<无>" : be.getClass().getSimpleName(),
                    e.getPos());
        } catch (Throwable t) {
            LogManager.getLogger("NCPB/diag").warn("[NCPB/diag] 探针自身出错", t);
        }
    }
}
