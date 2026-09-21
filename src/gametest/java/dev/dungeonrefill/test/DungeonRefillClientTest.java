package dev.dungeonrefill.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dungeonrefill.DungeonRefill;
import dev.dungeonrefill.RefillConfig;
import dev.dungeonrefill.RefillScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public class DungeonRefillClientTest implements FabricClientGameTest {
    private static final String MORT_START = "tellraw @a [{\"text\":\"[NPC] \",\"color\":\"yellow\"},{\"text\":\"Mort\",\"color\":\"yellow\"},"
            + "{\"text\":\": Here, I found this map when I first entered the dungeon.\",\"color\":\"white\"}]";
    /** 20 tick first delay + 30 ticks per /gfs, with margin. */
    private static final int REFILL_WAIT = 150;

    private final List<String> sent = new CopyOnWriteArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientSendMessageEvents.COMMAND.register(sent::add);

        // Start from the default settings
        context.runOnClient(client -> {
            RefillConfig config = DungeonRefill.get().config();
            config.autoRefill = true;
            config.items = RefillConfig.defaultItems();
            config.save();
        });

        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            context.waitTicks(40);
            sp.getServer().runCommand("clear @a");

            // --- 1. Inventory scanning with default settings ---
            // 10 real SkyBlock pearls, split between hotbar and main inventory
            sp.getServer().runCommand("item replace entity @a hotbar.3 with ender_pearl[custom_data={id:\"ENDER_PEARL\"}] 6");
            sp.getServer().runCommand("item replace entity @a inventory.10 with ender_pearl[custom_data={id:\"ENDER_PEARL\"}] 4");
            // Plain pearls without a SkyBlock ID must not count
            sp.getServer().runCommand("item replace entity @a inventory.11 with ender_pearl 5");
            // A full stack of leaps -> nothing to get
            sp.getServer().runCommand("item replace entity @a hotbar.4 with paper[custom_data={id:\"SPIRIT_LEAP\"},item_name=\"Spirit Leap\"] 16");
            context.waitTicks(10);

            sp.getServer().runCommand("tellraw @a {\"text\":\"[NPC] Mort: Good luck.\",\"color\":\"red\"}");
            context.waitTicks(REFILL_WAIT);
            check(gfs().isEmpty(), "unrelated message triggered a refill: " + sent);

            sp.getServer().runCommand(MORT_START);
            context.waitTicks(REFILL_WAIT);
            expect("default settings", "gfs ENDER_PEARL 6", "gfs SUPERBOOM_TNT 64");

            // --- 2. Change settings through the menu with real mouse + keyboard input ---
            context.runOnClient(client -> client.getConnection().sendCommand("dungeonrefill"));
            context.waitForScreen(RefillScreen.class);
            context.takeScreenshot("dungeonrefill-menu-default");

            click(context, toggleButtonFor(context, "Ender Pearl amount"));          // Ender Pearl -> OFF
            replaceText(context, editBox(context, "Superboom TNT amount"), "32");    // Superboom 64 -> 32
            replaceText(context, editBox(context, "SkyBlock item ID"), "dungeon_stone");
            replaceText(context, editBox(context, "New item amount"), "5");
            click(context, button(context, "Add"));
            context.waitTick();
            context.takeScreenshot("dungeonrefill-menu-edited");
            click(context, button(context, "Done"));
            context.waitFor(client -> client.gui.screen() == null);

            JsonObject saved = JsonParser.parseString(Files.readString(RefillConfig.path())).getAsJsonObject();
            check(!savedItem(saved, "ENDER_PEARL").get("enabled").getAsBoolean(), "Ender Pearl should be disabled in " + saved);
            check(savedItem(saved, "SUPERBOOM_TNT").get("amount").getAsInt() == 32, "Superboom amount should be 32 in " + saved);
            JsonObject stone = savedItem(saved, "DUNGEON_STONE");
            check(stone.get("amount").getAsInt() == 5 && stone.get("enabled").getAsBoolean() && stone.get("custom").getAsBoolean(),
                    "custom Dungeon Stone entry wrong: " + stone);

            // --- 3. New settings are used on the next run ---
            sp.getServer().runCommand("item replace entity @a inventory.12 with cobblestone[custom_data={id:\"DUNGEON_STONE\"}] 2");
            context.waitTicks(10);
            sent.clear();
            sp.getServer().runCommand(MORT_START);
            context.waitTicks(REFILL_WAIT);
            expect("edited settings", "gfs SUPERBOOM_TNT 32", "gfs DUNGEON_STONE 3");

            // --- 4. /dungeonrefill toggle turns the auto trigger off ---
            context.runOnClient(client -> client.getConnection().sendCommand("dungeonrefill toggle"));
            context.waitTick();
            sent.clear();
            sp.getServer().runCommand(MORT_START);
            context.waitTicks(REFILL_WAIT);
            expect("auto refill disabled");

            // --- 5. /dungeonrefill now still works manually ---
            context.runOnClient(client -> client.getConnection().sendCommand("dungeonrefill now"));
            context.waitTicks(REFILL_WAIT);
            expect("manual refill", "gfs SUPERBOOM_TNT 32", "gfs DUNGEON_STONE 3");
            context.runOnClient(client -> client.getConnection().sendCommand("dungeonrefill toggle"));

            System.out.println("[DungeonRefillTest] ALL PASSED");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- helpers ----

    private List<String> gfs() {
        return sent.stream().filter(s -> s.startsWith("gfs")).toList();
    }

    private void expect(String step, String... commands) {
        System.out.println("[DungeonRefillTest] " + step + ": " + gfs());
        check(gfs().equals(List.of(commands)), step + ": expected " + List.of(commands) + " but got " + gfs());
        sent.clear();
    }

    private static JsonObject savedItem(JsonObject config, String id) {
        JsonArray items = config.getAsJsonArray("items");
        for (JsonElement e : items) {
            if (e.getAsJsonObject().get("id").getAsString().equals(id)) return e.getAsJsonObject();
        }
        throw new AssertionError(id + " missing from saved config " + config);
    }

    private static AbstractWidget find(ClientGameTestContext context, Predicate<GuiEventListener> filter, String what) {
        return context.computeOnClient(client -> {
            for (GuiEventListener child : client.gui.screen().children()) {
                if (filter.test(child)) return (AbstractWidget) child;
            }
            throw new AssertionError("no widget: " + what);
        });
    }

    private static EditBox editBox(ClientGameTestContext context, String name) {
        return (EditBox) find(context, c -> c instanceof EditBox box && box.getMessage().getString().equals(name), name);
    }

    private static Button button(ClientGameTestContext context, String label) {
        return (Button) find(context, c -> c instanceof Button b && b.getMessage().getString().equals(label), label);
    }

    /** The ON/OFF button on the same row as the amount box. */
    private static Button toggleButtonFor(ClientGameTestContext context, String amountBoxName) {
        EditBox box = editBox(context, amountBoxName);
        return (Button) find(context, c -> c instanceof Button b && b.getY() == box.getY() && b.getX() < box.getX(), "toggle for " + amountBoxName);
    }

    private static void click(ClientGameTestContext context, AbstractWidget widget) {
        double scale = context.computeOnClient(DungeonRefillClientTest::guiToWindow);
        context.getInput().setCursorPos((widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    private static void replaceText(ClientGameTestContext context, EditBox box, String text) {
        click(context, box);
        context.getInput().holdControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_A);
        context.getInput().releaseControl();
        context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
        context.getInput().typeChars(text);
        context.waitTick();
        String value = context.computeOnClient(client -> box.getValue());
        check(value.equals(text), "typed '" + text + "' but box has '" + value + "'");
    }

    private static double guiToWindow(Minecraft client) {
        return client.getWindow().getScreenWidth() / (double) client.getWindow().getGuiScaledWidth();
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
