package com.zhongbai233.net_music_can_play_bili.blockentity;

import com.github.tartaricacid.netmusic.api.lyric.LyricRecord;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModBlocks;
import com.zhongbai233.net_music_can_play_bili.link.ClientLinkRegistry;
import com.zhongbai233.net_music_can_play_bili.link.LinkHelper;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class LyricProjectorBlockEntity extends SyncedBlockEntity {
   private static final String LINK_KEY = "LinkedTarget";
   private static final String PROJ_YAW = "ProjYaw";
   private static final String PROJ_PITCH = "ProjPitch";
   private static final String PROJ_SCALE = "ProjScale";
   private static final String PROJ_HEIGHT = "ProjHeight";
   private static final String PROJ_DISTANCE_X = "ProjDistanceX";
   private static final String PROJ_DISTANCE_Z = "ProjDistanceZ";
   private static final String PROJ_MODE = "ProjMode";
   private static final String ALLOW_AI = "AllowAi";
   @Nullable
   private BlockPos linkedTurntablePos;
   private float projectionYaw = 180.0F;
   private float projectionPitch = 0.0F;
   private float projectionScale = 1.0F;
   private float projectionHeight = 1.2F;
   private float projectionDistanceX = 0.0F;
   private float projectionDistanceZ = 0.0F;
   private int projectionMode;
   private boolean allowAi;
   private transient LyricRecord cachedLyricRecord;
   private transient LyricRecord cachedAiLyricRecord;
   private transient String cachedAiRawUrl;
   private transient long cachedAiBaseTick = -1L;

   public LyricProjectorBlockEntity(BlockPos pos, BlockState blockState) {
      super((BlockEntityType<?>)ModBlockEntities.LYRIC_PROJECTOR.get(), pos, blockState);
   }

   /**
    * <b>字幕面片的渲染裁剪包围盒</b>（第 99 次，采纳参考移植 `ChihayaAnonQWQ/NetMusicCanPlayBili-1.20.1` 的做法）。
    *
    * <p>字幕面片和视频投影幕一样**画在方块外面几格**：不给 BE 这个钩子，1.20.1 就会按
    * "方块所在 16³ 截面是否在视锥里"决定要不要调用渲染器 ⇒ 方块一离开视野、字幕就整块消失
    * （视频投影仪上就是这个症状，见 §6.31.7）。几何公式与 `LyricProjectorRenderer` 共用
    * {@link BlockEntityRenderBounds#lyricProjector}，避免两边各写一份。</p>
    */
   @Override
   public net.minecraft.world.phys.AABB getRenderBoundingBox() {
      return BlockEntityRenderBounds.lyricProjector(this.worldPosition, this.projectionScale,
         this.projectionDistanceX, this.projectionHeight, this.projectionDistanceZ);
   }

   public void linkTo(BlockPos turntablePos) {
      this.linkedTurntablePos = turntablePos.immutable();
      this.syncClientLink();
      this.markDirtyAndSync();
   }

   public void unlink() {
      this.linkedTurntablePos = null;
      this.cachedLyricRecord = null;
      this.syncClientLink();
      this.markDirtyAndSync();
   }

   @Nullable
   public BlockPos getLinkedTurntablePos() {
      return this.linkedTurntablePos;
   }

   public boolean isLinked() {
      return this.linkedTurntablePos != null;
   }

   public void cacheLyricRecord(LyricRecord record) {
      this.cachedLyricRecord = record;
   }

   @Nullable
   public LyricRecord getCachedLyricRecord() {
      return this.cachedLyricRecord;
   }

   @Nullable
   public LyricRecord getCachedAiLyricRecord() {
      return this.cachedAiLyricRecord;
   }

   public void setCachedAiLyricRecord(@Nullable LyricRecord record, String rawUrl) {
      this.cachedAiLyricRecord = record;
      this.cachedAiRawUrl = rawUrl;
      this.cachedAiBaseTick = -1L;
   }

   public String getCachedAiRawUrl() {
      return this.cachedAiRawUrl;
   }

   public long getCachedAiBaseTick() {
      return this.cachedAiBaseTick;
   }

   public void setCachedAiBaseTick(long tick) {
      this.cachedAiBaseTick = tick;
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

   public int getProjectionMode() {
      return this.projectionMode;
   }

   public void setProjectionMode(int v) {
      this.projectionMode = v;
      this.setChanged();
   }

   public boolean getAllowAi() {
      return this.allowAi;
   }

   public void setAllowAi(boolean v) {
      this.allowAi = v;
      this.setChanged();
   }

   public void onLoad() {
      super.onLoad();
      this.syncClientLink();
   }

   public void setRemoved() {
      if (this.level != null && this.level.isClientSide()) {
         boolean bindingDestroyed = !this.level.getBlockState(this.worldPosition).is((Block)ModBlocks.LYRIC_PROJECTOR.get());
         if (bindingDestroyed) {
            ClientLinkRegistry.unlink(this.worldPosition);
         }
      }

      super.setRemoved();
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
      output.putInt("ProjMode", this.projectionMode);
      output.putBoolean("AllowAi", this.allowAi);
   }

   @Override
   public void load(CompoundTag input) {
      super.load(input);
      this.linkedTurntablePos = LinkHelper.loadLinkFromBE(input, "LinkedTarget_has", "LinkedTarget_x", "LinkedTarget_y", "LinkedTarget_z");
      this.projectionYaw = LinkHelper.getFloatOr(input, "ProjYaw", 180.0F);
      this.projectionPitch = LinkHelper.getFloatOr(input, "ProjPitch", 0.0F);
      this.projectionScale = LinkHelper.getFloatOr(input, "ProjScale", 1.0F);
      this.projectionHeight = LinkHelper.getFloatOr(input, "ProjHeight", 1.2F);
      this.projectionDistanceX = LinkHelper.getFloatOr(input, "ProjDistanceX", 0.0F);
      this.projectionDistanceZ = LinkHelper.getFloatOr(input, "ProjDistanceZ", 0.0F);
      this.projectionMode = LinkHelper.getIntOr(input, "ProjMode", 0);
      this.allowAi = LinkHelper.getBooleanOr(input, "AllowAi", false);
      this.syncClientLink();
   }

   private void syncClientLink() {
      if (this.level != null && this.level.isClientSide()) {
         ClientLinkRegistry.unlink(this.worldPosition);
         if (this.linkedTurntablePos != null) {
            ClientLinkRegistry.linkSubtitleProjector(this.worldPosition, this.linkedTurntablePos);
         }
      }
   }

}
