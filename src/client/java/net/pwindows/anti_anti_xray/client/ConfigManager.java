package net.pwindows.anti_anti_xray.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Minecraft.getInstance().gameDirectory.toPath().resolve("anti_anti_xray_configs");

    public static class ServerConfig {
        public Map<String, String> seeds = new HashMap<>();
        public Map<String, String> datapacks = new HashMap<>();
    }

    public static String getKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip.replace(":", "_");
        } else if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "default";
    }

    public static String getCurrentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return mc.player.level().dimension().toString()
                    .replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        return "minecraft_overworld";
    }

    public static File saveDatapack(File source, String dimension) {
        try {
            Path dimensionDir = CONFIG_DIR
                    .resolve("datapacks")
                    .resolve(getKey())
                    .resolve(dimension.replace(":", "_"));
            Files.createDirectories(dimensionDir);
            Path destination = dimensionDir.resolve(source.getName());
            Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination.toFile();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ServerConfig load() {
        try {
            Path file = CONFIG_DIR.resolve(getKey()).resolve("config.json");
            if (Files.exists(file)) {
                Reader reader = Files.newBufferedReader(file);
                ServerConfig config = GSON.fromJson(reader, ServerConfig.class);
                reader.close();
                return config;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ServerConfig();
    }

    public static void save(ServerConfig config) {
        try {
            Path dir = CONFIG_DIR.resolve(getKey());
            Files.createDirectories(dir);
            Path file = dir.resolve("config.json");
            Writer writer = Files.newBufferedWriter(file);
            GSON.toJson(config, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}