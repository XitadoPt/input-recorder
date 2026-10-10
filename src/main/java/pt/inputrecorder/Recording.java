package pt.inputrecorder;

import java.util.ArrayList;
import java.util.List;

public final class Recording {
    public static final int FORMAT = 2;
    public static final int KEY_COUNT = 256;
    public static final int MAX_KEY_EVENTS = 1024;
    public static final int MAX_FRAMES = 20 * 60 * 30;
    public int formatVersion = FORMAT;
    public String name;
    public String[] actions;
    public int dimension;
    public double startX, startY, startZ;
    public List<Frame> frames = new ArrayList<Frame>();

    public static final class Frame {
        public float yaw, pitch;
        public int slot;
        public int view;
        public boolean dropStack;
        public boolean[] held;
        public int[] presses;
        public int[] keysHeld;
        public List<KeyEvent> keyEvents;

        public Frame(float yaw, float pitch, int slot, boolean[] held, int[] presses) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.slot = slot;
            this.held = held.clone();
            this.presses = presses.clone();
        }
    }

    public static final class KeyEvent {
        public int key, character;
        public boolean down, repeat;

        public KeyEvent(int key, int character, boolean down, boolean repeat) {
            this.key = key;
            this.character = character;
            this.down = down;
            this.repeat = repeat;
        }

        int bindingCode() { return key == 0 ? character + 256 : key; }
    }

    public boolean hasKeyboard() { return formatVersion >= 2; }

    public void validate(String[] expectedActions) {
        if ((formatVersion != 1 && formatVersion != FORMAT) || actions == null
                || !java.util.Arrays.equals(actions, expectedActions)) {
            throw new IllegalArgumentException("Formato de gravacao incompatível.");
        }

        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}")
                || !Double.isFinite(startX) || !Double.isFinite(startY) || !Double.isFinite(startZ)
                || frames == null || frames.isEmpty() || frames.size() > MAX_FRAMES) {
            throw new IllegalArgumentException("Gravacao vazia ou invalida.");
        }

        for (Frame frame : frames) {
            if (frame == null || !Float.isFinite(frame.yaw) || !Float.isFinite(frame.pitch)
                    || frame.pitch < -90 || frame.pitch > 90 || frame.slot < 0 || frame.slot > 8
                    || frame.view < 0 || frame.view > 2
                    || frame.held == null || frame.presses == null
                    || frame.held.length != actions.length || frame.presses.length != actions.length) {
                throw new IllegalArgumentException("Frame invalido na gravacao.");
            }
            
            for (int presses : frame.presses) {
                if (presses < 0 || presses > 64) {
                    throw new IllegalArgumentException("Contagem de teclas invalida.");
                }
            }
            if (hasKeyboard()) {
                if (frame.keysHeld == null || frame.keysHeld.length >= KEY_COUNT
                        || frame.keyEvents == null || frame.keyEvents.size() > MAX_KEY_EVENTS) {
                    throw new IllegalArgumentException("Teclado invalido na gravacao.");
                }
                int previousKey = 0;
                for (int key : frame.keysHeld) {
                    if (key <= previousKey || key >= KEY_COUNT) {
                        throw new IllegalArgumentException("Tecla mantida invalida na gravacao.");
                    }
                    previousKey = key;
                }
                for (KeyEvent event : frame.keyEvents) {
                    if (event == null || event.key < 0 || event.key >= KEY_COUNT
                            || event.character < 0 || event.character > Character.MAX_VALUE
                            || (event.repeat && !event.down)) {
                        throw new IllegalArgumentException("Evento de teclado invalido na gravacao.");
                    }
                }
            }
        }
    }
}
