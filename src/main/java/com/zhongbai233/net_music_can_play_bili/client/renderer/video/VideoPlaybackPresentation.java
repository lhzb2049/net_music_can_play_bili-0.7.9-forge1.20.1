package com.zhongbai233.net_music_can_play_bili.client.renderer.video;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.blockentity.VideoProjectorBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.HolographicGlassesClient;
import com.zhongbai233.net_music_can_play_bili.client.renderer.ControlConsoleRenderer;
import com.zhongbai233.net_music_can_play_bili.port.shim.PortSubmitNodeCollector;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;

/**
 * 视频会话的"表现层"：把解码出来的帧交给世界里的投影仪/中控台/全息眼镜，并据"有没有可见消费者"
 * 决定要不要继续解码（{@code markVisibility}）。
 *
 * <h2>本文件的来历（PORT-STATUS §6.29，务必先读）</h2>
 *
 * <p>移植早期为了把编译错误降到 0，这个类被写成了一个**最小桩**（所有方法返回
 * {@code ProjectorFrameSnapshot.empty()}、{@code submit} 什么都不做，62 行）。
 * 45 行代码的下场是：**投影仪能激活、能拿会话，却永远拿不到真帧** ——
 * 状态探针里那个 {@code frame=320x180} 其实是 {@link VideoPlaceholderFrames} 的
 * **LOADING 占位图**（那张图正好 320×180），而真正的视频帧被桩吃掉了。</p>
 *
 * <p>这里按上游 `0.7.9-beta+neo1.21` 的同一份实现**逐行搬回**，只做一处适配：
 * {@code net.neoforged.neoforge.client.event.RenderLevelStageEvent} →
 * {@code net.minecraftforge.client.event.RenderLevelStageEvent}（1.20.1/Forge 的同一个事件）。
 * 搬之前已逐项核对：本工程 {@code VideoPlaybackInstance} / {@code VideoBillboardPreview}
 * 需要的字段与方法**一个都不缺**（含 {@code textures.snapshot/hasYuvTexture/rgbaTextureId}、
 * {@code consumers.projectors/containsProjector}、{@code anchor.isForTurntable/timeline}、
 * {@code VideoBerConsumerVisibilityPolicy} / {@code VideoRestartSuppressionPolicy}）。</p>
 */
final class VideoPlaybackPresentation {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final VideoPipelineProperties.Offscreen OFFSCREEN = VideoPipelineProperties.offscreen();
   private static final long RESUME_RESTART_LAG_NANOS = OFFSCREEN.resumeRestartLagMillis() * 1000000L;
   private static final long STABILIZATION_MILLIS = VideoPipelineProperties.timing().decoderStabilizationMillis();
   private final VideoPlaybackInstance owner;

   VideoPlaybackPresentation(VideoPlaybackInstance owner) {
      this.owner = owner;
   }

   VideoBillboardState.ProjectorFrameSnapshot frameSnapshot(BlockPos projectorPos) {
      if (!this.owns(projectorPos)) {
         return VideoBillboardState.ProjectorFrameSnapshot.empty();
      } else if (this.owner.terminalFailure && VideoPlaceholderFrames.NETWORK_ERROR_ENABLED) {
         return this.placeholder(VideoPlaceholderFrames.Kind.NETWORK_ERROR);
      } else {
         return this.owner.hasFrame ? this.current() : VideoBillboardState.ProjectorFrameSnapshot.empty();
      }
   }

   VideoBillboardState.ProjectorFrameSnapshot displayFrameSnapshot(BlockPos projectorPos) {
      if (!this.owns(projectorPos)) {
         return VideoBillboardState.ProjectorFrameSnapshot.empty();
      } else {
         boolean placeholders = VideoPipelineProperties.loadingPlaceholderEnabled();
         if (this.owner.terminalFailure && VideoPlaceholderFrames.NETWORK_ERROR_ENABLED) {
            return this.placeholder(VideoPlaceholderFrames.Kind.NETWORK_ERROR);
         } else {
            boolean irisWarning = this.shouldShowIrisWarning();
            if (irisWarning && placeholders) {
               return this.placeholder(VideoPlaceholderFrames.Kind.IRIS_WARNING);
            } else if (this.owner.hasFrame) {
               return irisWarning ? VideoBillboardState.ProjectorFrameSnapshot.empty() : this.current();
            } else {
               return placeholders ? this.placeholder(VideoPlaceholderFrames.Kind.LOADING) : VideoBillboardState.ProjectorFrameSnapshot.empty();
            }
         }
      }
   }

