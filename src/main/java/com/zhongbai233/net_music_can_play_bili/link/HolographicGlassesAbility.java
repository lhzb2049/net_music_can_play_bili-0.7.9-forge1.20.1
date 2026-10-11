package com.zhongbai233.net_music_can_play_bili.link;

import com.zhongbai233.net_music_can_play_bili.init.ModAttributes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class HolographicGlassesAbility {
   public static final ResourceLocation HOLOGRAPHIC_GLASSES_ID = ResourceLocation.tryBuild("net_music_can_play_bili", "holographic_glasses");
   public static final ResourceKey<Enchantment> HOLOGRAPHIC_GLASSES_KEY = ResourceKey.create(Registries.ENCHANTMENT, HOLOGRAPHIC_GLASSES_ID);
   public static final TagKey<Enchantment> HOLOGRAPHIC_GLASSES_ENCHANTMENT_TAG = TagKey.create(Registries.ENCHANTMENT, HOLOGRAPHIC_GLASSES_ID);

   private HolographicGlassesAbility() {
   }

   public static boolean has(ItemStack stack) {
      return stack.isEmpty() ? false : isHeadEquipment(stack)
            && (hasHolographicAttribute(stack) || hasHolographicEnchantment(stack));
   }

   private static boolean isHeadEquipment(ItemStack stack) {
      Equipable equipable = Equipable.get(stack);
      return equipable != null && equipable.getEquipmentSlot() == EquipmentSlot.HEAD;
   }

   private static boolean hasHolographicAttribute(ItemStack stack) {
      boolean[] found = new boolean[]{false};
      stack.getAttributeModifiers(EquipmentSlot.HEAD).forEach((attribute, modifier) -> {
         if (attribute == ModAttributes.HOLOGRAPHIC_GLASSES.get() && modifier.getAmount() > 0.0) {
            found[0] = true;
         }
      });
      return found[0];
   }

   /**
    * 第 91 次续：**1.20.1 的附魔不能给任意属性加成**（那是 1.21 的数据驱动效果），
    * 所以"耳机附魔打在头盔上＝这头盔是耳机"这条语义改为**直接查附魔等级**。
    * 与上游"附魔给 HEADPHONES 属性"达到的效果一致。
    */
   private static boolean hasHolographicEnchantment(ItemStack stack) {
      return com.zhongbai233.net_music_can_play_bili.init.ModEnchantments.HOLOGRAPHIC_GLASSES.isPresent()
         && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
               com.zhongbai233.net_music_can_play_bili.init.ModEnchantments.HOLOGRAPHIC_GLASSES.get(), stack) > 0;
   }
}
