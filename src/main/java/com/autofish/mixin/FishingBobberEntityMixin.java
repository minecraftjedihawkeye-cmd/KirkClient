package com.autofish.mixin;

import com.autofish.AutoFishLogic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin {

    /**
     * Inject into tick() to register this bobber with AutoFishLogic every tick.
     * This works reliably in both singleplayer and multiplayer, unlike
     * mc.player.fishHook which may not be set on the client in multiplayer.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && bobber.getEntityWorld().isClient()) {
            PlayerEntity owner = bobber.getPlayerOwner();
            if (owner != null && owner == mc.player) {
                AutoFishLogic.getInstance().trackBobber(bobber);
            }
        }
    }
}
