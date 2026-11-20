package pl.autotpa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.regex.Pattern;

public class AutoTpaMod implements ClientModInitializer {
    private static final String CATEGORY = "category.autotpa";

    private static KeyBinding toggleKey;
    private static KeyBinding guiKey;

    /** Ile ticków zostało do wysłania komendy; -1 = nic nie czeka. */
    private static int pendingTicks = -1;
    /** Cooldown, żeby nie wysłać komendy kilka razy przez wieloliniową wiadomość. */
    private static int cooldown = 0;

    @Override
    public void onInitializeClient() {
        Config.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autotpa.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autotpa.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

        // Nasłuchiwanie wiadomości z serwera (systemowe/pluginowe).
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            handleMessage(message.getString());
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                setEnabled(client, !Config.get().enabled);
            }
            while (guiKey.wasPressed()) {
                client.setScreen(new AutoTpaScreen(null));
            }

            if (cooldown > 0) cooldown--;

            if (pendingTicks >= 0) {
                if (pendingTicks == 0) {
                    pendingTicks = -1;
                    sendAccept(client);
                } else {
                    pendingTicks--;
                }
            }
        });

        registerCommands();
    }

    private static void handleMessage(String raw) {
        Config cfg = Config.get();
        if (!cfg.enabled || pendingTicks >= 0 || cooldown > 0) return;

        String msg = raw.toLowerCase(Locale.ROOT);

        boolean matches = false;
        for (String p : cfg.patterns) {
            try {
                if (Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(raw).find()) {
                    matches = true;
                    break;
                }
            } catch (Exception ignored) {
                if (msg.contains(p.toLowerCase(Locale.ROOT))) {
                    matches = true;
                    break;
                }
            }
        }
        if (!matches) return;

        // Whitelista: jeśli nie jest pusta, wiadomość musi zawierać nick z listy.
        if (!cfg.whitelist.isEmpty()) {
            boolean allowed = false;
            for (String name : cfg.whitelist) {
                if (name.isBlank()) continue;
                Pattern np = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name.trim()) + "(?![A-Za-z0-9_])",
                        Pattern.CASE_INSENSITIVE);
                if (np.matcher(raw).find()) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) return;
        }

        pendingTicks = Math.max(0, cfg.delayTicks);
        cooldown = 40;
    }

    private static void sendAccept(MinecraftClient client) {
        if (client.player == null || client.player.networkHandler == null) return;
        String cmd = Config.get().command;
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        client.player.networkHandler.sendChatCommand(cmd);
        client.player.sendMessage(Text.literal("[AutoTPA] ").formatted(Formatting.AQUA)
                .append(Text.literal("Wysłano /" + cmd).formatted(Formatting.GRAY)), true);
    }

    public static void setEnabled(MinecraftClient client, boolean value) {
        Config.get().enabled = value;
        Config.save();
        pendingTicks = -1;
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[AutoTPA] ").formatted(Formatting.AQUA)
                    .append(Text.literal(value ? "WŁĄCZONY" : "WYŁĄCZONY")
                            .formatted(value ? Formatting.GREEN : Formatting.RED)), true);
        }
    }

    private static void feedback(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, String text) {
        src.sendFeedback(Text.literal("[AutoTPA] ").formatted(Formatting.AQUA)
                .append(Text.literal(text).formatted(Formatting.GRAY)));
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("autotpa")
                    .executes(ctx -> {
                        // Bez argumentów: przełącz on/off.
                        setEnabled(MinecraftClient.getInstance(), !Config.get().enabled);
                        return 1;
                    })
                    .then(ClientCommandManager.literal("on").executes(ctx -> {
                        setEnabled(MinecraftClient.getInstance(), true);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(ctx -> {
                        setEnabled(MinecraftClient.getInstance(), false);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                        setEnabled(MinecraftClient.getInstance(), !Config.get().enabled);
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("gui").executes(ctx -> {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        // send() - ekran czatu zamyka się po komendzie, więc otwieramy GUI w następnym ticku.
                        mc.send(() -> mc.setScreen(new AutoTpaScreen(null)));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        Config c = Config.get();
                        feedback(ctx.getSource(), "Stan: " + (c.enabled ? "ON" : "OFF")
                                + ", opóźnienie: " + c.delayTicks + " ticków, komenda: /" + c.command
                                + ", whitelista: " + (c.whitelist.isEmpty() ? "(wszyscy)" : String.join(", ", c.whitelist)));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("delay")
                            .then(ClientCommandManager.argument("ticks", IntegerArgumentType.integer(0, 200))
                                    .executes(ctx -> {
                                        Config.get().delayTicks = IntegerArgumentType.getInteger(ctx, "ticks");
                                        Config.save();
                                        feedback(ctx.getSource(), "Opóźnienie: " + Config.get().delayTicks + " ticków");
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("command")
                            .then(ClientCommandManager.argument("cmd", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String cmd = StringArgumentType.getString(ctx, "cmd").trim();
                                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                                        Config.get().command = cmd;
                                        Config.save();
                                        feedback(ctx.getSource(), "Komenda akceptująca: /" + cmd);
                                        return 1;
                                    })))
                    .then(ClientCommandManager.literal("whitelist")
                            .then(ClientCommandManager.literal("add")
                                    .then(ClientCommandManager.argument("nick", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String nick = StringArgumentType.getString(ctx, "nick");
                                                if (!Config.get().whitelist.contains(nick)) Config.get().whitelist.add(nick);
                                                Config.save();
                                                feedback(ctx.getSource(), "Dodano do whitelisty: " + nick);
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("remove")
                                    .then(ClientCommandManager.argument("nick", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String nick = StringArgumentType.getString(ctx, "nick");
                                                Config.get().whitelist.removeIf(n -> n.equalsIgnoreCase(nick));
                                                Config.save();
                                                feedback(ctx.getSource(), "Usunięto z whitelisty: " + nick);
                                                return 1;
                                            })))
                            .then(ClientCommandManager.literal("clear").executes(ctx -> {
                                Config.get().whitelist.clear();
                                Config.save();
                                feedback(ctx.getSource(), "Whitelista wyczyszczona (akceptuje wszystkich).");
                                return 1;
                            }))
                            .then(ClientCommandManager.literal("list").executes(ctx -> {
                                feedback(ctx.getSource(), Config.get().whitelist.isEmpty()
                                        ? "Whitelista pusta (akceptuje wszystkich)."
                                        : "Whitelista: " + String.join(", ", Config.get().whitelist));
                                return 1;
                            })))
            );
        });
    }
}
