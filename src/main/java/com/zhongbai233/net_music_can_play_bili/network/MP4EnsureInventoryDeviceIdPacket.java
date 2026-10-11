package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MP4EnsureInventoryDeviceIdPacket(int inventorySlot) {
   public static final String ID = "mp4_ensure_inventory_device_id";
   public static MP4EnsureInventoryDeviceIdPacket decode(FriendlyByteBuf buffer) {
      return new MP4EnsureInventoryDeviceIdPacket(buffer.readInt());
   }

   public static void encode(MP4EnsureInventoryDeviceIdPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.inventorySlot());
   }


   public static void handle(MP4EnsureInventoryDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null) {
         if (NetworkRateLimiter.allow(player.getUUID(), "mp4_ensure_inventory_device_id", 12)) {
            int slot = payload.inventorySlot();
            if (slot >= 0 && slot < Math.min(9, player.getInventory().getContainerSize())) {
               ItemStack stack = player.getInventory().getItem(slot);
               if (stack.getItem() instanceof MP4Item) {
                  ServerLevel level = (ServerLevel)player.level();
                  UUID deviceId = MP4DeviceIdentity.getOrCreateUnique(level, player, stack);
                  if (deviceId != null) {
                     NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MP4InventoryDeviceIdPacket(slot, deviceId));
                     NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), MP4DeviceStateMirrorPacket.fromEntry(
                           deviceId, MP4DeviceStateStore.getOrCreate(level, deviceId, stack), AudioLinkIndex.hasHeadphoneLinkedToMp4(deviceId)
                        ));
                  }
               }
            }
         }
      }
   }
}
