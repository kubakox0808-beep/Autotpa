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
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 214;
    private static final int PAD = 12;

    private static final int COLOR_ON = 0xFF3DDC84;
    private static final int COLOR_OFF = 0xFFE5534B;
    private static final int COLOR_LABEL = 0xFF9AA4AF;

    private final Screen parent;
    private ButtonWidget toggleButton;
    private TextFieldWidget commandField;
    private TextFieldWidget delayField;
    private TextFieldWidget whitelistField;
    private TextFieldWidget extraField;

    public AutoTpaScreen(Screen parent) {
        super(Text.literal("AutoTPA"));
        this.parent = parent;
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return Math.max(4, (this.height - PANEL_H) / 2);
    }

    private Text toggleText() {
        boolean on = Config.get().enabled;
        return Text.literal("Auto-akceptowanie: ")
                .append(Text.literal(on ? "WŁĄCZONE" : "WYŁĄCZONE").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    protected void init() {
        Config cfg = Config.get();
        int px = panelX();
        int py = panelY();
        int inner = PANEL_W - PAD * 2;

        // Przełącznik ON/OFF
        toggleButton = this.addDrawableChild(ButtonWidget.builder(toggleText(), b -> {
            cfg.enabled = !cfg.enabled;
            Config.save();
            b.setMessage(toggleText());
        }).dimensions(px + PAD, py + 36, inner, 22).build());

        // Komenda akceptująca (z ukośnikiem lub bez)
        commandField = new TextFieldWidget(this.textRenderer, px + PAD, py + 77, 150, 18, Text.literal("Komenda"));
        commandField.setMaxLength(64);
        commandField.setPlaceholder(Text.literal("np. /tpaccept"));
        commandField.setText("/" + cfg.command);
        commandField.setChangedListener(AutoTpaScreen::applyCommand);
        this.addDrawableChild(commandField);

        // Delay w tickach (20 ticków = 1 sekunda)
        delayField = new TextFieldWidget(this.textRenderer, px + PAD + 158, py + 77, inner - 158, 18, Text.literal("Delay"));
        delayField.setMaxLength(4);
        delayField.setTextPredicate(s -> s.matches("\\d{0,4}"));
        delayField.setPlaceholder(Text.literal("ticki"));
        delayField.setText(String.valueOf(cfg.delayTicks));
        delayField.setChangedListener(AutoTpaScreen::applyDelay);
        this.addDrawableChild(delayField);

        // Whitelista
        whitelistField = new TextFieldWidget(this.textRenderer, px + PAD, py + 115, inner, 18, Text.literal("Whitelista"));
        whitelistField.setMaxLength(256);
        whitelistField.setPlaceholder(Text.literal("nicki po przecinku (puste = wszyscy)"));
        whitelistField.setText(String.join(", ", cfg.whitelist));
        this.addDrawableChild(whitelistField);

        // Własny tekst prośby
        extraField = new TextFieldWidget(this.textRenderer, px + PAD, py + 153, inner, 18, Text.literal("Tekst prośby"));
        extraField.setMaxLength(128);
        extraField.setPlaceholder(Text.literal("opcjonalnie, np. prosi o teleportację"));
        extraField.setText(cfg.extraPattern == null ? "" : cfg.extraPattern);
        this.addDrawableChild(extraField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz i zamknij"), b -> this.close())
                .dimensions(px + PAD, py + 182, inner, 20).build());
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

    /** Panel rysujemy w tle, żeby widgety były nad nim. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        int px = panelX();
        int py = panelY();
        int accent = Config.get().enabled ? COLOR_ON : COLOR_OFF;

        // obramowanie + korpus
        context.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, 0xFF2A323B);
        context.fill(px, py, px + PANEL_W, py + PANEL_H, 0xF0111418);
        // nagłówek + linia akcentu w kolorze stanu
        context.fill(px, py, px + PANEL_W, py + 26, 0xFF181D23);
        context.fill(px, py + 26, px + PANEL_W, py + 27, accent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int px = panelX();
        int py = panelY();
        boolean on = Config.get().enabled;
        int accent = on ? COLOR_ON : COLOR_OFF;

        // nagłówek
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("AutoTPA").formatted(Formatting.BOLD), px + PAD, py + 9, 0xFFFFFFFF);
        Text status = Text.literal(on ? "ON" : "OFF").formatted(Formatting.BOLD);
        int sw = this.textRenderer.getWidth(status);
        context.drawTextWithShadow(this.textRenderer, status, px + PANEL_W - PAD - sw, py + 9, accent);

        // etykiety nad polami
        context.drawTextWithShadow(this.textRenderer, Text.literal("Komenda akceptująca"),
                px + PAD, py + 66, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(String.format("Delay: %.2f s", Config.get().delayTicks / 20.0)),
                px + PAD + 158, py + 66, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Akceptuj tylko od (whitelista)"),
                px + PAD, py + 104, COLOR_LABEL);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Własny tekst prośby (opcjonalnie)"),
                px + PAD, py + 142, COLOR_LABEL);
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
        Config.get().extraPattern = extraField.getText().trim();
        Config.save();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
