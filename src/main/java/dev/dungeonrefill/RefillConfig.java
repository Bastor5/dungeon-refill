package dev.dungeonrefill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RefillConfig {
    public static final int MIN_AMOUNT = 1;
    public static final int MAX_AMOUNT = 2240;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("dungeonrefill.json");

    /** Refill automatically when a dungeon run starts. */
    public boolean autoRefill = true;
    public List<RefillItem> items = defaultItems();

    public static List<RefillItem> defaultItems() {
        List<RefillItem> list = new ArrayList<>();
        list.add(new RefillItem("SPIRIT_LEAP", "Spirit Leap", 16, true, false));
        list.add(new RefillItem("ENDER_PEARL", "Ender Pearl", 16, true, false));
        list.add(new RefillItem("SUPERBOOM_TNT", "Superboom TNT", 64, true, false));
        list.add(new RefillItem("DECOY", "Decoy", 16, false, false));
        list.add(new RefillItem("INFLATABLE_JERRY", "Inflatable Jerry", 16, false, false));
        list.add(new RefillItem("ARCHITECT_FIRST_DRAFT", "Architect's First Draft", 1, false, false));
        list.add(new RefillItem("TOXIC_ARROW_POISON", "Toxic Arrow Poison", 32, false, false));
        list.add(new RefillItem("TWILIGHT_ARROW_POISON", "Twilight Arrow Poison", 16, false, false));
        return list;
    }

    public static Path path() {
        return PATH;
    }

    public static RefillConfig load() {
        if (Files.exists(PATH)) {
            try {
                RefillConfig config = GSON.fromJson(Files.readString(PATH), RefillConfig.class);
                if (config != null && config.items != null) {
                    config.items.removeIf(item -> item == null || item.id == null || item.id.isBlank());
                    for (RefillItem item : config.items) {
                        item.id = normalizeId(item.id);
                        item.amount = clampAmount(item.amount);
                        if (item.name == null || item.name.isBlank()) item.name = prettyName(item.id);
                    }
                    return config;
                }
            } catch (Exception e) {
                DungeonRefill.LOGGER.error("Failed to read {}, using defaults", PATH, e);
            }
        }
        RefillConfig config = new RefillConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            DungeonRefill.LOGGER.error("Failed to save {}", PATH, e);
        }
    }

    public static int clampAmount(int amount) {
        return Math.max(MIN_AMOUNT, Math.min(MAX_AMOUNT, amount));
    }

    public static String normalizeId(String id) {
        return id.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /** SUPERBOOM_TNT -> Superboom Tnt */
    public static String prettyName(String id) {
        StringBuilder sb = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
