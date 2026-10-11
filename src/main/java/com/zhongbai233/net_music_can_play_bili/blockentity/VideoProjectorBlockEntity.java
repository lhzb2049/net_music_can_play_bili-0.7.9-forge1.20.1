package com.zhongbai233.net_music_can_play_bili.blockentity;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.client.ModernTurntableVideoClient;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.VideoBillboardPreview;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import com.zhongbai233.net_music_can_play_bili.link.ClientLinkRegistry;
import com.zhongbai233.net_music_can_play_bili.link.LinkHelper;
import com.zhongbai233.net_music_can_play_bili.media.VideoSurfaceBrightness;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class VideoProjectorBlockEntity extends SyncedBlockEntity {
   private static final String LINK_KEY = "LinkedTarget";
   private static final String PROJ_YAW = "ProjYaw";
   private static final String PROJ_PITCH = "ProjPitch";
   private static final String PROJ_SCALE = "ProjScale";
   private static final String PROJ_HEIGHT = "ProjHeight";
   private static final String PROJ_DISTANCE_X = "ProjDistanceX";
   private static final String PROJ_DISTANCE_Z = "ProjDistanceZ";
   private static final String PROJ_BRIGHTNESS = "ProjBrightness";
   private static final String PREFERRED_QUALITY = "PreferredQuality";
   public static final int DEFAULT_PREFERRED_QUALITY = 116;
   @Nullable
   private BlockPos linkedTurntablePos;
   private float projectionYaw = 180.0F;
   private float projectionPitch = 0.0F;
   private float projectionScale = 1.0F;
   private float projectionHeight = 1.8F;
   private float projectionDistanceX = 0.0F;
   private float projectionDistanceZ = 0.0F;
   private float projectionBrightness = 1.0F;
   private int preferredQuality = 116;

   public VideoProjectorBlockEntity(BlockPos pos, BlockState blockState) {
      super((BlockEntityType<?>)ModBlockEntities.VIDEO_PROJECTOR.get(), pos, blockState);
   }

   /**
    * <b>投影幕的包围盒</b>（第 99 次，配合 {@code VideoProjectorRenderer#shouldRenderOffScreen} 一起用）。
    *
    * <h2>为什么必须有它</h2>
    *
    * <p>1.20.1 的方块实体渲染分两类（`ChunkRenderDispatcher$RenderChunk$RebuildTask` 反汇编实证）：</p>
    * <pre>
    *   renderer = dispatcher.getRenderer(be)
    *   if (renderer != null &amp;&amp; renderer.shouldRenderOffScreen(be)) → globalBlockEntities
    *   else                                                          → blockEntities（跟着 16³ 截面走）
    * </pre>
    *
    * <p>而**非全局**那类是按"它所在的 16³ 截面有没有进视锥"被遍历的 —— 截面一旦出视锥，
    * 渲染器**根本不会被调用**。投影幕画在方块**外面几格**、这时明明还看得见，却被一起剔掉
    * （用户实测："只要视野中没有视频投影仪这个方块，视频就会消失"）。</p>
    *
    * <p>全局那类则每帧都被遍历，并用 **BE 自己的包围盒**做视锥判定
    * （`LevelRenderer` 里 `BlockEntity.getRenderBoundingBox()` + `Frustum.isVisible(AABB)`，
    *  Forge 的默认实现是"1 格"——`SignBlockEntity` 就是靠覆盖它来支持超大方块实体）。
    * 所以这里给出**紧贴投影幕**的保守包围盒：屏幕中心 ± (右方向×半宽 + 上方向×半高) 的
    * 各轴绝对值投影，再留 0.5 格余量。</p>
    *
    * <p>纯数学、不引用任何客户端类 ⇒ 专用服务器加载本类也不会碰到客户端代码。</p>
    */
   @Override
   public net.minecraft.world.phys.AABB getRenderBoundingBox() {
      return BlockEntityRenderBounds.videoProjector(this.worldPosition, this.projectionDistanceX,
         this.projectionHeight, this.projectionDistanceZ, this.projectionYaw, this.projectionPitch,
         this.projectionScale);
   }

   public void linkTo(BlockPos turntablePos) {
      if (this.level instanceof ServerLevel serverLevel) {
         AudioLinkIndex.unregisterVideoProjector(serverLevel, this.worldPosition);
      }

      this.linkedTurntablePos = turntablePos.immutable();
      if (this.level instanceof ServerLevel serverLevel) {
         AudioLinkIndex.registerVideoProjector(serverLevel, this.worldPosition, this.linkedTurntablePos);
      }

      this.refreshClientLinkRegistration();
      this.markDirtyAndSync();
   }

   public void unlink() {
      if (this.level != null && this.level.isClientSide()) {
         ClientLinkRegistry.unlink(this.worldPosition);
      }

      if (this.level instanceof ServerLevel serverLevel) {
         AudioLinkIndex.unregisterVideoProjector(serverLevel, this.worldPosition);
      }

      this.linkedTurntablePos = null;
      this.markDirtyAndSync();
   }

   @Nullable
   public BlockPos getLinkedTurntablePos() {
      return this.linkedTurntablePos;
   }

   public boolean isLinked() {
      return this.linkedTurntablePos != null;
   }

   public float getProjectionYaw() {
      return this.projectionYaw;
   }

   public void setProjectionYaw(float v) {
      this.projectionYaw = v;
      this.setChanged();
   }

   public float getProjectionPitch() {
      return this.projectionPitch;
   }

   public void setProjectionPitch(float v) {
      this.projectionPitch = v;
      this.setChanged();
   }

   public float getProjectionScale() {
      return this.projectionScale;
   }

   public void setProjectionScale(float v) {
      this.projectionScale = v;
      this.setChanged();
   }

   public float getProjectionHeight() {
      return this.projectionHeight;
   }

   public void setProjectionHeight(float v) {
      this.projectionHeight = v;
      this.setChanged();
   }

   public float getProjectionDistance() {
      return this.projectionDistanceX;
   }

   public void setProjectionDistance(float v) {
      this.setProjectionDistanceX(v);
   }

   public float getProjectionDistanceX() {
      return this.projectionDistanceX;
   }

   public void setProjectionDistanceX(float v) {
      this.projectionDistanceX = v;
      this.setChanged();
   }

   public float getProjectionDistanceZ() {
      return this.projectionDistanceZ;
   }

   public void setProjectionDistanceZ(float v) {
      this.projectionDistanceZ = v;
      this.setChanged();
   }

   public float getProjectionBrightness() {
      return this.projectionBrightness;
   }

   public void setProjectionBrightness(float value) {
      this.projectionBrightness = VideoSurfaceBrightness.normalize(value);
      this.setChanged();
   }

   public int getPreferredQuality() {
      return this.preferredQuality;
   }

   public void setPreferredQuality(int v) {
      this.preferredQuality = v;
      this.setChanged();
   }

   public void setRemoved() {
      super.setRemoved();
      if (this.level != null && this.level.isClientSide()) {
         ClientLinkRegistry.unlink(this.worldPosition);
         VideoBillboardPreview.stopIfProjector(this.worldPosition);
      }

      if (this.level instanceof ServerLevel serverLevel) {
         AudioLinkIndex.unregisterVideoProjector(serverLevel, this.worldPosition);
      }
   }

   @Override
   protected void saveAdditional(CompoundTag output) {
      super.saveAdditional(output);
      LinkHelper.saveLinkToBE(output, this.linkedTurntablePos, "LinkedTarget_has", "LinkedTarget_x", "LinkedTarget_y", "LinkedTarget_z");
      output.putFloat("ProjYaw", this.projectionYaw);
      output.putFloat("ProjPitch", this.projectionPitch);
      output.putFloat("ProjScale", this.projectionScale);
      output.putFloat("ProjHeight", this.projectionHeight);
      output.putFloat("ProjDistanceX", this.projectionDistanceX);
      output.putFloat("ProjDistanceZ", this.projectionDistanceZ);
      output.putFloat("ProjBrightness", this.projectionBrightness);
      output.putInt("PreferredQuality", this.preferredQuality);
   }

   @Override
   public void load(CompoundTag input) {
      super.load(input);
      BlockPos oldLinkedTurntablePos = this.linkedTurntablePos;
      int oldPreferredQuality = this.preferredQuality;
      this.linkedTurntablePos = LinkHelper.loadLinkFromBE(input, "LinkedTarget_has", "LinkedTarget_x", "LinkedTarget_y", "LinkedTarget_z");
      this.projectionYaw = LinkHelper.getFloatOr(input, "ProjYaw", 180.0F);
      this.projectionPitch = LinkHelper.getFloatOr(input, "ProjPitch", 0.0F);
      this.projectionScale = LinkHelper.getFloatOr(input, "ProjScale", 1.0F);
      this.projectionHeight = LinkHelper.getFloatOr(input, "ProjHeight", 1.8F);
      this.projectionDistanceX = LinkHelper.getFloatOr(input, "ProjDistanceX", 0.0F);
      this.projectionDistanceZ = LinkHelper.getFloatOr(input, "ProjDistanceZ", 0.0F);
      this.projectionBrightness = VideoSurfaceBrightness.normalize(LinkHelper.getFloatOr(input, "ProjBrightness", 1.0F));
      this.preferredQuality = LinkHelper.getIntOr(input, "PreferredQuality", 116);
      this.refreshClientLinkRegistration();
      if (this.level instanceof ServerLevel serverLevel && this.linkedTurntablePos != null) {
         AudioLinkIndex.registerVideoProjector(serverLevel, this.worldPosition, this.linkedTurntablePos);
      }

      if (this.level != null
         && this.level.isClientSide()
         && VideoProjectorClientRefreshPolicy.shouldRefresh(oldLinkedTurntablePos, this.linkedTurntablePos, oldPreferredQuality, this.preferredQuality)) {
         ModernTurntableVideoClient.refreshProjector(this.worldPosition);
      }
   }


   private void refreshClientLinkRegistration() {
      if (this.level != null && this.level.isClientSide()) {
         ClientLinkRegistry.unlink(this.worldPosition);
         if (this.linkedTurntablePos != null) {
            ClientLinkRegistry.link(this.worldPosition, this.linkedTurntablePos);
            LogUtils.getLogger().debug("视频投影仪客户端链接注册: projector={} target={}", this.worldPosition, this.linkedTurntablePos);
         }
      }
   }
}
