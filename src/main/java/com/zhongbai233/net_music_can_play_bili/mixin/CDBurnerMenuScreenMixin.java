package com.zhongbai233.net_music_can_play_bili.mixin;

import com.github.tartaricacid.netmusic.client.gui.CDBurnerMenuScreen;
import com.github.tartaricacid.netmusic.item.ItemMusicCD.SongInfo;
import com.github.tartaricacid.netmusic.network.NetworkHandler;
import com.github.tartaricacid.netmusic.network.message.SetMusicIDMessage;
import com.zhongbai233.net_music_can_play_bili.bili.BiliApiClient;
import com.zhongbai233.net_music_can_play_bili.bili.BiliAudioResolver;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {CDBurnerMenuScreen.class}
)
public abstract class CDBurnerMenuScreenMixin {
   @Shadow
   private EditBox textField;
   @Shadow
   private Checkbox readOnlyButton;
   @Shadow
   private Component tips;
   @Unique
   private EditBox net_music_can_play_bili$pageField;
   @Unique
   private AbstractWidget net_music_can_play_bili$pageLabel;

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void net_music_can_play_bili$initPageField(CallbackInfo ci) {
      String pageValue = "1";
      boolean focused = false;
      if (this.net_music_can_play_bili$pageField != null) {
         pageValue = this.net_music_can_play_bili$pageField.getValue();
         focused = this.net_music_can_play_bili$pageField.isFocused();
      }

      this.net_music_can_play_bili$pageField = new EditBox(
         Minecraft.getInstance().font, this.textField.getX() + 100, this.textField.getY() + 30, 28, 16, Component.literal("P")
      );
      this.net_music_can_play_bili$pageField.setValue(pageValue != null && !pageValue.isBlank() ? pageValue : "1");
      this.net_music_can_play_bili$pageField.setBordered(true);
      this.net_music_can_play_bili$pageField.setMaxLength(4);
      this.net_music_can_play_bili$pageField.setTextColor(-790560);
      this.net_music_can_play_bili$pageField.setFocused(focused);
      int labelX = this.net_music_can_play_bili$pageField.getX();
      int labelY = this.net_music_can_play_bili$pageField.getY() - 10;
      this.net_music_can_play_bili$pageLabel = new AbstractWidget(labelX, labelY, 24, 12, Component.literal("分P")) {
         protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, "B站分P", this.getX(), this.getY(), -1);
         }

         public void updateWidgetNarration(NarrationElementOutput output) {
         }
      };
      // 第 91 次：原来走 ScreenAccessor(@Invoker addRenderableWidget)，而那个 @Invoker **在 refmap 里
      // 没有映射**（泛型方法，AP 生成不出条目）⇒ 运行期在 SRG 环境下找不到 `addRenderableWidget`
      // （它实际叫 m_142416_），Screen 的 mixin 应用直接失败。改用 AT 把该方法放开成 public，直接调。
      net.minecraft.client.gui.screens.Screen screen =
         (net.minecraft.client.gui.screens.Screen)(Object)this;
      screen.addRenderableWidget(this.net_music_can_play_bili$pageLabel);
      screen.addRenderableWidget(this.net_music_can_play_bili$pageField);
   }

   @Inject(
      method = {"handleCraftButton"},
      at = {@At("HEAD")},
      remap = false,
      cancellable = true
   )
   private void onHandleCraftButton(CallbackInfo ci) {
      String text = this.textField.getValue().trim();
      if (BiliApiClient.containsBiliVideoId(text)) {
         int page = 1;
         if (this.net_music_can_play_bili$pageField != null) {
            String pageText = this.net_music_can_play_bili$pageField.getValue().trim();
            if (!pageText.isEmpty()) {
               try {
                  page = Integer.parseInt(pageText);
               } catch (NumberFormatException var6) {
                  this.tips = Component.literal("分P 请输入正整数");
                  ci.cancel();
                  return;
               }
            }
         }

         if (page < 1) {
            this.tips = Component.literal("分P 请输入正整数");
            ci.cancel();
         } else {
            int selectedPage = page;
            this.tips = Component.literal("正在解析 B站 音频，请稍候...");
            CompletableFuture.<SongInfo>supplyAsync(() -> {
               try {
                  return BiliAudioResolver.resolveBiliSongInfo(text, selectedPage);
               } catch (Exception var3x) {
                  throw new RuntimeException(var3x);
               }
            }).whenComplete((info, error) -> Minecraft.getInstance().execute(() -> {
               if (error != null) {
                  Throwable cause = error.getCause() != null ? error.getCause() : error;
                  this.tips = Component.literal("B站解析失败: " + cause.getMessage());
               } else {
                  info.readOnly = this.readOnlyButton.selected();
                  // 1.20.1 的 netmusic `NetworkHandler` **没有** `sendToServer(...)`（`javap -p` 实证：
                  // 只有 sendToNearby / sendToClientPlayer + 公开字段 `CHANNEL`）⇒ 走它的 SimpleChannel。
                  NetworkHandler.CHANNEL.sendToServer(new SetMusicIDMessage(info));
               }
            }));
            ci.cancel();
         }
      }
   }
}
