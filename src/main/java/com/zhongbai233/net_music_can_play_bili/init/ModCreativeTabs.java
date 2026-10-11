package com.zhongbai233.net_music_can_play_bili.init;

import net.minecraft.world.item.enchantment.EnchantmentInstance;

import net.minecraft.world.item.EnchantedBookItem;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式物品栏。
 *
 * <p>本类来自外观层工程 `ncpb-1.20.1`（那里已经在 1.20.1 上跑通），
 * 组装进功能工程时只把 `P0ModEntry.MOD_ID` 换成 `NetMusicCanPlayBili.MODID`。</p>
 *
 * <p><b>为什么必须补它</b>：1.21.1 把页签注册在 `ModItems.TABS` 里，
 * 而组装用的 `ModItems` 是**外观层**那一版（没有 `TABS`）⇒ 主类里的
 * `ModItems.TABS.register(...)` 找不到符号。这不是"多写了一句"，是
 * 组装层的注册入口本来就是混的（见 PORT-STATUS 的待办⑥ `tools/assemble.py`）。</p>
 *
 * <p><b>已登记的差异</b>：1.21.1 的页签还往物品栏里放两本附魔书
 * （`HeadphoneAbility.HEADPHONES_KEY` / `HolographicGlassesAbility.HOLOGRAPHIC_GLASSES_KEY`）。
 * 1.20.1 的附魔是**代码注册**的（不是 1.21 的数据驱动），而本工程目前没有任何
 * 附魔注册类 ⇒ 第 91 次续**已补上**：见 init/ModEnchantments（1.20.1 口径见那里的类注释）。</p>
 */
public final class ModCreativeTabs {

   public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NetMusicCanPlayBili.MODID);

   public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
      .title(Component.translatable("itemGroup." + NetMusicCanPlayBili.MODID))
      .icon(() -> new ItemStack(ModItems.MODERN_TURNTABLE.get()))
      .displayItems((params, output) -> {
         output.accept(ModItems.MODERN_TURNTABLE.get());
         output.accept(ModItems.LYRIC_PROJECTOR.get());
         output.accept(ModItems.VIDEO_PROJECTOR.get());
         output.accept(ModItems.SPEAKER.get());
         output.accept(ModItems.LIVE_STREAMER.get());
         output.accept(ModItems.CONTROL_CONSOLE.get());
         output.accept(ModItems.MP4.get());
         output.accept(ModItems.PAD.get());
         output.accept(ModItems.MEDIA_MANAGEMENT_TOOL.get());
         output.accept(ModItems.INVISIBLE_HEADPHONES.get());
         output.accept(ModItems.CAT_HEADPHONES.get());
         output.accept(ModItems.HOLOGRAPHIC_GLASSES.get());
         // 第 91 次续：上游页签里的两本附魔书（用户实测指出缺了）。
         // 上游（1.21）是数据驱动附魔 + holders() 现查现造；1.20.1 用**代码注册**的附魔直接造书。
         // 语义差异见 init/ModEnchantments 的类注释（1.20.1 附魔不能给任意属性加成）。
         output.accept(EnchantedBookItem.createForEnchantment(
            new EnchantmentInstance(ModEnchantments.HEADPHONES.get(), 1)));
         output.accept(EnchantedBookItem.createForEnchantment(
            new EnchantmentInstance(ModEnchantments.HOLOGRAPHIC_GLASSES.get(), 1)));
      })
      .build());

   private ModCreativeTabs() {
   }

   public static void register(IEventBus bus) {
      TABS.register(bus);
   }
}
