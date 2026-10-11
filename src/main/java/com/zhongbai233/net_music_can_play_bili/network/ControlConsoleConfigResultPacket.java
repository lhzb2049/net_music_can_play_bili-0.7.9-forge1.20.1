package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleConflictAuthority;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.gui.HolographicScreenConfigTestScreen;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ControlConsoleConfigResultPacket(
   BlockPos pos, UUID operationId, long revision, ControlConsoleConfigResultPacket.Status status, ControlConsoleDocument authoritativeDocument
) {
   public static final String ID = "control_console_config_result";
   public static ControlConsoleConfigResultPacket decode(FriendlyByteBuf buf) {
      BlockPos pos = (BlockPos)buf.readBlockPos();
      UUID operationId = buf.readUUID();
      long revision = buf.readLong();
      ControlConsoleConfigResultPacket.Status status = ControlConsoleConfigResultPacket.Status.fromId(buf.readVarInt());
      ControlConsoleDocument authoritative = buf.readBoolean() ? ControlConsoleDocumentPacketCodec.decode(buf) : null;
      return new ControlConsoleConfigResultPacket(pos, operationId, revision, status, authoritative);
   }

   public static void encode(ControlConsoleConfigResultPacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeUUID(packet.operationId());
      buf.writeLong(packet.revision());
      buf.writeVarInt(packet.status().id());
      buf.writeBoolean(packet.authoritativeDocument() != null);
      if (packet.authoritativeDocument() != null) {
         ControlConsoleDocumentPacketCodec.encode(buf, packet.authoritativeDocument());
      }
   }

   public ControlConsoleConfigResultPacket(BlockPos pos, UUID operationId, long revision, ControlConsoleConfigResultPacket.Status status) {
      this(pos, operationId, revision, status, null);
   }

   public ControlConsoleConfigResultPacket(
      BlockPos pos, UUID operationId, long revision, ControlConsoleConfigResultPacket.Status status, ControlConsoleDocument authoritativeDocument
   ) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      operationId = Objects.requireNonNull(operationId, "operationId");
      status = Objects.requireNonNull(status, "status");
      if (revision < -1L) {
         throw new IllegalArgumentException("revision must be -1 or non-negative");
      } else {
         ControlConsoleConflictAuthority.validate(status == ControlConsoleConfigResultPacket.Status.CONFLICT, revision, authoritativeDocument);
         this.pos = pos;
         this.operationId = operationId;
         this.revision = revision;
         this.status = status;
         this.authoritativeDocument = authoritativeDocument;
      }
   }


   public static void handle(ControlConsoleConfigResultPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> HolographicScreenConfigTestScreen.acceptControlConsoleConfigResult(payload));
   }

   public static ControlConsoleConfigResultPacket.Status fromReplaceResult(ControlConsoleBlockEntity.ReplaceResult result) {
      return switch (result) {
         case APPLIED -> ControlConsoleConfigResultPacket.Status.APPLIED;
         case DUPLICATE -> ControlConsoleConfigResultPacket.Status.DUPLICATE;
         case CONFLICT -> ControlConsoleConfigResultPacket.Status.CONFLICT;
         case READ_ONLY -> ControlConsoleConfigResultPacket.Status.READ_ONLY;
         case REJECTED -> ControlConsoleConfigResultPacket.Status.REJECTED;
      };
   }

   public static enum Status {
      APPLIED(0),
      DUPLICATE(1),
      CONFLICT(2),
      REJECTED(3),
      READ_ONLY(4);

      private final int id;

      private Status(int id) {
         this.id = id;
      }

      int id() {
         return this.id;
      }

      static ControlConsoleConfigResultPacket.Status fromId(int id) {
         for (ControlConsoleConfigResultPacket.Status status : values()) {
            if (status.id == id) {
               return status;
            }
         }

         throw new IllegalArgumentException("unknown control console result status: " + id);
      }
   }
}
