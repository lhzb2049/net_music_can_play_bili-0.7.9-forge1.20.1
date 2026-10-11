package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.gui.WhitelistReviewScreen;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record WhitelistReviewMutationResultPacket(
   WhitelistReviewMutationResultPacket.Mutation mutation, WhitelistReviewMutationResultPacket.Status status, String targetId, String message, long requestId
) {
   private static final int MAX_TARGET_LENGTH = 512;
   private static final int MAX_MESSAGE_LENGTH = 256;
   public static final String ID = "whitelist_review_mutation_result";
   public static WhitelistReviewMutationResultPacket decode(FriendlyByteBuf buffer) {
      return new WhitelistReviewMutationResultPacket(
         WhitelistReviewMutationResultPacket.Mutation.byId(buffer.readVarInt()),
         WhitelistReviewMutationResultPacket.Status.byId(buffer.readVarInt()),
         buffer.readUtf(512),
         buffer.readUtf(256),
         WhitelistReviewMutationResultPacket.readRequestId(buffer)
      );
   }

   public static void encode(WhitelistReviewMutationResultPacket packet, FriendlyByteBuf buffer) {
      buffer.writeVarInt(packet.mutation().ordinal());
      buffer.writeVarInt(packet.status().ordinal());
      buffer.writeUtf(WhitelistReviewMutationResultPacket.bounded(packet.targetId(), 512), 512);
      buffer.writeUtf(WhitelistReviewMutationResultPacket.bounded(packet.message(), 256), 256);
      buffer.writeVarLong(Math.max(0L, packet.requestId()));
   }

   public WhitelistReviewMutationResultPacket(
      WhitelistReviewMutationResultPacket.Mutation mutation, WhitelistReviewMutationResultPacket.Status status, String targetId, String message
   ) {
      this(mutation, status, targetId, message, 0L);
   }

   public boolean successful() {
      return this.status == WhitelistReviewMutationResultPacket.Status.SUCCESS;
   }

   public static void sendTo(
      ServerPlayer player,
      WhitelistReviewMutationResultPacket.Mutation mutation,
      WhitelistReviewMutationResultPacket.Status status,
      String targetId,
      String message,
      long requestId
   ) {
      if (player != null) {
         NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WhitelistReviewMutationResultPacket(mutation, status, targetId, message, requestId));
      }
   }

   private static String bounded(String value, int maximum) {
      String safe = value == null ? "" : value;
      return safe.length() <= maximum ? safe : safe.substring(0, maximum);
   }

   private static long readRequestId(FriendlyByteBuf buffer) {
      long value = buffer.readVarLong();
      if (value < 0L) {
         throw new DecoderException("Invalid whitelist mutation request id: " + value);
      } else {
         return value;
      }
   }


   public static void handle(WhitelistReviewMutationResultPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> WhitelistReviewScreen.routeMutationResult(payload));
   }

   public static enum Mutation {
      REMOVE,
      COMMENT,
      REFRESH,
      PREVIEW,
      PREVIEW_SEEK;

      private static WhitelistReviewMutationResultPacket.Mutation byId(int id) {
         WhitelistReviewMutationResultPacket.Mutation[] values = values();
         if (id >= 0 && id < values.length) {
            return values[id];
         } else {
            throw new DecoderException("Invalid whitelist mutation id: " + id);
         }
      }
   }

   public static enum Status {
      SUCCESS,
      RATE_LIMITED,
      DENIED,
      MISSING,
      INVALID,
      REQUIRED,
      STALE,
      SAVE_FAILED;

      private static WhitelistReviewMutationResultPacket.Status byId(int id) {
         WhitelistReviewMutationResultPacket.Status[] values = values();
         if (id >= 0 && id < values.length) {
            return values[id];
         } else {
            throw new DecoderException("Invalid whitelist mutation result id: " + id);
         }
      }
   }
}