   VideoBillboardState.ProjectorFrameSnapshot realFrameSnapshot(BlockPos projectorPos) {
      return this.owns(projectorPos) && this.owner.hasFrame ? this.current() : VideoBillboardState.ProjectorFrameSnapshot.empty();
   }

   VideoBillboardState.ProjectorFrameSnapshot failurePlaceholderSnapshot() {
      return this.placeholder(VideoPlaceholderFrames.Kind.NETWORK_ERROR);
   }

   VideoBillboardState.ProjectorFrameSnapshot turntableFrameSnapshot(BlockPos turntablePos) {
      if (turntablePos == null || !this.owner.anchor.isForTurntable(turntablePos)) {
         return VideoBillboardState.ProjectorFrameSnapshot.empty();
      } else if (this.owner.terminalFailure && VideoPlaceholderFrames.NETWORK_ERROR_ENABLED) {
         return this.placeholder(VideoPlaceholderFrames.Kind.NETWORK_ERROR);
      } else {
         return this.owner.hasFrame ? this.current() : VideoBillboardState.ProjectorFrameSnapshot.empty();
      }
   }

   VideoBillboardState.ProjectorFrameSnapshot previewFrameSnapshot() {
      if (this.owner.terminalFailure && VideoPlaceholderFrames.NETWORK_ERROR_ENABLED) {
         return this.placeholder(VideoPlaceholderFrames.Kind.NETWORK_ERROR);
      } else {
         return this.owner.hasFrame ? this.current() : VideoBillboardState.ProjectorFrameSnapshot.empty();
      }
   }

   private boolean owns(BlockPos projectorPos) {
      return projectorPos == null || this.owner.consumers.containsProjector(projectorPos);
   }

   private VideoBillboardState.ProjectorFrameSnapshot placeholder(VideoPlaceholderFrames.Kind kind) {
      return VideoPlaceholderFrames.snapshot(kind, this.owner.startNanoTime);
   }

   private VideoBillboardState.ProjectorFrameSnapshot current() {
      return this.owner.textures.snapshot(this.owner.targetWidth, this.owner.targetHeight);
   }

