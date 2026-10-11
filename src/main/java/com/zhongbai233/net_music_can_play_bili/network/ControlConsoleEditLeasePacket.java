package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.server.ControlConsoleEditLeaseRegistry;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record ControlConsoleEditLeasePacket(BlockPos pos, ControlConsoleEditLeasePacket.Action action, UUID leaseId) {
   public static final String ID = "control_console_edit_lease";
   public static ControlConsoleEditLeasePacket decode(FriendlyByteBuf buf) {
      BlockPos pos = (BlockPos)buf.readBlockPos();
      ControlConsoleEditLeasePacket.Action action = ControlConsoleEditLeasePacket.Action.fromId(buf.readVarInt());
      UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
      return new ControlConsoleEditLeasePacket(pos, action, leaseId);
   }

   public static void encode(ControlConsoleEditLeasePacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeVarInt(packet.action().id);
      buf.writeBoolean(packet.leaseId() != null);
      if (packet.leaseId() != null) {
         buf.writeUUID(packet.leaseId());
      }
   }

   public ControlConsoleEditLeasePacket(BlockPos pos, ControlConsoleEditLeasePacket.Action action, UUID leaseId) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      action = Objects.requireNonNull(action, "action");
      if (action != ControlConsoleEditLeasePacket.Action.OPEN && leaseId == null) {
         throw new IllegalArgumentException("leaseId is required for " + action);
      } else {
         this.pos = pos;
         this.action = action;
         this.leaseId = leaseId;
      }
   }


   public static void handle(ControlConsoleEditLeasePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null && player.level() instanceof ServerLevel level) {
         ControlConsoleEditLeaseRegistry.Key var10 = key(level, payload.pos());
         if (payload.action() == ControlConsoleEditLeasePacket.Action.RELEASE) {
            ControlConsoleEditLeaseRegistry.release(var10, player.getUUID(), payload.leaseId());
         } else if (NetworkRateLimiter.allow(player.getUUID(), "control_console_edit_lease", 4)
            && !(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)
            && level.hasChunk(Math.floorDiv(payload.pos().getX(), 16), Math.floorDiv(payload.pos().getZ(), 16))
            && level.getBlockEntity(payload.pos()) instanceof ControlConsoleBlockEntity console) {
            console.claimIfUnowned(player.getUUID());
            if (!console.canEdit(player)) {
               send(player, payload.pos(), ControlConsoleEditLeaseResultPacket.Status.REJECTED, null);
            } else {
               long now = System.currentTimeMillis();
               if (payload.action() == ControlConsoleEditLeasePacket.Action.OPEN) {
                  ControlConsoleEditLeaseRegistry.AcquireResult result = ControlConsoleEditLeaseRegistry.acquire(var10, player.getUUID(), now);
                  send(
                     player,
                     payload.pos(),
                     result.status() == ControlConsoleEditLeaseRegistry.Status.GRANTED
                        ? ControlConsoleEditLeaseResultPacket.Status.GRANTED
                        : ControlConsoleEditLeaseResultPacket.Status.BUSY,
                     result.leaseId()
                  );
               } else {
                  boolean renewed = ControlConsoleEditLeaseRegistry.renew(var10, player.getUUID(), payload.leaseId(), now);
                  send(
                     player,
                     payload.pos(),
                     renewed ? ControlConsoleEditLeaseResultPacket.Status.GRANTED : ControlConsoleEditLeaseResultPacket.Status.EXPIRED,
                     renewed ? payload.leaseId() : null
                  );
               }
            }
         } else {
            send(player, payload.pos(), ControlConsoleEditLeaseResultPacket.Status.REJECTED, null);
         }
      }
   }

   public static ControlConsoleEditLeaseRegistry.Key key(ServerLevel level, BlockPos pos) {
      return new ControlConsoleEditLeaseRegistry.Key(level.dimension().location().toString(), pos.asLong());
   }

   private static void send(ServerPlayer player, BlockPos pos, ControlConsoleEditLeaseResultPacket.Status status, UUID leaseId) {
      NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ControlConsoleEditLeaseResultPacket(pos, status, leaseId));
   }

   public static enum Action {
      OPEN(0),
      RENEW(1),
      RELEASE(2);

      private final int id;

      private Action(int id) {
         this.id = id;
      }

      private static ControlConsoleEditLeasePacket.Action fromId(int id) {
         for (ControlConsoleEditLeasePacket.Action action : values()) {
            if (action.id == id) {
               return action;
            }
         }

         throw new IllegalArgumentException("unknown edit lease action: " + id);
      }
   }
}
