package pl.autotpa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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

    /** Czy wiadomość wygląda jak prośba o teleportację. */
    private static boolean looksLikeTpaRequest(String raw, Config cfg) {
        String low = raw.toLowerCase(Locale.ROOT);

        // 1) Serwer podpowiada komendę akceptującą, np. "Wpisz /tpaccept aby zaakceptować".
        String cmd = cfg.command == null ? "" : cfg.command.trim().toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty() && low.contains("/" + cmd)) return true;

        // 2) Własny tekst wpisany w GUI.
        String extra = cfg.extraPattern == null ? "" : cfg.extraPattern.trim().toLowerCase(Locale.ROOT);
        if (!extra.isEmpty() && low.contains(extra)) return true;

        // 3) Wzorce z configu (zwykły tekst albo regex).
        for (String p : cfg.patterns) {
            if (p == null || p.isBlank()) continue;
            if (low.contains(p.toLowerCase(Locale.ROOT))) return true;
            try {
                if (Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(raw).find()) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static boolean passesWhitelist(String raw, Config cfg) {
        if (cfg.whitelist.isEmpty()) return true;
        for (String name : cfg.whitelist) {
            if (name == null || name.isBlank()) continue;
            Pattern np = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name.trim()) + "(?![A-Za-z0-9_])",
                    Pattern.CASE_INSENSITIVE);
            if (np.matcher(raw).find()) return true;
        }
        return false;
    }

    private static void handleMessage(String raw) {
        Config cfg = Config.get();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (cfg.debug && mc.player != null) {
            mc.player.sendMessage(Text.literal("[AutoTPA debug] " + raw).formatted(Formatting.DARK_GRAY), false);
        }

        if (!cfg.enabled || pendingTicks >= 0 || cooldown > 0) return;
        if (!looksLikeTpaRequest(raw, cfg)) return;
        if (!passesWhitelist(raw, cfg)) return;

        pendingTicks = Math.max(0, cfg.delayTicks);
        cooldown = Math.max(40, cfg.delayTicks + 20);

        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[AutoTPA] ").formatted(Formatting.AQUA)
                    .append(Text.literal(String.format("Wykryto prośbę, akceptuję za %.2f s", cfg.delayTicks / 20.0))
                            .formatted(Formatting.GRAY)), true);
        }
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

    private static void feedback(FabricClientCommandSource src, String text) {
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
                    .then(ClientCommandManager.literal("debug").executes(ctx -> {
                        Config.get().debug = !Config.get().debug;
                        Config.save();
                        feedback(ctx.getSource(), "Tryb debug: " + (Config.get().debug
                                ? "ON (każda wiadomość z serwera pojawi się na czacie na szaro)" : "OFF"));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        Config c = Config.get();
                        feedback(ctx.getSource(), "Stan: " + (c.enabled ? "ON" : "OFF")
                                + ", opóźnienie: " + c.delayTicks + " ticków, komenda: /" + c.command
                                + ", własny tekst: " + (c.extraPattern.isBlank() ? "(brak)" : c.extraPattern)
                                + ", whitelista: " + (c.whitelist.isEmpty() ? "(wszyscy)" : String.join(", ", c.whitelist)));
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("delay")
                            .then(ClientCommandManager.argument("ticks", IntegerArgumentType.integer(0, 1200))
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
                    .then(ClientCommandManager.literal("text")
                            .then(ClientCommandManager.argument("fragment", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        Config.get().extraPattern = StringArgumentType.getString(ctx, "fragment").trim();
                                        Config.save();
                                        feedback(ctx.getSource(), "Własny tekst prośby: " + Config.get().extraPattern);
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
