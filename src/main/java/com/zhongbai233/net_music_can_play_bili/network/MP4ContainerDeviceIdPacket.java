package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MP4ContainerDeviceIdPacket(int containerSlotIndex, UUID deviceId) {
   public static final String ID = "mp4_container_device_id";
   public static MP4ContainerDeviceIdPacket decode(FriendlyByteBuf buffer) {
      return new MP4ContainerDeviceIdPacket(buffer.readInt(), buffer.readUUID());
   }

   public static void encode(MP4ContainerDeviceIdPacket packet, FriendlyByteBuf buffer) {
      buffer.writeInt(packet.containerSlotIndex());
      buffer.writeUUID(packet.deviceId());
   }


   public static void handle(MP4ContainerDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> MP4Client.receiveContainerDeviceId(payload.containerSlotIndex(), payload.deviceId()));
   }
}
