package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import com.zhongbai233.net_music_can_play_bili.item.HolographicGlassesItem;
import com.zhongbai233.net_music_can_play_bili.item.InvisibleHeadphonesItem;
import com.zhongbai233.net_music_can_play_bili.item.MP4Item;
import com.zhongbai233.net_music_can_play_bili.item.MediaManagementToolItem;
import com.zhongbai233.net_music_can_play_bili.item.PadItem;
import java.util.function.Function;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册（**外观优先阶段**：只保证"能拿到、有模型、有名字"，不含任何玩法逻辑）。
 *
 * <p>两类物品：
 * <ul>
 *   <li>方块的 BlockItem（六个方块各一个，注册名与方块同名 —— 资源路径一一对应）；</li>
 *   <li>独立物品（MP4 / Pad / 媒体管理工具 / 隐形耳机 / 猫耳耳机 / 全息眼镜）。</li>
 * </ul>
 *
 * <p>注册名与 1.21.1 工程的 `models/item/<name>.json` 与 `lang` 键严格一致。
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, NetMusicCanPlayBili.MODID);

    // ---- 方块物品 ----
    public static final RegistryObject<Item> MODERN_TURNTABLE = blockItem(ModBlocks.MODERN_TURNTABLE);
    public static final RegistryObject<Item> LYRIC_PROJECTOR  = blockItem(ModBlocks.LYRIC_PROJECTOR);
    public static final RegistryObject<Item> VIDEO_PROJECTOR  = blockItem(ModBlocks.VIDEO_PROJECTOR);
    public static final RegistryObject<Item> SPEAKER          = blockItem(ModBlocks.SPEAKER);
    public static final RegistryObject<Item> LIVE_STREAMER    = blockItem(ModBlocks.LIVE_STREAMER);
    public static final RegistryObject<Item> CONTROL_CONSOLE  = blockItem(ModBlocks.CONTROL_CONSOLE);

    // ---- 独立物品 ----
    // ⚠️ 第 90 次之前这里全是 `simple(name)`（造**裸 `Item`**）—— 那是"外观层"的写法：
    // jar 编得过，但物品**没有任何行为**（Pad 打不开界面、MP4 不认设备、耳机不带属性）。
    // 现在改为注册**功能类**，与 1.21.1 的 `ModItems` 对齐。
    // 保持不变式：字段类型仍是 `RegistryObject<Item>`（改成具体类型会让
    // `RegistryObject<X>` 的不变性在别处炸掉，而这里没有任何收益）。
    public static final RegistryObject<Item> MP4                   = functional("mp4", MP4Item::new);
    public static final RegistryObject<Item> PAD                   = functional("pad", PadItem::new);
    public static final RegistryObject<Item> MEDIA_MANAGEMENT_TOOL = functional("media_management_tool", MediaManagementToolItem::new);
    public static final RegistryObject<Item> INVISIBLE_HEADPHONES  = functional("invisible_headphones", InvisibleHeadphonesItem::new);
    public static final RegistryObject<Item> CAT_HEADPHONES        = functional("cat_headphones", InvisibleHeadphonesItem::new);
    public static final RegistryObject<Item> HOLOGRAPHIC_GLASSES    = functional("holographic_glasses", HolographicGlassesItem::new);

    private ModItems() {
    }

    /** 注册一个有**行为**的物品类（不是裸 `Item`）。 */
    private static RegistryObject<Item> functional(String name, Function<Item.Properties, Item> factory) {
        return ITEMS.register(name, () -> factory.apply(new Item.Properties()));
    }

    private static RegistryObject<Item> blockItem(RegistryObject<net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
