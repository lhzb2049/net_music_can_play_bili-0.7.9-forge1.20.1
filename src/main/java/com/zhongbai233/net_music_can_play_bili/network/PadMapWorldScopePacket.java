package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.PadMapClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record PadMapWorldScopePacket(String worldScopeId, String worldName) {
   public static final String ID = "pad_map_world_scope";
   private static final int MAX_SCOPE_LENGTH = 256;
   public static PadMapWorldScopePacket decode(FriendlyByteBuf buffer) {
      return new PadMapWorldScopePacket(buffer.readUtf(256), buffer.readUtf(256));
   }

   public static void encode(PadMapWorldScopePacket packet, FriendlyByteBuf buffer) {
      buffer.writeUtf(packet.worldScopeId(), 256);
      buffer.writeUtf(packet.worldName(), 256);
   }


   public static void handle(PadMapWorldScopePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> PadMapClientHooks.setServerWorldScope(payload.worldScopeId(), payload.worldName()));
   }
}
