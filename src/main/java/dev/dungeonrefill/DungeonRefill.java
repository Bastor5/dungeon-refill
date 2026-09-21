package dev.dungeonrefill;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

public class DungeonRefill implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("dungeonrefill");

    /** Mort's line when the run starts (sent right after "Starting in 1 second."). */
    private static final String START_MESSAGE = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    /** Delay before the first /gfs, then between each one, so Hypixel doesn't rate-limit them. */
    private static final int FIRST_DELAY_TICKS = 20;
    private static final int GAP_TICKS = 30;

    private static DungeonRefill instance;

    private RefillConfig config;
    private final ArrayDeque<RefillItem> queue = new ArrayDeque<>();
    private int cooldown;
    private boolean openMenuNextTick;

    public static DungeonRefill get() {
        return instance;
    }

    public RefillConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = RefillConfig.load();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && config.autoRefill && ChatFormatting.stripFormatting(message.getString()).trim().equals(START_MESSAGE)) {
                startRefill();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("dungeonrefill")
                        .executes(ctx -> {
                            // The chat screen is still closing while the command runs, so open the menu next tick
                            openMenuNextTick = true;
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(ClientCommands.literal("now").executes(ctx -> {
                            startRefill();
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommands.literal("toggle").executes(ctx -> {
                            config.autoRefill = !config.autoRefill;
                            config.save();
                            chat(Component.literal("Auto refill " + (config.autoRefill ? "enabled" : "disabled"))
                                    .withStyle(config.autoRefill ? ChatFormatting.GREEN : ChatFormatting.RED));
                            return Command.SINGLE_SUCCESS;
                        }))
        ));
    }

    /** Queues every enabled item; how many are missing is worked out right before each /gfs is sent. */
    public void startRefill() {
        queue.clear();
        for (RefillItem item : config.items) {
            if (item.enabled) queue.add(item);
        }
        cooldown = FIRST_DELAY_TICKS;
    }

    private void tick(Minecraft client) {
        if (openMenuNextTick) {
            openMenuNextTick = false;
            client.setScreen(new RefillScreen(null));
        }
        if (queue.isEmpty()) return;
        if (client.player == null || client.getConnection() == null) {
            queue.clear();
            return;
        }
        if (--cooldown > 0) return;

        while (!queue.isEmpty()) {
            RefillItem item = queue.poll();
            int missing = item.amount - countInInventory(client.player.getInventory(), item.id);
            if (missing > 0) {
                client.getConnection().sendCommand("gfs " + item.id + " " + missing);
                chat(Component.literal("Getting " + missing + "x " + item.name + " from sacks").withStyle(ChatFormatting.GRAY));
                cooldown = GAP_TICKS;
                return;
            }
        }
    }

    /** Counts items in the main inventory + hotbar whose SkyBlock ID matches. */
    public static int countInInventory(Inventory inventory, String skyblockId) {
        int count = 0;
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (!stack.isEmpty() && skyblockId.equals(getSkyblockId(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Hypixel stores the SkyBlock ID in the item's custom data as "id". */
    public static String getSkyblockId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("id", "");
    }

    private static void chat(MutableComponent message) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal("[Dungeon Refill] ").withStyle(ChatFormatting.GOLD).append(message));
        }
    }
}