   void submit(PortSubmitNodeCollector collector, Minecraft minecraft, Camera camera) {
      boolean renderable = false;
      boolean prewarm = false;
      List<BlockPos> projectorPositions = this.owner.consumers.projectors();
      List<VideoProjectorBlockEntity> renderableProjectors = new ArrayList<>();

      for (BlockPos pos : projectorPositions) {
         boolean berManaged = VideoBillboardPreview.isProjectorRenderedByBer(pos);
         boolean submittedByBer = VideoBillboardPreview.wasProjectorRecentlySubmittedByBer(this.owner.sessionId(), pos);
         VideoProjectorBlockEntity projectorOrNull = minecraft.level.getBlockEntity(pos) instanceof VideoProjectorBlockEntity projector
            ? projector : null;
         if (VideoBerConsumerVisibilityPolicy.usesBerSubmission(berManaged, submittedByBer)) {
            // ⚠️ 第 99 次的修复：**解码的存活不能只看"BER 这一帧提交了吗"**。
            //    `BER_SUBMITTED_PROJECTORS` 的容忍窗口只有 **1 帧**，而 BER 只在
            //    **它自己的渲染包围盒进视锥**时才会被调用 ⇒ 盒一旦先于画面离开视锥（或 BER 因任何原因
            //    这一帧没跑到），这里就翻 false ⇒ `markVisibility(false)` ⇒ 暂停解码
            //    ⇒ 用户看到的是"视线还没完全移开，视频就没了"（转回来还要重定位/加载图）。
            //    所以补一次**视线判定**作为兜底：它只决定"要不要继续解码"，
            //    **不改变谁来画**（仍由 BER 提交，不加入 renderableProjectors，避免重复绘制）。
            boolean viewFallback = !submittedByBer
               && projectorOrNull != null
               && VideoBillboardPreview.isProjectorScreenRenderable(
                  minecraft, camera, projectorOrNull, VideoBillboardPreview.viewDotThreshold()
               );
            boolean live = submittedByBer || viewFallback;
            renderable |= live;
            boolean predicted = ControlConsoleRenderer.isPredictivePrewarmActive(pos)
               || projectorOrNull != null && isPredictedProjectorVisibleSoon(minecraft, projectorOrNull);
            prewarm |= live || predicted;
            if (viewFallback) {
               // ⚠️⚠️ 1.20.1 的**截面剔除**（第 99 次实测确认）：非"全局方块实体"的 BER
               //     是按"它所在的 16³ 截面有没有进视锥"被遍历的 ⇒ **方块一旦离开视野，
               //     BE 根本不会被调用**，而投影幕画在方块**外面几格**、此时明明还看得见
               //     ⇒ 用户看到的就是"方块不在视野里，视频就消失"。
               //     上游（1.21/26.x）没有这个问题，因为那边的 BE 路径用 AABB 判可见性。
               //     这里的做法是：**BER 这一帧没画 ⇒ 由会话补画一次**（二者互补，不会重叠：
               //     只在 `!submittedByBer` 时才补）。
               renderableProjectors.add(projectorOrNull);
            }
            this.diagVisibility("ber", pos, berManaged, submittedByBer, live, prewarm, projectorOrNull);
         } else if (projectorOrNull == null) {
            prewarm |= ControlConsoleRenderer.isPredictivePrewarmActive(pos);
         } else {
            boolean projectorRenderable = VideoBillboardPreview.isProjectorScreenRenderable(
               minecraft, camera, projectorOrNull, VideoBillboardPreview.viewDotThreshold()
            );
            boolean projectorPrewarm = projectorRenderable || isPredictedProjectorVisibleSoon(minecraft, projectorOrNull);
            renderable |= projectorRenderable;
            prewarm |= projectorPrewarm;
            if (projectorRenderable) {
               renderableProjectors.add(projectorOrNull);
            }

            this.diagVisibility("view", pos, berManaged, submittedByBer, projectorRenderable, projectorPrewarm, projectorOrNull);
         }
      }

      boolean holographicVisible = this.owner.hasHolographicTurntableConsumer();
      this.markVisibility(renderable || holographicVisible, prewarm || holographicVisible);
      this.owner.pumpUploadOnRenderThread();

      for (VideoProjectorBlockEntity projector : renderableProjectors) {
         this.submitProjector(collector, minecraft, camera, projector);
      }
   }

   private void submitProjector(PortSubmitNodeCollector collector, Minecraft minecraft, Camera camera, VideoProjectorBlockEntity projector) {
      if (HolographicGlassesClient.shouldHideProjectorVideos()) {
         VideoBillboardPreview.submitProjectorPrivacyOverlay(collector, minecraft, camera, projector);
      } else if (this.owner.networkFailure && VideoPlaceholderFrames.NETWORK_ERROR_ENABLED) {
         VideoBillboardPreview.submitProjectorEmissiveGeometry(
            collector, minecraft, camera, projector, this.placeholderTexture(VideoPlaceholderFrames.Kind.NETWORK_ERROR), 320, 180
         );
      } else if (this.owner.hasFrame && this.owner.textures.hasRgbaTexture()) {
         VideoBillboardPreview.submitProjectorGeometry(
            collector, minecraft, camera, projector, this.owner.textures.rgbaTextureId(), this.owner.targetWidth, this.owner.targetHeight
         );
      } else if (this.owner.hasFrame
         && this.owner.textures.hasYuvTexture()
         && VideoBillboardPreview.isCustomYuvShaderAvailable()
         && !VideoBillboardPreview.shouldDrawYuvImmediateWithIris()) {
         VideoBillboardPreview.submitProjectorYuvGeometry(collector, minecraft, camera, projector, this.owner.textures.yuvTextureSet());
      } else if (VideoPipelineProperties.loadingPlaceholderEnabled()) {
         this.submitLoadingPlaceholder(collector, minecraft, camera, projector);
      }
   }

