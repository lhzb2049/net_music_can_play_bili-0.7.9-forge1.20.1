package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.PadClient;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadDocument;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record PadStateMirrorPacket(UUID deviceId, PadDocument document, long updatedGameTime) {
   public static final String ID = "pad_state_mirror";
   public static PadStateMirrorPacket decode(FriendlyByteBuf buffer) {
      return new PadStateMirrorPacket(buffer.readUUID(), PadStatePacket.readDocument(buffer), buffer.readLong());
   }

   public static void encode(PadStateMirrorPacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.deviceId());
      PadStatePacket.writeDocument(buffer, packet.document());
      buffer.writeLong(packet.updatedGameTime());
   }


   public static void handle(PadStateMirrorPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> PadClient.receiveMirroredDocument(payload.deviceId(), payload.document(), payload.updatedGameTime()));
   }
}
