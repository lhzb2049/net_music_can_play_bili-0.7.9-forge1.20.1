package com.zhongbai233.net_music_can_play_bili.item;

import com.zhongbai233.net_music_can_play_bili.link.AudioLinkData;
import com.zhongbai233.net_music_can_play_bili.link.AudioLinkIndex;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level;

public class InvisibleHeadphonesItem extends Item implements Equipable {

   /**
    * 耳机属性的修饰符 UUID。
    *
    * <p>⚠️ **不能用** 1.20.1 的 `AttributeModifier(String, double, Operation)` 三参构造器 ——
    * `javap -c` 实证它内部走 `Mth.createInsecureUUID(RandomSource)`，
    * **每次构造出来的 UUID 都不同**，修饰符的身份就不稳定了（1.21 那三参是确定性的）。
    * 所以这里用带显式 UUID 的四参构造器，并且 UUID 是**写死的常量**：
    * 换掉它 = 改变修饰符身份，跨存档会静默变成"两个不同的修饰符"。</p>
    */
   private static final UUID HEADPHONES_MODIFIER_ID = UUID.fromString("8f2a1c3e-4b5d-4e6f-9a7b-1c2d3e4f5a60");

   /**
    * ⚠️ **修饰符必须在调用时构造，不能放进 `static` 初始化。**
    *
    * <p>`ModItems` 的 `INVISIBLE_HEADPHONES = functional(..., InvisibleHeadphonesItem::new)`
    * 会让本类在 **`ModItems` 类初始化时**就被加载；而那一刻 `DeferredRegister` 还没收到
    * 注册事件 ⇒ `ModAttributes.HEADPHONES.get()` 会抛
    * "Registry Object not present"。**编译照样通过**，只在启动时炸 ——
    * 正是本项目最怕的"静默/延迟失败"。</p>
    */
   private static com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
      net.minecraft.world.entity.ai.attributes.AttributeModifier> headphoneModifier() {
      com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
         net.minecraft.world.entity.ai.attributes.AttributeModifier> map = com.google.common.collect.HashMultimap.create();
      map.put(com.zhongbai233.net_music_can_play_bili.init.ModAttributes.HEADPHONES.get(),
         new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            HEADPHONES_MODIFIER_ID, "net_music_can_play_bili.headphones", 1.0,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
      return map;
   }

   public InvisibleHeadphonesItem(Properties properties) {
      super(properties.stacksTo(1));
   }

   /**
    * 装备时给出的属性修饰符。
    *
    * <p>1.21 是在 `ModItems` 里用 `Item.Properties.attributes(ItemAttributeModifiers)` 给的；
    * 1.20.1 **没有** `ItemAttributeModifiers`（1.20.5 才有），正道是重写这个 vanilla 钩子。
    * 证据：`IForgeItem.getAttributeModifiers(slot, stack)` 的默认实现就是
    * `Item.getDefaultAttributeModifiers(slot)`，而 `ItemStack.getAttributeModifiers(slot)`
    * 正是调它 —— `HeadphoneAbility.has()` 读的就是 `ItemStack.getAttributeModifiers`。</p>
    */
   @Override
   public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
      net.minecraft.world.entity.ai.attributes.AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
      if (slot != EquipmentSlot.HEAD) {
         return super.getDefaultAttributeModifiers(slot);
      }
      // 基类默认返回 `ImmutableMultimap.of()`（javap 实证）⇒ 必须拷进可变容器再放。
      com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
         net.minecraft.world.entity.ai.attributes.AttributeModifier> result =
         com.google.common.collect.HashMultimap.create(super.getDefaultAttributeModifiers(slot));
      result.putAll(headphoneModifier());
      return result;
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResultHolder.pass(stack);
      } else if (level.isClientSide()) {
         return InteractionResultHolder.success(stack);
      } else {
         ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
         if (head.isEmpty()) {
            player.setItemSlot(EquipmentSlot.HEAD, stack.copyWithCount(1));
            stack.shrink(1);
            if (player instanceof ServerPlayer serverPlayer) {
               AudioLinkIndex.updatePlayerHeadphones(serverPlayer);
            }

            player.sendSystemMessage(Component.translatable("message.net_music_can_play_bili.headphones.equipped"));
            return InteractionResultHolder.success(stack);
         } else {
            player.sendSystemMessage(Component.translatable("message.net_music_can_play_bili.headphones.equip_slot_occupied"));
            return InteractionResultHolder.pass(stack);
         }
      }
   }

   public EquipmentSlot getEquipmentSlot() {
      return EquipmentSlot.HEAD;
   }

   public SoundEvent getEquipSound() {
      return SoundEvents.ARMOR_EQUIP_GENERIC;
   }

   public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag flag) {
      BlockPos turntable = AudioLinkData.readHeadphoneTurntable(stack);
      if (turntable != null) {
         tooltipComponents.add(
            Component.translatable("tooltip.net_music_can_play_bili.headphones.turntable", new Object[]{turntable.getX(), turntable.getY(), turntable.getZ()})
               .withStyle(ChatFormatting.GRAY)
         );
      }

      UUID mp4 = AudioLinkData.readHeadphoneMediaDevice(stack);
      if (mp4 != null) {
         String shortId = mp4.toString();
         if (shortId.length() > 8) {
            shortId = shortId.substring(0, 8);
         }

         tooltipComponents.add(
            Component.translatable("tooltip.net_music_can_play_bili.headphones.media_device", new Object[]{shortId}).withStyle(ChatFormatting.GRAY)
         );
      }
   }
}
