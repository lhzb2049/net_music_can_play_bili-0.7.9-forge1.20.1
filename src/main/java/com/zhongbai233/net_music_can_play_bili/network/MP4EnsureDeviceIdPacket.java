package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 手动改写件（Forge 1.20.1）。由 `tools/port_manual.py` 覆盖进 stage1-src。
 *
 * <p>来源形态 `StreamCodec.composite(HAND_CODEC, packet -> packet.hand(),
 * hand -> new MP4EnsureDeviceIdPacket(hand))` —— 单字段的复合。</p>
 *
 * <p>`handle` 的处理逻辑与原文逐行一致，只改了两处平台差异：</p>
 * <ul>
 *   <li>`context.getSender() instanceof ServerPlayer player` → `getSender()` 的返回类型**就是**
 *       `ServerPlayer`，再用 instanceof 会被 javac 判为错误；改成取出后判空。</li>
 *   <li>发送已由发送点迁移统一改成 `NcpbNetwork.CHANNEL.send(...)`。</li>
 * </ul>
 */
public record MP4EnsureDeviceIdPacket(InteractionHand hand) {

    public static final String ID = "mp4_ensure_device_id";

    public static void encode(MP4EnsureDeviceIdPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.hand() == InteractionHand.OFF_HAND);
    }

    public static MP4EnsureDeviceIdPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return new MP4EnsureDeviceIdPacket(hand);
    }

    public static void handle(MP4EnsureDeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                if (NetworkRateLimiter.allow(player.getUUID(), "mp4_ensure_device_id", 4)) {
                    ItemStack stack = player.getItemInHand(payload.hand());
                    if (stack.getItem() instanceof MP4Item) {
                        ServerLevel level = (ServerLevel) player.level();
                        UUID deviceId = MP4DeviceIdentity.getOrCreateUnique(level, player, stack);
                        if (deviceId != null) {
                            NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                    new MP4DeviceIdPacket(payload.hand(), deviceId));
                            MP4DeviceStateStore.syncQueueCopy(level, deviceId, stack);
                            NcpbNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                    MP4DeviceStateMirrorPacket.fromEntry(
                                            deviceId,
                                            MP4DeviceStateStore.getOrCreate(level, deviceId, stack),
                                            AudioLinkIndex.hasHeadphoneLinkedToMp4(deviceId)));
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
