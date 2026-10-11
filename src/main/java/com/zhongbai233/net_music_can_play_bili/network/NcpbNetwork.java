package com.zhongbai233.net_music_can_play_bili.network;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本模组的网络注册器（1.20.1 / Forge）。
 *
 * <p><b>由 `tools/port_network_registrar.py` 从 NeoForge 版注册器生成</b>，
 * 方向直接取自原文的 playToServer/playToClient —— 没有推断成分。
 * 目标写法与 tools/_net-probe/…/NcpbNetwork.java 里那份**编译验证过**的样板一致。</p>
 *
 * <p>共 42 个包：18 个 TO_CLIENT / 24 个 TO_SERVER</p>
 *
 * <p>包 id 按下面的出现顺序从 0 递增，**顺序不能改**：改了就是客户端/服务端包号错位。</p>
 */
public final class NcpbNetwork {

    /** 协议版本：客户端与服务端不一致时握手直接失败（沿用原文的版本号）。 */
    private static final String PROTOCOL = "6";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Objects.requireNonNull(ResourceLocation.tryBuild("net_music_can_play_bili", "main")),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private NcpbNetwork() {
    }

    /** 在 mod 构造期调用一次（Forge 侧不走 mod 事件总线）。 */
    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(ModernTurntableControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ModernTurntableControlPacket::encode)
                .decoder(ModernTurntableControlPacket::decode)
                .consumerMainThread(ModernTurntableControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(ModernTurntableStopPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ModernTurntableStopPacket::encode)
                .decoder(ModernTurntableStopPacket::decode)
                .consumerMainThread(ModernTurntableStopPacket::handle)
                .add();
        CHANNEL.messageBuilder(AudioEndpointSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AudioEndpointSnapshotPacket::encode)
                .decoder(AudioEndpointSnapshotPacket::decode)
                .consumerMainThread(AudioEndpointSnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(AreaAudioListenerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AreaAudioListenerPacket::encode)
                .decoder(AreaAudioListenerPacket::decode)
                .consumerMainThread(AreaAudioListenerPacket::handle)
                .add();
        CHANNEL.messageBuilder(LiveStreamerControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LiveStreamerControlPacket::encode)
                .decoder(LiveStreamerControlPacket::decode)
                .consumerMainThread(LiveStreamerControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(LyricProjectorConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LyricProjectorConfigPacket::encode)
                .decoder(LyricProjectorConfigPacket::decode)
                .consumerMainThread(LyricProjectorConfigPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpeakerConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SpeakerConfigPacket::encode)
                .decoder(SpeakerConfigPacket::decode)
                .consumerMainThread(SpeakerConfigPacket::handle)
                .add();
        CHANNEL.messageBuilder(VideoProjectorConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VideoProjectorConfigPacket::encode)
                .decoder(VideoProjectorConfigPacket::decode)
                .consumerMainThread(VideoProjectorConfigPacket::handle)
                .add();
        CHANNEL.messageBuilder(HolographicGlassesConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HolographicGlassesConfigPacket::encode)
                .decoder(HolographicGlassesConfigPacket::decode)
                .consumerMainThread(HolographicGlassesConfigPacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ControlConsoleConfigPacket::encode)
                .decoder(ControlConsoleConfigPacket::decode)
                .consumerMainThread(ControlConsoleConfigPacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleAccessPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ControlConsoleAccessPacket::encode)
                .decoder(ControlConsoleAccessPacket::decode)
                .consumerMainThread(ControlConsoleAccessPacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleEditLeasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ControlConsoleEditLeasePacket::encode)
                .decoder(ControlConsoleEditLeasePacket::decode)
                .consumerMainThread(ControlConsoleEditLeasePacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleEditLeaseResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ControlConsoleEditLeaseResultPacket::encode)
                .decoder(ControlConsoleEditLeaseResultPacket::decode)
                .consumerMainThread(ControlConsoleEditLeaseResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleConsumerLeasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ControlConsoleConsumerLeasePacket::encode)
                .decoder(ControlConsoleConsumerLeasePacket::decode)
                .consumerMainThread(ControlConsoleConsumerLeasePacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleConsumerLeaseResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ControlConsoleConsumerLeaseResultPacket::encode)
                .decoder(ControlConsoleConsumerLeaseResultPacket::decode)
                .consumerMainThread(ControlConsoleConsumerLeaseResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(ControlConsoleConfigResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ControlConsoleConfigResultPacket::encode)
                .decoder(ControlConsoleConfigResultPacket::decode)
                .consumerMainThread(ControlConsoleConfigResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(ClearEquippedBindingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClearEquippedBindingPacket::encode)
                .decoder(ClearEquippedBindingPacket::decode)
                .consumerMainThread(ClearEquippedBindingPacket::handle)
                .add();
        CHANNEL.messageBuilder(MediaToolConfirmBindingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MediaToolConfirmBindingPacket::encode)
                .decoder(MediaToolConfirmBindingPacket::decode)
                .consumerMainThread(MediaToolConfirmBindingPacket::handle)
                .add();
        CHANNEL.messageBuilder(MediaToolClearBindingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MediaToolClearBindingPacket::encode)
                .decoder(MediaToolClearBindingPacket::decode)
                .consumerMainThread(MediaToolClearBindingPacket::handle)
                .add();
        CHANNEL.messageBuilder(MediaToolReportPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MediaToolReportPacket::encode)
                .decoder(MediaToolReportPacket::decode)
                .consumerMainThread(MediaToolReportPacket::handle)
                .add();
        CHANNEL.messageBuilder(WhitelistReviewActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WhitelistReviewActionPacket::encode)
                .decoder(WhitelistReviewActionPacket::decode)
                .consumerMainThread(WhitelistReviewActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4StatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MP4StatePacket::encode)
                .decoder(MP4StatePacket::decode)
                .consumerMainThread(MP4StatePacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4PlaybackControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MP4PlaybackControlPacket::encode)
                .decoder(MP4PlaybackControlPacket::decode)
                .consumerMainThread(MP4PlaybackControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4PlaybackRetryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MP4PlaybackRetryPacket::encode)
                .decoder(MP4PlaybackRetryPacket::decode)
                .consumerMainThread(MP4PlaybackRetryPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4EnsureDeviceIdPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MP4EnsureDeviceIdPacket::encode)
                .decoder(MP4EnsureDeviceIdPacket::decode)
                .consumerMainThread(MP4EnsureDeviceIdPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4EnsureInventoryDeviceIdPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MP4EnsureInventoryDeviceIdPacket::encode)
                .decoder(MP4EnsureInventoryDeviceIdPacket::decode)
                .consumerMainThread(MP4EnsureInventoryDeviceIdPacket::handle)
                .add();
        CHANNEL.messageBuilder(PadStatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PadStatePacket::encode)
                .decoder(PadStatePacket::decode)
                .consumerMainThread(PadStatePacket::handle)
                .add();
        CHANNEL.messageBuilder(PadPublishPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PadPublishPacket::encode)
                .decoder(PadPublishPacket::decode)
                .consumerMainThread(PadPublishPacket::handle)
                .add();
        CHANNEL.messageBuilder(PadPlaybackControlPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PadPlaybackControlPacket::encode)
                .decoder(PadPlaybackControlPacket::decode)
                .consumerMainThread(PadPlaybackControlPacket::handle)
                .add();
        CHANNEL.messageBuilder(PadPlaybackRetryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PadPlaybackRetryPacket::encode)
                .decoder(PadPlaybackRetryPacket::decode)
                .consumerMainThread(PadPlaybackRetryPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4DeviceIdPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MP4DeviceIdPacket::encode)
                .decoder(MP4DeviceIdPacket::decode)
                .consumerMainThread(MP4DeviceIdPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4InventoryDeviceIdPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MP4InventoryDeviceIdPacket::encode)
                .decoder(MP4InventoryDeviceIdPacket::decode)
                .consumerMainThread(MP4InventoryDeviceIdPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4ContainerDeviceIdPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MP4ContainerDeviceIdPacket::encode)
                .decoder(MP4ContainerDeviceIdPacket::decode)
                .consumerMainThread(MP4ContainerDeviceIdPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4DeviceStateMirrorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MP4DeviceStateMirrorPacket::encode)
                .decoder(MP4DeviceStateMirrorPacket::decode)
                .consumerMainThread(MP4DeviceStateMirrorPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4PlaybackSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MP4PlaybackSyncPacket::encode)
                .decoder(MP4PlaybackSyncPacket::decode)
                .consumerMainThread(MP4PlaybackSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(MP4PlaybackTimelinePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MP4PlaybackTimelinePacket::encode)
                .decoder(MP4PlaybackTimelinePacket::decode)
                .consumerMainThread(MP4PlaybackTimelinePacket::handle)
                .add();
        CHANNEL.messageBuilder(PadStateMirrorPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PadStateMirrorPacket::encode)
                .decoder(PadStateMirrorPacket::decode)
                .consumerMainThread(PadStateMirrorPacket::handle)
                .add();
        CHANNEL.messageBuilder(WhitelistCsvExportPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WhitelistCsvExportPacket::encode)
                .decoder(WhitelistCsvExportPacket::decode)
                .consumerMainThread(WhitelistCsvExportPacket::handle)
                .add();
        CHANNEL.messageBuilder(WhitelistReviewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WhitelistReviewPacket::encode)
                .decoder(WhitelistReviewPacket::decode)
                .consumerMainThread(WhitelistReviewPacket::handle)
                .add();
        CHANNEL.messageBuilder(WhitelistReviewMutationResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WhitelistReviewMutationResultPacket::encode)
                .decoder(WhitelistReviewMutationResultPacket::decode)
                .consumerMainThread(WhitelistReviewMutationResultPacket::handle)
                .add();
        CHANNEL.messageBuilder(WhitelistPreviewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WhitelistPreviewPacket::encode)
                .decoder(WhitelistPreviewPacket::decode)
                .consumerMainThread(WhitelistPreviewPacket::handle)
                .add();
        CHANNEL.messageBuilder(PadMapWorldScopePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PadMapWorldScopePacket::encode)
                .decoder(PadMapWorldScopePacket::decode)
                .consumerMainThread(PadMapWorldScopePacket::handle)
                .add();
    }
}
