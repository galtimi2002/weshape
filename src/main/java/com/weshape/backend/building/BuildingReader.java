package com.weshape.backend.building;

import java.io.IOException;
import java.util.List;

/** Reads the buildings whose bounding box touches a square, from the given Overture files. */
public interface BuildingReader {

    /** One building as stored: its outline (WKB) and what Overture knows about it. */
    record Raw(String id, byte[] wkb, Double heightM, Integer floors, String roofShape,
               Double roofDirectionDeg, String kind) {
    }

    List<Raw> read(List<String> files, double west, double south, double east, double north) throws IOException;
}
