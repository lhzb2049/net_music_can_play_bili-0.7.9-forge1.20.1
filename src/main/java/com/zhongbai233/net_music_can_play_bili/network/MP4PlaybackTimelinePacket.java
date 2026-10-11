package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.MP4ClientMediaSync;
import com.zhongbai233.net_music_can_play_bili.client.sync.ClientMediaTimelinePayload;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MP4PlaybackTimelinePacket(
   UUID sourceId, String sessionId, long elapsedMillis, int volumePerMille, boolean headphoneRouted, AreaAudioZone areaAudioZone
) implements ClientMediaTimelinePayload {
   public static final String ID = "mp4_playback_timeline";
   public static MP4PlaybackTimelinePacket decode(FriendlyByteBuf buffer) {
      return new MP4PlaybackTimelinePacket(
         buffer.readUUID(),
         buffer.readUtf(128),
         buffer.readVarLong(),
         buffer.readVarInt(),
         buffer.readBoolean(),
         AreaAudioZoneCodec.decode(buffer)
      );
   }

   public static void encode(MP4PlaybackTimelinePacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.sourceId());
      buffer.writeUtf(packet.sessionId() == null ? "" : packet.sessionId(), 128);
      buffer.writeVarLong(Math.max(0L, packet.elapsedMillis()));
      buffer.writeVarInt(Math.max(0, Math.min(1000, packet.volumePerMille())));
      buffer.writeBoolean(packet.headphoneRouted());
      AreaAudioZoneCodec.encode(buffer, packet.areaAudioZone());
   }

   public MP4PlaybackTimelinePacket(UUID sourceId, String sessionId, long elapsedMillis, int volumePerMille, boolean headphoneRouted) {
      this(sourceId, sessionId, elapsedMillis, volumePerMille, headphoneRouted, AreaAudioZone.unrestricted());
   }

   public MP4PlaybackTimelinePacket(
      UUID sourceId, String sessionId, long elapsedMillis, int volumePerMille, boolean headphoneRouted, AreaAudioZone areaAudioZone
   ) {
      areaAudioZone = areaAudioZone != null ? areaAudioZone : AreaAudioZone.unrestricted();
      this.sourceId = sourceId;
      this.sessionId = sessionId;
      this.elapsedMillis = elapsedMillis;
      this.volumePerMille = volumePerMille;
      this.headphoneRouted = headphoneRouted;
      this.areaAudioZone = areaAudioZone;
   }


   public static void handle(MP4PlaybackTimelinePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      MP4ClientMediaSync.handleTimeline(payload);
   }
}
