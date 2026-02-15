package com.autofish.mixin;

import com.autofish.AutoFishLogic;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    // Title display
    @Inject(method = "setTitle", at = @At("HEAD"))
    private void onSetTitle(Text title, CallbackInfo ci) {
        checkForHypixelBite(title);
    }

    // Subtitle display
    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void onSetSubtitle(Text subtitle, CallbackInfo ci) {
        checkForHypixelBite(subtitle);
    }

    // Action bar / overlay message (above hotbar)
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        checkForHypixelBite(message);
    }

    private void checkForHypixelBite(Text text) {
        if (text == null) return;
        String str = text.getString().trim();
        if (str.contains("!!!")) {
            AutoFishLogic.getInstance().onHypixelBite();
        }
    }
}
