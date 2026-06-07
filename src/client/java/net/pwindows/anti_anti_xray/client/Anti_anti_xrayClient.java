package net.pwindows.anti_anti_xray.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.pwindows.anti_anti_xray.Anti_anti_xray;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;

public class Anti_anti_xrayClient implements ClientModInitializer {

    private boolean configLoaded = false;

    @Override
    public void onInitializeClient()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Load config when world is ready
            if (client.level != null && client.player != null && !configLoaded) {
                loadConfig();
                configLoaded = true;
            }

            while (this.OPEN_SETTINGS.consumeClick()) {
                Screen currentScreen = Minecraft.getInstance().screen;
                Minecraft.getInstance().setScreen(
                        new SettingsScreen(Component.empty(), currentScreen)
                );
                configLoaded = false; // Reload config when settings screen closes
            }
        });

        ChunkLoadHandler.register();
    }

    private void loadConfig() {
        ConfigManager.ServerConfig config = ConfigManager.load();
        String dim = ConfigManager.getCurrentDimension();

        // Load seed
        String seedStr = config.seeds.get(dim);
        if (seedStr != null && !seedStr.isEmpty()) {
            System.out.println("Loading seed for " + dim + ": " + seedStr);
            OreCache.setSeed(seedStr);
        } else {
            System.out.println("No seed found for dimension " + dim);
        }

        // Load datapack
        String datapackPath = config.datapacks.get(dim);
        if (datapackPath != null) {
            File datapackFile = new File(datapackPath);
            if (datapackFile.exists()) {
                System.out.println("Loading datapack: " + datapackPath);
                DatapackParser parser = new DatapackParser();
                try {
                    parser.parseDatapack(datapackFile);
                    OreCache.setRules(parser.getRulesForDimension(dim));
                } catch (Exception e) {
                    System.err.println("Failed to parse datapack: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            // Even without a datapack, we should have vanilla ore rules
            // Let's load the built-in vanilla rules
            System.out.println("No datapack found, loading vanilla rules");
            OreCache.setRules(getVanillaOreRules());
        }
    }

    private List<DatapackParser.OreRule> getVanillaOreRules() {
        // Return empty for now - we'll add hardcoded vanilla rules later if needed
        // The seed calculation should still work without explicit rules
        return new java.util.ArrayList<>();
    }

    KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Anti_anti_xray.MOD_ID, "anti_anti_xray")
    );

    KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.antiantixray.opensettings",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_X,
                    this.CATEGORY
            )
    );

    KeyMapping PANIK_BUTTON = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.antiantixray.panikbutton",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_P,
                    this.CATEGORY
            )
    );
}