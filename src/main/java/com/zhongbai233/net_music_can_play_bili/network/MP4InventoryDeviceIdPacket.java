package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MP4InventoryDeviceIdPacket(int inventorySlot, UUID deviceId) {
   public static final String ID = "mp4_inventory_device_id";
   public static MP4InventoryDeviceIdPacket decode(FriendlyByteBuf buffer) {
      return new MP4InventoryDeviceIdPacket(buffer.readInt(), buffer.readUUID());
   }

   public static void encode(MP4InventoryDeviceIdPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.inventorySlot());
      buffer.writeUUID(packet.deviceId());
   }


   public static void handle(MP4InventoryDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> MP4Client.receiveInventoryDeviceId(payload.inventorySlot(), payload.deviceId()));
   }
}
