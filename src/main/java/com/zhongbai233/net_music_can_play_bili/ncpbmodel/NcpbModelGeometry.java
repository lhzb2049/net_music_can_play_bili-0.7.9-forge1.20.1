package com.zhongbai233.net_music_can_play_bili.ncpbmodel;

import com.zhongbai233.net_music_can_play_bili.NetMusicCanPlayBili;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把 {@code net_music_can_play_bili:euler_elements} 这个模型 loader 注册进 Forge。
 *
 * <p>上游（1.21.1 / NeoForge）用的是独立的 {@code @EventBusSubscriber} 注解；
 * 1.20.1 里它是 {@code @Mod.EventBusSubscriber} 这个**嵌套注解**，其余用法相同。</p>
 *
 * <p>没有这一条，模型会报
 * {@code Model loader 'net_music_can_play_bili:euler_elements' not found}，
 * 表现为<b>名字对、模型贴图全错</b>（实测踩过一次，见 misakanet-pending/L21）。</p>
 */
@Mod.EventBusSubscriber(modid = NetMusicCanPlayBili.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NcpbModelGeometry {

    /**
     * 对应模型 JSON 里的 {@code "loader": "net_music_can_play_bili:euler_elements"}。
     *
     * <p>只留 path 部分：{@code ModelEvent.RegisterGeometryLoaders#register} 收的第一个参数就是 String。
     * 这样也避开了 {@code new ResourceLocation(String, String)} —— 它在 Forge 47.4.26 上
     * 已被标记为"过时且计划删除"（按 L22 的口径：遇到标记删除的 API，先想能不能不用它）。</p>
     */
    public static final String EULER_ELEMENTS = "euler_elements";

    private NcpbModelGeometry() {
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(EULER_ELEMENTS, new EulerElementsLoader());
    }
}
