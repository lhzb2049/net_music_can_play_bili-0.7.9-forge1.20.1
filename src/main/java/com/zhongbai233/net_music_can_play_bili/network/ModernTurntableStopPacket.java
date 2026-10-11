package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.audio.ModernTurntablePlaybackCoordinator;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSessionId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ModernTurntableStopPacket(BlockPos pos, String sessionId) {
   private static final int MAX_SESSION_ID_LENGTH = 128;
   public static final String ID = "modern_turntable_stop";
   public static ModernTurntableStopPacket decode(FriendlyByteBuf buffer) {
      return new ModernTurntableStopPacket((BlockPos)buffer.readBlockPos(), buffer.readUtf(128));
   }

   public static void encode(ModernTurntableStopPacket packet, FriendlyByteBuf buffer) {
      buffer.writeBlockPos(packet.pos());
      buffer.writeUtf(packet.sessionId(), 128);
   }

   public ModernTurntableStopPacket(BlockPos pos, String sessionId) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      sessionId = PlaybackSessionId.of(sessionId).value();
      this.pos = pos;
      this.sessionId = sessionId;
   }


   public static void handle(ModernTurntableStopPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ModernTurntablePlaybackCoordinator.stop(payload.pos(), payload.sessionId()));
   }
}
