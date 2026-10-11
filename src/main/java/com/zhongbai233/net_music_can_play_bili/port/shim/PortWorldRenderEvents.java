package com.zhongbai233.net_music_can_play_bili.port.shim;

import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;

public final class PortWorldRenderEvents {
   private static final int SCRATCH_BUFFER_BYTES = 32768;
   private static final ThreadLocal<PortWorldRenderEvents.Scratch> SCRATCH = ThreadLocal.withInitial(PortWorldRenderEvents.Scratch::new);

   private PortWorldRenderEvents() {
   }

   public static PortSubmitNodeCollector begin() {
      PortWorldRenderEvents.Scratch scratch = SCRATCH.get();
      return new PortSubmitNodeCollector(scratch.source, true);
   }

   public static void end(PortSubmitNodeCollector collector) {
      if (collector != null) {
         collector.end();
         // 1.20.1 的 BufferBuilder 没有 discard()（1.21 才有）：
         // endBatch() 已经把这一批交付掉，缓冲留给下一帧复用。**登记为行为差异**。
      }
   }

   public static boolean isStage(RenderLevelStageEvent event, Stage stage) {
      return event != null && stage != null && event.getStage() == stage;
   }

   private static final class Scratch {
      // ⚠️ 第 99 次的修复：**必须按 RenderType 分配缓冲**。
      //    这里原来是 1.21 写法的直译 `MultiBufferSource.immediate(builder)` —— 而 1.20.1 的
      //    `immediate(...)` 传进去的 `fixedBuffers` 是**空表**，于是所有 RenderType 共用同一个
      //    `BufferBuilder`：切换类型时既不重新 begin、也不正确结算上一次，提交的几何要么被按错的
      //    格式解释、要么根本画不出来（§6.23 续 2 在 GUI 上抓过同一个坑）。
      //    症状正是"**只有 BER 画得出来**"：BER 用的是原版按类型分配的 bufferSource，
      //    而会话（世界渲染阶段）这条路用它 ⇒ 提交了也看不见。
      final PortGuiBufferSource source = new PortGuiBufferSource(false);

      Scratch() {
      }
   }
}
