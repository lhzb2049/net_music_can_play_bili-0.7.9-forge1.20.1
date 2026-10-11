package com.zhongbai233.net_music_can_play_bili.network;

import com.zhongbai233.net_music_can_play_bili.menu.MediaToolReportMenu;
import com.zhongbai233.net_music_can_play_bili.server.PlaybackAuditManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MediaToolReportPacket(String sourceKey) {
   private static final int MAX_KEY_LENGTH = 256;
   public static final String ID = "media_tool_report";
   public static MediaToolReportPacket decode(FriendlyByteBuf buffer) {
      return new MediaToolReportPacket(buffer.readUtf(256));
   }

   public static void encode(MediaToolReportPacket packet, FriendlyByteBuf buffer) {
      buffer.writeUtf(packet.sourceKey() != null ? packet.sourceKey() : "", 256);
   }


   public static void handle(MediaToolReportPacket payload, Supplier<NetworkEvent.Context> contextSupplier) {
      ServerPlayer player = contextSupplier.get().getSender();
      if (player != null
         && player.containerMenu instanceof MediaToolReportMenu menu
         && menu.containsSourceKey(payload.sourceKey())) {
         PlaybackAuditManager.ActiveSource source = PlaybackAuditManager.findByKey(player.level().getServer(), payload.sourceKey());
         if (source == null) {
            player.sendSystemMessage(Component.translatable("message.net_music_can_play_bili.media_tool.no_active_source").withStyle(ChatFormatting.RED));
         } else {
            PlaybackAuditManager.ReportResult result = PlaybackAuditManager.notifyOpsOfReport(player, source, "玩家使用媒体管理工具举报");
            String messageKey = result.notifiedOps()
               ? "message.net_music_can_play_bili.media_tool.report_sent"
               : (result.merged() ? "message.net_music_can_play_bili.media_tool.report_merged" : "message.net_music_can_play_bili.media_tool.report_logged");
            player.sendSystemMessage(
               Component.translatable(messageKey, new Object[]{result.totalReports(), result.uniqueReporters()}).withStyle(ChatFormatting.GOLD)
            );
            player.closeContainer();
         }
      }
   }
}
