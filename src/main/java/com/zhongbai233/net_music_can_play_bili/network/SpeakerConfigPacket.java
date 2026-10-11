package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.blockentity.SpeakerBlockEntity;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/**
 * 手动改写件（Forge 1.20.1）。**由 `tools/port_manual.py` 在自动改造之后覆盖进 stage1-src。**
 *
 * <p>来源是 NeoForge 的 `StreamCodec.composite(...)` 形态 —— 这种形态**没有**做 codemod：
 * 它把"字段顺序"分散在 codec / getter 交替的参数表里，还要逐个翻译
 * `ByteBufCodecs.*` 与 `BlockPos.STREAM_CODEC`，机械替换容易改错顺序，
 * 而顺序错了就是**包体和字段错位**（能编译、但语义错）—— 所以这 5 个包手工写。</p>
 *
 * <p>逐项映射（1.21 的 codec → 1.20.1 的 FriendlyByteBuf 方法）：</p>
 * <pre>
 *   BlockPos.STREAM_CODEC     -> writeBlockPos / readBlockPos
 *   ByteBufCodecs.INT         -> writeInt      / readInt
 *   ByteBufCodecs.FLOAT       -> writeFloat    / readFloat
 *   ByteBufCodecs.BOOL        -> writeBoolean  / readBoolean
 *   ByteBufCodecs.VAR_INT     -> writeVarInt   / readVarInt
 *   ByteBufCodecs.stringUtf8(n) -> writeUtf(s, n) / readUtf(n)
 * </pre>
 *
 * <p>另外 `IPayloadContext.getSender()` → `NetworkEvent.Context.getSender()`。</p>
 */
public record SpeakerConfigPacket(BlockPos pos, int channelIndex, float volume, boolean autoMixJoc) {

    /** 稳定的注册 id（集中登记在 {@link NcpbNetwork#register()}）。 */
    public static final String ID = "speaker_config";

    public static void encode(SpeakerConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos());
        buffer.writeInt(packet.channelIndex());
        buffer.writeFloat(packet.volume());
        buffer.writeBoolean(packet.autoMixJoc());
    }

    public static SpeakerConfigPacket decode(FriendlyByteBuf buffer) {
        // 顺序必须与 encode 严格一致 —— 顺序错了能编译，但字段会错位
        BlockPos pos = buffer.readBlockPos();
        int channelIndex = buffer.readInt();
        float volume = buffer.readFloat();
        boolean autoMixJoc = buffer.readBoolean();
        return new SpeakerConfigPacket(pos, channelIndex, volume, autoMixJoc);
    }

    public static void handle(SpeakerConfigPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 注意：Forge 的 getSender() 声明返回类型**就是** ServerPlayer，
            // 所以不能再写 `instanceof ServerPlayer player`（javac 报
            // "expression type ServerPlayer is a subtype of pattern type ServerPlayer"）；
            // 客户端侧它会返回 null，所以要判空。
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel level) {
                if (!(player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0)) {
                    if (level.getBlockEntity(payload.pos()) instanceof SpeakerBlockEntity be) {
                        be.setChannelIndex(payload.channelIndex());
                        be.setVolume(payload.volume());
                        be.setAutoMixJoc(payload.autoMixJoc());
                        be.markDirtyAndSync();
                    }
                }
            }
        });
        // Forge 侧必须显式声明已处理，否则包会被丢弃
        context.setPacketHandled(true);
    }
}
