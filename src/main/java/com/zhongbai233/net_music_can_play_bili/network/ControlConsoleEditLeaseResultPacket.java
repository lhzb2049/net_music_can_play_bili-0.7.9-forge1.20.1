package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.ControlConsoleClient;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ControlConsoleEditLeaseResultPacket(BlockPos pos, ControlConsoleEditLeaseResultPacket.Status status, UUID leaseId) {
   public static final String ID = "control_console_edit_lease_result";
   public static ControlConsoleEditLeaseResultPacket decode(FriendlyByteBuf buf) {
      BlockPos pos = (BlockPos)buf.readBlockPos();
      ControlConsoleEditLeaseResultPacket.Status status = ControlConsoleEditLeaseResultPacket.Status.fromId(buf.readVarInt());
      UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
      return new ControlConsoleEditLeaseResultPacket(pos, status, leaseId);
   }

   public static void encode(ControlConsoleEditLeaseResultPacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeVarInt(packet.status().id);
      buf.writeBoolean(packet.leaseId() != null);
      if (packet.leaseId() != null) {
         buf.writeUUID(packet.leaseId());
      }
   }

   public ControlConsoleEditLeaseResultPacket(BlockPos pos, ControlConsoleEditLeaseResultPacket.Status status, UUID leaseId) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      status = Objects.requireNonNull(status, "status");
      if (status == ControlConsoleEditLeaseResultPacket.Status.GRANTED && leaseId == null) {
         throw new IllegalArgumentException("granted lease requires leaseId");
      } else {
         this.pos = pos;
         this.status = status;
         this.leaseId = leaseId;
      }
   }


   public static void handle(ControlConsoleEditLeaseResultPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> ControlConsoleClient.acceptLeaseResult(payload));
   }

   public static enum Status {
      GRANTED(0),
      BUSY(1),
      REJECTED(2),
      EXPIRED(3);

      private final int id;

      private Status(int id) {
         this.id = id;
      }

      private static ControlConsoleEditLeaseResultPacket.Status fromId(int id) {
         for (ControlConsoleEditLeaseResultPacket.Status status : values()) {
            if (status.id == id) {
               return status;
            }
         }

         throw new IllegalArgumentException("unknown edit lease result status: " + id);
      }
   }
}
