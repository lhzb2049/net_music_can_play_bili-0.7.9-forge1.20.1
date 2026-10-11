package com.zhongbai233.net_music_can_play_bili.client;

import com.zhongbai233.net_music_can_play_bili.client.renderer.ClientDisplayProperties;
import com.zhongbai233.net_music_can_play_bili.item.HolographicGlassesItem;
import com.zhongbai233.net_music_can_play_bili.link.EquippedMediaItems;
import com.zhongbai233.net_music_can_play_bili.link.HolographicGlassesAbility;
import com.zhongbai233.net_music_can_play_bili.link.MediaBindingData;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class HolographicGlassesClient {
   private HolographicGlassesClient() {
   }

   public static boolean active() {
      Player player = Minecraft.getInstance().player;
      return player != null && HolographicGlassesAbility.has(EquippedMediaItems.firstHolographicGlasses(player));
   }

   public static boolean shouldHideProjectorVideos() {
      return active();
   }

   public static UUID boundMp4DeviceId() {
      List<HolographicGlassesItem.ScreenBinding> bindings = screenBindings();
      return bindings.isEmpty() ? null : bindings.get(0).deviceId();
   }

   public static List<HolographicGlassesItem.ScreenBinding> screenBindings() {
      Player player = Minecraft.getInstance().player;
      if (player == null) {
         return List.of();
      } else {
         ItemStack head = EquippedMediaItems.firstHolographicGlasses(player);
         return !HolographicGlassesAbility.has(head) ? List.of() : HolographicGlassesItem.readScreenBindings(head);
      }
   }

   /**
    * 一句话摘要"眼镜此刻戴没戴、绑了什么"（诊断用，第 104 次）。
    *
    * <p>"全息眼镜没画面"的三道门依次是：**戴上 → 有绑定 → 找得到帧**。
    * 前两道全在这里，所以心跳里带上它，一眼就能定位。</p>
    */
   public static String describeBindings() {
      Player player = Minecraft.getInstance().player;
      if (player == null) {
         return "无玩家";
      }
      ItemStack head = EquippedMediaItems.firstHolographicGlasses(player);
      if (head.isEmpty()) {
         return "头部没戴眼镜";
      }
      if (!HolographicGlassesAbility.has(head)) {
         return "头上有物品但不是可戴眼镜（缺 HEAD 装备槽）";
      }
      List<HolographicGlassesItem.ScreenBinding> bindings = HolographicGlassesItem.readScreenBindings(head);
      if (bindings.isEmpty()) {
         return "戴上了但**没有任何绑定**（需要在配置界面绑定媒体源）";
      }
      StringBuilder out = new StringBuilder();
      for (int i = 0; i < bindings.size(); i++) {
         MediaBindingData.MediaSource source = bindings.get(i).source();
         if (i > 0) {
            out.append(", ");
         }
         out.append('#').append(i).append('=')
            .append(source == null ? "空来源" : source.isMediaDevice() ? "设备" + source.deviceId() : source.isProjector() ? "投影仪" + source.pos() : source.isTurntable() ? "唱片机" + source.pos() : "未知");
      }
      return out.toString();
   }

   public static boolean handlesTurntable(BlockPos turntablePos) {
      Minecraft minecraft = Minecraft.getInstance();
      ResourceKey<Level> dimension = minecraft.level != null ? minecraft.level.dimension() : null;
      return handlesTurntable(dimension, turntablePos);
   }

   public static boolean handlesTurntable(ResourceKey<Level> dimension, BlockPos turntablePos) {
      if (ClientDisplayProperties.holographicWorldScreenEnabled() && dimension != null && turntablePos != null) {
         for (HolographicGlassesItem.ScreenBinding binding : screenBindings()) {
            if (binding.source() != null
               && binding.source().isTurntable()
               && dimension.equals(binding.source().dimension())
               && turntablePos.equals(binding.source().pos())) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static HolographicGlassesItem.ScreenConfig screenConfig() {
      List<HolographicGlassesItem.ScreenBinding> bindings = screenBindings();
      return bindings.isEmpty() ? HolographicGlassesItem.defaultScreenConfig() : bindings.get(0).config();
   }
}
