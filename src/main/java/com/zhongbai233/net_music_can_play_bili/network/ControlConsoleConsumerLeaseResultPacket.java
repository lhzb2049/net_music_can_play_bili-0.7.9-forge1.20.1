package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.renderer.ControlConsoleRenderer;
import com.zhongbai233.net_music_can_play_bili.media.audio.AreaAudioZone;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ControlConsoleConsumerLeaseResultPacket(
   BlockPos pos,
   ControlConsoleConsumerLeaseResultPacket.Status status,
   UUID leaseId,
   long consumerGeneration,
   List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> audioOutputZones
) {
   private static final int MAX_AUDIO_OUTPUTS = 4096;
   public static final String ID = "control_console_consumer_lease_result";
   public static ControlConsoleConsumerLeaseResultPacket decode(FriendlyByteBuf buf) {
      BlockPos pos = (BlockPos)buf.readBlockPos();
      ControlConsoleConsumerLeaseResultPacket.Status status = ControlConsoleConsumerLeaseResultPacket.Status.fromId(buf.readVarInt());
      UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
      long generation = buf.readVarLong();
      int count = buf.readVarInt();
      if (count >= 0 && count <= 4096) {
         List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> zones = new ArrayList<>(count);

         for (int index = 0; index < count; index++) {
            zones.add(ControlConsoleConsumerLeaseResultPacket.AudioOutputZone.decode(buf));
         }

         return new ControlConsoleConsumerLeaseResultPacket(pos, status, leaseId, generation, zones);
      } else {
         throw new IllegalArgumentException("invalid console audio output zone count: " + count);
      }
   }

   public static void encode(ControlConsoleConsumerLeaseResultPacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeVarInt(packet.status().id);
      buf.writeBoolean(packet.leaseId() != null);
      if (packet.leaseId() != null) {
         buf.writeUUID(packet.leaseId());
      }

      buf.writeVarLong(packet.consumerGeneration());
      buf.writeVarInt(packet.audioOutputZones().size());
      packet.audioOutputZones().forEach(zone -> zone.encode(buf));
   }

   public ControlConsoleConsumerLeaseResultPacket(
      BlockPos pos,
      ControlConsoleConsumerLeaseResultPacket.Status status,
      UUID leaseId,
      long consumerGeneration,
      List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> audioOutputZones
   ) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      status = Objects.requireNonNull(status, "status");
      if (status == ControlConsoleConsumerLeaseResultPacket.Status.GRANTED && leaseId == null) {
         throw new IllegalArgumentException("granted consumer lease requires leaseId");
      } else {
         audioOutputZones = List.copyOf(Objects.requireNonNull(audioOutputZones, "audioOutputZones"));
         if (audioOutputZones.size() > 4096) {
            throw new IllegalArgumentException("too many console audio output zones");
         } else {
            this.pos = pos;
            this.status = status;
            this.leaseId = leaseId;
            this.consumerGeneration = consumerGeneration;
            this.audioOutputZones = audioOutputZones;
         }
      }
   }

   public ControlConsoleConsumerLeaseResultPacket(BlockPos pos, ControlConsoleConsumerLeaseResultPacket.Status status, UUID leaseId, long consumerGeneration) {
      this(pos, status, leaseId, consumerGeneration, List.of());
   }


   public static void handle(ControlConsoleConsumerLeaseResultPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ControlConsoleRenderer.acceptConsumerLeaseResult(payload));
   }

   public record AudioOutputZone(BlockPos outputKey, AreaAudioZone zone) {
      public AudioOutputZone(BlockPos outputKey, AreaAudioZone zone) {
         outputKey = Objects.requireNonNull(outputKey, "outputKey").immutable();
         zone = Objects.requireNonNull(zone, "zone");
         this.outputKey = outputKey;
         this.zone = zone;
      }

      private static ControlConsoleConsumerLeaseResultPacket.AudioOutputZone decode(FriendlyByteBuf buffer) {
         return new ControlConsoleConsumerLeaseResultPacket.AudioOutputZone((BlockPos)buffer.readBlockPos(), AreaAudioZoneCodec.decode(buffer));
      }

      private void encode(FriendlyByteBuf buffer) {
         buffer.writeBlockPos(this.outputKey);
         AreaAudioZoneCodec.encode(buffer, this.zone);
      }
   }

   public static enum Status {
      GRANTED(0),
      OUTSIDE(1),
      REJECTED(2);

      private final int id;

      private Status(int id) {
         this.id = id;
      }

      private static ControlConsoleConsumerLeaseResultPacket.Status fromId(int id) {
         for (ControlConsoleConsumerLeaseResultPacket.Status status : values()) {
            if (status.id == id) {
               return status;
            }
         }

         throw new IllegalArgumentException("unknown consumer lease result status: " + id);
      }
   }
}
