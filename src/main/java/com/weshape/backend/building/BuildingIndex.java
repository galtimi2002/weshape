package com.weshape.backend.building;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Which part of the world each Overture building file covers (buildings/index.json in R2, built by
 * the "Build building index" workflow). A lookup opens only the files that touch its square.
 */
public record BuildingIndex(String release, String base, List<FileBox> files) {

    /** One building file and the area its buildings fall in. */
    public record FileBox(String name, double west, double south, double east, double north) {
        boolean touches(double w, double s, double e, double n) {
            return west <= e && east >= w && south <= n && north >= s;
        }
    }

    public static BuildingIndex parse(byte[] json) throws IOException {
        JsonNode root = new ObjectMapper().readTree(json);
        String release = root.path("release").asText("");
        String base = root.path("base").asText("");
        if (release.isEmpty() || base.isEmpty() || !root.path("files").isArray()) {
            throw new IOException("Building index without release, base or files");
        }
        List<FileBox> files = new ArrayList<>();
        for (JsonNode f : root.get("files")) {
            if (f.size() != 5) {
                throw new IOException("Bad building index row: " + f);
            }
            files.add(new FileBox(f.get(0).asText(), f.get(1).asDouble(), f.get(2).asDouble(),
                    f.get(3).asDouble(), f.get(4).asDouble()));
        }
        if (files.isEmpty()) {
            throw new IOException("Building index lists no files");
        }
        return new BuildingIndex(release, base.endsWith("/") ? base : base + "/", List.copyOf(files));
    }

    /** Full addresses of the files that touch the square. */
    public List<String> filesFor(double w, double s, double e, double n) {
        List<String> out = new ArrayList<>();
        for (FileBox f : files) {
            if (f.touches(w, s, e, n)) {
                out.add(base + f.name());
            }
        }
        return out;
    }
}
