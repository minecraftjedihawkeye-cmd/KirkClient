package com.autofish;

import com.autofish.mixin.FishingBobberAccessor;
import com.autofish.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Random;

public class AutoFishLogic {

    private static final AutoFishLogic INSTANCE = new AutoFishLogic();
    private static final Random RANDOM = new Random();
    private static final double BITE_VELOCITY_THRESHOLD = -0.3;
    private static final double ENTITY_SEARCH_RADIUS = 5.0;

    public enum State {
        IDLE,
        WAITING_FOR_BITE,
        BITE_DETECTED,
        REELING,
        WAITING_TO_RECAST,
        ON_BREAK,
        RECASTING
    }

    private State state = State.IDLE;
    private long stateChangeTime = 0;
    private int currentReelDelay = 0;
    private int currentRecastDelay = 0;
    private boolean needRelease = false;
    private WeakReference<FishingBobberEntity> trackedBobber = new WeakReference<>(null);
    private double prevBobberVelY = 0;
    private boolean bobberSettled = false;
    private int ticksInWater = 0;

    // Hypixel entity tracking
    private int lastTriggeredEntityId = -1;

    // Safe mode: miss cooldown
    private long missUntil = 0;

    // Safe mode: auto-break
    private long continuousFishingStart = 0;
    private long nextBreakTime = 0;
    private long breakUntilTime = 0;

    public static AutoFishLogic getInstance() {
        return INSTANCE;
    }

    public State getState() {
        return state;
    }

    public void trackBobber(FishingBobberEntity bobber) {
        trackedBobber = new WeakReference<>(bobber);
    }

    /**
     * Called from InGameHudMixin/chat listeners when "!!!" text is detected.
     * Fallback Hypixel detection via title/subtitle/action bar/chat.
     */
    public void onHypixelBite() {
        if (!AutoFishConfig.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (AutoFishConfig.onlyWithRod && !isHoldingFishingRod(mc)) return;
        if (state == State.IDLE || state == State.WAITING_FOR_BITE) {
            if (!rollMiss()) {
                triggerBite();
            }
        }
    }

    private void triggerBite() {
        state = State.BITE_DETECTED;
        stateChangeTime = System.currentTimeMillis();
        currentReelDelay = calculateDelay(AutoFishConfig.reelDelay);
        currentRecastDelay = calculateRecastDelay();
    }

    /**
     * Roll miss chance. Returns true if this bite should be missed.
     * Sets a cooldown to avoid re-triggering on the same bite.
     */
    private boolean rollMiss() {
        if (!AutoFishConfig.safeMode || AutoFishConfig.missChance <= 0) return false;
        if (RANDOM.nextInt(100) < AutoFishConfig.missChance) {
            // Miss this bite — ignore bites for 3–6 seconds
            missUntil = System.currentTimeMillis() + 3000 + RANDOM.nextInt(3000);
            return true;
        }
        return false;
    }

    private FishingBobberEntity getBobber() {
        FishingBobberEntity bobber = trackedBobber.get();
        if (bobber != null && !bobber.isRemoved()) return bobber;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.fishHook != null && !mc.player.fishHook.isRemoved())
            return mc.player.fishHook;
        return null;
    }

    private boolean isBobberAlive() {
        return getBobber() != null;
    }

