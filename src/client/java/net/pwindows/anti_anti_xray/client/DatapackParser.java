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
        public int featureIndex = -1;
        public int generationStep = 6; // UNDERGROUND_ORES

        @Override
        public String toString() {
            return "OreRule{" +
                    "oreBlock=" + oreBlock +
                    ", count=" + count +
                    ", height=[" + minY + "," + maxY + "]" +
                    ", index=" + featureIndex +
                    ", step=" + generationStep +
                    '}';
        }
    }

    private final Map<String, List<OreRule>> dimensionRules = new HashMap<>();

    public void parseDatapack(File zipFile) throws IOException {
        Map<String, JsonObject> configuredFeatures = new HashMap<>();
        Map<String, JsonObject> placedFeatures = new HashMap<>();
        Map<String, JsonObject> biomes = new HashMap<>();

        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith("data/") || !name.endsWith(".json")) continue;
                try {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(zip.getInputStream(entry))).getAsJsonObject();
                    if (name.contains("worldgen/configured_feature")) {
                        configuredFeatures.put(name.substring(name.lastIndexOf('/') + 1, name.length() - 5), json);
                    } else if (name.contains("worldgen/placed_feature")) {
                        placedFeatures.put(name.substring(name.lastIndexOf('/') + 1, name.length() - 5), json);
                    } else if (name.contains("worldgen/biome/")) {
                        biomes.put(name.substring(name.lastIndexOf('/') + 1, name.length() - 5), json);
                    }
                } catch (Exception ignored) {}
            }
        }

        Map<String, BiomeIndexResolver.FeatureIndex> indexMap = BiomeIndexResolver.resolveIndices(biomes);

        for (Map.Entry<String, JsonObject> entry : placedFeatures.entrySet()) {
            String placedKey = entry.getKey();
            JsonObject placed = entry.getValue();
            try {
                String featureRef = placed.get("feature").getAsString();
                String featureId = featureRef.contains(":") ? featureRef.split(":")[1] : featureRef;
                JsonObject configured = configuredFeatures.get(featureId);
                if (configured == null) continue;
                String type = configured.get("type").getAsString();
                if (!type.equals("minecraft:ore")) continue;

                JsonArray placements = placed.getAsJsonArray("placement");
                OreRule rule = parseOreRule(configured, placements);
                if (rule != null && rule.oreBlock != null) {
                    BiomeIndexResolver.FeatureIndex fi = indexMap.get(featureRef);
                    if (fi != null) {
                        rule.featureIndex = fi.index();
                        rule.generationStep = fi.stepOrdinal();
                    }
                    dimensionRules.computeIfAbsent("overworld", k -> new ArrayList<>()).add(rule);
                }
            } catch (Exception ignored) {}
        }

        if (dimensionRules.isEmpty()) {
            dimensionRules.put("overworld", getVanillaOreRules());
        }
    }

    private List<OreRule> getVanillaOreRules() {
        List<OreRule> rules = new ArrayList<>();
        rules.add(createRule(Blocks.COAL_ORE, new Block[]{Blocks.STONE}, 17, 0.0f, 20, 136, 320, "trapezoid", 0, 0, 6));   // ore_coal_upper
        rules.add(createRule(Blocks.COAL_ORE, new Block[]{Blocks.STONE}, 17, 0.5f, 20, 0, 192, "trapezoid", 0, 1, 6));    // ore_coal_lower
        rules.add(createRule(Blocks.IRON_ORE, new Block[]{Blocks.STONE}, 9, 0.0f, 10, 80, 320, "uniform", 0, 2, 6));      // ore_iron_upper
        rules.add(createRule(Blocks.IRON_ORE, new Block[]{Blocks.DEEPSLATE}, 9, 0.0f, 10, -32, 32, "uniform", 0, 3, 6));  // ore_iron_middle
        rules.add(createRule(Blocks.IRON_ORE, new Block[]{Blocks.DEEPSLATE}, 9, 0.0f, 10, -64, -8, "uniform", 0, 4, 6));  // ore_iron_small
        rules.add(createRule(Blocks.COPPER_ORE, new Block[]{Blocks.STONE, Blocks.DEEPSLATE}, 10, 0.0f, 16, -16, 112, "trapezoid", 0, 5, 6)); // ore_copper
        rules.add(createRule(Blocks.GOLD_ORE, new Block[]{Blocks.STONE}, 9, 0.5f, 4, -64, 32, "trapezoid", 0, 6, 6));    // ore_gold
        rules.add(createRule(Blocks.REDSTONE_ORE, new Block[]{Blocks.STONE, Blocks.DEEPSLATE}, 8, 0.0f, 4, -64, 15, "uniform", 0, 7, 6)); // ore_redstone
        rules.add(createRule(Blocks.DIAMOND_ORE, new Block[]{Blocks.STONE, Blocks.DEEPSLATE}, 4, 0.5f, 7, -64, 16, "trapezoid", 0, 8, 6)); // ore_diamond
        rules.add(createRule(Blocks.LAPIS_ORE, new Block[]{Blocks.STONE, Blocks.DEEPSLATE}, 7, 0.0f, 2, -64, 64, "trapezoid", 0, 9, 6)); // ore_lapis
        rules.add(createRule(Blocks.EMERALD_ORE, new Block[]{Blocks.STONE, Blocks.DEEPSLATE}, 3, 0.0f, 100, -16, 320, "trapezoid", 0, 10, 6)); // ore_emerald
        return rules;
    }

    private OreRule createRule(Block oreBlock, Block[] replaceTargets, int veinSize, float discardChance, int count,
                               int minY, int maxY, String heightType, int plateau, int index, int step) {
        OreRule rule = new OreRule();
        rule.oreBlock = oreBlock;
        rule.replaceTargets.addAll(Arrays.asList(replaceTargets));
        rule.veinSize = veinSize;
        rule.discardChance = discardChance;
        rule.count = count;
        rule.minY = minY;
        rule.maxY = maxY;
        rule.heightProviderType = heightType;
        rule.plateau = plateau;
        rule.featureIndex = index;
        rule.generationStep = step;
        return rule;
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
                Block oreBlock = BuiltInRegistries.BLOCK.get(oreId).map(Holder.Reference::value).orElse(null);
                if (rule.oreBlock == null && oreBlock != null) {
                    rule.oreBlock = oreBlock;
                }
                JsonObject targetPredicate = t.getAsJsonObject("target");
                if (targetPredicate.has("blocks")) {
                    JsonArray blocks = targetPredicate.getAsJsonArray("blocks");
                    for (JsonElement b : blocks) {
                        Identifier blockId = Identifier.tryParse(b.getAsString());
                        Block replaceBlock = BuiltInRegistries.BLOCK.get(blockId).map(Holder.Reference::value).orElse(null);
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
                        rule.minY = height.getAsJsonObject("min_inclusive").get("absolute").getAsInt();
                        rule.maxY = height.getAsJsonObject("max_inclusive").get("absolute").getAsInt();
                    } else if (rule.heightProviderType.equals("minecraft:trapezoid")) {
                        rule.minY = height.getAsJsonObject("min_inclusive").get("absolute").getAsInt();
                        rule.maxY = height.getAsJsonObject("max_inclusive").get("absolute").getAsInt();
                        if (height.has("plateau")) rule.plateau = height.get("plateau").getAsInt();
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return rule;
    }

    public List<OreRule> getRulesForDimension(String dimension) {
        return dimensionRules.getOrDefault(dimension, Collections.emptyList());
    }
}