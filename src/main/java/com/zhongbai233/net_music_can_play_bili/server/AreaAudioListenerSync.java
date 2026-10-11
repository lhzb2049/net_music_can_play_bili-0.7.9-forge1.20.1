package com.zhongbai233.net_music_can_play_bili.server;

import com.zhongbai233.net_music_can_play_bili.compat.areacontrol.AreaControlAudioCompat;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import com.zhongbai233.net_music_can_play_bili.network.AreaAudioListenerPacket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;
import com.zhongbai233.net_music_can_play_bili.network.NcpbNetwork;

public final class AreaAudioListenerSync {
   private static final Map<UUID, AreaAudioZone> LAST_SENT = new ConcurrentHashMap<>();

   private AreaAudioListenerSync() {
   }

   public static void onPlayerLoggedIn(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         sync(player, true);
      }
   }

   public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         LAST_SENT.remove(player.getUUID());
      }
   }

   public static void onServerTick(TickEvent.ServerTickEvent event) {

      if (event.phase != TickEvent.Phase.END) {

         return;

      }
      for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
         sync(player, false);
      }
   }

   public static void onServerStopping(ServerStoppingEvent event) {
      LAST_SENT.clear();
   }

   private static void sync(ServerPlayer player, boolean force) {
      AreaAudioZone zone = AreaControlAudioCompat.zoneAt((ServerLevel)player.level(), BlockPos.containing(player.position()));
      AreaAudioZone previous = LAST_SENT.put(player.getUUID(), zone);
      if (force || !zone.equals(previous)) {
         NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AreaAudioListenerPacket(zone));
      }
   }
}
