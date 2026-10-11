package com.zhongbai233.net_music_can_play_bili.client;

import com.zhongbai233.net_music_can_play_bili.port.shim.PortSubmitNodeCollector;
import com.zhongbai233.net_music_can_play_bili.port.shim.PortWorldRenderEvents;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.event.InputEvent.MouseScrollingEvent;
import net.minecraftforge.client.event.InputEvent.MouseButton.Pre;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;

public final class ControlConsoleRoamingEvents {
   private ControlConsoleRoamingEvents() {
   }

   public static void onClientTick(TickEvent.ClientTickEvent event) {

      if (event.phase != TickEvent.Phase.END) {

         return;

      }
      ControlConsoleRoamingSession.tick();
   }

   public static void onMovementInput(MovementInputUpdateEvent event) {
      ControlConsoleRoamingSession.suppressPlayerInput(event.getInput());
   }

   public static void onMouseButton(Pre event) {
      if (ControlConsoleRoamingSession.handleMouseButton(event.getButton(), event.getAction())) {
         event.setCanceled(true);
      }
   }

   public static void onInteraction(InteractionKeyMappingTriggered event) {
      if (ControlConsoleRoamingSession.isActive() && (event.isAttack() || event.isUseItem() || event.isPickBlock())) {
         event.setSwingHand(false);
         event.setCanceled(true);
      }
   }

   public static void onMouseScroll(MouseScrollingEvent event) {
      if (ControlConsoleRoamingSession.isActive()) {
         ControlConsoleRoamingSession.adjustFlyingSpeed(event.getScrollDelta());
         event.setCanceled(true);
      }
   }

   public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
      ControlConsoleRoamingSession.drawHud(event.getGuiGraphics());
   }

   public static void onSubmitGeometry(RenderLevelStageEvent event) {
      if (PortWorldRenderEvents.isStage(event, Stage.AFTER_TRANSLUCENT_BLOCKS)) {
         PortSubmitNodeCollector collector = PortWorldRenderEvents.begin();

         try {
            ControlConsoleRoamingSession.submitGeometry(collector);
         } finally {
            PortWorldRenderEvents.end(collector);
         }
      }
   }

   public static void onLogout(LoggingOut event) {
      ControlConsoleRoamingSession.stop(false);
   }

   public static void onClone(Clone event) {
      ControlConsoleRoamingSession.stop(false);
   }
}
