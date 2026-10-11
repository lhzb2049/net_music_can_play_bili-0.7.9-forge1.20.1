package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.MP4ClientMediaSync;
import com.zhongbai233.net_music_can_play_bili.client.sync.ClientMediaSyncPayload;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MP4PlaybackSyncPacket(
   UUID ownerId,
   UUID sourceId,
   int sourceType,
   int sourceEntityId,
   double sourceX,
   double sourceY,
   double sourceZ,
   boolean playing,
   int queueIndex,
   String playUrl,
   String rawUrl,
   String songName,
   int durationSeconds,
   int volumePerMille,
   String sessionId,
   long elapsedMillis,
   boolean headphoneRouted,
   AreaAudioZone areaAudioZone
) implements ClientMediaSyncPayload {
   public static final int SOURCE_PLAYER = 0;
   public static final int SOURCE_ITEM = 1;
   public static final int SOURCE_BLOCK = 2;
   public static final int SOURCE_CONTAINER_ENTITY = 3;
   public static final String ID = "mp4_playback_sync";
   public static MP4PlaybackSyncPacket decode(FriendlyByteBuf buffer) {
      return new MP4PlaybackSyncPacket(
         buffer.readUUID(),
         buffer.readUUID(),
         buffer.readVarInt(),
         buffer.readVarInt(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readDouble(),
         buffer.readBoolean(),
         buffer.readInt(),
         buffer.readUtf(),
         buffer.readUtf(),
         buffer.readUtf(),
         buffer.readInt(),
         buffer.readInt(),
         buffer.readUtf(),
         buffer.readVarLong(),
         buffer.readBoolean(),
         AreaAudioZoneCodec.decode(buffer)
      );
   }

   public static void encode(MP4PlaybackSyncPacket packet, FriendlyByteBuf buffer) {
      buffer.writeUUID(packet.ownerId());
      buffer.writeUUID(packet.sourceId());
      buffer.writeVarInt(packet.sourceType());
      buffer.writeVarInt(packet.sourceEntityId());
      buffer.writeDouble(packet.sourceX());
      buffer.writeDouble(packet.sourceY());
      buffer.writeDouble(packet.sourceZ());
      buffer.writeBoolean(packet.playing());
      buffer.writeInt(packet.queueIndex());
      buffer.writeUtf(packet.playUrl());
      buffer.writeUtf(packet.rawUrl());
      buffer.writeUtf(packet.songName());
      buffer.writeInt(packet.durationSeconds());
      buffer.writeInt(packet.volumePerMille());
      buffer.writeUtf(packet.sessionId());
      buffer.writeVarLong(packet.elapsedMillis());
      buffer.writeBoolean(packet.headphoneRouted());
      AreaAudioZoneCodec.encode(buffer, packet.areaAudioZone());
   }

   public MP4PlaybackSyncPacket(
      UUID ownerId,
      UUID sourceId,
      int sourceType,
      int sourceEntityId,
      double sourceX,
      double sourceY,
      double sourceZ,
      boolean playing,
      int queueIndex,
      String playUrl,
      String rawUrl,
      String songName,
      int durationSeconds,
      int volumePerMille,
      String sessionId,
      long elapsedMillis,
      boolean headphoneRouted
   ) {
      this(
         ownerId,
         sourceId,
         sourceType,
         sourceEntityId,
         sourceX,
         sourceY,
         sourceZ,
         playing,
         queueIndex,
         playUrl,
         rawUrl,
         songName,
         durationSeconds,
         volumePerMille,
         sessionId,
         elapsedMillis,
         headphoneRouted,
         AreaAudioZone.unrestricted()
      );
   }

   public MP4PlaybackSyncPacket(
      UUID ownerId,
      UUID sourceId,
      int sourceType,
      int sourceEntityId,
      double sourceX,
      double sourceY,
      double sourceZ,
      boolean playing,
      int queueIndex,
      String playUrl,
      String rawUrl,
      String songName,
      int durationSeconds,
      int volumePerMille,
      String sessionId,
      long elapsedMillis,
      boolean headphoneRouted,
      AreaAudioZone areaAudioZone
   ) {
      areaAudioZone = areaAudioZone != null ? areaAudioZone : AreaAudioZone.unrestricted();
      this.ownerId = ownerId;
      this.sourceId = sourceId;
      this.sourceType = sourceType;
      this.sourceEntityId = sourceEntityId;
      this.sourceX = sourceX;
      this.sourceY = sourceY;
      this.sourceZ = sourceZ;
      this.playing = playing;
      this.queueIndex = queueIndex;
      this.playUrl = playUrl;
      this.rawUrl = rawUrl;
      this.songName = songName;
      this.durationSeconds = durationSeconds;
      this.volumePerMille = volumePerMille;
      this.sessionId = sessionId;
      this.elapsedMillis = elapsedMillis;
      this.headphoneRouted = headphoneRouted;
      this.areaAudioZone = areaAudioZone;
   }

   public static MP4PlaybackSyncPacket stop(UUID ownerId, int queueIndex) {
      return stop(ownerId, ownerId, queueIndex);
   }

   public static MP4PlaybackSyncPacket stop(UUID ownerId, UUID sourceId, int queueIndex) {
      UUID normalizedSourceId = sourceId != null ? sourceId : ownerId;
      return new MP4PlaybackSyncPacket(ownerId, normalizedSourceId, 0, -1, 0.0, 0.0, 0.0, false, queueIndex, "", "", "", 0, 0, "", 0L, false);
   }


   public static void handle(MP4PlaybackSyncPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      MP4ClientMediaSync.handleSync(payload);
   }
}
