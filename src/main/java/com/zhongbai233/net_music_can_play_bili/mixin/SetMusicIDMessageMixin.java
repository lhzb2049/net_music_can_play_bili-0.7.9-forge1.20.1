package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.item.ItemMusicCD.SongInfo;
import com.github.tartaricacid.netmusic.network.message.SetMusicIDMessage;
import com.zhongbai233.net_music_can_play_bili.bili.BiliSongInfoSanitizer;
import com.zhongbai233.net_music_can_play_bili.port.bridge.NetmusicMessageAccess;
import com.zhongbai233.net_music_can_play_bili.server.BiliWhitelistManager;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦住"创建唱片/音源头"里的 B 站直链（白名单）。
 *
 * <p><b>第 91 次把它修回可编译的（它列在 `mixins.json` 里，缺失就是启动崩溃）</b>，
 * 三处 1.21.1 → 1.20.1 的差异（都有 `javap -p` 实证）：</p>
 *
 * <ol>
 *   <li>`message.song()` —— 1.20.1 的 netmusic `SetMusicIDMessage` 的 `song` 是
 *       `private final` 且**没有访问器** ⇒ 走 {@link NetmusicMessageAccess} 反射读；</li>
 *   <li>`context.flow().isServerbound()` —— 1.20.1 的 `NetworkEvent.Context` 没有 `flow()`，
 *       只有 `getDirection()` ⇒ 判 `== NetworkDirection.PLAY_TO_SERVER`；</li>
 *   <li>`SetMusicIDMessage.handle(msg, context)` —— 1.20.1 的第二参是
 *       `Supplier&lt;NetworkEvent.Context&gt;` ⇒ 传 `() -&gt; context`。</li>
 * </ol>
 */
@Mixin(
   value = {SetMusicIDMessage.class},
   remap = false
)
public abstract class SetMusicIDMessageMixin {
   @Inject(
      method = {"handle"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void net_music_can_play_bili$guardBiliWhitelist(SetMusicIDMessage message, Supplier<NetworkEvent.Context> contextSupplier, CallbackInfo ci) {
      NetworkEvent.Context context = contextSupplier.get();
      if (message != null && context != null && context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
         SongInfo song = (SongInfo)NetmusicMessageAccess.setMusicIdSong(message);
         String songUrl = song == null ? null : song.songUrl;
         SongInfo normalized = BiliSongInfoSanitizer.sanitize(song);
         if (normalized != song) {
            SetMusicIDMessage.handle(new SetMusicIDMessage(Objects.requireNonNull(normalized)), () -> context);
            ci.cancel();
         } else {
            songUrl = normalized == null ? songUrl : normalized.songUrl;
            boolean forbiddenBiliDirectUrl = BiliSongInfoSanitizer.isForbiddenBiliDirectUrl(songUrl);
            if (forbiddenBiliDirectUrl || BiliWhitelistManager.enabled() && !BiliWhitelistManager.canonicalResource(songUrl).isEmpty()) {
               ServerPlayer player = context.getSender();
               if (player != null) {
                  if (forbiddenBiliDirectUrl) {
                     player.sendSystemMessage(BiliWhitelistManager.denialMessage(player, songUrl, "创建唱片/音源头"));
                     ci.cancel();
                  } else if (!BiliWhitelistManager.isAllowed(player.level().getServer(), songUrl)) {
                     player.sendSystemMessage(BiliWhitelistManager.denialMessage(player, songUrl, "创建唱片/音源头"));
                     ci.cancel();
                  }
               } else {
                  ci.cancel();
               }
            }
         }
      }
   }
}
