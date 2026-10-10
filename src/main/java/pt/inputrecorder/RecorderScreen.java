package pt.inputrecorder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class RecorderScreen extends GuiScreen {
    private static final int BG = 0xFF14151E, CARD = 0xFF1C1E2A, ACCENT = 0xFF9B7AFF;
    private static final int TEXT = 0xFFEEEFFA, MUTED = 0xFF85899F;
    private final RecorderController controller;
    private List<String> names = new ArrayList<String>(), filtered = new ArrayList<String>();
    private String selected, status = "Escolha uma gravacao para comecar.";
    private boolean statusError;
    private Recording preview;
    private ClickGuiRenderer ui;
    private GuiTextField search, recordName;
    private int px, py, pw, ph, split, listTop, listBottom, footer, rowHeight, bodyTop, scroll;
    private boolean positioned, compact, dragging, draggingScroll;
    private int dragX, dragY;
    private String lastClickName;
    private long lastClickTime, lastDraw;
    private final float[] hover = new float[3];

    public RecorderScreen(RecorderController controller) { this.controller = controller; }

    @Override public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (ui == null) ui = new ClickGuiRenderer();
        if (search == null) {
            search = new GuiTextField(0, fontRendererObj, 0, 0, 180, 20);
            recordName = new GuiTextField(1, fontRendererObj, 0, 0, 180, 20);
            search.setMaxStringLength(64); recordName.setMaxStringLength(64);
        }
        buttonList.clear();
        pw = Math.min(620, width - 24); ph = Math.min(370, height - 24); compact = ph < 300;
        if (!positioned) { px = (width - pw) / 2; py = (height - ph) / 2; positioned = true; }
        clampPanel(); layout(); refresh();
    }

    private void layout() {
        split = px + pw / 2; bodyTop = py + (compact ? 48 : 64);
        footer = py + ph - (compact ? 44 : 58);
        listTop = bodyTop + 44; listBottom = footer - 26;
        rowHeight = compact ? 28 : 36; clampScroll();
    }

    private void clampPanel() {
        px = Math.max(6, Math.min(px, width - pw - 6)); py = Math.max(6, Math.min(py, height - ph - 6));
    }

    private void refresh() {
        try {
            names = controller.list();
            if (selected == null || !names.contains(selected)) {
                Recording loaded = controller.getLoadedRecording();
                selected = loaded != null && names.contains(loaded.name) ? loaded.name : (names.isEmpty() ? null : names.get(0));
            }
            if (selected != null) select(selected); else { preview = null; setStatus("Crie sua primeira gravacao abaixo.", false); }
            filter();
            int chosen = filtered.indexOf(selected);
            if (chosen >= 0) {
                int top = chosen * rowHeight;
                if (top < scroll) scroll = top;
                if (top + rowHeight > scroll + listBottom - listTop) scroll = top + rowHeight - (listBottom - listTop);
                clampScroll();
            }
        } catch (IOException exception) { names.clear(); filtered.clear(); selected = null; preview = null; setStatus(exception.getMessage(), true); }
    }
    private void filter() {
        filtered = new ArrayList<String>(); String query = search.getText().toLowerCase(Locale.ROOT);
        for (String name : names) if (name.toLowerCase(Locale.ROOT).contains(query)) filtered.add(name);
        clampScroll();
    }
    private int maxScroll() { return Math.max(0, filtered.size() * rowHeight - Math.max(0, listBottom - listTop)); }
    private void clampScroll() { scroll = Math.max(0, Math.min(scroll, maxScroll())); }
    private void select(String name) {
        selected = name;
        try { preview = controller.inspect(name); setStatus("Duplo clique para carregar. F8 interrompe a reproducao.", false); }
        catch (IOException | IllegalArgumentException exception) { preview = null; setStatus("Arquivo invalido: " + exception.getMessage(), true); }
    }
    private void setStatus(String value, boolean error) { status = value == null ? "Falha ao acessar a gravacao." : value; statusError = error; }
    private boolean loadSelected() {
        if (selected == null || preview == null) return false;
        try { controller.load(selected); preview = controller.getLoadedRecording(); setStatus("Carregada: " + selected, false); return true; }
        catch (IOException | IllegalArgumentException exception) { setStatus(exception.getMessage(), true); return false; }
    }
    private void play() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.thePlayer.isDead) { setStatus("Entre em um mundo para reproduzir.", true); return; }
        if (!loadSelected()) return;
        if (preview.dimension != mc.thePlayer.dimension) { setStatus("Entre na mesma dimensao da gravacao.", true); return; }
        closeMenu(); controller.requestPlay(null);
    }
    private void record() {
        String name = recordName.getText().trim();
        try { controller.validateNewName(name.isEmpty() ? null : name); closeMenu(); controller.requestRecord(name.isEmpty() ? null : name); }
        catch (IOException | IllegalArgumentException exception) { setStatus(exception.getMessage(), true); recordName.setFocused(true); }
    }
    private void closeMenu() { mc.displayGuiScreen(null); mc.setIngameFocus(); }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean wasCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        
        GlStateManager.disableCull();
        try {
            drawContent(mouseX, mouseY, partialTicks);
        } finally {
            if (wasCullEnabled) GlStateManager.enableCull(); else GlStateManager.disableCull();
            GlStateManager.color(1, 1, 1, 1); GlStateManager.enableTexture2D(); GlStateManager.enableAlpha(); GlStateManager.enableDepth();
        }
    }
    private void drawContent(int mouseX, int mouseY, float partialTicks) {
        if (dragging) { px = mouseX - dragX; py = mouseY - dragY; clampPanel(); layout(); }
        if (draggingScroll && maxScroll() > 0) scroll = (int)(Math.max(0, Math.min(1f, (mouseY - listTop) / (float)(listBottom - listTop))) * maxScroll());
        long now = System.nanoTime(); float delta = lastDraw == 0 ? 1 : Math.min(1, (now - lastDraw) / 100000000f); lastDraw = now;
        GlStateManager.disableDepth(); GlStateManager.disableLighting(); GlStateManager.disableAlpha();
        drawRect(0, 0, width, height, 0xA6080910);
        for (int layer = 10; layer > 0; layer--) ClickGuiRenderer.rounded(px - layer, py - layer + 4, pw + layer * 2, ph + layer * 2, 12 + layer, 0x06000000);
        ClickGuiRenderer.rounded(px, py, pw, ph, 12, BG);
        ClickGuiRenderer.rounded(px, py, pw, compact ? 36 : 48, 12, 0xFF1B1C28);
        drawRect(px, py + 24, px + pw, py + (compact ? 36 : 48), 0xFF1B1C28);
        ClickGuiRenderer.rounded(px + 16, py + 14, 18, 18, 6, 0xFF302744);
        ClickGuiRenderer.rounded(px + 22, py + 20, 6, 6, 3, ACCENT);
        ui.title.draw("Input Recorder", px + 44, py + 8, TEXT);
        if (!compact) ui.small.draw("CAPTURE  /  PLAYBACK", px + 45, py + 29, MUTED);
        ui.small.draw("F10", px + pw - 65, py + 17, MUTED);
        boolean closeHover = inside(mouseX, mouseY, px + pw - 31, py + 9, 22, 22);
        ClickGuiRenderer.rounded(px + pw - 31, py + 9, 22, 22, 6, closeHover ? 0xFF3A2939 : 0xFF252634);
        ClickGuiRenderer.line(px + pw - 24, py + 16, px + pw - 16, py + 24, closeHover ? 0xFFFF8FAD : MUTED);
        ClickGuiRenderer.line(px + pw - 16, py + 16, px + pw - 24, py + 24, closeHover ? 0xFFFF8FAD : MUTED);
        drawRect(split, py + (compact ? 42 : 54), split + 1, footer - 10, 0xFF252735);
        ui.small.draw("GRAVACOES", px + 16, bodyTop, MUTED);
        ui.small.draw(String.valueOf(names.size()), split - 44, bodyTop, ACCENT);
        boolean refreshHover = inside(mouseX, mouseY, split - 31, bodyTop - 4, 18, 16);
        ClickGuiRenderer.refreshIcon(split - 22, bodyTop + 4, refreshHover ? TEXT : MUTED);
        input(search, "Buscar gravacao...", px + 16, bodyTop + 15, split - px - 32, 23);
        drawRows(mouseX, mouseY); drawDetails(mouseX, mouseY, delta);
        ui.small.draw(ui.small.trim(status, pw - 32), px + 16, footer - 18, statusError ? 0xFFFF829B : MUTED);
        drawRect(px + 12, footer, px + pw - 12, footer + 1, 0xFF292B3A);
        ui.small.draw("NOVA GRAVACAO", px + 16, footer + 5, MUTED);
        int iy = footer + (compact ? 16 : 21), ih = compact ? 23 : 26, bw = compact ? 88 : 114;
        input(recordName, "Nome opcional...", px + 16, iy, pw - bw - 40, ih);
        button("Gravar", px + pw - bw - 16, iy, bw, ih, mouseX, mouseY, true, 2, delta, true);
    }
    private void drawRows(int mx, int my) {
        int x = px + 16, w = split - px - 34, viewHeight = listBottom - listTop;
        scissor(x - 1, listTop, w + 6, Math.max(0, viewHeight));
        try {
            Recording loaded = controller.getLoadedRecording();
            for (int i = scroll / rowHeight; i < filtered.size(); i++) {
                int y = listTop + i * rowHeight - scroll; if (y >= listBottom) break;
                String name = filtered.get(i); boolean chosen = name.equals(selected), active = loaded != null && name.equals(loaded.name);
                boolean hot = inside(mx, my, x, y, w, rowHeight - 4) && my >= listTop && my < listBottom;
                ClickGuiRenderer.rounded(x, y, w, rowHeight - 4, 6, chosen ? 0xFF30283F : hot ? 0xFF242635 : CARD);
                if (chosen) ClickGuiRenderer.rounded(x, y + 5, 3, rowHeight - 14, 1.5f, ACCENT);
                ui.regular.draw(ui.regular.trim(name, w - 22), x + 10, y + (compact ? 5 : 3), chosen ? TEXT : 0xFFC7CADB);
                if (!compact) ui.small.draw(active ? "CARREGADA" : "GRAVACAO SALVA", x + 10, y + 17, active ? ACCENT : MUTED);
            }
            if (filtered.isEmpty()) ui.small.centered(names.isEmpty() ? "Nenhuma gravacao salva" : "Nenhum resultado", x + w / 2f, listTop + 12, MUTED);
        } finally { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
        if (maxScroll() > 0) {
            float thumb = Math.max(16, viewHeight * viewHeight / (float)(filtered.size() * rowHeight));
            float top = listTop + scroll / (float)maxScroll() * (viewHeight - thumb);
            ClickGuiRenderer.rounded(split - 12, listTop, 3, viewHeight, 1.5f, 0xFF282A3B);
            ClickGuiRenderer.rounded(split - 12, top, 3, thumb, 1.5f, ACCENT);
        }
    }
    private int actionY() { return bodyTop + (compact ? 54 : 90); }
    private int loopY() { return bodyTop + (compact ? 86 : 132); }
    private void drawDetails(int mx, int my, float delta) {
        int x = split + 16, w = px + pw - 16 - x;
        ui.small.draw("SELECIONADA", x, bodyTop, MUTED);
        ui.regular.draw(ui.regular.trim(selected == null ? "Escolha uma gravacao" : selected, w), x, bodyTop + 14, TEXT);
        if (preview != null) {
            String duration = String.format(Locale.ROOT, "%.1f s", preview.frames.size() / 20.0);
            if (compact) ui.small.draw(ui.small.trim(duration + "  /  " + dimension(preview.dimension), w), x, bodyTop + 31, ACCENT);
            else {
                ClickGuiRenderer.rounded(x, bodyTop + 38, w, 42, 7, CARD);
                ui.small.draw("DURACAO", x + 10, bodyTop + 43, MUTED); ui.regular.draw(duration, x + 10, bodyTop + 56, TEXT);
                ui.small.draw("MUNDO", x + w / 2, bodyTop + 43, MUTED);
                ui.regular.draw(ui.regular.trim(dimension(preview.dimension), w / 2 - 10), x + w / 2, bodyTop + 56, TEXT);
            }
        } else ui.small.draw(ui.small.trim(selected == null ? "Selecione na lista ao lado." : "Arquivo indisponivel", w), x, bodyTop + 32, MUTED);
        int bh = compact ? 24 : 30, half = (w - 6) / 2;
        button("Carregar", x, actionY(), half, bh, mx, my, preview != null, 0, delta, false);
        button("Play", x + half + 6, actionY(), w - half - 6, bh, mx, my, preview != null, 1, delta, true);
        boolean enabled = controller.isLoopEnabled(), hot = inside(mx, my, x, loopY(), w, compact ? 25 : 42);
        ClickGuiRenderer.rounded(x, loopY(), w, compact ? 25 : 42, 7, hot ? 0xFF262537 : CARD);
        ui.regular.draw("Loop", x + 10, loopY() + 5, TEXT);
        if (!compact) ui.small.draw("Repetir ate parar", x + 10, loopY() + 22, MUTED);
        int tx = x + w - 43, ty = loopY() + (compact ? 6 : 12);
        ClickGuiRenderer.rounded(tx, ty, 32, 16, 8, enabled ? ACCENT : 0xFF3D4054);
        ClickGuiRenderer.rounded(tx + (enabled ? 18 : 2), ty + 2, 12, 12, 6, 0xFFF5F2FF);
        if (!compact && preview != null) {
            int y = loopY() + 60;
            ui.small.draw("PONTO INICIAL", x, y, MUTED);
            ui.small.draw(ui.small.trim(String.format(Locale.ROOT, "X %.1f  Y %.1f  Z %.1f", preview.startX, preview.startY, preview.startZ), w), x, y + 15, 0xFFB9BDD0);
            Recording loaded = controller.getLoadedRecording();
            if (y + 48 < footer - 24 && loaded != null && loaded.name.equals(selected)) ui.small.draw("Pronta para F7", x, y + 36, ACCENT);
        }
    }
    private void button(String label, int x, int y, int w, int h, int mx, int my, boolean enabled, int id, float delta, boolean primary) {
        boolean hot = enabled && inside(mx, my, x, y, w, h); hover[id] += ((hot ? 1 : 0) - hover[id]) * delta;
        ClickGuiRenderer.rounded(x, y, w, h, 6, enabled ? mix(primary ? 0xFF8A66EB : 0xFF2A2D40, primary ? 0xFFAB88FF : 0xFF3D4057, hover[id]) : 0xFF222430);
        ui.regular.centered(label, x + w / 2f, y + (h - 14) / 2f, enabled ? 0xFFFFFFFF : 0xFF686D84);
    }
    private static int mix(int a, int b, float t) {
        return 0xFF000000 | ((int)((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t) << 16)
                | ((int)((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t) << 8) | (int)((a & 255) + ((b & 255) - (a & 255)) * t);
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static String dimension(int d) { return d == 0 ? "Overworld" : d == -1 ? "Nether" : d == 1 ? "End" : "Dim " + d; }
    private int textStart(GuiTextField field, int space) {
        int start = 0, cursor = field.getCursorPosition();
        while (start < cursor && ui.regular.width(field.getText().substring(start, cursor)) > space) start++; return start;
    }
    private void input(GuiTextField field, String placeholder, int x, int y, int w, int h) {
        field.xPosition = x; field.yPosition = y; field.width = w; field.height = h;
        ClickGuiRenderer.rounded(x, y, w, h, 6, field.isFocused() ? 0xFF6B519F : 0xFF2D3040);
        ClickGuiRenderer.rounded(x + 1, y + 1, w - 2, h - 2, 5, 0xFF191B26);
        String value = field.getText(); float ty = y + (h - 14) / 2f;
        if (value.isEmpty() && !field.isFocused()) { ui.regular.draw(placeholder, x + 9, ty, 0xFF686D84); return; }
        int start = textStart(field, w - 22), end = start;
        while (end < value.length() && ui.regular.width(value.substring(start, end + 1)) <= w - 18) end++;
        int a = Math.max(start, Math.min(end, Math.min(field.getCursorPosition(), field.getSelectionEnd())));
        int b = Math.max(start, Math.min(end, Math.max(field.getCursorPosition(), field.getSelectionEnd())));
        if (field.isFocused() && b > a) ClickGuiRenderer.rounded(x + 9 + ui.regular.width(value.substring(start, a)), ty + 1, ui.regular.width(value.substring(a, b)), 12, 0, 0xFF493E6A);
        ui.regular.draw(value.substring(start, end), x + 9, ty, TEXT);
        if (field.isFocused() && System.currentTimeMillis() / 500 % 2 == 0) ClickGuiRenderer.rounded(x + 9 + ui.regular.width(value.substring(start, field.getCursorPosition())), ty + 2, 1, 11, 0, ACCENT);
    }
    private void focusInput(GuiTextField field, int mx, int my) {
        boolean hit = inside(mx, my, field.xPosition, field.yPosition, field.width, field.height); field.setFocused(hit);
        if (!hit) return;
        int start = textStart(field, field.width - 22), cursor = start; String value = field.getText();
        float target = Math.max(0, mx - field.xPosition - 9);
        while (cursor < value.length() && ui.regular.width(value.substring(start, cursor + 1)) < target) cursor++;
        field.setCursorPosition(cursor);
    }
    private void scissor(int x, int y, int w, int h) {
        int factor = new ScaledResolution(mc).getScaleFactor(); GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * factor, mc.displayHeight - (y + h) * factor, w * factor, h * factor);
    }
    @Override protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (button != 0) return;
        focusInput(search, mx, my); focusInput(recordName, mx, my);
        if (inside(mx, my, px + pw - 31, py + 9, 22, 22)) { closeMenu(); return; }
        if (inside(mx, my, px, py, pw, compact ? 36 : 48)) { dragging = true; dragX = mx - px; dragY = my - py; return; }
        if (inside(mx, my, split - 31, bodyTop - 4, 18, 16)) { refresh(); return; }
        if (inside(mx, my, split - 15, listTop, 10, listBottom - listTop) && maxScroll() > 0) { draggingScroll = true; return; }
        if (inside(mx, my, px + 16, listTop, split - px - 34, listBottom - listTop)) {
            int index = (my - listTop + scroll) / rowHeight;
            if (index < filtered.size()) {
                String name = filtered.get(index); long now = System.currentTimeMillis();
                boolean twice = name.equals(lastClickName) && now - lastClickTime < 350;
                select(name); lastClickName = name; lastClickTime = now; if (twice) loadSelected();
            }
            return;
        }
        int x = split + 16, w = px + pw - 16 - x, half = (w - 6) / 2, bh = compact ? 24 : 30;
        if (inside(mx, my, x, actionY(), half, bh)) loadSelected();
        else if (inside(mx, my, x + half + 6, actionY(), w - half - 6, bh)) play();
        else if (inside(mx, my, x, loopY(), w, compact ? 25 : 42)) controller.toggleLoop();
        int bw = compact ? 88 : 114;
        if (inside(mx, my, px + pw - bw - 16, footer + (compact ? 16 : 21), bw, compact ? 23 : 26)) record();
    }
    @Override protected void mouseReleased(int mx, int my, int state) { dragging = draggingScroll = false; }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int wheel = Mouse.getEventDWheel();
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && inside(mx, my, px, listTop, split - px, listBottom - listTop)) { scroll += wheel > 0 ? -rowHeight : rowHeight; clampScroll(); }
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { closeMenu(); return; }
        if (keyCode == Keyboard.KEY_TAB) { boolean was = search.isFocused(); search.setFocused(!was); recordName.setFocused(was); return; }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { if (recordName.isFocused()) record(); else loadSelected(); return; }
        if (search.isFocused()) { if (search.textboxKeyTyped(typedChar, keyCode)) { scroll = 0; filter(); } }
        else if (recordName.isFocused()) recordName.textboxKeyTyped(typedChar, keyCode);
    }
    @Override public void onGuiClosed() { dragging = draggingScroll = false; Keyboard.enableRepeatEvents(false); if (ui != null) { ui.close(); ui = null; } }
    @Override public boolean doesGuiPauseGame() { return false; }
}