   private void submitLoadingPlaceholder(PortSubmitNodeCollector collector, Minecraft minecraft, Camera camera, VideoProjectorBlockEntity projector) {
      VideoPlaceholderFrames.Kind kind = this.shouldShowIrisWarning() ? VideoPlaceholderFrames.Kind.IRIS_WARNING : VideoPlaceholderFrames.Kind.LOADING;
      if (kind == VideoPlaceholderFrames.Kind.IRIS_WARNING && VideoPlaceholderFrames.IRIS_VIEW_DEPTH_OFFSET > 0.0) {
         VideoBillboardPreview.submitProjectorViewDepthOffsetGeometry(
            collector, minecraft, camera, projector, this.placeholderTexture(kind), 320, 180, VideoPlaceholderFrames.IRIS_VIEW_DEPTH_OFFSET
         );
      } else {
         VideoBillboardPreview.submitProjectorEmissiveGeometry(collector, minecraft, camera, projector, this.placeholderTexture(kind), 320, 180);
      }
   }

   private void markVisibility(boolean renderable, boolean prewarm) {
      long nowNs = System.nanoTime();
      this.owner.prewarmVisible = prewarm;
      if (renderable || prewarm) {
         this.owner.grantDecodeAdmission();
         long offscreenSince = this.owner.offscreenSinceNanoTime;
         this.owner.lastVisibleNanoTime = nowNs;
         this.owner.offscreenSinceNanoTime = 0L;
         if (offscreenSince > 0L) {
            this.owner.resetFirstFrameWatchdogAfterVisibilityResume(nowNs);
            this.maybeRestartForVisibleResume(nowNs - offscreenSince);
         }

         this.owner.loggedOffscreenPause = false;
      } else if (this.owner.offscreenSinceNanoTime == 0L) {
         this.owner.offscreenSinceNanoTime = nowNs;
      }
   }

   private void maybeRestartForVisibleResume(long offscreenDurationNs) {
      if (this.owner.running && this.restartAllowed() && RESUME_RESTART_LAG_NANOS > 0L) {
         long masterMillis = this.owner.anchor.timeline().mediaMillis();
         if (masterMillis >= 0L) {
            long bestVideoMillis = Math.max(this.owner.queuedMediaMillis(), this.owner.mediaMillis());
            long lagNs = bestVideoMillis >= 0L ? (masterMillis - bestVideoMillis) * 1000000L : offscreenDurationNs;
            if (lagNs >= RESUME_RESTART_LAG_NANOS) {
               long restartOffsetMillis = this.owner.totalMillis > 0L ? Math.min(this.owner.totalMillis, masterMillis) : masterMillis;
               LOGGER.debug(
                  "视频会话离屏恢复重定位: session={}, offscreen={}ms, master={}ms, video={}ms, offset={}ms",
                  new Object[]{this.owner.sessionId(), offscreenDurationNs / 1000000L, masterMillis, bestVideoMillis, restartOffsetMillis}
               );
               this.owner.restartDecoder(this.owner.targetWidth, this.owner.targetHeight, restartOffsetMillis, true);
            }
         }
      }
   }

   boolean restartAllowed() {
      long generationStart = this.owner.decoderGenerationStartedNanoTime;
      long sinceStartMillis = generationStart > 0L ? Math.max(0L, (System.nanoTime() - generationStart) / 1000000L) : 0L;
      return VideoRestartSuppressionPolicy.allowsRestart(this.owner.liveSource, this.owner.restartInProgress, sinceStartMillis, STABILIZATION_MILLIS);
   }

