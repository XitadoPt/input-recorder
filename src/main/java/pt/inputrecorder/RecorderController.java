package pt.inputrecorder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MouseHelper;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

public final class RecorderController {
    static final String[] ACTIONS = {"forward", "back", "left", "right", "jump", "sneak",
            "sprint", "attack", "use", "pick", "drop"};
    private enum Mode { IDLE, RECORDING, PLAYING }
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Logger logger = LogManager.getLogger("InputRecorder");
    private final KeyBinding recordKey = new KeyBinding("key.inputrecorder.record", Keyboard.KEY_F6, "key.categories.inputrecorder");
    private final KeyBinding playKey = new KeyBinding("key.inputrecorder.play", Keyboard.KEY_F7, "key.categories.inputrecorder");
    private final KeyBinding stopKey = new KeyBinding("key.inputrecorder.stop", Keyboard.KEY_F8, "key.categories.inputrecorder");
    private final KeyBinding loopKey = new KeyBinding("key.inputrecorder.loop", Keyboard.KEY_F9, "key.categories.inputrecorder");
    private final KeyBinding menuKey = new KeyBinding("key.inputrecorder.menu", Keyboard.KEY_F10, "key.categories.inputrecorder");
    private final KeyBinding[] bindings = {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack,
            mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindRight, mc.gameSettings.keyBindJump,
            mc.gameSettings.keyBindSneak, mc.gameSettings.keyBindSprint, mc.gameSettings.keyBindAttack,
            mc.gameSettings.keyBindUseItem, mc.gameSettings.keyBindPickBlock, mc.gameSettings.keyBindDrop};
    private final KeyBinding[] blocked = {mc.gameSettings.keyBindInventory, mc.gameSettings.keyBindChat,
            mc.gameSettings.keyBindCommand, mc.gameSettings.keyBindSmoothCamera};
    private final Field pressTime = ReflectionHelper.findField(KeyBinding.class, "pressTime", "field_151474_i");
    private final RecordingStore store;
    private final KeyboardReplay keyboard = new KeyboardReplay();
    private KeyboardReplay.Snapshot capturedKeyboard;
    private final int[] pendingPresses = new int[ACTIONS.length];
    private Mode mode = Mode.IDLE;
    private Recording latest, recording;
    private Recording.Frame captured, playingFrame;
    private Object sessionWorld, sessionPlayer;
    private PlaybackCursor playback;
    private boolean loopEnabled;
    private MouseHelper previousMouse, playbackMouse;
    private boolean previousSmoothCamera;
    private boolean previousPauseOnLostFocus;
    private int previousView;
    private Runnable pending;

    public RecorderController() {
        ClientRegistry.registerKeyBinding(recordKey);
        ClientRegistry.registerKeyBinding(playKey);
        ClientRegistry.registerKeyBinding(stopKey);
        ClientRegistry.registerKeyBinding(loopKey);
        ClientRegistry.registerKeyBinding(menuKey);
        Path directory = mc.mcDataDir.toPath().resolve("config/inputrecorder/recordings");
        store = new RecordingStore(directory, ACTIONS);
        try {
            for (String name : store.list()) {
                try { latest = store.load(name); break; }
                catch (IOException exception) { logger.warn("Skipping invalid recording {}", name, exception); }
            }
        } catch (IOException exception) { logger.warn("Cannot list recordings", exception); }
    }

