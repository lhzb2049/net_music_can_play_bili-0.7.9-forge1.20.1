package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 附魔注册：{@code headphones}（耳机）与 {@code holographic_glasses}（全息眼镜）。
 *
 * <h2>为什么之前"少了两本附魔书"（第 91 次续）</h2>
 *
 * <p>上游（NeoForge 1.21）的附魔是**数据驱动**的：
 * {@code data/net_music_can_play_bili/enchantment/headphones.json}，效果是
 * {@code minecraft:attributes} 给 HEAD 槽加 1.0 的 {@code net_music_can_play_bili:headphones} 属性；
 * 页签里那两本书由 {@code ModItems} 用
 * {@code parameters.holders().lookupOrThrow(Registries.ENCHANTMENT).get(KEY)}
 * 现查现造（{@code EnchantedBookItem.createForEnchantment} 在 1.21 叫别的名字）。
 * 本工程连**附魔注册**都没有（{@code ForgeRegistries.ENCHANTMENTS} 全树为零）
 * ⇒ 那两个 {@code ResourceKey} 指向虚空 ⇒ 页签里自然没有书。用户实测指出。</p>
 *
 * <h2>1.20.1 的口径差异（必须写清楚，否则等于骗人）</h2>
 *
 * <p><b>1.20.1 的附魔不能给任意属性加成</b> —— {@code minecraft:attributes} 这种数据驱动效果是 1.21 才有的；
 * 1.20.1 的 {@code Enchantment} 只有 {@code getDamageBonus}/{@code getDamageProtection} 这类固定钩子。
 * 所以这里落地为**等价语义**：附魔本身只做"标记"，判定改成</p>
 * <pre>戴着头部装备 且（物品自带 HEADPHONES 属性 <b>或</b> 物品带 headphones 附魔）</pre>
 * <p>即"把耳机附魔打在任意头盔上，那头盔就是耳机" —— 与上游"附魔给属性"达到的效果一致，
 * 只是实现从"属性加成"换成"直接查附魔等级"。见
 * {@code HeadphoneAbility.has} / {@code HolographicGlassesAbility.has}。</p>
 *
 * <p>附魔类别用 {@link EnchantmentCategory#ARMOR_HEAD}（对应上游 JSON 的
 * {@code "slots": ["head"]}），等级上限 1，费用沿用上游 JSON 的 {@code min_cost=12 / max_cost=35}。</p>
 */
public final class ModEnchantments {

    public static final String HEADPHONES_ID = "headphones";
    public static final String HOLOGRAPHIC_GLASSES_ID = "holographic_glasses";

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, NetMusicCanPlayBili.MODID);

    public static final RegistryObject<Enchantment> HEADPHONES =
            ENCHANTMENTS.register(HEADPHONES_ID,
                    () -> new AbilityEnchantment(Enchantment.Rarity.RARE, 12, 35));

    public static final RegistryObject<Enchantment> HOLOGRAPHIC_GLASSES =
            ENCHANTMENTS.register(HOLOGRAPHIC_GLASSES_ID,
                    () -> new AbilityEnchantment(Enchantment.Rarity.RARE, 12, 35));

    private ModEnchantments() {
    }

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }

    /**
     * 能力附魔：只有 1 级、只作用于头部装备、**可以上书**（否则页签里那两本附魔书没意义 ——
     * {@code EnchantedBookItem} 只在 {@code isAllowedOnBooks()} 为真时才有用）。
     */
    public static final class AbilityEnchantment extends Enchantment {

        private final int minCost;
        private final int maxCost;

        AbilityEnchantment(Enchantment.Rarity rarity, int minCost, int maxCost) {
            super(rarity, EnchantmentCategory.ARMOR_HEAD, new EquipmentSlot[]{EquipmentSlot.HEAD});
            this.minCost = minCost;
            this.maxCost = maxCost;
        }

        @Override
        public int getMaxLevel() {
            return 1;
        }

        @Override
        public int getMinCost(int level) {
            return this.minCost;
        }

        @Override
        public int getMaxCost(int level) {
            return this.maxCost;
        }
    }
}
