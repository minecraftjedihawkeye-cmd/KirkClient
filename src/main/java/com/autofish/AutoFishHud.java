package com.autofish;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class AutoFishHud {

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.inGameHud.getDebugHud().shouldShowDebugHud()) return;

        String status;
        int color;

        if (!AutoFishConfig.enabled) {
            status = "AutoFish: OFF";
            color = 0xFFAA0000;
        } else {
            AutoFishLogic.State state = AutoFishLogic.getInstance().getState();
            switch (state) {
                case WAITING_FOR_BITE:
                    status = "AutoFish: Waiting...";
                    color = 0xFFFFFF00;
                    break;
                case BITE_DETECTED:
                case REELING:
                    status = "AutoFish: Reeling!";
                    color = 0xFF00FF00;
                    break;
                case WAITING_TO_RECAST:
                case RECASTING:
                    status = "AutoFish: Recasting...";
                    color = 0xFF00AAFF;
                    break;
                case ON_BREAK:
                    status = "AutoFish: Break...";
                    color = 0xFFAAAAAA;
                    break;
                default:
                    status = "AutoFish: ON";
                    color = 0xFF00FF00;
                    break;
            }
        }

        context.drawTextWithShadow(mc.textRenderer, status, 4, 4, color);
    }
}
