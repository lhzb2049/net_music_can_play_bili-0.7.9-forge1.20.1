package com.zhongbai233.net_music_can_play_bili.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zhongbai233.net_music_can_play_bili.block.VideoProjectorBlock;
import com.zhongbai233.net_music_can_play_bili.blockentity.BlockEntityRenderBounds;
import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.VideoProjectorBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.HolographicGlassesClient;
import com.zhongbai233.net_music_can_play_bili.client.ModernTurntableVideoClient;
import com.zhongbai233.net_music_can_play_bili.client.audio.ModernTurntablePlaybackTracker;
import com.zhongbai233.net_music_can_play_bili.client.media.ClientLocalImageProjection;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.VideoBillboardPreview;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.VideoBillboardState;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.VideoSurfacePrivacyPolicy;
import com.zhongbai233.net_music_can_play_bili.link.ClientLinkRegistry;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSessionId;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSync;
import com.zhongbai233.net_music_can_play_bili.port.shim.PortSubmitNodeCollector;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class VideoProjectorRenderer implements BlockEntityRenderer<VideoProjectorBlockEntity> {
   private static final ProjectorRenderProperties.VideoBounds RENDER_BOUNDS = ProjectorRenderProperties.videoBounds();

   public VideoProjectorRenderer(Context context) {
   }

   /**
    * <b>让投影仪成为"全局方块实体"</b>（第 99 次，配合 {@code VideoProjectorBlockEntity#getRenderBoundingBox}）。
    *
    * <p>这是用户实测症状"只要视野中没有视频投影仪这个方块，投影画面就消失"的正解：
    * 1.20.1 里**非全局**的方块实体是按"它所在的 16³ 截面有没有进视锥"被遍历的
    * （`ChunkRenderDispatcher$RenderChunk$RebuildTask` 反汇编：`renderer.shouldRenderOffScreen(be)`
    * 为真 → `globalBlockEntities`，否则 → 截面列表）。截面一走，渲染器就不被调用，
    * 而投影幕画在方块外面几格、这时明明还看得见。
    * 全局那类每帧都遍历，并用 **BE 自己的包围盒**判可见（即投影幕的包围盒）。</p>
    */
   @Override
   public boolean shouldRenderOffScreen(VideoProjectorBlockEntity blockEntity) {
      return true;
   }

   public void render(
      VideoProjectorBlockEntity projector, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay
   ) {
      // 【心跳】放在**最前面**（连距离门之前）：它要回答的是"**引擎还在调用这个渲染器吗**" ——
      // 这正是"投影幕在方块离开视野后是否消失"的判据（方块不在视野 ≠ 渲染器不该被调用）。
      com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.heartbeat(
         "video_projector BER", 5000L, "pos=" + projector.getBlockPos());
      // 变成全局方块实体之后，引擎不再按距离替我们筛（它只看 BE 的包围盒），
      // 所以这里自己保留原来的距离门（`getViewDistance()`，默认 64 格），免得远处的投影幕也在画。
      if (!this.shouldRender(projector, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())) {
         return;
      }

      VideoProjectorRenderer.State state = this.extract(projector);
      // 【状态探针】"投影仪启动了但没有画面"要能一眼看出断在哪一环：
      //    绑定 → 唱片机在播 → 会话 → 拿到帧 → 画出去。每环都写进一行（载荷变化才打，不刷屏）。
      if (com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.ON) {
         String payload = "visible=" + state.visible
            + " linked=" + state.linkedPos
            + " session=" + (state.playbackSessionId().isPresent() ? state.sessionId() : "无")
            + " frame=" + (state.frame != null && state.frame.hasFrame() ? state.frame.width() + "x" + state.frame.height() : "无")
            + " privacy=" + state.hideVideoForPrivacy;
         if (com.zhongbai233.net_music_can_play_bili.client.media.MediaLogThrottle
            .shouldLog("video-projector|" + state.projectorPos, payload)) {
            com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.r("video_projector {} 状态：{}", state.projectorPos, payload);
         }
      }

      this.submit(state, poseStack, new PortSubmitNodeCollector(bufferSource));
   }

   private VideoProjectorRenderer.State extract(VideoProjectorBlockEntity projector) {
      VideoProjectorRenderer.State state = new VideoProjectorRenderer.State();
      state.projectorPos = projector.getBlockPos().immutable();
      state.linkedPos = null;
      state.projectionYaw = projector.getProjectionYaw();
      state.projectionPitch = projector.getProjectionPitch();
      state.projectionScale = projector.getProjectionScale();
      state.projectionHeight = projector.getProjectionHeight();
      state.projectionDistanceX = projector.getProjectionDistanceX();
      state.projectionDistanceZ = projector.getProjectionDistanceZ();
      state.projectionBrightness = projector.getProjectionBrightness();
      state.frame = VideoBillboardState.ProjectorFrameSnapshot.empty();
      state.playbackSessionId = Optional.empty();
      state.hideVideoForPrivacy = VideoSurfacePrivacyPolicy.hideVideo(
         HolographicGlassesClient.shouldHideProjectorVideos(), VideoSurfacePrivacyPolicy.SurfaceKind.PUBLIC_PROJECTOR
      );
      state.visible = false;
      BlockPos linkedPos = projector.getLinkedTurntablePos();
      if (linkedPos != null && projector.getLevel() != null) {
         state.linkedPos = linkedPos.immutable();
         Level level = projector.getLevel();
         BlockEntity linkedBlockEntity = level.getBlockEntity(linkedPos);
         if (linkedBlockEntity instanceof LiveStreamerBlockEntity live) {
            ClientLinkRegistry.link(projector.getBlockPos(), linkedPos);
            state.visible = live.isPlaying();
            if (state.visible) {
               VideoBillboardPreview.attachProjectorToTurntable(linkedPos, projector.getBlockPos());
               String liveSessionId = ModernTurntablePlaybackTracker.currentSessionId(linkedPos);
               state.setPlaybackSessionId(PlaybackSessionId.parse(liveSessionId));
               state.frame = VideoBillboardPreview.currentProjectorDisplayFrame(projector.getBlockPos());
            } else {
               VideoBillboardPreview.stopIfProjector(projector.getBlockPos());
            }

            syncActivatedState(projector, state.visible);
            return state;
         } else if (!(linkedBlockEntity instanceof ModernTurntableBlockEntity turntable)) {
            ClientLinkRegistry.unlink(projector.getBlockPos());
            VideoBillboardPreview.stopIfProjector(projector.getBlockPos());
            projector.unlink();
            syncActivatedState(projector, false);
            return state;
         } else {
            ClientLinkRegistry.link(projector.getBlockPos(), linkedPos);
            // 本地图片：海报式静态画面，与唱片机是否在播放无关。同步只做查表 + TTL 判定，
            // 真正的纹理加载排在客户端线程队列里（避免在方块实体渲染过程中做 GL 上传）。
            ClientLocalImageProjection.syncFromTurntable(turntable, projector.getBlockPos());
            VideoBillboardState.ProjectorFrameSnapshot localImage = ClientLocalImageProjection.frameForConsumer(
               projector.getBlockPos()
            );
            state.visible = localImage != null || turntable.isPlaying();
            if (localImage != null) {
               state.frame = localImage;
            } else if (state.visible) {
               VideoBillboardPreview.attachProjectorToTurntable(linkedPos, projector.getBlockPos());
               PlaybackSync.Metadata sync = turntable.getPlaybackSyncMetadata();
               state.setPlaybackSessionId(sync.playbackSessionId());
               if (!sync.hasSession() || !VideoBillboardPreview.hasSessionForTurntable(linkedPos, sync.sessionId())) {
                  ModernTurntableVideoClient.syncFromTurntableForProjectorIfPossible(turntable, projector);
               }

               state.frame = VideoBillboardPreview.currentProjectorDisplayFrame(projector.getBlockPos());
            }

            if (!state.visible) {
               VideoBillboardPreview.stopIfProjector(projector.getBlockPos());
            }

            syncActivatedState(projector, state.visible);
            return state;
         }
      } else {
         ClientLinkRegistry.unlink(projector.getBlockPos());
         VideoBillboardPreview.stopIfProjector(projector.getBlockPos());
         syncActivatedState(projector, false);
         return state;
      }
   }

   private void submit(VideoProjectorRenderer.State state, PoseStack poseStack, PortSubmitNodeCollector collector) {
      try {
         if (!state.visible || state.linkedPos == null || state.projectorPos == null) {
            return;
         }

         if (!state.hideVideoForPrivacy && VideoBillboardPreview.isProjectorScreenPotentiallyVisible(state.projectorPos)) {
            state.playbackSessionId().ifPresent(sessionId -> VideoBillboardPreview.markProjectorSubmittedByBer(sessionId, state.projectorPos));
         }

         VideoBillboardState.ProjectorFrameSnapshot frame = state.frame;
         if (frame != null && frame.hasFrame() && frame.width() > 0 && frame.height() > 0) {
            float scale = Math.abs(state.projectionScale);
            float aspect = (float)frame.width() / frame.height();
            float halfHeight = 1.35F * scale * 0.5F;
            float halfWidth = halfHeight * aspect;
            poseStack.pushPose();
            poseStack.translate(0.5 + state.projectionDistanceX, state.projectionHeight, 0.5 + state.projectionDistanceZ);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.projectionYaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(-state.projectionPitch));
            Matrix4f screenPose = new Matrix4f(poseStack.last().pose());
            if (state.playbackSessionId().isPresent() && !state.hideVideoForPrivacy) {
               VideoBillboardPreview.captureProjectorImmediatePose(
                  state.playbackSessionId().orElseThrow(), state.projectorPos, screenPose, halfHeight, 1.0F, state.projectionBrightness
               );
            }

            if (state.hideVideoForPrivacy) {
               VideoBillboardPreview.submitProjectorPrivacyOverlayOnPose(collector, poseStack, halfWidth, halfHeight);
            } else {
               boolean drawn = VideoBillboardPreview.submitProjectorFrameOnPose(
                  collector,
                  poseStack,
                  frame,
                  halfWidth,
                  halfHeight,
                  VideoBillboardPreview.cameraRelativeBackOffset(screenPose, frame.rgbaDepthOffset()),
                  1.0F,
                  state.projectionBrightness
               );
               // 【绘制探针】"拿到帧却没画面"就把最后一环拆开：帧是 YUV 还是 RGBA、YUV 纹理齐不齐、
               //    自定义 YUV 着色器可不可用、到底画出去没有（`submit…OnPose` 自己会返回 false）。
               if (com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.ON) {
                  String payload = "yuv=" + frame.yuv()
                     + " rgbaTex=" + (frame.rgbaTexture() != null)
                     + " yuvTex=" + (frame.yTexture() != null) + "/" + (frame.uTexture() != null) + "/" + (frame.vTexture() != null)
                     + " yuvShader=" + VideoBillboardPreview.isCustomYuvShaderAvailable()
                     + " drawn=" + drawn
                     + " halfSize=" + halfWidth + "x" + halfHeight
                     + " yaw=" + state.projectionYaw + " pitch=" + state.projectionPitch + " scale=" + state.projectionScale
                     + " height=" + state.projectionHeight;
                  if (com.zhongbai233.net_music_can_play_bili.client.media.MediaLogThrottle
                     .shouldLog("video-projector-draw|" + state.projectorPos, payload)) {
                     com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.r("video_projector {} 绘制：{}", state.projectorPos, payload);
                  }
               }
            }

            poseStack.popPose();
            return;
         }
      } finally {
         collector.end();
      }
   }

   public AABB getRenderBoundingBox(VideoProjectorBlockEntity blockEntity) {
      VideoBillboardState.ProjectorFrameSnapshot localImage = ClientLocalImageProjection.frameForConsumer(
         blockEntity.getBlockPos()
      );
      VideoBillboardState.ProjectorFrameSnapshot frame = localImage != null
         ? localImage
         : VideoBillboardPreview.currentProjectorDisplayFrame(blockEntity.getBlockPos());
      double aspect = frame.width() > 0 && frame.height() > 0 ? (double)frame.width() / frame.height() : 1.7777777777777777;
      // 与 BE 侧共用同一份几何（`BlockEntityRenderBounds`），这里传入**真实画面宽高比**；
      // 钳制规则与余量也由那份实现统一处理（`clampVideoAspect` / `videoMargin`）。
      return BlockEntityRenderBounds.videoProjector(
         blockEntity.getBlockPos(),
         blockEntity.getProjectionDistanceX(),
         blockEntity.getProjectionHeight(),
         blockEntity.getProjectionDistanceZ(),
         blockEntity.getProjectionYaw(),
         blockEntity.getProjectionPitch(),
         blockEntity.getProjectionScale(),
         aspect
      );
   }

   public boolean shouldRender(VideoProjectorBlockEntity blockEntity, Vec3 cameraPos) {
      double viewDistance = this.getViewDistance();
      // 距离口径也走公共实现（与 BE 侧的包围盒同源，避免两套几何算出不同结果）
      return BlockEntityRenderBounds.distanceToSqr(this.getRenderBoundingBox(blockEntity), cameraPos) < viewDistance * viewDistance;
   }

   private static void syncActivatedState(VideoProjectorBlockEntity projector, boolean visible) {
      Level level = projector.getLevel();
      if (level != null && level == Minecraft.getInstance().level) {
         BlockPos pos = projector.getBlockPos();
         BlockState currentState = level.getBlockState(pos);
         if (currentState.hasProperty(VideoProjectorBlock.ACTIVATED)) {
            boolean currentlyActivated = (Boolean)currentState.getValue(VideoProjectorBlock.ACTIVATED);
            if (visible != currentlyActivated) {
               Minecraft.getInstance().execute(() -> {
                  Level lvl = projector.getLevel();
                  if (lvl != null) {
                     BlockState bs = lvl.getBlockState(pos);
                     if (bs.hasProperty(VideoProjectorBlock.ACTIVATED)) {
                        lvl.setBlock(pos, (BlockState)bs.setValue(VideoProjectorBlock.ACTIVATED, visible), 3);
                     }
                  }
               });
            }
         }
      }
   }

   public static class State {
      public boolean visible;
      public BlockPos projectorPos;
      public BlockPos linkedPos;
      public float projectionYaw;
      public float projectionPitch;
      public float projectionScale;
      public float projectionHeight;
      public float projectionDistanceX;
      public float projectionDistanceZ;
      public float projectionBrightness = 1.0F;
      public boolean hideVideoForPrivacy;
      private Optional<PlaybackSessionId> playbackSessionId = Optional.empty();
      public VideoBillboardState.ProjectorFrameSnapshot frame = VideoBillboardState.ProjectorFrameSnapshot.empty();

      public Optional<PlaybackSessionId> playbackSessionId() {
         return this.playbackSessionId;
      }

      void setPlaybackSessionId(Optional<PlaybackSessionId> playbackSessionId) {
         this.playbackSessionId = playbackSessionId;
      }

      public String sessionId() {
         return this.playbackSessionId.<String>map(session -> session.value()).orElse("");
      }
   }
}
