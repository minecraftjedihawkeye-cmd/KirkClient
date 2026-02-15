package com.autofish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoFishConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("autofish.json");

    public static boolean enabled = false;
    public static int reelDelay = 100;
    public static int recastDelay = 150;
    public static boolean onlyWithRod = true;
    public static boolean randomizeDelays = true;
    public static boolean safeMode = true;
    public static int missChance = 5;
    public static int autoBreakMinutes = 15;

    private static class ConfigData {
        boolean enabled = false;
        int reelDelay = 100;
        int recastDelay = 150;
        boolean onlyWithRod = true;
        boolean randomizeDelays = true;
        boolean safeMode = true;
        int missChance = 5;
        int autoBreakMinutes = 15;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                ConfigData data = GSON.fromJson(json, ConfigData.class);
                if (data != null) {
                    enabled = data.enabled;
                    reelDelay = data.reelDelay;
                    recastDelay = data.recastDelay;
                    onlyWithRod = data.onlyWithRod;
                    randomizeDelays = data.randomizeDelays;
                    safeMode = data.safeMode;
                    missChance = data.missChance;
                    autoBreakMinutes = data.autoBreakMinutes;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            ConfigData data = new ConfigData();
            data.enabled = enabled;
            data.reelDelay = reelDelay;
            data.recastDelay = recastDelay;
            data.onlyWithRod = onlyWithRod;
            data.randomizeDelays = randomizeDelays;
            data.safeMode = safeMode;
            data.missChance = missChance;
            data.autoBreakMinutes = autoBreakMinutes;
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