    public void message(String text) {
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("\u00a7b[Recorder]\u00a7r " + text));
        else logger.info(text);
    }

    public void requestRecord(final String name) { pending = () -> startRecording(name); }
    public void requestPlay(final String name) { pending = () -> startPlayback(name); }
    public void requestMenu() { pending = () -> openMenu(); }

    private void openMenu() {
        if (mc.theWorld == null || mc.thePlayer == null || mc.thePlayer.isDead) {
            message("Entre no mundo para abrir o menu de gravacoes.");
            return;
        }
        stop("Menu de gravacoes aberto.");
        mc.displayGuiScreen(new RecorderScreen(this));
    }

    public boolean isLoopEnabled() { return loopEnabled; }
    Recording getLoadedRecording() { return latest; }
    Recording inspect(String name) throws IOException { return store.load(name); }

    void validateNewName(String name) throws IOException {
        if (name != null && store.exists(name)) throw new IOException("Esse nome ja existe. Escolha outro.");
    }

    public void setLoop(boolean enabled) {
        loopEnabled = enabled;
        message("Loop " + (enabled ? "ativado. F7 reproduz ate voce parar." : "desativado. A reproducao termina no fim do ciclo atual."));
    }

    public void toggleLoop() { setLoop(!loopEnabled); }
    public void loopStatus() { message("Loop: " + (loopEnabled ? "ativado" : "desativado") + ". F9 para alternar."); }
    public List<String> list() throws IOException { return store.list(); }
    public void load(String name) throws IOException { latest = store.load(name); message("Carregada: " + name); }

    private boolean worldReady() {
        return mc.theWorld != null && mc.thePlayer != null && !mc.thePlayer.isDead
                && mc.currentScreen == null && !mc.isGamePaused();
    }

    private boolean ready() {
        return worldReady() && mc.inGameHasFocus && Display.isActive();
    }

    private void allowBackgroundSession() {
        previousPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
    }

    private boolean sessionValid() {
        return worldReady() && sessionWorld == mc.theWorld && sessionPlayer == mc.thePlayer
                && recording.dimension == mc.thePlayer.dimension;
    }

    private void startRecording(String requestedName) {
        if (!ready()) { message("Entre no mundo e feche os menus para gravar."); return; }
        if (mode != Mode.IDLE) stop("Interrompido.");
        String name = requestedName;
        if (name == null) {
            String base = "recording-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
            name = base;
            int suffix = 2;
            while (store.exists(name)) name = base + "-" + suffix++;
        }

        try {
            if (store.exists(name)) { message("Esse nome ja existe. Escolha outro."); return; }
        } catch (IllegalArgumentException exception) { message(exception.getMessage()); return; }
        try { keyboard.capture(this::isRecorderKey); }
        catch (IllegalStateException exception) {
            logger.error("Cannot capture full keyboard", exception);
            message(exception.getMessage());
            return;
        }
        recording = new Recording();
        recording.name = name;
        recording.actions = ACTIONS.clone();
        recording.dimension = mc.thePlayer.dimension;
        recording.startX = mc.thePlayer.posX;
        recording.startY = mc.thePlayer.posY;
        recording.startZ = mc.thePlayer.posZ;
        sessionWorld = mc.theWorld;
        sessionPlayer = mc.thePlayer;
        allowBackgroundSession();
        mode = Mode.RECORDING;
        message("Gravando " + name + ". F6 ou F8 para salvar e parar.");
    }

    private void startPlayback(String name) {
        if (!ready()) { message("Entre no mundo e feche os menus para reproduzir."); return; }
        if (mode != Mode.IDLE) stop("Interrompido.");

        if (name != null) {
            try { latest = store.load(name); }
            catch (IOException | IllegalArgumentException exception) { message(exception.getMessage()); return; }
        }
        if (latest == null) { message("Nenhuma gravacao. Use F6 primeiro."); return; }

        if (latest.dimension != mc.thePlayer.dimension) {
            message("Entre na mesma dimensao da gravacao para reproduzir."); return;
        }
        if (latest.hasKeyboard()) {
            try { keyboard.start(this::isRecorderKey); }
            catch (IllegalStateException exception) {
                logger.error("Cannot replay full keyboard", exception);
                message(exception.getMessage());
                return;
            }
            KeyBinding.unPressAllKeys();
        }
        recording = latest;
        sessionWorld = mc.theWorld;
        sessionPlayer = mc.thePlayer;
        playback = new PlaybackCursor(recording.frames.size());
        playingFrame = null;
        previousMouse = mc.mouseHelper;
        playbackMouse = new PlaybackMouseHelper(previousMouse);
        mc.mouseHelper = playbackMouse;
        previousSmoothCamera = mc.gameSettings.smoothCamera;
        previousView = mc.gameSettings.thirdPersonView;
        mc.gameSettings.smoothCamera = false;
        releaseBindings();
        allowBackgroundSession();
        mode = Mode.PLAYING;
        message("Reproduzindo " + recording.name + (loopEnabled ? " em loop" : "") + ". F8 ou Esc para parar; F9 alterna loop.");
        if (mc.thePlayer.getDistanceSq(recording.startX, recording.startY, recording.startZ) > 1.0) {
            message("Para repetir o percurso, comece em " + String.format(Locale.ROOT,
                    "X %.2f, Y %.2f, Z %.2f.", recording.startX, recording.startY, recording.startZ));
        }
    }

    public void stop(String reason) {
        pending = null;
        Mode previousMode = mode;
        mode = Mode.IDLE;
        if (previousMode != Mode.IDLE) mc.gameSettings.pauseOnLostFocus = previousPauseOnLostFocus;

        if (previousMode == Mode.PLAYING) {
            try { keyboard.stop(); }
            catch (IllegalStateException exception) { logger.error("Cannot restore keyboard", exception); }
            if (recording.hasKeyboard()) KeyBinding.unPressAllKeys();
            releaseBindings();
            if (mc.mouseHelper == playbackMouse) mc.mouseHelper = previousMouse;
            mc.gameSettings.smoothCamera = previousSmoothCamera;
            setView(previousView);
            playbackMouse = previousMouse = null;
        }

        if (previousMode == Mode.RECORDING && !recording.frames.isEmpty()) {
            latest = recording;
            try {
                store.save(recording);
                message("Salva: " + recording.name + " (" + seconds(recording.frames.size()) + " s). " + reason);
            } catch (IOException | IllegalArgumentException exception) {
                logger.error("Could not save recording", exception);
                message("A gravacao ficou na memoria, mas falhou ao salvar: " + exception.getMessage());
            }
        } else if (previousMode != Mode.IDLE) message(reason);
        recording = null;
        playback = null;
        playingFrame = captured = null;
        capturedKeyboard = null;
        sessionWorld = sessionPlayer = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Arrays.fill(pendingPresses, 0);
            captured = null;
            capturedKeyboard = null;
            if (mode != Mode.IDLE && !sessionValid()) { stop("Parado: menu ou mundo mudou."); return; }
            if (mode != Mode.IDLE) mc.gameSettings.pauseOnLostFocus = false;

            if (mode == Mode.RECORDING) {
                try { capturedKeyboard = keyboard.capture(this::isRecorderKey); }
                catch (IllegalStateException exception) {
                    logger.error("Keyboard capture failed", exception);
                    stop("Falha ao gravar o teclado: " + exception.getMessage());
                }
            }
            
            if (mode == Mode.PLAYING) {
                if (recording.hasKeyboard() && keyboard.hasControlPress(
                        key -> key == Keyboard.KEY_ESCAPE || key == stopKey.getKeyCode())) {
                    stop("Parado.");
                    return;
                }
                if (!playback.prepare(loopEnabled)) { stop("Reproducao concluida."); return; }
                playingFrame = recording.frames.get(playback.frameIndex());
                if (recording.hasKeyboard()) {
                    try { keyboard.play(playingFrame); }
                    catch (IllegalStateException exception) {
                        logger.error("Keyboard playback failed", exception);
                        stop("Falha ao reproduzir o teclado: " + exception.getMessage());
                        return;
                    }
                }
                applyPlaybackInputs();
                if (!recording.hasKeyboard() || bindings[10].getKeyCode() < 0) {
                    for (int i = 0; i < playingFrame.presses[10]; i++) {
                        if (!mc.thePlayer.isSpectator()) mc.thePlayer.dropOneItem(playingFrame.dropStack);
                    }
                }
                setLook(playingFrame.yaw, playingFrame.pitch);
            }
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mode != Mode.IDLE && !sessionValid()) stop("Parado: menu ou mundo mudou.");

        if (mode == Mode.RECORDING && captured != null) {
            recording.frames.add(captured);
            if (recording.frames.size() >= Recording.MAX_FRAMES) stop("Limite de 30 minutos atingido.");
        } else if (mode == Mode.PLAYING) playback.advance();

        boolean stopPressed = consume(stopKey), recordPressed = consume(recordKey), playPressed = consume(playKey);
        boolean loopPressed = consume(loopKey);
        boolean menuPressed = consume(menuKey);

        if (loopPressed && !stopPressed) toggleLoop();
        if (stopPressed) stop("Parado.");
        else if (menuPressed) openMenu();
        else if (recordPressed) {
            if (mode == Mode.RECORDING) stop("Gravacao concluida."); else startRecording(null);
        } else if (playPressed) startPlayback(null);
        if (pending != null) {
            Runnable action = pending;
            pending = null;
            action.run();
        }
    }

    private boolean consume(KeyBinding binding) {
        boolean result = false;
        while (binding.isPressed()) result = true;
        return result;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player != mc.thePlayer) return;
        
        if (mode == Mode.RECORDING && sessionValid() && capturedKeyboard != null) {
            boolean[] held = new boolean[bindings.length];
            for (int i = 0; i < held.length; i++) held[i] = bindings[i].isKeyDown();
            captured = new Recording.Frame(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                    mc.thePlayer.inventory.currentItem, held, pendingPresses);
            captured.view = mc.gameSettings.thirdPersonView;
            captured.dropStack = GuiScreen.isCtrlKeyDown();
            captured.keysHeld = capturedKeyboard.held;
            captured.keyEvents = capturedKeyboard.events;
        } else if (mode == Mode.PLAYING && playingFrame != null) {
            mc.thePlayer.inventory.currentItem = playingFrame.slot;
            if (recording.hasKeyboard()) setView(playingFrame.view);
            setLook(playingFrame.yaw, playingFrame.pitch);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onKeyboard(InputEvent.KeyInputEvent event) {
        int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        if (mode == Mode.RECORDING && Keyboard.getEventKeyState()) capturePress(key);
        else if (mode == Mode.PLAYING && playingFrame != null && !recording.hasKeyboard()) applyPlaybackInputs();
    }

    private boolean isRecorderKey(int key) {
        return key == Keyboard.KEY_ESCAPE || key == recordKey.getKeyCode() || key == playKey.getKeyCode()
                || key == stopKey.getKeyCode() || key == loopKey.getKeyCode() || key == menuKey.getKeyCode();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (mode == Mode.PLAYING) event.setCanceled(true);
        else if (mode == Mode.RECORDING && event.button >= 0 && event.buttonstate) capturePress(event.button - 100);
    }

    private void capturePress(int key) {
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i].getKeyCode() == key) pendingPresses[i] = Math.min(64, pendingPresses[i] + 1);
        }
    }

    private void applyPlaybackInputs() {
        for (int i = 0; i < bindings.length; i++) {
            // Format 2 keyboard actions are handled by Minecraft's real event loop once.
            if (recording.hasKeyboard() && bindings[i].getKeyCode() >= 0) continue;
            KeyBinding.setKeyBindState(bindings[i].getKeyCode(), playingFrame.held[i]);
            setPresses(bindings[i], i == 10 ? 0 : playingFrame.presses[i]);
        }
        if (!recording.hasKeyboard()) {
            for (KeyBinding binding : blocked) {
                KeyBinding.setKeyBindState(binding.getKeyCode(), false);
                setPresses(binding, 0);
            }
            for (KeyBinding binding : mc.gameSettings.keyBindsHotbar) {
                KeyBinding.setKeyBindState(binding.getKeyCode(), false);
                setPresses(binding, 0);
            }
        }
        mc.thePlayer.inventory.currentItem = playingFrame.slot;
        mc.gameSettings.smoothCamera = false;
        if (!recording.hasKeyboard()) setView(playingFrame.view);
    }

    private void setPresses(KeyBinding binding, int count) {
        try { pressTime.setInt(binding, count); }
        catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot access KeyBinding.pressTime", exception); }
    }

    private void releaseBindings() {
        for (KeyBinding binding : bindings) {
            KeyBinding.setKeyBindState(binding.getKeyCode(), false);
            setPresses(binding, 0);
        }
    }

    private void setLook(float yaw, float pitch) {
        if (mc.thePlayer == null) return;
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
    }

    private void setView(int view) {
        if (mc.gameSettings.thirdPersonView == view) return;
        mc.gameSettings.thirdPersonView = view;
        mc.entityRenderer.loadEntityShader(view == 0 ? mc.getRenderViewEntity() : null);
        mc.renderGlobal.setDisplayListEntitiesDirty();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && mode != Mode.IDLE) mc.gameSettings.pauseOnLostFocus = false;
        if (event.phase != TickEvent.Phase.START || mode != Mode.PLAYING || playingFrame == null || mc.thePlayer == null) return;
        int nextIndex = playback.previewIndex(loopEnabled);
        Recording.Frame next = recording.frames.get(nextIndex);
        float partial = MathHelper.clamp_float(event.renderTickTime, 0, 1);
        float yaw = playingFrame.yaw + MathHelper.wrapAngleTo180_float(next.yaw - playingFrame.yaw) * partial;
        float pitch = playingFrame.pitch + (next.pitch - playingFrame.pitch) * partial;
        setLook(yaw, pitch);
    }

    @SubscribeEvent
    public void onHud(RenderGameOverlayEvent.Text event) {
        if (mode == Mode.IDLE) return;
        int tick = mode == Mode.RECORDING ? recording.frames.size() : playback.frameIndex();
        String status = mode == Mode.RECORDING ? "GRAVANDO" : "REPRODUZINDO";
        String total = mode == Mode.PLAYING ? " / " + seconds(recording.frames.size()) + " s" : " s";
        String loop = mode == Mode.PLAYING && loopEnabled ? "  LOOP #" + playback.cycleNumber() : "";
        mc.fontRendererObj.drawStringWithShadow(status + "  " + seconds(tick) + total + loop + "  [F8 parar | F9 loop]",
                6, 6, mode == Mode.RECORDING ? 0xFF6666 : 0x66FF99);
    }

    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20.0); }

    private static final class PlaybackMouseHelper extends MouseHelper {
        private final MouseHelper delegate;
        private PlaybackMouseHelper(MouseHelper delegate) { this.delegate = delegate; }
        @Override public void mouseXYChange() {
            delegate.mouseXYChange();
            deltaX = deltaY = 0;
        }
        @Override public void grabMouseCursor() { delegate.grabMouseCursor(); }
        @Override public void ungrabMouseCursor() { delegate.ungrabMouseCursor(); }
    }
}
