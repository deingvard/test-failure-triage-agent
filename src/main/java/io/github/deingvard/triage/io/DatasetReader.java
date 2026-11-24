package io.github.deingvard.triage.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.deingvard.triage.model.FailureCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the labelled evaluation dataset: one JSON object per line (JSON Lines).
 */
public final class DatasetReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DatasetReader() {
    }

    public static List<FailureCase> read(Path file) {
        List<FailureCase> cases = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    cases.add(MAPPER.readValue(line, FailureCase.class));
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            "Invalid JSON on line " + (i + 1) + " of " + file + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read dataset " + file, e);
        }
        return cases;
    }
}
