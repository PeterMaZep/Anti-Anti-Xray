package net.pwindows.anti_anti_xray.client;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class BiomeIndexResolver {

    public record FeatureIndex(int stepOrdinal, int index) {}

    // GenerationStep.Decoration ordinals
    private static final Map<String, Integer> STEP_ORDINALS = Map.ofEntries(
            Map.entry("raw_generation", 0),
            Map.entry("lakes", 1),
            Map.entry("local_modifications", 2),
            Map.entry("underground_structures", 3),
            Map.entry("surface_structures", 4),
            Map.entry("strongholds", 5),
            Map.entry("underground_ores", 6),
            Map.entry("underground_decoration", 7),
            Map.entry("fluid_springs", 8),
            Map.entry("vegetal_decoration", 9),
            Map.entry("top_layer_modification", 10)
    );

    public static Map<String, FeatureIndex> resolveIndices(Map<String, JsonObject> biomeJsons) {
        Map<String, FeatureIndex> result = new HashMap<>();

        for (JsonObject biomeJson : biomeJsons.values()) {
            if (!biomeJson.has("features")) continue;
            JsonElement featuresElem = biomeJson.get("features");
            if (!featuresElem.isJsonArray()) continue;

            JsonArray featuresArray = featuresElem.getAsJsonArray();
            Map<String, Integer> stepCounters = new HashMap<>();

            for (JsonElement entry : featuresArray) {
                if (!entry.isJsonObject()) continue;
                JsonObject featureEntry = entry.getAsJsonObject();
                if (!featureEntry.has("feature") || !featureEntry.has("step")) continue;

                String placedFeatureId = featureEntry.get("feature").getAsString();
                String stepName = featureEntry.get("step").getAsString();
                int stepOrdinal = STEP_ORDINALS.getOrDefault(stepName, -1);
                if (stepOrdinal < 0) continue;

                int index = stepCounters.merge(stepName, 1, Integer::sum) - 1; // 0-based

                result.merge(placedFeatureId, new FeatureIndex(stepOrdinal, index),
                        (existing, incoming) -> existing.index() <= incoming.index() ? existing : incoming);
            }
        }
        return result;
    }

    public static Map<String, JsonObject> loadBiomes(File datapackZip) {
        Map<String, JsonObject> biomes = new HashMap<>();
        if (datapackZip != null) {
            try (ZipFile zip = new ZipFile(datapackZip)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("data/") && name.contains("worldgen/biome/") && name.endsWith(".json")) {
                        try {
                            JsonObject json = JsonParser.parseReader(
                                    new InputStreamReader(zip.getInputStream(entry))
                            ).getAsJsonObject();
                            biomes.put(name.substring(name.lastIndexOf('/') + 1), json);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Fallback to bundled vanilla biomes
        if (biomes.isEmpty()) {
            try {
                Path biomeDir = Path.of(BiomeIndexResolver.class.getClassLoader()
                        .getResource("data/minecraft/worldgen/biome").toURI());
                Files.walk(biomeDir).filter(Files::isRegularFile).forEach(path -> {
                    try {
                        JsonObject json = JsonParser.parseReader(Files.newBufferedReader(path)).getAsJsonObject();
                        biomes.put(path.getFileName().toString(), json);
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return biomes;
    }
}