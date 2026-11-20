package pl.autotpa;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class AutoTpaScreen extends Screen {
    private final Screen parent;
    private ButtonWidget toggleButton;
    private TextFieldWidget commandField;
    private TextFieldWidget delayField;
    private TextFieldWidget whitelistField;

    public AutoTpaScreen(Screen parent) {
        super(Text.literal("AutoTPA"));
        this.parent = parent;
    }

    private Text toggleText() {
        boolean on = Config.get().enabled;
        return Text.literal("Auto-akceptowanie: ")
                .append(Text.literal(on ? "WŁĄCZONE" : "WYŁĄCZONE").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    protected void init() {
        Config cfg = Config.get();
        int cx = this.width / 2;
        int y = this.height / 4 - 10;

        // ON / OFF
        toggleButton = this.addDrawableChild(ButtonWidget.builder(toggleText(), b -> {
            cfg.enabled = !cfg.enabled;
            Config.save();
            b.setMessage(toggleText());
        }).dimensions(cx - 100, y, 200, 20).build());

        // Komenda akceptująca (wpisujesz sam, z ukośnikiem lub bez)
        commandField = new TextFieldWidget(this.textRenderer, cx - 100, y + 42, 200, 20, Text.literal("Komenda"));
        commandField.setMaxLength(64);
        commandField.setPlaceholder(Text.literal("np. tpaccept"));
        commandField.setText("/" + cfg.command);
        commandField.setChangedListener(s -> applyCommand(s));
        this.addDrawableChild(commandField);

        // Delay w tickach (20 ticków = 1 sekunda)
        delayField = new TextFieldWidget(this.textRenderer, cx - 100, y + 84, 200, 20, Text.literal("Delay"));
        delayField.setMaxLength(4);
        delayField.setTextPredicate(s -> s.matches("\\d{0,4}"));
        delayField.setPlaceholder(Text.literal("ticki, 20 = 1 s"));
        delayField.setText(String.valueOf(cfg.delayTicks));
        delayField.setChangedListener(s -> applyDelay(s));
        this.addDrawableChild(delayField);

        // Whitelista
        whitelistField = new TextFieldWidget(this.textRenderer, cx - 100, y + 126, 200, 20, Text.literal("Whitelista"));
        whitelistField.setMaxLength(256);
        whitelistField.setPlaceholder(Text.literal("nicki po przecinku (puste = wszyscy)"));
        whitelistField.setText(String.join(", ", cfg.whitelist));
        this.addDrawableChild(whitelistField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> this.close())
                .dimensions(cx - 100, y + 158, 200, 20).build());
    }

    private static void applyCommand(String text) {
        String cmd = text.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isBlank()) return;
        Config.get().command = cmd;
    }

    private static void applyDelay(String text) {
        if (text.isBlank()) return;
        try {
            Config.get().delayTicks = Math.min(1200, Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int y = this.height / 4 - 10;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, y - 16, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Komenda akceptująca:"), cx, y + 30, 0xAAAAAA);

        double seconds = Config.get().delayTicks / 20.0;
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Delay w tickach (" + String.format("%.2f", seconds) + " s):"), cx, y + 72, 0xAAAAAA);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Akceptuj tylko od (whitelista):"), cx, y + 114, 0xAAAAAA);
    }

    /** Zapis wszystkich pól przy zamknięciu ekranu. */
    @Override
    public void removed() {
        applyCommand(commandField.getText());
        applyDelay(delayField.getText());

        List<String> names = new ArrayList<>();
        for (String part : whitelistField.getText().split("[,;\\s]+")) {
            if (!part.isBlank()) names.add(part.trim());
        }
        Config.get().whitelist = names;
        Config.save();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
