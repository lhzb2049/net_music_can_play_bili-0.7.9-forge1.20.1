package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.link.MediaBindingData;
import com.zhongbai233.net_music_can_play_bili.menu.MediaToolBindingMenu;
import com.zhongbai233.net_music_can_play_bili.server.MediaBindingCleanupService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MediaToolClearBindingPacket() {
   public static final String ID = "media_tool_clear_binding";
   public static MediaToolClearBindingPacket decode(FriendlyByteBuf buffer) {
      return new MediaToolClearBindingPacket();
   }
   public static void encode(MediaToolClearBindingPacket packet, FriendlyByteBuf buffer) {
      // 无字段：不写任何东西
   }


   public static void handle(MediaToolClearBindingPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null && player.containerMenu instanceof MediaToolBindingMenu menu) {
         MediaBindingData.MediaSource targetSource = menu.targetSource(player);
         if (targetSource != null) {
            MediaBindingCleanupService.TargetBindingStats stats = MediaBindingCleanupService.clearTargetBindings(player, targetSource);
            player.sendSystemMessage(
               Component.translatable(
                     stats.total() > 0
                        ? "message.net_music_can_play_bili.media_tool.target_unlinked"
                        : "message.net_music_can_play_bili.media_tool.target_no_links",
                     new Object[]{stats.headphoneCount(), stats.holographicCount()}
                  )
                  .withStyle(ChatFormatting.GOLD)
            );
            menu.refreshTargetBindingStats(player);
         } else {
            ItemStack equipment = menu.getSlot(1).getItem();
            if (equipment.isEmpty()) {
               player.sendSystemMessage(
                  Component.translatable("message.net_music_can_play_bili.media_tool.need_target_or_equipment").withStyle(ChatFormatting.RED)
               );
            } else {
               MediaBindingCleanupService.ClearEquipmentResult result = MediaBindingCleanupService.clearEquipmentBindings(player, equipment);
               player.sendSystemMessage(
                  Component.translatable(
                        result.total() > 0
                           ? "message.net_music_can_play_bili.media_tool.equipment_unlinked"
                           : "message.net_music_can_play_bili.media_tool.equipment_no_links",
                        new Object[]{result.holographicCount(), result.headphoneCount()}
                     )
                     .withStyle(ChatFormatting.GOLD)
               );
               menu.broadcastChanges();
            }
         }
      }
   }
}
