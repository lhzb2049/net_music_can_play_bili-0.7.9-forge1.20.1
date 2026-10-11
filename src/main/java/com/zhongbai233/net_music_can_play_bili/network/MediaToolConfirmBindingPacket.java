package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.link.MediaBindingData;
import com.zhongbai233.net_music_can_play_bili.menu.MediaToolBindingMenu;
import com.zhongbai233.net_music_can_play_bili.server.MediaBindingCleanupService;
import com.zhongbai233.net_music_can_play_bili.server.MediaEquipmentBindingService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MediaToolConfirmBindingPacket() {
   public static final String ID = "media_tool_confirm_binding";
   public static MediaToolConfirmBindingPacket decode(FriendlyByteBuf buffer) {
      return new MediaToolConfirmBindingPacket();
   }
   public static void encode(MediaToolConfirmBindingPacket packet, FriendlyByteBuf buffer) {
      // 无字段：不写任何东西
   }


   public static void handle(MediaToolConfirmBindingPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null && player.containerMenu instanceof MediaToolBindingMenu menu) {
         ItemStack equipment = menu.getSlot(1).getItem();
         if (equipment.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.net_music_can_play_bili.media_tool.need_equipment_input").withStyle(ChatFormatting.RED));
         } else {
            MediaBindingData.MediaSource source = menu.targetSource(player);
            if (source == null) {
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
               if (result.total() > 0) {
                  menu.confirmBinding(player);
               }

               menu.broadcastChanges();
            } else {
               MediaEquipmentBindingService.BindResult result = MediaEquipmentBindingService.bind(player, equipment, source);
               if (result.bound() && menu.confirmBinding(player)) {
                  player.getInventory().setChanged();
                  menu.refreshTargetBindingStats(player);
               }
            }
         }
      }
   }
}
