package com.zhongbai233.net_music_can_play_bili.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.zhongbai233.net_music_can_play_bili.media.VideoSurfaceBrightness;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class RenderVertexUtils {
   public static final int FULL_BRIGHT = 15728880;

   private RenderVertexUtils() {
   }

   public static void texturedVertex(VertexConsumer buffer, Pose pose, float x, float y, float z, float u, float v) {
      texturedVertex(buffer, pose, x, y, z, u, v, 1.0F);
   }

   public static void texturedVertex(VertexConsumer buffer, Pose pose, float x, float y, float z, float u, float v, float opacity) {
      texturedVertex(buffer, pose, x, y, z, u, v, opacity, 1.0F);
   }

   public static void texturedVertex(VertexConsumer buffer, Pose pose, float x, float y, float z, float u, float v, float opacity, float brightness) {
      buffer.vertex(pose.pose(), x, y, z)
         .color(VideoSurfaceBrightness.vertexColor(brightness, opacity))
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(15728880)
         .normal(pose.normal(), 0.0F, 0.0F, 1.0F);
   }
}
