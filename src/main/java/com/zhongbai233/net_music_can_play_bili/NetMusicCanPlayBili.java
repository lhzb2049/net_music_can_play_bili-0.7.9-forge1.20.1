package com.zhongbai233.net_music_can_play_bili;

import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.mojang.logging.LogUtils;
import com.zhongbai233.net_music_can_play_bili.bili.BiliAudioResolver;
import com.zhongbai233.net_music_can_play_bili.bili.BiliClientAudioHandlers;
import com.zhongbai233.net_music_can_play_bili.bili.BiliConfig;
import com.zhongbai233.net_music_can_play_bili.blockentity.ModernTurntableBlockEntity;
import com.zhongbai233.net_music_can_play_bili.client.ModernTurntableClientEvents;
import com.zhongbai233.net_music_can_play_bili.client.tooltip.ClientMP4QueueTooltip;
import com.zhongbai233.net_music_can_play_bili.client.tooltip.MP4QueueTooltip;
import com.zhongbai233.net_music_can_play_bili.init.ModAttributes;
import com.zhongbai233.net_music_can_play_bili.init.ModBlockEntities;
import com.zhongbai233.net_music_can_play_bili.init.ModBlocks;
import com.zhongbai233.net_music_can_play_bili.init.ModCreativeTabs;
import com.zhongbai233.net_music_can_play_bili.init.ModEnchantments;
import com.zhongbai233.net_music_can_play_bili.init.ModItems;
import com.zhongbai233.net_music_can_play_bili.init.ModMenus;
import com.zhongbai233.net_music_can_play_bili.media.stream.TempFileByteSpool;
import com.zhongbai233.net_music_can_play_bili.network.NcpbNetwork;
import com.zhongbai233.net_music_can_play_bili.server.AreaAudioListenerSync;
import com.zhongbai233.net_music_can_play_bili.server.NetMusicBiliServerCommands;
import com.zhongbai233.net_music_can_play_bili.server.NetMusicPermissions;
import com.zhongbai233.net_music_can_play_bili.server.PadMapScopeSync;
import com.zhongbai233.net_music_can_play_bili.server.PlaybackAuditManager;
import java.lang.reflect.Method;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

@Mod("net_music_can_play_bili")
public class NetMusicCanPlayBili {
   public static final String MODID = "net_music_can_play_bili";
   private static final Logger LOGGER = LogUtils.getLogger();

