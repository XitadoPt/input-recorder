package pt.inputrecorder;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class RecordingStore {
    private final Path directory;
    private final String[] actions;
    private final Gson gson = new Gson();

    public RecordingStore(Path directory, String[] actions) {
        this.directory = directory;
        this.actions = actions.clone();
    }

    private Path file(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Use de 1 a 64 letras, numeros, _ ou - no nome.");
        }
        return directory.resolve(name + ".json");
    }

    public boolean exists(String name) { return Files.exists(file(name)); }

    public void save(Recording recording) throws IOException {
        recording.validate(actions);
        Files.createDirectories(directory);
        Path destination = file(recording.name);
        if (Files.exists(destination)) throw new IOException("Esse nome ja existe; escolha outro.");
        Path temporary = Files.createTempFile(directory, ".saving-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(recording, writer);
            }
            Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Recording load(String name) throws IOException {
        Path path = file(name);
        if (Files.size(path) > 32 * 1024 * 1024) throw new IOException("Arquivo muito grande.");
        
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Recording recording = gson.fromJson(reader, Recording.class);
            if (recording == null) throw new IllegalArgumentException("Arquivo vazio.");
            recording.validate(actions);
            recording.name = name;
            return recording;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Gravacao invalida: " + exception.getMessage(), exception);
        }
    }

    public List<String> list() throws IOException {
        List<Path> paths = new ArrayList<Path>();
        if (!Files.isDirectory(directory)) return new ArrayList<String>();

        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().matches("[a-zA-Z0-9_-]{1,64}\\.json"))
                    .forEach(paths::add);
        }

        paths.sort(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed()
                .thenComparing(path -> path.getFileName().toString()));
        List<String> names = new ArrayList<String>();
        for (Path path : paths) {
            String filename = path.getFileName().toString();
            names.add(filename.substring(0, filename.length() - 5));
        }
        return names;
    }
}
