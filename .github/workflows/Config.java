package pl.autotpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("autotpa.json");
    private static Config instance = new Config();

    /** Czy auto-akceptowanie jest włączone. */
    public boolean enabled = false;
    /** Opóźnienie przed wysłaniem komendy (20 ticków = 1 s). */
    public int delayTicks = 10;
    /** Komenda wysyłana po wykryciu prośby (bez ukośnika). */
    public String command = "tpaccept";
    /** Jeśli lista nie jest pusta, akceptuje tylko prośby od tych graczy. */
    public List<String> whitelist = new ArrayList<>();
    /** Własny fragment tekstu, po którym mod rozpoznaje prośbę (ustawiany w GUI). */
    public String extraPattern = "";
    /** Tryb diagnostyczny: wypisuje na czacie każdą wiadomość z serwera. */
    public boolean debug = false;
    /** Fragmenty tekstu / wyrażenia regularne (bez rozróżniania wielkości liter) rozpoznające prośbę o tpa. */
    public List<String> patterns = new ArrayList<>(List.of(
            "has requested to teleport to you",
            "wants to teleport to you",
            "requested to teleport to you",
            "has requested that you teleport to",
            "tpa request",
            "teleport request",
            "prosi o teleportacj",
            "prośbę o teleportacj",
            "prosbe o teleportacj",
            "chce się do ciebie teleportować",
            "chce sie do ciebie teleportowac",
            "chce się teleportować do ciebie",
            "chce sie teleportowac do ciebie",
            "prośba o teleportację",
            "prosba o teleportacje"
    ));

    public static Config get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) {
                    if (c.whitelist == null) c.whitelist = new ArrayList<>();
                    if (c.patterns == null) c.patterns = new ArrayList<>();
                    if (c.command == null || c.command.isBlank()) c.command = "tpaccept";
                    if (c.extraPattern == null) c.extraPattern = "";
                    instance = c;
                }
            }
            save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
