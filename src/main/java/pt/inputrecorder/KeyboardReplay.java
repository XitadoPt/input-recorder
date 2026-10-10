package pt.inputrecorder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.InputImplementation;

final class KeyboardReplay {
    static final class Snapshot {
        final int[] held;
        final List<Recording.KeyEvent> events;

        Snapshot(int[] held, List<Recording.KeyEvent> events) {
            this.held = held;
            this.events = events;
        }
    }

    private Field eventsField, heldField, implementationField;
    private Object lock;
    private InputImplementation original, replacement;
    private IntPredicate reserved;
    private final boolean[] held = new boolean[Recording.KEY_COUNT];
    private long lastEventTime;

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void initialize() {
        if (lock != null) return;
        if (!Keyboard.isCreated()) throw new IllegalStateException("Teclado do jogo indisponivel.");
        try {
            eventsField = field(Keyboard.class, "readBuffer");
            heldField = field(Keyboard.class, "keyDownBuffer");
            implementationField = field(Keyboard.class, "implementation");
            lock = field(Class.forName("org.lwjgl.input.OpenGLPackageAccess"), "global_lock").get(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Nao foi possivel acessar o teclado LWJGL 2.", exception);
        }
    }

    Snapshot capture(IntPredicate excluded) {
        initialize();
        synchronized (lock) {
            ByteBuffer states = buffer(heldField);
            int[] keys = new int[Recording.KEY_COUNT];
            int count = 0;
            for (int key = 1; key < Recording.KEY_COUNT; key++) {
                if (states.get(key) != 0 && !excluded.test(key)) keys[count++] = key;
            }
            List<Recording.KeyEvent> events = decode(buffer(eventsField).duplicate(),
                    code -> !excluded.test(code), Keyboard.areRepeatEventsEnabled());
            if (events.size() > Recording.MAX_KEY_EVENTS) {
                throw new IllegalStateException("Demasiados eventos de teclado neste tick.");
            }
            return new Snapshot(Arrays.copyOf(keys, count), events);
        }
    }

    void start(IntPredicate reservedKeys) {
        initialize();
        synchronized (lock) {
            reserved = reservedKeys;
            original = implementation();
            replacement = (InputImplementation) Proxy.newProxyInstance(
                    InputImplementation.class.getClassLoader(), new Class<?>[] {InputImplementation.class},
                    (proxy, method, args) -> {
                        try {
                            int start = "readKeyboard".equals(method.getName())
                                    ? ((ByteBuffer) args[0]).position() : 0;
                            Object result = method.invoke(original, args);
                            if ("pollKeyboard".equals(method.getName())) {
                                overlay((ByteBuffer) args[0]);
                            } else if ("readKeyboard".equals(method.getName())) {
                                ByteBuffer target = (ByteBuffer) args[0];
                                ByteBuffer incoming = target.duplicate();
                                incoming.limit(target.position()).position(start);
                                copyReserved(incoming, target, start, reserved);
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            setImplementation(replacement);
        }
    }

    void play(Recording.Frame frame) {
        synchronized (lock) {
            Arrays.fill(held, false);
            for (int key : frame.keysHeld) if (!reserved.test(key)) held[key] = true;
            List<Recording.KeyEvent> replay = new ArrayList<Recording.KeyEvent>();
            for (Recording.KeyEvent event : frame.keyEvents) {
                if (!reserved.test(event.bindingCode())) replay.add(event);
            }
            replaceEvents(replay);
            overlay(buffer(heldField));
            for (int key = 1; key < held.length; key++) {
                if (!reserved.test(key)) {
                    net.minecraft.client.settings.KeyBinding.setKeyBindState(key, held[key]);
                }
            }
        }
    }

    boolean hasControlPress(IntPredicate control) {
        synchronized (lock) {
            for (Recording.KeyEvent event : decode(buffer(eventsField).duplicate(), control, false)) {
                if (event.down) return true;
            }
            return false;
        }
    }

    void stop() {
        if (replacement == null) return;
        synchronized (lock) {
            List<Recording.KeyEvent> releases = new ArrayList<Recording.KeyEvent>();
            for (int key = 1; key < held.length; key++) {
                if (held[key]) releases.add(new Recording.KeyEvent(key, 0, false, false));
            }
            try {
                replaceEvents(releases);
                Arrays.fill(held, false);
                overlay(buffer(heldField));
            } finally {
                if (implementation() == replacement) setImplementation(original);
                replacement = null;
                if (Keyboard.isCreated()) Keyboard.poll();
            }
        }
    }

    private void overlay(ByteBuffer states) {
        for (int key = 1; key < held.length; key++) {
            if (!reserved.test(key)) states.put(key, held[key] ? (byte) 1 : (byte) 0);
        }
    }

    private void replaceEvents(List<Recording.KeyEvent> replay) {
        ByteBuffer current = buffer(eventsField);
        ByteBuffer controls = ByteBuffer.allocate(current.remaining()).order(current.order());
        copyReserved(current.duplicate(), controls, 0, reserved);
        controls.flip();
        int bytes = controls.remaining() + replay.size() * Keyboard.EVENT_SIZE;
        ByteBuffer target = current;

        if (target.capacity() < bytes) {
            target = ByteBuffer.allocate(bytes).order(current.order());
            try { eventsField.set(null, target); }
            catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
        }

        target.clear();
        target.put(controls);
        for (Recording.KeyEvent event : replay) {
            lastEventTime = Math.max(System.nanoTime(), lastEventTime + 1);
            encode(target, event, lastEventTime);
        }
        target.flip();
    }

    static List<Recording.KeyEvent> decode(ByteBuffer source, IntPredicate include, boolean repeats) {
        List<Recording.KeyEvent> events = new ArrayList<Recording.KeyEvent>();
        while (source.remaining() >= Keyboard.EVENT_SIZE) {
            int key = source.getInt() & 0xFF;
            boolean down = source.get() != 0;
            int character = source.getInt();
            source.getLong();
            boolean repeat = source.get() == 1;
            Recording.KeyEvent event = new Recording.KeyEvent(key, character, down, repeat);
            if (include.test(event.bindingCode()) && (repeats || !repeat)) events.add(event);
        }
        return events;
    }

    static void encode(ByteBuffer target, Recording.KeyEvent event, long nanos) {
        target.putInt(event.key).put(event.down ? (byte) 1 : (byte) 0)
                .putInt(event.character).putLong(nanos).put(event.repeat ? (byte) 1 : (byte) 0);
    }

    static void copyReserved(ByteBuffer source, ByteBuffer target, int start, IntPredicate reserved) {
        target.position(start);
        while (source.remaining() >= Keyboard.EVENT_SIZE) {
            int position = source.position();
            int key = source.getInt() & 0xFF;
            source.get();
            int character = source.getInt();
            source.position(position + Keyboard.EVENT_SIZE);
            if (reserved.test(key == 0 ? character + 256 : key)) {
                ByteBuffer event = source.duplicate();
                event.limit(source.position()).position(position);
                target.put(event);
            }
        }
    }

    private ByteBuffer buffer(Field field) {
        try { return (ByteBuffer) field.get(null); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private InputImplementation implementation() {
        try { return (InputImplementation) implementationField.get(null); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private void setImplementation(InputImplementation implementation) {
        try { implementationField.set(null, implementation); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }
}
