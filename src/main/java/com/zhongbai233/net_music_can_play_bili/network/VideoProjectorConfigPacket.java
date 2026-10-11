package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.VideoProjectorBlockEntity;
import com.zhongbai233.net_music_can_play_bili.media.VideoSurfaceBrightness;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record VideoProjectorConfigPacket(
   BlockPos pos, float yaw, float pitch, float scale, float height, float distanceX, float distanceZ, float brightness, int preferredQuality
) {
   public static final String ID = "video_projector_config";
   public static VideoProjectorConfigPacket decode(FriendlyByteBuf buf) {
      return new VideoProjectorConfigPacket(
         (BlockPos)buf.readBlockPos(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Float)buf.readFloat(),
         (Integer)buf.readInt()
      );
   }

   public static void encode(VideoProjectorConfigPacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeFloat(packet.yaw());
      buf.writeFloat(packet.pitch());
      buf.writeFloat(packet.scale());
      buf.writeFloat(packet.height());
      buf.writeFloat(packet.distanceX());
      buf.writeFloat(packet.distanceZ());
      buf.writeFloat(packet.brightness());
      buf.writeInt(packet.preferredQuality());
   }

   public VideoProjectorConfigPacket(
      BlockPos pos, float yaw, float pitch, float scale, float height, float distanceX, float distanceZ, float brightness, int preferredQuality
   ) {
      pos = pos.immutable();
      brightness = VideoSurfaceBrightness.normalize(brightness);
      this.pos = pos;
      this.yaw = yaw;
      this.pitch = pitch;
      this.scale = scale;
      this.height = height;
      this.distanceX = distanceX;
      this.distanceZ = distanceZ;
      this.brightness = brightness;
      this.preferredQuality = preferredQuality;
   }


   public static void handle(VideoProjectorConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null && player.level() instanceof ServerLevel level) {
         if (NetworkRateLimiter.allow(player.getUUID(), "video_projector_config", 8)) {
            if (!(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)) {
               if (level.getBlockEntity(payload.pos()) instanceof VideoProjectorBlockEntity be) {
                  be.setProjectionYaw(payload.yaw());
                  be.setProjectionPitch(payload.pitch());
                  be.setProjectionScale(payload.scale());
                  be.setProjectionHeight(payload.height());
                  be.setProjectionDistanceX(payload.distanceX());
                  be.setProjectionDistanceZ(payload.distanceZ());
                  be.setProjectionBrightness(payload.brightness());
                  be.setPreferredQuality(payload.preferredQuality());
                  be.markDirtyAndSync();
               }
            }
         }
      }
   }
}
