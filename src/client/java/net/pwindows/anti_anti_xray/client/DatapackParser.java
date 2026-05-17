package net.pwindows.anti_anti_xray.client;

import com.google.gson.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class DatapackParser {

    public static class OreRule {
        public Block oreBlock;
        public List<Block> replaceTargets = new ArrayList<>();
        public int veinSize;
        public float discardChance;
        public int count;
        public int minY, maxY;
        public String heightProviderType;
        public int plateau;

        @Override
        public String toString() {
            return "OreRule{" +
                    "oreBlock=" + oreBlock +
                    ", replaceTargets=" + replaceTargets +
                    ", veinSize=" + veinSize +
                    ", count=" + count +
                    ", height=[" + minY + "," + maxY + "]" +
                    '}';
        }
    }

    private final Map<String, List<OreRule>> dimensionRules = new HashMap<>();

    public void parseDatapack(File zipFile) throws IOException {
        Map<String, JsonObject> configuredFeatures = new HashMap<>();
        Map<String, JsonObject> placedFeatures = new HashMap<>();

        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith("data/") || !name.endsWith(".json")) continue;
                if (!name.contains("worldgen/configured_feature") &&
                        !name.contains("worldgen/placed_feature")) continue;

                try {
                    JsonObject json = JsonParser.parseReader(
                            new InputStreamReader(zip.getInputStream(entry))
                    ).getAsJsonObject();

                    String key = name.substring(name.lastIndexOf('/') + 1, name.length() - 5);

                    if (name.contains("configured_feature")) {
                        configuredFeatures.put(key, json);
                    } else if (name.contains("placed_feature")) {
                        placedFeatures.put(key, json);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse: " + name + " - " + e.getMessage());
                }
            }
        }

        System.out.println("Found " + configuredFeatures.size() + " configured features");
        System.out.println("Found " + placedFeatures.size() + " placed features");

        for (Map.Entry<String, JsonObject> entry : placedFeatures.entrySet()) {
            String placedKey = entry.getKey();
            JsonObject placed = entry.getValue();

            try {
                String featureRef = placed.get("feature").getAsString();
                String featureId;
                if (featureRef.contains(":")) {
                    featureId = featureRef.split(":")[1];
                } else {
                    featureId = featureRef;
                }

                JsonObject configured = configuredFeatures.get(featureId);
                if (configured == null) {
                    System.out.println("No configured feature found for: " + featureId);
                    continue;
                }

                String type = configured.get("type").getAsString();
                if (!type.equals("minecraft:ore") && !type.equals("minecraft:ore_feature")) {
                    continue;
                }

                JsonArray placements = placed.getAsJsonArray("placement");
                OreRule rule = parseOreRule(configured, placements);

                if (rule != null && rule.oreBlock != null) {
                    dimensionRules.computeIfAbsent("overworld", k -> new ArrayList<>()).add(rule);
                    System.out.println("Parsed ore rule: " + rule);
                }
            } catch (Exception e) {
                System.err.println("Failed to process placed feature " + placedKey + ": " + e.getMessage());
            }
        }

        System.out.println("Total ore rules parsed: " + dimensionRules.values().stream().mapToInt(List::size).sum());
    }

    private OreRule parseOreRule(JsonObject configured, JsonArray placements) {
        OreRule rule = new OreRule();

        try {
            JsonObject config = configured.getAsJsonObject("config");

            JsonArray targets = config.getAsJsonArray("targets");
            for (JsonElement target : targets) {
                JsonObject t = target.getAsJsonObject();
                JsonObject state = t.getAsJsonObject("state");
                String oreName = state.get("Name").getAsString();
                Identifier oreId = Identifier.tryParse(oreName);

                // Fixed: get() returns Optional<Holder.Reference<Block>>
                Block oreBlock = BuiltInRegistries.BLOCK.get(oreId)
                        .map(Holder.Reference::value)
                        .orElse(null);
                if (rule.oreBlock == null && oreBlock != null) {
                    rule.oreBlock = oreBlock;
                }

                JsonObject targetPredicate = t.getAsJsonObject("target");
                if (targetPredicate.has("blocks")) {
                    JsonArray blocks = targetPredicate.getAsJsonArray("blocks");
                    for (JsonElement b : blocks) {
                        Identifier blockId = Identifier.tryParse(b.getAsString());
                        Block replaceBlock = BuiltInRegistries.BLOCK.get(blockId)
                                .map(Holder.Reference::value)
                                .orElse(null);
                        if (replaceBlock != null && replaceBlock != Blocks.AIR) {
                            rule.replaceTargets.add(replaceBlock);
                        }
                    }
                }
            }

            rule.veinSize = config.get("size").getAsInt();

            if (config.has("discard_chance_on_air_exposure")) {
                rule.discardChance = config.get("discard_chance_on_air_exposure").getAsFloat();
            }

            for (JsonElement p : placements) {
                JsonObject placement = p.getAsJsonObject();
                String type = placement.get("type").getAsString();

                if (type.equals("minecraft:count")) {
                    rule.count = placement.get("count").getAsInt();
                } else if (type.equals("minecraft:height_range")) {
                    JsonObject height = placement.getAsJsonObject("height");
                    rule.heightProviderType = height.get("type").getAsString();

                    if (rule.heightProviderType.equals("minecraft:uniform")) {
                        JsonObject min = height.getAsJsonObject("min_inclusive");
                        JsonObject max = height.getAsJsonObject("max_inclusive");
                        rule.minY = min.get("absolute").getAsInt();
                        rule.maxY = max.get("absolute").getAsInt();
                    } else if (rule.heightProviderType.equals("minecraft:trapezoid")) {
                        rule.minY = height.getAsJsonObject("min_inclusive")
                                .get("absolute").getAsInt();
                        rule.maxY = height.getAsJsonObject("max_inclusive")
                                .get("absolute").getAsInt();
                        if (height.has("plateau")) {
                            rule.plateau = height.get("plateau").getAsInt();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing ore rule: " + e.getMessage());
            return null;
        }

        return rule;
    }

    public List<OreRule> getRulesForDimension(String dimension) {
        return dimensionRules.getOrDefault(dimension, Collections.emptyList());
    }
}