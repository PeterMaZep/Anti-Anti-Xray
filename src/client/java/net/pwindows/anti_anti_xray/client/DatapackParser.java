package net.pwindows.anti_anti_xray.client;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLoction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class DatapackParser {

    public static class OreRule {
        public Block oreBlock;          // e.g., Blocks.DIAMOND_ORE
        public Block[] replaceTargets;  // e.g., [Blocks.STONE, Blocks.DEEPSLATE]
        public int veinSize;            // max blocks per vein
        public float discardChance;     // air exposure discard chance
        public int count;               // veins per chunk
        public int minY, maxY;          // height range

        // Trapezoid distribution params from height provider
        public String heightProviderType; // "uniform" or "trapezoid"
        public int plateau;             // for trapezoid
    }

    private Map<String, List<OreRule>> dimensionRules = new HashMap<>();

    public void parseDatapack(File zipFile) throws IOException {
        Map<String, JsonObject> configuredFeatures = new HashMap<>();
        Map<String, JsonObject> placedFeatures = new HashMap<>();

        // Extract and parse JSONs from zip
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith("data/") && name.endsWith(".json")) {
                    // Skip non-worldgen files
                    if (!name.contains("worldgen/configured_feature") &&
                            !name.contains("worldgen/placed_feature")) continue;

                    JsonObject json = JsonParser.parseReader(
                            new InputStreamReader(zip.getInputStream(entry))
                    ).getAsJsonObject();

                    String key = name.substring(name.lastIndexOf('/') + 1, name.length() - 5);

                    if (name.contains("configured_feature")) {
                        configuredFeatures.put(key, json);
                    } else if (name.contains("placed_feature")) {
                        placedFeatures.put(key, json);
                    }
                }
            }
        }

        // Link placed features to configured features
        for (Map.Entry<String, JsonObject> entry : placedFeatures.entrySet()) {
            JsonObject placed = entry.getValue();
            JsonArray placements = placed.getAsJsonArray("placement");

            // Find the feature reference
            String featureId = placed.get("feature").getAsString();
            JsonObject configured = configuredFeatures.get(featureId.split(":")[1]);

            if (configured == null || !configured.get("type").getAsString().equals("minecraft:ore")) {
                continue; // Not an ore feature
            }

            OreRule rule = parseOreRule(configured, placements);
            if (rule != null) {
                // Group by dimension from biome filters in placement
                String dimension = extractDimension(placements);
                dimensionRules.computeIfAbsent(dimension, k -> new ArrayList<>()).add(rule);
            }
        }
    }

    private OreRule parseOreRule(JsonObject configured, JsonArray placements) {
        JsonObject config = configured.getAsJsonObject("config");
        OreRule rule = new OreRule();

        // Parse targets (what blocks get replaced)
        JsonArray targets = config.getAsJsonArray("targets");
        List<Block> replaceBlocks = new ArrayList<>();
        Block oreBlock = null;

        for (JsonElement target : targets) {
            JsonObject t = target.getAsJsonObject();
            // The "target" field has a block predicate for what to replace
            // The "state" field has what block to place
            JsonObject state = t.getAsJsonObject("state");
            String oreName = state.get("Name").getAsString();
            oreBlock = BuiltInRegistries.BLOCK.get(new ResourceLocation(oreName));

            // Parse the target predicate - usually a list of blocks
            JsonObject targetPredicate = t.getAsJsonObject("target");
            if (targetPredicate.has("blocks")) {
                JsonArray blocks = targetPredicate.getAsJsonArray("blocks");
                for (JsonElement b : blocks) {
                    Block replaceBlock = BuiltInRegistries.BLOCK.get(
                            new ResourceLocation(b.getAsString())
                    );
                    replaceBlocks.add(replaceBlock);
                }
            }
        }

        rule.oreBlock = oreBlock;
        rule.replaceTargets = replaceBlocks.toArray(new Block[0]);
        rule.veinSize = config.get("size").getAsInt();
        rule.discardChance = config.has("discard_chance_on_air_exposure")
                ? config.get("discard_chance_on_air_exposure").getAsFloat()
                : 0.0f;

        // Parse placements
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
                    rule.minY = height.get("min_inclusive").getAsJsonObject()
                            .get("absolute").getAsInt();
                    rule.maxY = height.get("max_inclusive").getAsJsonObject()
                            .get("absolute").getAsInt();
                    rule.plateau = height.has("plateau")
                            ? height.get("plateau").getAsInt() : 0;
                }
            }
        }

        return rule;
    }

    private String extractDimension(JsonArray placements) {
        // Check for biome-based placement filters to determine dimension
        // This is simplified — a full implementation needs to check biome tags
        for (JsonElement p : placements) {
            JsonObject placement = p.getAsJsonObject();
            if (placement.get("type").getAsString().equals("minecraft:biome")) {
                // Parse biome filter to determine dimension
                // For now, default to overworld
                return "overworld";
            }
        }
        return "overworld"; // Default
    }

    public List<OreRule> getRulesForDimension(String dimension) {
        return dimensionRules.getOrDefault(dimension, Collections.emptyList());
    }
}