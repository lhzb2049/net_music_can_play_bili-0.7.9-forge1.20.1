package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.server.ControlConsoleEditLeaseRegistry;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ControlConsoleAccessPacket(
   BlockPos pos, UUID leaseId, UUID operationId, long expectedRevision, ControlConsoleDocument.AccessMode accessMode, Set<UUID> trustedPlayerIds
) {
   public static final String ID = "control_console_access";
   public static ControlConsoleAccessPacket decode(FriendlyByteBuf buf) {
      BlockPos pos = (BlockPos)buf.readBlockPos();
      UUID leaseId = buf.readUUID();
      UUID operationId = buf.readUUID();
      long revision = buf.readLong();
      ControlConsoleDocument.AccessMode mode = ControlConsoleDocument.AccessMode.parse(buf.readUtf(16));
      int count = buf.readVarInt();
      if (count >= 0 && count <= 256) {
         Set<UUID> trusted = new LinkedHashSet<>();

         for (int i = 0; i < count; i++) {
            trusted.add(buf.readUUID());
         }

         return new ControlConsoleAccessPacket(pos, leaseId, operationId, revision, mode, trusted);
      } else {
         throw new IllegalArgumentException("invalid trusted player count: " + count);
      }
   }

   public static void encode(ControlConsoleAccessPacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeUUID(packet.leaseId());
      buf.writeUUID(packet.operationId());
      buf.writeLong(packet.expectedRevision());
      buf.writeUtf(packet.accessMode().name(), 16);
      buf.writeVarInt(packet.trustedPlayerIds().size());
      packet.trustedPlayerIds().forEach(buf::writeUUID);
   }

   public ControlConsoleAccessPacket(
      BlockPos pos, UUID leaseId, UUID operationId, long expectedRevision, ControlConsoleDocument.AccessMode accessMode, Set<UUID> trustedPlayerIds
   ) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      leaseId = Objects.requireNonNull(leaseId, "leaseId");
      operationId = Objects.requireNonNull(operationId, "operationId");
      accessMode = Objects.requireNonNull(accessMode, "accessMode");
      if (expectedRevision < 0L) {
         throw new IllegalArgumentException("expectedRevision must not be negative");
      } else {
         trustedPlayerIds = Set.copyOf(Objects.requireNonNull(trustedPlayerIds, "trustedPlayerIds"));
         if (trustedPlayerIds.size() > 256) {
            throw new IllegalArgumentException("too many trusted players");
         } else {
            this.pos = pos;
            this.leaseId = leaseId;
            this.operationId = operationId;
            this.expectedRevision = expectedRevision;
            this.accessMode = accessMode;
            this.trustedPlayerIds = trustedPlayerIds;
         }
      }
   }


   public static void handle(ControlConsoleAccessPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      ServerPlayer player = context.getSender();
      if (!(player != null
            && player.level() instanceof ServerLevel level
            && NetworkRateLimiter.allow(player.getUUID(), "control_console_access", 2)
            && !(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)
            && level.hasChunk(Math.floorDiv(payload.pos().getX(), 16), Math.floorDiv(payload.pos().getZ(), 16))
            && level.getBlockEntity(payload.pos()) instanceof ControlConsoleBlockEntity console
      )) {
         reject(payload, contextSupplier, -1L);
      } else if (!ControlConsoleEditLeaseRegistry.validate(
         ControlConsoleEditLeasePacket.key(level, payload.pos()), player.getUUID(), payload.leaseId(), System.currentTimeMillis()
      )) {
         reject(payload, contextSupplier, console.documentRevision());
      } else {
         ControlConsoleBlockEntity.ReplaceResult result = console.replaceAccessControl(
            player, payload.operationId(), payload.expectedRevision(), payload.accessMode(), payload.trustedPlayerIds()
         );
         ControlConsoleConfigResultPacket.Status status = ControlConsoleConfigResultPacket.fromReplaceResult(result);
         NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ControlConsoleConfigResultPacket(
               payload.pos(),
               payload.operationId(),
               console.documentRevision(),
               status,
               status == ControlConsoleConfigResultPacket.Status.CONFLICT ? console.document() : null
            ));
      }
   }

   private static void reject(ControlConsoleAccessPacket payload, Supplier<NetworkEvent.Context> contextSupplier, long revision) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null) {
         NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ControlConsoleConfigResultPacket(payload.pos(), payload.operationId(), revision, ControlConsoleConfigResultPacket.Status.REJECTED));
      }
   }
}
