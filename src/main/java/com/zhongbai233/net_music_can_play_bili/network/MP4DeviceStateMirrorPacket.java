package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MP4DeviceStateMirrorPacket(UUID deviceId, MP4StatePacket state, long updatedGameTime, boolean headphoneLinked, List<ItemStack> queue)
   {
   public static final String ID = "mp4_device_state_mirror";
   public static MP4DeviceStateMirrorPacket decode(FriendlyByteBuf buffer) {
      UUID deviceId = buffer.readUUID();
      MP4StatePacket state = (MP4StatePacket)MP4StatePacket.decode(buffer);
      long updatedGameTime = buffer.readLong();
      boolean headphoneLinked = buffer.readBoolean();
      int size = Math.max(0, Math.min(18, buffer.readVarInt()));
      List<ItemStack> queue = new ArrayList<>(size);

      for (int i = 0; i < size; i++) {
         queue.add(buffer.readItem());
      }

      return new MP4DeviceStateMirrorPacket(deviceId, state, updatedGameTime, headphoneLinked, List.copyOf(queue));
   }

   public static void encode(MP4DeviceStateMirrorPacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.deviceId());
      MP4StatePacket.encode(packet.state(), buffer);
      buffer.writeLong(packet.updatedGameTime());
      buffer.writeBoolean(packet.headphoneLinked());
      List<ItemStack> queue = packet.queue() == null ? List.of() : packet.queue();
      int size = Math.min(18, queue.size());
      buffer.writeVarInt(size);

      for (int i = 0; i < size; i++) {
         buffer.writeItem(queue.get(i));
      }
   }


   public static MP4DeviceStateMirrorPacket fromEntry(UUID deviceId, MP4DeviceStateStore.DeviceEntry entry, boolean headphoneLinked) {
      return new MP4DeviceStateMirrorPacket(
         deviceId, MP4StatePacket.fromState(entry.state(), deviceId), entry.updatedGameTime(), headphoneLinked, entry.queue()
      );
   }

   public static void handle(MP4DeviceStateMirrorPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> MP4Client.receiveMirroredState(
            payload.deviceId(), payload.state().toState(), payload.updatedGameTime(), payload.headphoneLinked(), payload.queue()
         )
      );
   }
}
