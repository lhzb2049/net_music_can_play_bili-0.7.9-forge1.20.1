package com.zhongbai233.net_music_can_play_bili.block;

import com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag;

import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.LiveStreamerClientHooks;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModItems;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import com.zhongbai233.net_music_can_play_bili.link.LinkHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

public class LiveStreamerBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final BooleanProperty PLAYING = BooleanProperty.create("playing");

   public LiveStreamerBlock(Properties properties) {
      super(properties.sound(SoundType.METAL).strength(1.5F));
      this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.SOUTH)).setValue(PLAYING, false));
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new LiveStreamerBlockEntity(pos, state);
   }

   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return !level.isClientSide() && type == ModBlockEntities.LIVE_STREAMER.get()
         ? (tickLevel, pos, tickState, blockEntity) -> LiveStreamerBlockEntity.tick(tickLevel, pos, tickState, (LiveStreamerBlockEntity)blockEntity)
         : null;
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      if (level instanceof ServerLevel serverLevel && (movedByPiston || !state.is(newState.getBlock()))) {
         AudioLinkIndex.removePlaybackSource(serverLevel, pos);
      }

      super.onRemove(state, level, pos, newState, movedByPiston);
   }
   /**
    * 【第 91 次：右键入口修复】1.21 把方块交互拆成 `useItemOn`（手持物品）与 `useWithoutItem`（空手），
    * 而 1.20.1 **只有** `use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)`
    * （javap 实证：BlockBehaviour 里没有 useItemOn/useWithoutItem）。
    * 所以原来的两个方法名**不是重写、永远不会被调用** —— 表现就是"右键方块毫无反应，连 GUI 都不尝试打开"。
    * 这里按 1.21 的语义接起来：手持物品那条先走；它返回 PASS（"不是我的物品"）时再走空手那条
    * （对应 1.21 的 PASS_TO_DEFAULT_BLOCK_INTERACTION 落到同方块的空手处理）。
    */
   @Override
   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
      UseDiag.enter("live_streamer", level, player, hand);
      ItemStack stack = player.getItemInHand(hand);
      InteractionResult withItem = this.ncpb$useWithItem(stack, state, level, pos, player, hand, hitResult);
      return UseDiag.exit("live_streamer", withItem == InteractionResult.PASS ? this.ncpb$useEmpty(state, level, pos, player, hitResult) : withItem);
   }


   private InteractionResult ncpb$useWithItem(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
   ) {
      if (hand == InteractionHand.OFF_HAND) {
         return InteractionResult.PASS;
      } else if (stack.getItem() == ModItems.SPEAKER.get()) {
         if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(level.isClientSide());
         } else {
            LinkHelper.writeLinkToItem(stack, pos);
            player.sendSystemMessage(
               Component.translatable("message.net_music_can_play_bili.speaker.item_linked", new Object[]{pos.getX(), pos.getY(), pos.getZ()})
                  .withStyle(ChatFormatting.GOLD)
            );
            return InteractionResult.sidedSuccess(level.isClientSide());
         }
      } else if (stack.getItem() == ModItems.VIDEO_PROJECTOR.get()) {
         if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(level.isClientSide());
         } else {
            LinkHelper.writeLinkToItem(stack, pos);
            VideoProjectorBlock.writeLinkedBlockEntityData(stack, pos);
            player.sendSystemMessage(
               Component.translatable("message.net_music_can_play_bili.video_projector.item_linked", new Object[]{pos.getX(), pos.getY(), pos.getZ()})
                  .withStyle(ChatFormatting.GOLD)
            );
            return InteractionResult.sidedSuccess(level.isClientSide());
         }
      } else if (stack.getItem() == ModItems.CONTROL_CONSOLE.get()) {
         if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(level.isClientSide());
         } else {
            LinkHelper.writeControlConsoleLinkToItem(stack, pos, level.dimension().location().toString(), LinkHelper.ControlConsoleSourceKind.LIVE_STREAMER);
            player.sendSystemMessage(
               Component.translatable("message.net_music_can_play_bili.control_console.item_linked", new Object[]{pos.getX(), pos.getY(), pos.getZ()})
                  .withStyle(ChatFormatting.GOLD)
            );
            return InteractionResult.sidedSuccess(level.isClientSide());
         }
      } else {
         if (level.isClientSide()) {
            LiveStreamerClientHooks.openLiveStreamerScreen(pos);
         }

         return InteractionResult.sidedSuccess(level.isClientSide());
      }
   }

   private InteractionResult ncpb$useEmpty(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (level.isClientSide()) {
         LiveStreamerClientHooks.openLiveStreamerScreen(pos);
      }

      return InteractionResult.SUCCESS;
   }

   public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
      if (level instanceof Level realLevel && !realLevel.isClientSide() && realLevel.getBlockEntity(pos) instanceof LiveStreamerBlockEntity streamer) {
         streamer.stopForBlockRemoval();
      }

      super.destroy(level, pos, state);
   }

   public BlockState getStateForPlacement(BlockPlaceContext context) {
      return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING, PLAYING});
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

}
