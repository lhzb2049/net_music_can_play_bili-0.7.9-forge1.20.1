package com.zhongbai233.net_music_can_play_bili.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import com.zhongbai233.net_music_can_play_bili.gui.HolographicScreenConfigTestScreen;
import com.zhongbai233.net_music_can_play_bili.link.EquippedMediaItems;
import com.zhongbai233.net_music_can_play_bili.link.HolographicGlassesAbility;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

@EventBusSubscriber({Dist.CLIENT})
public final class HolographicGlassesKeyHandler {
   private static final String CATEGORY = "key.categories.net_music_can_play_bili.main";
   private static final KeyMapping OPEN_CONFIG = new KeyMapping(
      "key.net_music_can_play_bili.holographic_glasses_config", Type.KEYSYM, 72, "key.categories.net_music_can_play_bili.main"
   );

   private HolographicGlassesKeyHandler() {
   }

   public static void register(RegisterKeyMappingsEvent event) {
      event.register(OPEN_CONFIG);
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft minecraft = Minecraft.getInstance();

      while (OPEN_CONFIG.consumeClick()) {
         if (minecraft.screen == null) {
            Player player = minecraft.player;
            if (player != null) {
               if (!HolographicGlassesAbility.has(EquippedMediaItems.firstHolographicGlasses(player))) {
                  player.sendSystemMessage(Component.translatable("message.net_music_can_play_bili.holographic_glasses.need_equipped_for_config"));
               } else {
                  minecraft.setScreen(new HolographicScreenConfigTestScreen(true));
               }
            }
         }
      }
   }
}
