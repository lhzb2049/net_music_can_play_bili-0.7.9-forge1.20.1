package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.LiveStreamerBlockEntity;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * 手动改写件（Forge 1.20.1）。由 `tools/port_manual.py` 覆盖进 stage1-src。
 *
 * <p>来源是 `StreamCodec.composite(...)`，字段顺序 = 参数表里 codec/getter 的交替顺序：
 * pos → action → roomInput → value。encode/decode 必须保持这个顺序。</p>
 *
 * <p>codec 翻译：`BlockPos.STREAM_CODEC` → writeBlockPos/readBlockPos；
 * `ACTION_CODEC`（自定义 VarInt 枚举）→ 内联成 writeVarInt/readVarInt；
 * `ByteBufCodecs.stringUtf8(256)` → writeUtf(s, 256)/readUtf(256)；
 * `ByteBufCodecs.VAR_INT` → writeVarInt/readVarInt。</p>
 */
public record LiveStreamerControlPacket(BlockPos pos, LiveStreamerControlPacket.Action action,
                                       String roomInput, int value) {

    public static final String ID = "live_streamer_control";

    private static final int MAX_ROOM_INPUT_LENGTH = 256;

    public static void encode(LiveStreamerControlPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.action().id());
        buffer.writeUtf(packet.roomInput(), MAX_ROOM_INPUT_LENGTH);
        buffer.writeVarInt(packet.value());
    }

    public static LiveStreamerControlPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        Action action = Action.byId(buffer.readVarInt());
        String roomInput = buffer.readUtf(MAX_ROOM_INPUT_LENGTH);
        int value = buffer.readVarInt();
        return new LiveStreamerControlPacket(pos, action, roomInput, value);
    }

    public static void handle(LiveStreamerControlPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 注意：getSender() 的返回类型就是 ServerPlayer，不能再用 instanceof（javac 会报错）
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel level) {
                if (!(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)) {
                    if (level.getBlockEntity(payload.pos()) instanceof LiveStreamerBlockEntity streamer) {
                        switch (payload.action()) {
                            case START:
                                if (streamer.setRoomId(level, payload.roomInput(), player)) {
                                    streamer.startLive(level, player);
                                }
                                break;
                            case STOP:
                                streamer.stopLive();
                                break;
                            case SET_ROOM:
                                streamer.setRoomId(level, payload.roomInput(), player);
                                break;
                            case SET_VOLUME:
                                if (player.mayBuild()) {
                                    streamer.setVolumePerMille(Math.max(0, Math.min(1000, payload.value())));
                                }
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    public static enum Action {
        START,
        STOP,
        SET_ROOM,
        SET_VOLUME;

        public int id() {
            return this.ordinal();
        }

        public static LiveStreamerControlPacket.Action byId(int id) {
            LiveStreamerControlPacket.Action[] values = values();
            return id >= 0 && id < values.length ? values[id] : STOP;
        }
    }
}
