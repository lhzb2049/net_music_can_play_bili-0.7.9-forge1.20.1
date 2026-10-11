package com.zhongbai233.net_music_can_play_bili.client.renderer.video;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

public final class IrisShaderpackCompat {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static volatile boolean initialized;
   private static volatile boolean available;
   private static volatile boolean lastShaderPackInUse;

   private IrisShaderpackCompat() {
   }

   static String configuredYuvProgramName() {
      return IrisShaderpackProperties.yuvProgramName();
   }

   static String configuredYuvShaderKeyName() {
      return IrisShaderpackProperties.yuvShaderKeyName();
   }

   static boolean isTexturedProbeProgram() {
      return shouldApplyIrisYuvCompatibility() && "TEXTURED".equals(configuredYuvProgramName());
   }

   static boolean isThreePlaneIrisYuvAllowed() {
      return IrisShaderpackProperties.threePlaneYuvAllowed();
   }

   static boolean isYuvShaderpackBypassEnabled() {
      return IrisShaderpackProperties.yuvShaderpackBypassEnabled();
   }

   static boolean isSolidYuvRenderTypeExperimentEnabled() {
      return IrisVideoRenderTypePolicy.isSolidClassificationEnabled();
   }

   static boolean shouldForceSolidYuvRenderType() {
      return IrisVideoRenderTypePolicy.shouldForceSolidClassification(shouldApplyIrisYuvCompatibility());
   }

   static boolean shouldDrawYuvImmediate() {
      return IrisVideoRenderTypePolicy.shouldUseImmediateDraw(shouldApplyIrisYuvCompatibility());
   }

   static boolean shouldApplyIrisYuvCompatibility() {
      return isShaderPackInUse() && VideoBillboardGeometrySupport.isCustomYuvShaderAvailable();
   }

   static boolean shouldUseSingleSamplerProbe() {
      return shouldApplyIrisYuvCompatibility() && !isThreePlaneIrisYuvAllowed();
   }

   static boolean shouldForceSafeProbeRenderType() {
      return shouldApplyIrisYuvCompatibility() && !isThreePlaneIrisYuvAllowed();
   }

   static boolean shouldDisableCustomYuvShader() {
      Boolean explicit = IrisShaderpackProperties.explicitCustomYuvShaderDisabled();
      return explicit != null ? explicit : isShaderPackInUse();
   }

   public static boolean isShaderPackInUse() {
      ensureInitialized();
      if (!available) {
         return false;
      } else {
         try {
            boolean inUse;
            try {
               inUse = detectShaderPackInUse();
            } catch (ReflectiveOperationException var2) {
               LOGGER.debug("Iris API 查询 shaderpack 状态失败，按未启用 shaderpack 处理", var2);
               return false;
            }

            if (inUse != lastShaderPackInUse) {
               lastShaderPackInUse = inUse;
               LOGGER.info(
                  "Iris shaderpack 状态变化: shaderpackInUse={}, customYuvShaderDisabled={}, yuvMode={}",
                  inUse,
                  IrisShaderpackProperties.customYuvShaderDisabledWhen(inUse),
                  IrisShaderpackProperties.yuvMode()
               );
            }

            return inUse;
         } catch (RuntimeException var3) {
            LOGGER.debug("Iris API 查询 shaderpack 状态失败，按未启用 shaderpack 处理", var3);
            return false;
         }
      }
   }

   private static boolean detectShaderPackInUse() throws ReflectiveOperationException {
      Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
      Object api = apiClass.getMethod("getInstance").invoke(null);

      try {
         Object active = apiClass.getMethod("isShaderPackActive").invoke(api);
         return Boolean.TRUE.equals(active);
      } catch (NoSuchMethodException var4) {
         Object inUse = apiClass.getMethod("isShaderPackInUse").invoke(api);
         return Boolean.TRUE.equals(inUse);
      }
   }

   private static void ensureInitialized() {
      if (!initialized) {
         synchronized (IrisShaderpackCompat.class) {
            if (!initialized) {
               initialized = true;

               try {
                  // ⚠️ 第 101 次（用户实测："开启光影后画面消失"）：**不能只认 mod id `iris`**。
                  //    1.21.1（NeoForge）上光影就是 Iris 本体，mod id = `iris`；
                  //    1.20.1（Forge）上光影是 **Oculus**，mod id = `oculus`（Embeddium 的搭档），
                  //    它提供的是**同一套** `net.irisshaders.iris.api.v0.IrisApi`（javap 实证：
                  //    `getInstance()` + `isShaderPackInUse()`）。
                  //    原来的写法在 Oculus 上直接 `available = false` ⇒ `isShaderPackInUse()` 恒 false
                  //    ⇒ 开了光影还以为没开 ⇒ 继续用自订 YUV 核心着色器 ⇒ 画面消失。
                  //    现在的判据：**API 类能不能拿到**（真正决定"能不能问"），mod id 只用于日志说明。
                  String provider = shaderModProvider();

                  try {
                     detectShaderPackInUse();
                  } catch (ReflectiveOperationException var3) {
                     available = false;
                     if (provider != null) {
                        LOGGER.warn("检测到光影 mod {}，但没有可用的 Iris API v0，无法判断 shaderpack 状态", provider);
                     }
                     return;
                  }

                  available = true;
                  LOGGER.info(
                     "已启用光影（shaderpack）兼容检测：提供者={}（可用属性：{} / {}）",
                     provider == null ? "未知（Iris API 类直接可见）" : provider,
                     IrisShaderpackProperties.DISABLE_CUSTOM_YUV_SHADER,
                     IrisShaderpackProperties.YUV_MODE
                  );
               } catch (LinkageError var4) {
                  available = false;
               } catch (RuntimeException var5) {
                  available = false;
                  LOGGER.debug("Iris API 初始化失败，按未安装光影处理", var5);
               }
            }
         }
      }
   }

   /** 已知提供 Iris API v0 的光影 mod：1.21.1 是 `iris`；1.20.1 Forge 是 `oculus`。 */
   private static final String[] SHADER_MOD_IDS = {"iris", "oculus"};

   private static String shaderModProvider() {
      for (String id : SHADER_MOD_IDS) {
         try {
            if (ModList.get().isLoaded(id)) {
               return id;
            }
         } catch (RuntimeException ignored) {
            // ModList 还没就绪时按"查不到"处理
         }
      }
      return null;
   }
}
