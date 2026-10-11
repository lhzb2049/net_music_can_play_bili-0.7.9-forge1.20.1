package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.client.MP4Client;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

/**
 * 手动改写件（Forge 1.20.1）。由 `tools/port_manual.py` 覆盖进 stage1-src。
 *
 * <p>来源形态 `StreamCodec.composite(HAND_CODEC, packet -> packet.hand(),
 * UUID_CODEC, packet -> packet.deviceId(), (hand, deviceId) -> new ...)`。</p>
 *
 * <p>复合顺序：先 hand（1 个 boolean），再 deviceId（UUID）——
 * encode 与 decode 必须保持同一顺序。</p>
 */
public record MP4DeviceIdPacket(InteractionHand hand, UUID deviceId) {

    public static final String ID = "mp4_device_id";

    public static void encode(MP4DeviceIdPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.hand() == InteractionHand.OFF_HAND);
        buffer.writeUUID(packet.deviceId());
    }

    public static MP4DeviceIdPacket decode(FriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        UUID deviceId = buffer.readUUID();
        return new MP4DeviceIdPacket(hand, deviceId);
    }

    public static void handle(MP4DeviceIdPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> MP4Client.receiveDeviceId(payload.hand(), payload.deviceId()));
        context.setPacketHandled(true);
    }
}
