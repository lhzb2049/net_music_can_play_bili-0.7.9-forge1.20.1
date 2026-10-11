package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ControlConsoleBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.compat.areacontrol.AreaControlAudioCompat;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleAudioElementKey;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleDocument;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElement;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.document.ControlConsoleElementPosition;
import com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.media.ControlConsoleRangeGate;
import com.zhongbai233.net_music_can_play_bili.server.ControlConsoleConsumerLeaseRegistry;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import org.joml.Vector3d;

public record ControlConsoleConsumerLeasePacket(BlockPos pos, ControlConsoleConsumerLeasePacket.Action action, UUID leaseId, long consumerGeneration)
   {
   public static final String ID = "control_console_consumer_lease";
   public static ControlConsoleConsumerLeasePacket decode(FriendlyByteBuf buf) {
      BlockPos pos = (BlockPos)buf.readBlockPos();
      ControlConsoleConsumerLeasePacket.Action action = ControlConsoleConsumerLeasePacket.Action.fromId(buf.readVarInt());
      UUID leaseId = buf.readBoolean() ? buf.readUUID() : null;
      return new ControlConsoleConsumerLeasePacket(pos, action, leaseId, buf.readVarLong());
   }

   public static void encode(ControlConsoleConsumerLeasePacket packet, FriendlyByteBuf buf) {
      buf.writeBlockPos(packet.pos());
      buf.writeVarInt(packet.action().id);
      buf.writeBoolean(packet.leaseId() != null);
      if (packet.leaseId() != null) {
         buf.writeUUID(packet.leaseId());
      }

      buf.writeVarLong(packet.consumerGeneration());
   }

   public ControlConsoleConsumerLeasePacket(BlockPos pos, ControlConsoleConsumerLeasePacket.Action action, UUID leaseId, long consumerGeneration) {
      pos = Objects.requireNonNull(pos, "pos").immutable();
      action = Objects.requireNonNull(action, "action");
      if (action == ControlConsoleConsumerLeasePacket.Action.RELEASE && leaseId == null) {
         throw new IllegalArgumentException("release requires leaseId");
      } else {
         this.pos = pos;
         this.action = action;
         this.leaseId = leaseId;
         this.consumerGeneration = consumerGeneration;
      }
   }


   public static void handle(ControlConsoleConsumerLeasePacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null && player.level() instanceof ServerLevel level) {
         ControlConsoleConsumerLeaseRegistry.Key var13 = key(level, payload.pos(), player.getUUID());
         if (payload.action() == ControlConsoleConsumerLeasePacket.Action.RELEASE) {
            ControlConsoleConsumerLeaseRegistry.release(var13, payload.leaseId());
         } else if (NetworkRateLimiter.allow(player.getUUID(), "control_console_consumer_lease_global", 64)
            && NetworkRateLimiter.allow(player.getUUID(), "control_console_consumer_lease:" + payload.pos().asLong(), 4)
            && level.hasChunk(Math.floorDiv(payload.pos().getX(), 16), Math.floorDiv(payload.pos().getZ(), 16))
            && level.getBlockEntity(payload.pos()) instanceof ControlConsoleBlockEntity console) {
            long var14 = System.currentTimeMillis();
            boolean existing = ControlConsoleConsumerLeaseRegistry.hasActive(var13, var14);
            ControlConsoleDocument document = console.document();
            if (!hasValidLoadedSource(level, document)) {
               if (payload.leaseId() != null) {
                  ControlConsoleConsumerLeaseRegistry.release(var13, payload.leaseId());
               }

               send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.REJECTED, null, payload.consumerGeneration());
            } else {
               ControlConsoleRangeGate.Result range = ControlConsoleRangeGate.evaluate(
                  existing,
                  player.getX() - (payload.pos().getX() + 0.5),
                  player.getY() - (payload.pos().getY() + 0.5),
                  player.getZ() - (payload.pos().getZ() + 0.5),
                  document.hardRangeX(),
                  document.hardRangeY(),
                  document.hardRangeZ()
               );
               if (!range.active()) {
                  if (payload.leaseId() != null) {
                     ControlConsoleConsumerLeaseRegistry.release(var13, payload.leaseId());
                  }

                  send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.OUTSIDE, null, payload.consumerGeneration());
               } else {
                  UUID leaseId;
                  if (existing) {
                     if (payload.leaseId() == null) {
                        leaseId = ControlConsoleConsumerLeaseRegistry.acquireOrRenew(var13, var14);
                     } else {
                        if (!ControlConsoleConsumerLeaseRegistry.renew(var13, payload.leaseId(), var14)) {
                           send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.REJECTED, null, payload.consumerGeneration());
                           return;
                        }

                        leaseId = payload.leaseId();
                     }
                  } else {
                     leaseId = ControlConsoleConsumerLeaseRegistry.acquireOrRenew(var13, var14);
                  }

                  send(
                     player,
                     payload.pos(),
                     ControlConsoleConsumerLeaseResultPacket.Status.GRANTED,
                     leaseId,
                     payload.consumerGeneration(),
                     audioOutputZones(level, payload.pos(), document)
                  );
               }
            }
         } else {
            send(player, payload.pos(), ControlConsoleConsumerLeaseResultPacket.Status.REJECTED, null, payload.consumerGeneration());
         }
      }
   }

   public static ControlConsoleConsumerLeaseRegistry.Key key(ServerLevel level, BlockPos pos, UUID playerId) {
      return new ControlConsoleConsumerLeaseRegistry.Key(level.dimension().location().toString(), pos.asLong(), playerId);
   }

   private static boolean hasValidLoadedSource(ServerLevel level, ControlConsoleDocument document) {
      if (document.hasSourceBinding() && level.dimension().location().toString().equals(document.sourceDimension())) {
         BlockPos sourcePos = new BlockPos(document.sourceX(), document.sourceY(), document.sourceZ());
         if (!level.hasChunk(Math.floorDiv(sourcePos.getX(), 16), Math.floorDiv(sourcePos.getZ(), 16))) {
            return false;
         } else {
            BlockEntity source = level.getBlockEntity(sourcePos);

            return switch (document.sourceKind()) {
               case TURNTABLE -> source instanceof ModernTurntableBlockEntity;
               case LIVE_STREAMER -> source instanceof LiveStreamerBlockEntity;
            };
         }
      } else {
         return false;
      }
   }

   private static void send(ServerPlayer player, BlockPos pos, ControlConsoleConsumerLeaseResultPacket.Status status, UUID leaseId, long consumerGeneration) {
      send(player, pos, status, leaseId, consumerGeneration, List.of());
   }

   private static void send(
      ServerPlayer player,
      BlockPos pos,
      ControlConsoleConsumerLeaseResultPacket.Status status,
      UUID leaseId,
      long consumerGeneration,
      List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> audioOutputZones
   ) {
      NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ControlConsoleConsumerLeaseResultPacket(pos, status, leaseId, consumerGeneration, audioOutputZones));
   }

   private static List<ControlConsoleConsumerLeaseResultPacket.AudioOutputZone> audioOutputZones(
      ServerLevel level, BlockPos consolePos, ControlConsoleDocument document
   ) {
      return document.elements()
         .stream()
         .filter(element -> element != null && element.enabled())
         .filter(element -> element.type() == ControlConsoleElement.Type.AUDIO)
         .map(
            element -> {
               Vector3d world = ControlConsoleElementPosition.worldPosition(consolePos.getX(), consolePos.getY(), consolePos.getZ(), element);
               BlockPos outputPos = BlockPos.containing(world.x, world.y, world.z);
               return new ControlConsoleConsumerLeaseResultPacket.AudioOutputZone(
                  ControlConsoleAudioElementKey.of(consolePos, element), AreaControlAudioCompat.zoneAt(level, outputPos)
               );
            }
         )
         .toList();
   }

   public static enum Action {
      ACQUIRE_OR_RENEW(0),
      RELEASE(1);

      private final int id;

      private Action(int id) {
         this.id = id;
      }

      private static ControlConsoleConsumerLeasePacket.Action fromId(int id) {
         for (ControlConsoleConsumerLeasePacket.Action action : values()) {
            if (action.id == id) {
               return action;
            }
         }

         throw new IllegalArgumentException("unknown consumer lease action: " + id);
      }
   }
}
