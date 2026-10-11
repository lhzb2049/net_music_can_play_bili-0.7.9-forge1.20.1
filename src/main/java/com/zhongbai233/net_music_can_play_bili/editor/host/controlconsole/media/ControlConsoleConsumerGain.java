package com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.media;

import net.minecraft.util.Mth;

public final class ControlConsoleConsumerGain {
   private ControlConsoleConsumerGain() {
   }

   public static float combine(float rangeGain, float envelopeGain) {
      return Float.isFinite(rangeGain) && Float.isFinite(envelopeGain) ? Mth.clamp(rangeGain, 0.0F, 1.0F) * Mth.clamp(envelopeGain, 0.0F, 1.0F) : 0.0F;
   }
}