   /**
    * ⚠️ **1.20.1 Forge 的 mod 构造器签名**：FML 只认 `()` / `(FMLJavaModLoadingContext)` 这类；
    * **`(IEventBus, ModContainer)` 是 NeoForge 1.21 的写法** —— 在 1.20.1 上用它会抛
    * `java.lang.NoSuchMethodException: NetMusicCanPlayBili.<init>()`（`FMLModContainer.constructMod`），
    * 于是模组被标成 **broken mod state**，FML 之后**拒绝给它发任何事件**
    * （日志里刷 "Cowardly refusing to send event … to a broken mod state"），最终以 crash report 收场。
    * 第 91 次实测。本地可跑的参考就是外观层的 `P0ModEntry(FMLJavaModLoadingContext context)`。
    */
   public NetMusicCanPlayBili(FMLJavaModLoadingContext context) {
      IEventBus modEventBus = context.getModEventBus();
      ModAttributes.ATTRIBUTES.register(modEventBus);
      ModEnchantments.register(modEventBus);
      ModBlocks.BLOCKS.register(modEventBus);
      ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
      ModItems.ITEMS.register(modEventBus);
      ModCreativeTabs.register(modEventBus);
      ModMenus.MENU_TYPES.register(modEventBus);
      // Forge 侧在 mod 构造期直接建通道（NeoForge 才用 RegisterPayloadHandlersEvent）
        NcpbNetwork.register();
      MinecraftForge.EVENT_BUS.addListener(NetMusicPermissions::onPermissionGather);
      MinecraftForge.EVENT_BUS.addListener(NetMusicBiliServerCommands::onRegisterCommands);
      MinecraftForge.EVENT_BUS.addListener(PadMapScopeSync::onPlayerLoggedIn);
      MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onPlayerLoggedIn);
      MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onPlayerLoggedOut);
      MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onServerTick);
      MinecraftForge.EVENT_BUS.addListener(AreaAudioListenerSync::onServerStopping);
      registerDevelopmentSelfTests();
      com.zhongbai233.net_music_can_play_bili.port.diag.UseDiag.register();   // 临时诊断（用完删）
      MinecraftForge.EVENT_BUS.addListener(PlaybackAuditManager::onServerTick);
      MinecraftForge.EVENT_BUS.addListener(PlaybackAuditManager::onPlayerLoggedIn);
      modEventBus.addListener(Config::onLoad);
      ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
      MusicPlayResolverManager.registerResolver(new BiliAudioResolver());
      TempFileByteSpool.cleanupOrphanedSpoolFiles();
      LOGGER.info("BiliAudioResolver registered with NetMusic");
      if (FMLEnvironment.dist == Dist.CLIENT) {
         ModernTurntableClientEvents.register(modEventBus);
         BiliConfig.load();
         BiliClientAudioHandlers.register();
         modEventBus.addListener((RegisterClientTooltipComponentFactoriesEvent event) -> event.register(MP4QueueTooltip.class, ClientMP4QueueTooltip::new));
      }
   }

   // 【第 91 次：删掉了原来的 registerCapabilities 监听器】
   // 上游（NeoForge 1.21）在 RegisterCapabilitiesEvent 里为**方块实体**注册物品处理器：
   //   event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.X.get(), (be, side) -> be.getItemHandler())
   // 移植时退化成了一句 `event.register(IItemHandler.class)`（丢参数的伪影），而 1.20.1 的
   // `RegisterCapabilitiesEvent.register` 是给 ICapabilityProvider **默认实现**用的，
   // 且 **Forge 自己已经注册过 IItemHandler** ⇒ 直接抛
   //   IllegalArgumentException: Cannot register a capability implementation multiple times
   // 落在 mod 的 common_setup 阶段 ⇒ 整个游戏崩在启动（第 91 次实测）。
   //
   // 1.20.1 的正道是**重写 BlockEntity#getCapability(Capability, Direction)** 返回 IItemHandler。
   // 现在方块实体还没接（见 PORT-STATUS §6.21 待办⑮）—— 那是**功能缺口**（物品进不出转盘/唱片机），
   // 不是启动阻断，所以先把致命的注册删掉。

   private static void registerDevelopmentSelfTests() {
      if (PadDiagnosticsProperties.mapServerSelfTestEnabled()) {
         registerDevelopmentSelfTest("com.zhongbai233.net_music_can_play_bili.server.PadMapSamplerServerSelfTest", "Pad map server self-test");
      }

      if (Boolean.getBoolean("ncpb.scene_editor.server_self_test")) {
         registerDevelopmentSelfTest(
            "com.zhongbai233.net_music_can_play_bili.server.SceneEditorDedicatedServerSelfTest", "Scene Editor dedicated-server self-test"
         );
      }
   }

   private static void registerDevelopmentSelfTest(String className, String description) {
      try {
         Class<?> selfTest = Class.forName(className);
         Method handler = selfTest.getMethod("onServerStarted", ServerStartedEvent.class);
         MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> invokeDevelopmentSelfTest(handler, event));
      } catch (ReflectiveOperationException var4) {
         throw new IllegalStateException("Unable to register " + description, var4);
      }
   }

   private static void invokeDevelopmentSelfTest(Method handler, ServerStartedEvent event) {
      try {
         handler.invoke(null, event);
      } catch (ReflectiveOperationException var3) {
         throw new IllegalStateException("Unable to run Pad map server self-test", var3);
      }
   }
}
