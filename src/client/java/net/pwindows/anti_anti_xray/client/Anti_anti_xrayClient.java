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

public class Anti_anti_xrayClient implements ClientModInitializer {

    @Override
    public void onInitializeClient()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (this.OPEN_SETTINGS.consumeClick()) {
                Screen currentScreen = Minecraft.getInstance().screen;
                Minecraft.getInstance().setScreen(
                        new SettingsScreen(Component.empty(), currentScreen)
                );
            }
        });
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
