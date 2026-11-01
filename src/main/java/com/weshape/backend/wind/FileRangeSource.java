package com.weshape.backend.wind;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/** Wind layers from a local folder, for development and tests (same keys as in the bucket). */
public class FileRangeSource implements RangeSource {

    private final Path root;

    public FileRangeSource(Path root) {
        this.root = root;
    }

    @Override
    public byte[] read(String key, long offset, int length) throws IOException {
        Path file = root.resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new FileNotFoundException(file.toString());
        }
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long available = Math.max(0, f.length() - offset);
            byte[] out = new byte[(int) Math.min(length, available)];
            f.seek(offset);
            f.readFully(out);
            return out;
        }
    }

    @Override
    public String describe() {
        return "local folder " + root;
    }
}
