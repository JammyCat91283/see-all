package com.marc.seeall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.player.InputCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SeeAllMod implements ClientModInitializer {
    public static final String MOD_ID = "see-all";
    private static final int TICK_INTERVAL = 20; // Check every second (20 ticks)
    private int tickCounter = 0;

    public static SeeAllConfig config;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(MinecraftClient.getInstance().runDirectory, "config/see-all.json");

    private SeeAllWarningManager warningManager;

    @Override
    public void onInitializeClient() {
        // Load configuration
        loadConfig();
        warningManager = new SeeAllWarningManager(config.getWarningCooldown());

        // Register client tick event for player detection
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter >= TICK_INTERVAL) {
                tickCounter = 0;
                if (client.player != null && client.world != null) {
                    checkNearbyPlayers(client.player, client);
                }
            }
        });

        // Register mouse click event for whitelist/blacklist
        InputCallback.EVENT.register((client, mouseX, mouseY, button, action, mods) -> {
            // Check for middle click (button 2) and if action is press (GLFW_PRESS)
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && action == GLFW.GLFW_PRESS && client.player != null && client.crosshairTarget != null) {
                if (client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHitResult = (EntityHitResult) client.crosshairTarget;
                    Entity targetEntity = entityHitResult.getEntity();

                    if (targetEntity instanceof PlayerEntity && targetEntity != client.player) {
                        UUID targetUuid = targetEntity.getUuid();
                        String targetName = targetEntity.getName().getString();

                        if (config.isWhitelistMode()) {
                            // Whitelist mode: middle click to add to whitelist
                            if (config.getPlayerList().contains(targetUuid.toString())) {
                                config.removePlayerFromList(targetUuid.toString());
                                client.player.sendMessage(Text.literal("§aRemoved " + targetName + " from whitelist. Warnings will now show."), false);
                            } else {
                                config.addPlayerToList(targetUuid.toString());
                                client.player.sendMessage(Text.literal("§aAdded " + targetName + " to whitelist. Warnings will now be suppressed for this player."), false);
                            }
                        } else {
                            // Blacklist mode: middle click to add to blacklist
                            if (config.getPlayerList().contains(targetUuid.toString())) {
                                config.removePlayerFromList(targetUuid.toString());
                                client.player.sendMessage(Text.literal("§aRemoved " + targetName + " from blacklist. Warnings will now show for this player."), false);
                            } else {
                                config.addPlayerToList(targetUuid.toString());
                                client.player.sendMessage(Text.literal("§aAdded " + targetName + " to blacklist. Warnings will now be suppressed for this player."), false);
                            }
                        }
                        saveConfig(); // Save configuration after changes
                        return true; // Consume event
                    }
                }
            }
            return false; // Don't consume event
        });

        // Register a keybinding to toggle whitelist/blacklist mode
        KeyBinding toggleModeKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seeall.toggle_mode", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_UNKNOWN, // The keycode of the key
                "category.seeall.general" // The translation key of the keybinding's category.
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleModeKeyBinding.wasPressed()) {
                config.setWhitelistMode(!config.isWhitelistMode());
                saveConfig();
                client.player.sendMessage(Text.literal("§eSee-All mode toggled to: " + (config.isWhitelistMode() ? "§bWhitelist" : "§cBlacklist")), false);
            }
        });

        // Register a keybinding to toggle mod active status
        KeyBinding toggleModKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seeall.toggle_mod", // The translation key of the keybinding's name
                InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
                GLFW.GLFW_KEY_UNKNOWN, // The keycode of the key
                "category.seeall.general" // The translation key of the keybinding's category.
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (toggleModKeyBinding.wasPressed()) {
                config.setActive(!config.isActive());
                saveConfig();
                client.player.sendMessage(Text.literal("§eSee-All mod toggled " + (config.isActive() ? "§aON" : "§cOFF")), false);
            }
        });

        // Register a keybinding to adjust warning radius
        KeyBinding increaseRadiusKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seeall.increase_radius",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.seeall.general"
        ));
        KeyBinding decreaseRadiusKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.seeall.decrease_radius",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.seeall.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (increaseRadiusKeyBinding.wasPressed()) {
                config.setDetectionRadius(Math.min(100, config.getDetectionRadius() + 5)); // Max 100 blocks
                saveConfig();
                client.player.sendMessage(Text.literal("§eDetection radius set to: §f" + config.getDetectionRadius() + " blocks"), false);
            }
            if (decreaseRadiusKeyBinding.wasPressed()) {
                config.setDetectionRadius(Math.max(5, config.getDetectionRadius() - 5)); // Min 5 blocks
                saveConfig();
                client.player.sendMessage(Text.literal("§eDetection radius set to: §f" + config.getDetectionRadius() + " blocks"), false);
            }
        });

        System.out.println("See-All mod initialized!");
    }

    private void checkNearbyPlayers(PlayerEntity player, MinecraftClient client) {
        if (!config.isActive()) {
            return; // Mod is inactive
        }

        for (PlayerEntity otherPlayer : client.world.getPlayers()) {
            if (otherPlayer == player) {
                continue; // Skip the local player
            }

            double distance = player.distanceTo(otherPlayer);

            // Apply whitelist/blacklist filter first
            boolean shouldWarn = true;
            if (config.isWhitelistMode()) {
                // In whitelist mode, if player is NOT in list, warn. If player IS in list, DON'T warn.
                if (config.getPlayerList().contains(otherPlayer.getUuid().toString())) {
                    shouldWarn = false;
                }
            } else {
                // In blacklist mode, if player IS in list, DON'T warn. If player is NOT in list, warn.
                if (config.getPlayerList().contains(otherPlayer.getUuid().toString())) {
                    shouldWarn = false;
                }
            }

            if (!shouldWarn) {
                continue; // Skip warning if filtered
            }

            // Condition 1: Player is NOT visible AND within WARNING_RADIUS
            boolean isVisible = isPlayerVisible(player, otherPlayer, client);
            if (!isVisible && distance <= config.getDetectionRadius()) {
                // Check if behind
                if (isPlayerBehind(player, otherPlayer)) {
                    warningManager.sendWarning(player, otherPlayer, Text.literal("§4BEHIND YOU! (" + otherPlayer.getName().getString() + ")"));
                } else {
                    warningManager.sendWarning(player, otherPlayer, Text.literal("§eUnseen player nearby: " + otherPlayer.getName().getString() + " (Dist: " + String.format("%.1f", distance) + "m)"));
                }
                continue; // Process next player
            }

            // Condition 2: Player has bow AND is aiming where it'll land near you (simplified)
            // This checks if the player is actively using a bow and is generally facing towards the local player.
            if (otherPlayer.isUsingItem() && otherPlayer.getActiveItem().getItem() instanceof BowItem) {
                if (isPlayerFacingTowards(otherPlayer, player)) {
                    warningManager.sendWarning(player, otherPlayer, Text.literal("§c" + otherPlayer.getName().getString() + " is aiming a bow near you!"));
                }
            }
        }
    }

    private boolean isPlayerVisible(PlayerEntity player, PlayerEntity otherPlayer, MinecraftClient client) {
        // Perform a raycast from the local player's eyes to the other player's head.
        // If the ray hits a block before reaching the other player, they are not visible.
        Vec3d eyePos = player.getEyePos();
        Vec3d targetPos = otherPlayer.getEyePos(); // Aim for the other player's head/eyes for visibility

        HitResult hitResult = client.world.raycast(
                eyePos, // Start position
                targetPos, // End position
                player.getBoundingBox().expand(0.1), // Expand bounding box slightly for self-exclusion
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, // Check against block colliders
                net.minecraft.world.RaycastContext.FluidHandling.NONE // Don't check against fluids
        );

        // If the raycast hit something and it wasn't the other player, then the other player is not visible.
        // It's possible the raycast hits the other player's bounding box itself, so we ensure it's a block.
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return false; // Raycast hit a block, so the player is not visible
        }

        // If the raycast hit an entity, check if it's the target player.
        // If it hit something else first, then the player is not visible.
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hitResult).getEntity() == otherPlayer;
        }

        // If no hit or hit the target player directly, consider them visible.
        return true;
    }


    private boolean isPlayerBehind(PlayerEntity player, PlayerEntity otherPlayer) {
        // Calculate the vector from the player to the other player
        Vec3d playerToOther = otherPlayer.getPos().subtract(player.getPos()).normalize();

        // Get the player's look vector
        Vec3d playerLook = player.getRotationVector().normalize();

        // Calculate the dot product. A negative dot product means they are generally behind.
        // A value of -1 means directly behind, 0 means to the side, 1 means directly in front.
        double dotProduct = playerLook.dotProduct(playerToOther);

        // Define a threshold for "behind" (e.g., within ~100 degrees of directly behind)
        // Cosine of 90 degrees is 0, so anything less than 0 is behind.
        // -0.2 corresponds to an angle of ~101.5 degrees, giving a decent arc for "behind".
        return dotProduct < -0.2; // Adjust threshold as needed
    }

    private boolean isPlayerFacingTowards(PlayerEntity aimer, PlayerEntity target) {
        // Calculate the vector from the aimer to the target
        Vec3d aimerToTarget = target.getPos().subtract(aimer.getPos()).normalize();

        // Get the aimer's look vector
        Vec3d aimerLook = aimer.getRotationVector().normalize();

        // Calculate the dot product. A positive dot product means they are generally facing each other.
        double dotProduct = aimerLook.dotProduct(aimerToTarget);

        // Define a threshold for "facing towards" (e.g., within 45 degrees of straight ahead)
        // Cosine of 45 degrees is approx 0.707. This means if the aimer's view vector is within
        // +/- 45 degrees of the vector pointing to the target, they are considered to be facing them.
        return dotProduct > 0.7; // Adjust threshold as needed
    }


    // --- Configuration Management ---
    private static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, SeeAllConfig.class);
                if (config == null) { // Handle case where file is empty or malformed
                    config = new SeeAllConfig();
                    System.err.println("See-All: Config file was empty or malformed, creating new default config.");
                    saveConfig(); // Save newly created default config
                }
            } catch (IOException e) {
                System.err.println("See-All: Failed to load config from " + CONFIG_FILE.getAbsolutePath() + ": " + e.getMessage());
                config = new SeeAllConfig();
                saveConfig(); // Create and save a new config if loading fails
            }
        } else {
            config = new SeeAllConfig();
            saveConfig(); // Create and save default config if file doesn't exist
        }
    }

    public static void saveConfig() {
        // Ensure the config directory exists
        if (!CONFIG_FILE.getParentFile().exists()) {
            CONFIG_FILE.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("See-All: Failed to save config to " + CONFIG_FILE.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    // --- Nested Static Class for Configuration ---
    public static class SeeAllConfig {
        private boolean active = true; // Is the mod active?
        private int detectionRadius = 20; // Default warning radius in blocks
        private boolean whitelistMode = false; // true for whitelist, false for blacklist
        private Set<String> playerList = new HashSet<>(); // Stores player UUIDs as strings
        private long warningCooldown = 5000; // Cooldown in milliseconds per player

        public SeeAllConfig() {
            // Default constructor for Gson
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public int getDetectionRadius() {
            return detectionRadius;
        }

        public void setDetectionRadius(int detectionRadius) {
            this.detectionRadius = detectionRadius;
        }

        public boolean isWhitelistMode() {
            return whitelistMode;
        }

        public void setWhitelistMode(boolean whitelistMode) {
            this.whitelistMode = whitelistMode;
        }

        public Set<String> getPlayerList() {
            return playerList;
        }

        public void addPlayerToList(String uuid) {
            playerList.add(uuid);
        }

        public void removePlayerFromList(String uuid) {
            playerList.remove(uuid);
        }

        public long getWarningCooldown() {
            return warningCooldown;
        }

        public void setWarningCooldown(long warningCooldown) {
            this.warningCooldown = warningCooldown;
        }
    }

    // --- Nested Static Class for Warning Management ---
    public static class SeeAllWarningManager {
        private final long cooldownDuration; // Cooldown in milliseconds
        private final Map<UUID, Long> lastWarningTimes = new HashMap<>();

        public SeeAllWarningManager(long cooldownDuration) {
            this.cooldownDuration = cooldownDuration;
        }

        public void sendWarning(PlayerEntity localPlayer, PlayerEntity warnedPlayer, Text message) {
            UUID warnedPlayerUuid = warnedPlayer.getUuid();
            long currentTime = System.currentTimeMillis();

            // Check if enough time has passed since the last warning for this player
            if (!lastWarningTimes.containsKey(warnedPlayerUuid) || (currentTime - lastWarningTimes.get(warnedPlayerUuid) >= cooldownDuration)) {
                localPlayer.sendMessage(message, false); // Send a non-persistent chat message
                lastWarningTimes.put(warnedPlayerUuid, currentTime); // Update last warning time
            }
        }
    }
}