    /**
     * Scan for Hypixel "!!!" hologram entities near the bobber.
     */
    private boolean checkForHypixelEntity(MinecraftClient mc) {
        if (mc.world == null) return false;

        Vec3d searchCenter = null;
        FishingBobberEntity bobber = getBobber();
        if (bobber != null) {
            searchCenter = new Vec3d(bobber.getX(), bobber.getY(), bobber.getZ());
        } else if (mc.player != null) {
            searchCenter = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
        if (searchCenter == null) return false;

        Box searchBox = new Box(
                searchCenter.x - ENTITY_SEARCH_RADIUS,
                searchCenter.y - ENTITY_SEARCH_RADIUS,
                searchCenter.z - ENTITY_SEARCH_RADIUS,
                searchCenter.x + ENTITY_SEARCH_RADIUS,
                searchCenter.y + ENTITY_SEARCH_RADIUS,
                searchCenter.z + ENTITY_SEARCH_RADIUS
        );

        List<Entity> entities = mc.world.getOtherEntities(null, searchBox);
        for (Entity entity : entities) {
            String name = entity.getName().getString();
            if (name.contains("!!!")) {
                if (entity.getId() != lastTriggeredEntityId) {
                    lastTriggeredEntityId = entity.getId();
                    return true;
                }
            }
        }
        return false;
    }

    public void onTick() {
        if (!AutoFishConfig.enabled) {
            if (state != State.IDLE) resetState();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }
        if (AutoFishConfig.onlyWithRod && !isHoldingFishingRod(mc)) {
            resetState();
            return;
        }

        if (needRelease) {
            releaseUseKey(mc);
            needRelease = false;
        }

        long now = System.currentTimeMillis();

        switch (state) {
            case IDLE:
                if (isBobberAlive()) {
                    state = State.WAITING_FOR_BITE;
                    bobberSettled = false;
                    ticksInWater = 0;
                    prevBobberVelY = 0;
                    // Start tracking continuous fishing time
                    if (continuousFishingStart == 0) {
                        continuousFishingStart = now;
                        scheduleNextBreak(now);
                    }
                }
                break;

            case WAITING_FOR_BITE:
                FishingBobberEntity bobber = getBobber();
                if (bobber == null) {
                    state = State.IDLE;
                    break;
                }

                ticksInWater++;
                if (ticksInWater > 20) bobberSettled = true;

                // If in miss cooldown, skip bite detection but keep tracking velocity
                if (now < missUntil) {
                    prevBobberVelY = bobber.getVelocity().y;
                    break;
                }

                // Method 1: Hypixel entity scan (no settling needed)
                if (checkForHypixelEntity(mc)) {
                    if (!rollMiss()) {
                        triggerBite();
                    }
                    break;
                }

                // Method 2: caughtFish tracked data field (vanilla)
                try {
                    if (((FishingBobberAccessor) bobber).getCaughtFish()) {
                        if (!rollMiss()) {
                            triggerBite();
                        }
                        break;
                    }
                } catch (Exception ignored) {
                }

                // Method 3: Velocity fallback (needs settling)
                Vec3d vel = bobber.getVelocity();
                double currentVelY = vel.y;
                if (bobberSettled) {
                    double velocityDrop = currentVelY - prevBobberVelY;
                    if (currentVelY < BITE_VELOCITY_THRESHOLD && velocityDrop < -0.2) {
                        prevBobberVelY = currentVelY;
                        if (!rollMiss()) {
                            triggerBite();
                        }
                        break;
                    }
                }
                prevBobberVelY = currentVelY;
                break;

            case BITE_DETECTED:
                if (now - stateChangeTime >= currentReelDelay) {
                    pressUseKey(mc);
                    needRelease = true;
                    state = State.REELING;
                    stateChangeTime = now;
                }
                break;

            case REELING:
                state = State.WAITING_TO_RECAST;
                stateChangeTime = System.currentTimeMillis();
                break;

            case WAITING_TO_RECAST:
                if (!isBobberAlive() && now - stateChangeTime >= currentRecastDelay) {
                    // Check if it's time for an auto-break
                    if (shouldTakeBreak(now)) {
                        int breakSeconds = 30 + RANDOM.nextInt(61); // 30–90 seconds
                        breakUntilTime = now + breakSeconds * 1000L;
                        state = State.ON_BREAK;
                        stateChangeTime = now;
                        break;
                    }
                    pressUseKey(mc);
                    needRelease = true;
                    state = State.RECASTING;
                } else if (now - stateChangeTime > 3000) {
                    state = State.IDLE;
                }
                break;

            case ON_BREAK:
                if (now >= breakUntilTime) {
                    // Break is over — recast and reset timer
                    continuousFishingStart = now;
                    scheduleNextBreak(now);
                    pressUseKey(mc);
                    needRelease = true;
                    state = State.RECASTING;
                }
                break;

            case RECASTING:
                state = State.IDLE;
                break;
        }
    }

    /**
     * Check if auto-break should trigger.
     */
    private boolean shouldTakeBreak(long now) {
        if (!AutoFishConfig.safeMode || AutoFishConfig.autoBreakMinutes <= 0) return false;
        return now >= nextBreakTime && continuousFishingStart > 0;
    }

    /**
     * Schedule the next break with gaussian variance (±30%).
     */
    private void scheduleNextBreak(long now) {
        if (AutoFishConfig.autoBreakMinutes <= 0) return;
        long baseInterval = AutoFishConfig.autoBreakMinutes * 60 * 1000L;
        // Gaussian variance: ±30% of the interval
        double variance = baseInterval * 0.15;
        long offset = (long) (RANDOM.nextGaussian() * variance);
        nextBreakTime = now + baseInterval + offset;
    }

    private void resetState() {
        state = State.IDLE;
        needRelease = false;
        bobberSettled = false;
        ticksInWater = 0;
        prevBobberVelY = 0;
        lastTriggeredEntityId = -1;
        continuousFishingStart = 0;
        nextBreakTime = 0;
        missUntil = 0;
    }

    /**
     * Calculate reel delay using gaussian distribution in safe mode.
     * Enforces a 250ms minimum reaction time when safe mode is on.
     */
    private int calculateDelay(int baseDelay) {
        if (!AutoFishConfig.randomizeDelays) return baseDelay;

        if (AutoFishConfig.safeMode) {
            // Enforce minimum human reaction time
            int effectiveBase = Math.max(250, baseDelay);
            // Gaussian: mean = effectiveBase, stddev = effectiveBase/4
            double stddev = effectiveBase / 4.0;
            int delay = (int) (effectiveBase + RANDOM.nextGaussian() * stddev);
            return Math.max(150, Math.min(delay, effectiveBase * 3));
        }

        // Non-safe mode: uniform ±25%
        if (baseDelay > 0) {
            int variance = Math.max(1, baseDelay / 4);
            return Math.max(0, baseDelay + RANDOM.nextInt(variance * 2 + 1) - variance);
        }
        return baseDelay;
    }

    /**
     * Calculate recast delay with extra hesitation in safe mode.
     * Adds 0–2 seconds of random "thinking" time on top of the base delay.
     */
    private int calculateRecastDelay() {
        int base = calculateDelay(AutoFishConfig.recastDelay);
        if (AutoFishConfig.safeMode) {
            base += RANDOM.nextInt(2001); // 0–2000ms extra hesitation
        }
        return base;
    }

    private void pressUseKey(MinecraftClient mc) {
        KeyBindingAccessor a = (KeyBindingAccessor) mc.options.useKey;
        a.setPressed(true);
        a.setTimesPressed(a.getTimesPressed() + 1);
    }

    private void releaseUseKey(MinecraftClient mc) {
        ((KeyBindingAccessor) mc.options.useKey).setPressed(false);
    }

    private boolean isHoldingFishingRod(MinecraftClient mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() instanceof FishingRodItem
                || mc.player.getOffHandStack().getItem() instanceof FishingRodItem;
    }
}
