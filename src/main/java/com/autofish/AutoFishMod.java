package com.autofish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoFishMod implements ClientModInitializer {

    public static final String MOD_ID = "autofish";

    private static final KeyBinding.Category AUTOFISH_CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "category"));

    public static KeyBinding toggleKey;
    public static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        AutoFishConfig.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autofish.toggle",
                GLFW.GLFW_KEY_V,
                AUTOFISH_CATEGORY
        ));

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autofish.settings",
                GLFW.GLFW_KEY_B,
                AUTOFISH_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleKey.wasPressed()) {
                AutoFishConfig.enabled = !AutoFishConfig.enabled;
                AutoFishConfig.save();
            }
            if (settingsKey.wasPressed()) {
                client.setScreen(new AutoFishScreen(null));
            }
            AutoFishLogic.getInstance().onTick();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            AutoFishHud.render(drawContext);
        });

        // Hypixel bite detection via chat/game messages
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            checkChatForBite(message);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            checkChatForBite(message);
        });
    }

    private void checkChatForBite(Text message) {
        if (message == null) return;
        String str = message.getString().trim();
        if (str.contains("!!!")) {
            AutoFishLogic.getInstance().onHypixelBite();
        }
    }
}
