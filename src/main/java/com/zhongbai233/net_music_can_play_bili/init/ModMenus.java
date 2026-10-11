package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.menu.MediaToolBindingMenu;
import com.zhongbai233.net_music_can_play_bili.menu.MediaToolReportMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModMenus {
   public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, "net_music_can_play_bili");
   public static final RegistryObject<MenuType<MediaToolBindingMenu>> MEDIA_TOOL_BINDING = MENU_TYPES.register(
      "media_tool_binding", () -> new MenuType<MediaToolBindingMenu>(MediaToolBindingMenu::new, FeatureFlags.DEFAULT_FLAGS)
   );
   public static final RegistryObject<MenuType<MediaToolReportMenu>> MEDIA_TOOL_REPORT = MENU_TYPES.register(
      "media_tool_report", () -> new MenuType<MediaToolReportMenu>(MediaToolReportMenu::new, FeatureFlags.DEFAULT_FLAGS)
   );

   private ModMenus() {
   }
}
