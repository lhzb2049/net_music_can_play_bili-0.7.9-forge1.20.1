package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.network.message.MusicToClientMessage;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.audio.ClientMediaPreparer;
import com.zhongbai233.net_music_can_play_bili.client.audio.ClientPlaybackCommand;
import com.zhongbai233.net_music_can_play_bili.client.audio.ModernTurntablePlaybackCoordinator;
import com.zhongbai233.net_music_can_play_bili.media.sync.PlaybackSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.zhongbai233.net_music_can_play_bili.port.bridge.NetmusicMessageAccess;

@Mixin(
   value = {MusicToClientMessage.class},
   remap = false
)
public abstract class MusicToClientMessageClientMixin {
   @Inject(
      method = {"onHandle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void net_music_can_play_bili$injectBiliLyrics(MusicToClientMessage message, CallbackInfo ci) {
      PlaybackSync.Metadata sync = PlaybackSync.parse(NetmusicMessageAccess.url(message));
      boolean modernTurntable = sync.hasSession() || net_music_can_play_bili$isModernTurntable(message);
      boolean biliSelection = ClientMediaPreparer.hasStoredBiliSelection(NetmusicMessageAccess.rawUrl(message), NetmusicMessageAccess.url(message));
      if (modernTurntable || biliSelection) {
         if (modernTurntable) {
            PlaybackSync.MinecartAnchor minecartAnchor = PlaybackSync.parseMinecartAnchor(NetmusicMessageAccess.url(message));
            ClientPlaybackCommand command = ModernTurntablePlaybackCoordinator.command(
               NetmusicMessageAccess.pos(message), NetmusicMessageAccess.rawUrl(message), NetmusicMessageAccess.url(message), NetmusicMessageAccess.songName(message), NetmusicMessageAccess.timeSecond(message), sync, minecartAnchor, biliSelection
            );
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> ModernTurntablePlaybackCoordinator.play(command));
            ci.cancel();
         } else {
            ClientPlaybackCommand command = ModernTurntablePlaybackCoordinator.command(
               NetmusicMessageAccess.pos(message), NetmusicMessageAccess.rawUrl(message), NetmusicMessageAccess.url(message), NetmusicMessageAccess.songName(message), NetmusicMessageAccess.timeSecond(message), sync, null, biliSelection
            );
            Minecraft.getInstance().execute(() -> ModernTurntablePlaybackCoordinator.playCompatible(command));
            ci.cancel();
         }
      }
   }

   @Unique
   private static boolean net_music_can_play_bili$isModernTurntable(MusicToClientMessage message) {
      ClientLevel level = Minecraft.getInstance().level;
      return level != null && level.getBlockEntity(NetmusicMessageAccess.pos(message)) instanceof ModernTurntableBlockEntity;
   }
}
