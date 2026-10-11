package com.zhongbai233.net_music_can_play_bili.client;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.VideoBillboardPreview;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

@EventBusSubscriber({Dist.CLIENT})
public final class VideoBenchTest {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final boolean BENCH_FEATURES_ENABLED = VideoFeatureFlags.benchFeaturesEnabled();
   private static final boolean REAL_BENCH_MANAGED = VideoFeatureProperties.realBenchManaged();
   private static final AtomicBoolean hasRun = new AtomicBoolean(false);
   private static final int TARGET_W = 854;
   private static final int TARGET_H = 480;
   private static final int TARGET_FPS = 30;
   private static final boolean CPU_BARS = VideoFeatureFlags.advancedBoolean("ncpb.video.cpu_bars", false);

   private VideoBenchTest() {
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      if (BENCH_FEATURES_ENABLED) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            if (REAL_BENCH_MANAGED || !BiliRealVideoPlaybackBench.tryStart()) {
               if (CPU_BARS) {
                  if (hasRun.compareAndSet(false, true)) {
                     LOGGER.info("ncpb.video.cpu_bars=true，跳过 B站解析/下载，直接启动 CPU 彩条渲染诊断");
                     VideoBillboardPreview.startTestPattern(854, 480, 30);
                  }
               }
            }
         }
      }
   }
}
