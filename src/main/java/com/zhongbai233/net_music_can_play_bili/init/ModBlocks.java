package com.zhongbai233.net_music_can_play_bili.init;

import com.zhongbai233.net_music_can_play_bili.block.ControlConsoleBlock;
import com.zhongbai233.net_music_can_play_bili.block.LiveStreamerBlock;
import com.zhongbai233.net_music_can_play_bili.block.LyricProjectorBlock;
import com.zhongbai233.net_music_can_play_bili.block.ModernTurntableBlock;
import com.zhongbai233.net_music_can_play_bili.block.SpeakerBlock;
import com.zhongbai233.net_music_can_play_bili.block.VideoProjectorBlock;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 方块注册（**外观优先阶段**：只保证"能放置、有模型、有名字"，不含任何玩法逻辑）。
 *
 * <p><b>关键点：方块状态属性必须与 blockstate JSON 精确匹配。</b>
 * blockstate 是按属性组合来选模型的（例如 {@code facing=north,has_disc=false}）。
 * 如果只注册一个光板 {@code Block}，那些属性并不存在 ⇒ 变体解析失败
 * ⇒ 方块回落到"缺失模型"，在游戏里就是**经典紫黑方块**；
 * 而物品走的是 {@code models/item/*} 另一条路径，所以症状会呈现为
 * "**物品看着是对的、放下就变紫黑**"（实测踩过一次，见 misakanet-pending/L22）。</p>
 *
 * <p>反过来也不能"多给属性"：blockstate 没覆盖到的状态同样会变成缺失模型。</p>
 *
 * <table>
 *   <tr><th>方块</th><th>blockstate 需要的属性</th></tr>
 *   <tr><td>{@code control_console}</td><td>（无）</td></tr>
 *   <tr><td>{@code lyric_projector} / {@code video_projector}</td><td>{@code activated}</td></tr>
 *   <tr><td>{@code speaker}</td><td>{@code activated} + {@code facing}</td></tr>
 *   <tr><td>{@code live_streamer}</td><td>{@code playing} + {@code facing}</td></tr>
 *   <tr><td>{@code modern_turntable}</td><td>{@code has_disc} + {@code facing}</td></tr>
 * </table>
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, NetMusicCanPlayBili.MODID);

    public static final RegistryObject<Block> MODERN_TURNTABLE = BLOCKS.register("modern_turntable",
            () -> new ModernTurntableBlock(props()));
    public static final RegistryObject<Block> LYRIC_PROJECTOR = BLOCKS.register("lyric_projector",
            () -> new LyricProjectorBlock(props()));
    public static final RegistryObject<Block> VIDEO_PROJECTOR = BLOCKS.register("video_projector",
            () -> new VideoProjectorBlock(props()));
    public static final RegistryObject<Block> SPEAKER = BLOCKS.register("speaker",
            () -> new SpeakerBlock(props()));
    public static final RegistryObject<Block> LIVE_STREAMER = BLOCKS.register("live_streamer",
            () -> new LiveStreamerBlock(props()));
    public static final RegistryObject<Block> CONTROL_CONSOLE = BLOCKS.register("control_console",
            () -> new ControlConsoleBlock(props()));

    private ModBlocks() {
    }

    /**
     * 六个方块共用的属性：都不是满方块（模型是机械/投影仪形状），所以必须 noOcclusion，
     * 否则相邻面会被错误剔除、出现"缺面"。
     */
    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of()
                .strength(1.5F, 6.0F)
                .sound(SoundType.WOOD)
                .noOcclusion();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    // ------------------------------------------------------------------
    // 🔴 第 91 次（续）：**删掉了 4 个占位方块类**（ActivatedBlock / FacingActivatedBlock /
    //    FacingPlayingBlock / FacingHasDiscBlock），它们全都 `extends Block` —— 纯原版方块，
    //    没有 use()、没有方块实体。注册用它们 ⇒ 功能类（ModernTurntableBlock 等）**从未被实例化**
    //    ⇒ **空手右键也毫无反应**（用户实测报告"方块右键还是没反应"的最终根因）。
    //
    //    这是**第三次**同一形状的缺陷：外观层为了"先有模型"造了一批占位类型，
    //    组装时没有换成功能类。
    //      · 物品：`simple(name)` → 已改成 `functional(name, XxxItem::new)`；
    //      · 方块：就是这里；
    //      · 桥接口/访问器：已改成 AT。
    //
    //    换类的**安全性依据（实测，不是推断）**：
    //      ① 功能类与上游**逐字一致**（基类 + 属性集合全部比对过，6/6 一致）；
    //      ② blockstate JSON 与上游**逐键一致**（6/6），即"这些 JSON 配这些类"上游已验证；
    //      ③ 属性里多出来的状态（转盘的 `playing`、歌词投影仪的 `linked`）在 JSON 里没列 ——
    //         这在 1.20.1 是**允许**的：未列出的属性是通配的（multipart / variants 都是），
    //         上游同样如此且能正常显示。
    //    换完必须复核：日志里不能出现 blockstate 属性/缺失模型相关的报错。
    // ------------------------------------------------------------------

}
