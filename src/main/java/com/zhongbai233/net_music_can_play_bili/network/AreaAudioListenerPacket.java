package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.audio.ClientAreaAudioZoneRegistry;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record AreaAudioListenerPacket(AreaAudioZone zone) {
   public static final String ID = "area_audio_listener";
   public static AreaAudioListenerPacket decode(FriendlyByteBuf buffer) {
      return new AreaAudioListenerPacket(AreaAudioZoneCodec.decode(buffer));
   }

   public static void encode(AreaAudioListenerPacket packet, FriendlyByteBuf buffer) {
      AreaAudioZoneCodec.encode(buffer, packet.zone());
   }

   public AreaAudioListenerPacket(AreaAudioZone zone) {
      zone = Objects.requireNonNull(zone, "zone");
      this.zone = zone;
   }


   public static void handle(AreaAudioListenerPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ClientAreaAudioZoneRegistry.acceptListener(packet.zone()));
   }
}
