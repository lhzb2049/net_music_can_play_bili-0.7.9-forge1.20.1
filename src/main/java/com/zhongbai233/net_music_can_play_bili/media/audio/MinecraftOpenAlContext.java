package com.zhongbai233.net_music_can_play_bili.media.audio;

import com.mojang.blaze3d.audio.Library;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.slf4j.Logger;

final class MinecraftOpenAlContext {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ThreadLocal<Long> CAPABILITIES_CONTEXT = ThreadLocal.withInitial(() -> 0L);
   private static final Object CACHE_LOCK = new Object();
   private static volatile MinecraftOpenAlContext.OpenAlHandles cachedHandles;
   private static volatile boolean warningLogged;

   private MinecraftOpenAlContext() {
   }

   static boolean ensure(String operation) {
      long context = ALC10.alcGetCurrentContext();
      long device = 0L;
      if (context == 0L) {
         MinecraftOpenAlContext.OpenAlHandles handles = handles();
         context = handles.context();
         device = handles.device();
         if (context == 0L) {
            LOGGER.debug("OpenAL spatial {} skipped: no current Minecraft OpenAL context", operation);
            return false;
         }

         if (!ALC10.alcMakeContextCurrent(context)) {
            LOGGER.warn("OpenAL spatial {} skipped: failed to make Minecraft OpenAL context current", operation);
            return false;
         }
      }

      if (device == 0L) {
         device = ALC10.alcGetContextsDevice(context);
      }

      if (device == 0L) {
         LOGGER.warn("OpenAL spatial {} skipped: no OpenAL device for context", operation);
         return false;
      } else {
         if (CAPABILITIES_CONTEXT.get() != context) {
            try {
               AL.createCapabilities(ALC.createCapabilities(device));
               CAPABILITIES_CONTEXT.set(context);
            } catch (IllegalStateException var6) {
               invalidate();
               LOGGER.warn("OpenAL spatial {} skipped: context lost between check and capability init", operation);
               return false;
            }
         }

         return true;
      }
   }

   static void invalidate() {
      cachedHandles = null;
   }

   private static MinecraftOpenAlContext.OpenAlHandles handles() {
      MinecraftOpenAlContext.OpenAlHandles cached = cachedHandles;
      if (cached != null) {
         return cached;
      } else {
         synchronized (CACHE_LOCK) {
            cached = cachedHandles;
            if (cached == null) {
               cached = resolve();
               cachedHandles = cached;
            }

            return cached;
         }
      }
   }

   private static MinecraftOpenAlContext.OpenAlHandles resolve() {
      try {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft == null) {
            return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
         } else {
            SoundManager soundManager = minecraft.getSoundManager();
            if (soundManager == null) {
               return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
            } else {
               // 第 91 次：这里原来走 3 个 Mixin 访问器（SoundManagerAccessor / SoundEngineAccessor /
               // LibraryAccessor + 对应桥接口），而那几个 @Shadow 字段**在 refmap 里没有映射**
               // ⇒ 运行期在 SRG 环境下找不到 `loaded`/`library`/`soundEngine`/`context`/`currentDevice`
               // （它们实际叫 f_120219_/f_120220_/f_120349_/f_83687_/f_193465_），Mixin 应用直接失败。
               // 现在改用 AccessTransformer 把这 5 个字段放开成 public，直接读 —— AT 在编译期与运行期都生效，
               // 不依赖 refmap，也就不需要 mixin 了（4 个 mixin 与 3 个桥接口一并删除）。
               SoundEngine soundEngine = soundManager.soundEngine;
               if (soundEngine == null) {
                  return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
               } else {
                  if (!soundEngine.loaded) {
                     return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
                  } else {
                     Library library = soundEngine.library;
                     if (library == null) {
                        return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
                     } else {
                        long context = library.context;
                        long device = library.currentDevice;
                        if (context == 0L) {
                           return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
                        } else {
                           LOGGER.debug("Cached Minecraft OpenAL handles: context=0x{} device=0x{}", Long.toHexString(context), Long.toHexString(device));
                           return new MinecraftOpenAlContext.OpenAlHandles(context, device);
                        }
                     }
                  }
               }
            }
         }
      } catch (Throwable var10) {
         if (!warningLogged) {
            warningLogged = true;
            LOGGER.warn(
               "[NetMusicCanPlayBili] Dolby 空间音频不可用：无法获取 Minecraft SoundEngine 的 OpenAL 句柄。这通常是因为当前 Minecraft/NeoForge 版本与模组不兼容（SoundEngine/Library 内部字段名已变更）。音频将自动降级为 FLAC/AAC 立体声。具体异常: {}",
               var10.toString()
            );
         } else {
            LOGGER.debug("OpenAL handle resolution retry failed: {}", var10.toString());
         }

         return MinecraftOpenAlContext.OpenAlHandles.EMPTY;
      }
   }

   private record OpenAlHandles(long context, long device) {
      private static final MinecraftOpenAlContext.OpenAlHandles EMPTY = new MinecraftOpenAlContext.OpenAlHandles(0L, 0L);
   }
}
