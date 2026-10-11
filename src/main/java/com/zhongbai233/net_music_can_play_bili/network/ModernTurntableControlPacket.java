package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
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
 * <p>来源是 `StreamCodec.composite(...)`，其中 targetMillis 用的是**内联匿名 codec**
 * （`readVarLong`/`writeVarLong`）。字段顺序：pos → action(VarInt) → targetMillis(VarLong)。</p>
 */
public record ModernTurntableControlPacket(BlockPos pos, ModernTurntableControlPacket.Action action,
                                          long targetMillis) {

    public static final String ID = "modern_turntable_control";

    public static void encode(ModernTurntableControlPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeVarInt(packet.action().id());
        buffer.writeVarLong(packet.targetMillis());
    }

    public static ModernTurntableControlPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        Action action = Action.byId(buffer.readVarInt());
        long targetMillis = buffer.readVarLong();
        return new ModernTurntableControlPacket(pos, action, targetMillis);
    }

    public static void handle(ModernTurntableControlPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // getSender() 的返回类型就是 ServerPlayer，不能再用 instanceof（javac 会报错）
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel level) {
                if (!(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)) {
                    if (level.getBlockEntity(payload.pos()) instanceof ModernTurntableBlockEntity turntable) {
                        switch (payload.action()) {
                            case REPLAY:
                                turntable.replayFromBeginning(player);
                                break;
                            case PAUSE:
                                turntable.pauseFromControl(level);
                                break;
                            case START:
                                turntable.resumePlayback(player, payload.targetMillis());
                                break;
                            case SEEK:
                                turntable.seekTo(level, payload.targetMillis());
                                break;
                            case TOGGLE_REPEAT_ONE:
                                turntable.toggleRepeatOne();
                                break;
                            case CYCLE_REDSTONE_MODE:
                                turntable.cycleRedstoneMode(level);
                                break;
                            case CYCLE_EXTRACTION_MODE:
                                turntable.cycleExtractionMode();
                                break;
                            case SET_VOLUME:
                                if (player.mayBuild()) {
                                    turntable.setVolumePerMille(
                                            (int) Math.max(0L, Math.min(1000L, payload.targetMillis())));
                                }
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    public static enum Action {
        REPLAY,
        PAUSE,
        START,
        SEEK,
        TOGGLE_REPEAT_ONE,
        CYCLE_REDSTONE_MODE,
        CYCLE_EXTRACTION_MODE,
        SET_VOLUME;

        public int id() {
            return this.ordinal();
        }

        public static ModernTurntableControlPacket.Action byId(int id) {
            ModernTurntableControlPacket.Action[] values = values();
            return id >= 0 && id < values.length ? values[id] : START;
        }
    }
}