   void renderYuvImmediate(RenderLevelStageEvent event, String route) {
      if ((!this.owner.networkFailure || !VideoPlaceholderFrames.NETWORK_ERROR_ENABLED)
         && this.owner.hasFrame
         && this.owner.textures.hasYuvTexture()
         && VideoBillboardPreview.isCustomYuvShaderAvailable()
         && VideoBillboardPreview.shouldDrawYuvImmediateWithIris()) {
         this.owner.pumpUploadOnRenderThread();
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.level != null && minecraft.player != null) {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            boolean drew = false;
            boolean prewarm = false;

            for (BlockPos pos : this.owner.consumers.projectors()) {
               if (VideoBillboardPreview.drawCapturedProjectorYuvImmediate(event, this.owner.sessionId(), pos, this.owner.textures.yuvTextureSet(), route)) {
                  drew = true;
                  prewarm = true;
               } else if (minecraft.level.getBlockEntity(pos) instanceof VideoProjectorBlockEntity projector) {
                  prewarm |= isPredictedProjectorVisibleSoon(minecraft, projector);
                  if (HolographicGlassesClient.shouldHideProjectorVideos()) {
                     VideoBillboardPreview.drawProjectorPrivacyOverlayImmediate(event, minecraft, camera, projector, route);
                     drew = true;
                  } else {
                     drew |= VideoBillboardPreview.drawProjectorYuvImmediate(event, minecraft, camera, projector, this.owner.textures.yuvTextureSet(), route);
                  }
               } else {
                  prewarm |= ControlConsoleRenderer.isPredictivePrewarmActive(pos);
               }
            }

            this.markVisibility(drew, prewarm);
            if (drew && !this.owner.firstYuvImmediateLogged) {
               this.owner.firstYuvImmediateLogged = true;
               LOGGER.debug(
                  "Iris/YUV: session={} 的投影仪 YUV 使用实例纹理 immediate 绘制，route={}, texture={}x{}",
                  new Object[]{this.owner.sessionId(), route, this.owner.textures.yuvTextureSet().width(), this.owner.textures.yuvTextureSet().height()}
               );
            }
         }
      }
   }

   private static boolean isPredictedProjectorVisibleSoon(Minecraft minecraft, VideoProjectorBlockEntity projector) {
      return minecraft != null && VideoBillboardPreview.isProjectorScreenPredictedVisible(minecraft, minecraft.gameRenderer.getMainCamera(), projector);
   }

   /**
    * <b>可见性判定的状态探针</b>（第 98 次加，用于“视频在视线未完全移开时就消失”）。
    *
    * <p>把每一道子门都打出来：走的是 BER 路线还是会话里的视线路线、
    * 距离门 / 视线门（点积）/ 遮挡门各自的结果。载荷变化才打（`MediaLogThrottle`）。</p>
    */
   private void diagVisibility(
      String route, BlockPos pos, boolean berManaged, boolean submittedByBer, boolean renderable, boolean prewarm, VideoProjectorBlockEntity projector
   ) {
      if (!com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.ON) {
         return;
      }

      StringBuilder payload = new StringBuilder();
      payload.append("route=").append(route)
         .append(" berManaged=").append(berManaged)
         .append(" submittedByBer=").append(submittedByBer)
         .append(" renderable=").append(renderable)
         .append(" prewarm=").append(prewarm);
      if (projector != null) {
         Minecraft minecraft = Minecraft.getInstance();
         Camera camera = minecraft.gameRenderer.getMainCamera();
         double centerX = pos.getX() + 0.5 + projector.getProjectionDistanceX();
         double centerY = pos.getY() + projector.getProjectionHeight();
         double centerZ = pos.getZ() + 0.5 + projector.getProjectionDistanceZ();
         double aspect = 1.7777777777777777;
         payload.append(" distOk=").append(
            VideoBillboardPreview.isProjectorWithinRenderDistance(camera.getPosition(), projector, centerX, centerY, centerZ, aspect)
         );
         payload.append(" dotOk=").append(
            VideoBillboardPreview.isScreenInView(camera, centerX, centerY, centerZ, VideoBillboardPreview.viewDotThreshold())
         );
         payload.append(" occluded=").append(
            VideoBillboardPreview.isOccluded(minecraft, camera.getPosition(), new net.minecraft.world.phys.Vec3(centerX, centerY, centerZ), pos)
         );
         payload.append(" dotThreshold=").append(VideoBillboardPreview.viewDotThreshold());
      }

      String text = payload.toString();
      if (com.zhongbai233.net_music_can_play_bili.client.media.MediaLogThrottle
         .shouldLog("video-visibility|" + pos, text)) {
         com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.r("video_projector {} 可见性：{}", pos, text);
      }
   }

   private ResourceLocation placeholderTexture(VideoPlaceholderFrames.Kind kind) {
      return VideoPlaceholderFrames.texture(kind, this.owner.startNanoTime);
   }

   private boolean shouldShowIrisWarning() {
      return this.owner.hasFrame
         && this.owner.textures.hasYuvTexture()
         && VideoBillboardPreview.isCustomYuvShaderAvailable()
         && IrisShaderpackCompat.shouldApplyIrisYuvCompatibility();
   }

   boolean isWithinAudioRange(Minecraft minecraft) {
      return minecraft.player != null
         && this.owner.hasVideoConsumer()
         && this.owner.anchor.isWithinAudioRange(minecraft, this.owner.consumers.projectors(), VideoBillboardPreview.AUDIO_SYNC_RANGE_SQR);
   }
}
