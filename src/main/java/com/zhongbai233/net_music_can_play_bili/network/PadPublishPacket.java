package com.zhongbai233.net_music_can_play_bili.network;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.item.PadItem;
import com.zhongbai233.net_music_can_play_bili.item.pad.PadDocument;
import com.zhongbai233.net_music_can_play_bili.media.sync.MonotonicMediaClock;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * 手动改写件（Forge 1.20.1）。由 `tools/port_manual.py` 覆盖进 stage1-src。
 *
 * <p>来源是**第四种形态**：`StreamCodec.of(encoder, decoder)` —— 两个 lambda，
 * 而不是 `composite` 的交替参数表。展开成一对静态方法即可
 * （`(buffer, packet) -> ...` → `encode(packet, buffer)`，注意形参顺序对调）。</p>
 */
public record PadPublishPacket(UUID deviceId) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String ID = "pad_publish";

    public static void encode(PadPublishPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.deviceId());
    }

    public static PadPublishPacket decode(FriendlyByteBuf buffer) {
        return new PadPublishPacket(buffer.readUUID());
    }

    public static void handle(PadPublishPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // getSender() 的返回类型就是 ServerPlayer，不能再用 instanceof（javac 会报错）
            ServerPlayer player = context.getSender();
            if (player != null && payload.deviceId() != null) {
                if (!NetworkRateLimiter.allow(player.getUUID(), "pad_publish", 2)) {
                    LOGGER.debug("丢弃过频 Pad 发布包: player={} device={}", player.getUUID(), payload.deviceId());
                } else {
                    ItemStack draft = PadItem.findAllByDeviceId(player, payload.deviceId())
                            .stream()
                            .filter(PadItem::isPad)
                            .filter(stack -> !PadItem.readLocked(stack))
                            .findFirst()
                            .orElse(ItemStack.EMPTY);
                    if (PadItem.isPad(draft)) {
                        PadDocument draftDocument = PadItem.readLegacyDocument(draft);
                        draftDocument = (draftDocument == null ? PadDocument.DEFAULT : draftDocument)
                                .copyWithLocked(false);
                        if (player.level() instanceof ServerLevel level) {
                            draftDocument = PadDocumentStore
                                    .getOrCreate(level, payload.deviceId(), draft).copyWithLocked(false);
                            PadDocumentStore.update(level, payload.deviceId(), draftDocument);
                        }

                        PadItem.writeDeviceId(draft, payload.deviceId());
                        PadItem.writeDocument(draft, draftDocument.copyWithLocked(false));
                        PadItem.writeDocumentToLockedCopies(player, payload.deviceId(), draftDocument);
                        ItemStack published = draft.copyWithCount(1);
                        PadItem.writeDeviceId(published, payload.deviceId());
                        PadItem.writeDocument(published, draftDocument.copyWithLocked(true));
                        if (!player.getInventory().add(published)) {
                            player.drop(published, false);
                        }

                        PadDeviceHolderTracker.invalidate(payload.deviceId());
                        NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new PadStateMirrorPacket(payload.deviceId(), draftDocument,
                                        MonotonicMediaClock.nowTick()));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
