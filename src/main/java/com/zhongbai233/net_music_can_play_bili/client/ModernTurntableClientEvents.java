package com.zhongbai233.net_music_can_play_bili.client;

import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.client.renderer.ControlConsoleRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.LyricProjectorRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.ModernTurntableRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.SpeakerRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.VideoProjectorRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.item.CuriosHeadGearLayer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.item.MP4ItemScreenRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.item.PadItemScreenRenderer;
import com.zhongbai233.net_music_can_play_bili.client.renderer.video.YuvVideoRenderTypes;
import com.zhongbai233.net_music_can_play_bili.gui.HolographicScreenConfigTestScreen;
import com.zhongbai233.net_music_can_play_bili.gui.MediaToolBindingScreen;
import com.zhongbai233.net_music_can_play_bili.gui.MediaToolReportScreen;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;

public final class ModernTurntableClientEvents {
   private static final Logger LOGGER = LogUtils.getLogger();

   private ModernTurntableClientEvents() {
   }

   public static void register(IEventBus modEventBus) {
      modEventBus.addListener(ModernTurntableClientEvents::registerRenderers);
      // 第 97 次补回：这两个监听器与上游一字不差，当年是随"摘引用"一起被删掉的
      // （CuriosHeadGearLayer 的类一直在编译范围内，所以这条纯属漏接线）。
      modEventBus.addListener(CuriosHeadGearLayer::register);
      // 第 102 次补回（§6.33.2 点名的"真缺口"之一，症状=中控台编辑器中间视口全黑）：
      // 上游这里注册了画中画预览渲染器的工厂。没有这一行 ⇒ `PortPictureInPictureRenderer`
      // 的工厂表为空 ⇒ `createPipRenderer(...)` 恒返回 null ⇒ 整层预览不画。
      com.zhongbai233.net_music_can_play_bili.port.shim.PortPictureInPictureRenderer.registerPipRenderer(
         com.zhongbai233.net_music_can_play_bili.client.renderer.gui.HolographicPreviewPipRenderState.class,
         com.zhongbai233.net_music_can_play_bili.client.renderer.gui.HolographicPreviewPipRenderer::new);
      // 第 96 次补回（§6.27.5 清单里那条）：上游有这一行，移植时随"摘引用"一起丢了。
      // 它本身只在开启 YUV 可视化诊断时打一条提示，但它是**视频这条链**的注册点，接回来更干净。
      modEventBus.addListener(YuvVideoRenderTypes::registerPipelines);
      modEventBus.addListener(ModernTurntableClientEvents::registerMenuScreens);
      modEventBus.addListener(HolographicGlassesKeyHandler::register);
      MinecraftForge.EVENT_BUS.addListener(ModernTurntableClientEvents::onClientLogout);
      MinecraftForge.EVENT_BUS.addListener(ModernTurntableClientEvents::onRenderFrame);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onClientTick);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onMovementInput);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onMouseButton);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onInteraction);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onMouseScroll);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onRenderGui);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onSubmitGeometry);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onLogout);
      MinecraftForge.EVENT_BUS.addListener(ControlConsoleRoamingEvents::onClone);
   }

   private static void onRenderFrame(TickEvent.RenderTickEvent event) {
      if (Minecraft.getInstance().screen instanceof HolographicScreenConfigTestScreen screen) {
         double deltaSeconds = Mth.clamp(Minecraft.getInstance().getFrameTimeNs() / 1.0E9, 0.0, 0.1);
         screen.advanceCameraFrame(deltaSeconds);
      }
   }

   private static void onClientLogout(LoggingOut event) {
   }

   private static void registerMenuScreens(FMLClientSetupEvent event) {
      // 1.20.1 **没有** `RegisterMenuScreensEvent`（javap 实证类不存在）——
      // 菜单界面在 1.20.1 要在客户端 setup 里直接调静态的 `MenuScreens.register`。
      // 用 `event.enqueueWork` 是 Forge 推荐做法：这个回调可能在别的线程上跑。
      event.enqueueWork(() -> {
         MenuScreens.register((MenuType)ModMenus.MEDIA_TOOL_BINDING.get(), MediaToolBindingScreen::new);
         MenuScreens.register((MenuType)ModMenus.MEDIA_TOOL_REPORT.get(), MediaToolReportScreen::new);
      });
   }

   private static void registerRenderers(RegisterRenderers event) {
      // 第 97 次：**唱片机**。它的渲染器原本也在排除清单里，本轮成对体检
      // （它依赖的 `NetMusicDiscModelAdapter` 也是），唯一缺口是
      // `Model#renderToBuffer` 的颜色参数（1.21 一个 int → 1.20.1 四个 float），补一行就编过了。
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.MODERN_TURNTABLE.get(), ModernTurntableRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.LYRIC_PROJECTOR.get(), LyricProjectorRenderer::new);
      // 第 94 次：视频投影仪的渲染器。它此前被登记在 `deferred-excludes.txt` 里（不参与编译），
      // 而**摘除引用时把这条注册一起删掉了** ⇒ 方块放下去只有一个静态模型，投影幕永远不会出现。
      // 实测把它放回编译是 **0 错误**（规则表这一年已把它需要的 API 都补齐了），故直接接回来。
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.VIDEO_PROJECTOR.get(), VideoProjectorRenderer::new);
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.SPEAKER.get(), SpeakerRenderer::new);
      // 第 97 次：**中控台**。它与视频投影仪是同一个缺陷 —— 渲染器一直在编译范围内、
      // `ControlConsoleBlockEntity` 也一直在把自己注册成消费者，唯独这行 BER 注册被"摘引用"删掉了
      // ⇒ 中控台的屏幕**永远不画**（而它的 renderer 还兼着两条每帧视频同步的调用点，
      //    所以中控台的视频也一并受影响）。上游同位置有这一行。
      event.registerBlockEntityRenderer((BlockEntityType)ModBlockEntities.CONTROL_CONSOLE.get(), ControlConsoleRenderer::new);
      warmupClientResources();
   }

   private static void warmupClientResources() {
      Minecraft.getInstance().execute(() -> {
         try {
            // 第 97 次补回：上游这里会预热手持设备两套离屏资源；移植版只留下了一句日志
            // （懒初始化也能跑，Pad/MP4 已实测可用，所以这条属于"补回上游行为"而非修缺陷）。
            MP4ItemScreenRenderer.warmup();
            PadItemScreenRenderer.warmup();
            LOGGER.debug("MP4/Pad handheld GUI resources warmed up");
         } catch (Exception var1) {
            LOGGER.warn("MP4/Pad handheld GUI resource warmup failed; falling back to lazy initialization", var1);
         }
      });
   }
}
