package com.zhongbai233.net_music_can_play_bili.server;

import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;

@EventBusSubscriber
public final class ModernTurntableSpectatorSync {
   private static final int SYNC_INTERVAL_TICKS = 20;

   private ModernTurntableSpectatorSync() {
   }

   @SubscribeEvent
   public static void onServerTick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      MinecraftServer server = event.getServer();
      if (server != null && server.getTickCount() % 20 == 0) {
         for (ServerLevel level : server.getAllLevels()) {
            ModernTurntableBlockEntity.syncLoadedTurntablesToSpectators(level);
         }
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      ModernTurntableBlockEntity.clearLoadedServerTurntables();
   }
}
