package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.LyricProjectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record LyricProjectorConfigPacket(
   BlockPos pos, float yaw, float pitch, float scale, float height, float distanceX, float distanceZ, int mode, boolean allowAi
) {
   public static final String ID = "lyric_projector_config";
   public static LyricProjectorConfigPacket decode(FriendlyByteBuf buf) {
      return new LyricProjectorConfigPacket(
         (BlockPos)buf.readBlockPos(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Integer)buf.readInt(),
         (Boolean)buf.readBoolean()
      );
   }

   public static void encode(LyricProjectorConfigPacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeFloat(packet.yaw());
      buf.writeFloat(packet.pitch());
      buf.writeFloat(packet.scale());
      buf.writeFloat(packet.height());
      buf.writeFloat(packet.distanceX());
      buf.writeFloat(packet.distanceZ());
      buf.writeInt(packet.mode());
      buf.writeBoolean(packet.allowAi());
   }


   public static void handle(LyricProjectorConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null && player.level() instanceof ServerLevel level) {
         if (NetworkRateLimiter.allow(player.getUUID(), "lyric_projector_config", 8)) {
            if (!(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)) {
               if (level.getBlockEntity(payload.pos()) instanceof LyricProjectorBlockEntity be) {
                  be.setProjectionYaw(payload.yaw());
                  be.setProjectionPitch(payload.pitch());
                  be.setProjectionScale(payload.scale());
                  be.setProjectionHeight(payload.height());
                  be.setProjectionDistanceX(payload.distanceX());
                  be.setProjectionDistanceZ(payload.distanceZ());
                  be.setProjectionMode(payload.mode());
                  be.setAllowAi(payload.allowAi());
                  be.markDirtyAndSync();
               }
            }
         }
      }
   }
}
