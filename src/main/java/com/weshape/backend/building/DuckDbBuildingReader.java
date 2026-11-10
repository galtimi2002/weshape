package com.weshape.backend.building;

import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads buildings straight from Overture's public Parquet files over HTTPS with an in-process DuckDB.
 * DuckDB fetches only the parts of the files it needs: the bbox statistics, then the rows in the square.
 * One connection, one query at a time; it is opened on the first lookup, not at start-up.
 */
@Component
public class DuckDbBuildingReader implements BuildingReader {

    private static final Logger log = LoggerFactory.getLogger(DuckDbBuildingReader.class);

    private final String memoryLimit;
    private final int threads;
    private final String workDir;
    private Connection connection;
    /** How the outline column comes out as WKB in this DuckDB version; found on the first query. */
    private String geometrySql;

    public DuckDbBuildingReader(
            @Value("${weshape.buildings.duckdb.memory-limit:128MB}") String memoryLimit,
            @Value("${weshape.buildings.duckdb.threads:2}") int threads,
            @Value("${weshape.buildings.duckdb.work-dir:${java.io.tmpdir}/duckdb}") String workDir) {
        this.memoryLimit = memoryLimit;
        this.threads = threads;
        this.workDir = workDir;
    }

    @Override
    public synchronized List<Raw> read(List<String> files, double west, double south, double east, double north)
            throws IOException {
        if (files.isEmpty()) {
            return List.of();
        }
        try {
            Connection con = connection();
            if (geometrySql != null) {
                return query(con, geometrySql, files, west, south, east, north);
            }
            return pickGeometrySql(con, files, west, south, east, north);
        } catch (SQLException | RuntimeException e) {
            // Start afresh next time (a broken connection, an extension that did not load).
            close();
            throw new IOException("Building lookup failed: " + e.getMessage(), e);
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            return connection;
        }
        Connection con = DriverManager.getConnection("jdbc:duckdb:");
        try (Statement st = con.createStatement()) {
            st.execute("SET extension_directory = '" + sqlString(workDir + "/extensions") + "'");
            st.execute("SET temp_directory = '" + sqlString(workDir + "/tmp") + "'");
            st.execute("SET memory_limit = '" + sqlString(memoryLimit) + "'");
            st.execute("SET threads = " + Math.max(1, threads));
            // Keep the Parquet footers between lookups: the same few files are read again and again.
            st.execute("SET enable_object_cache = true");
            st.execute("INSTALL httpfs");
            st.execute("LOAD httpfs");
        } catch (SQLException e) {
            con.close();
            throw e;
        }
        connection = con;
        log.info("Buildings: DuckDB ready (memory limit {}, {} thread(s))", memoryLimit, threads);
        return con;
    }

    /**
     * Older DuckDB reads GeoParquet outlines as WKB bytes; newer versions turn them into a GEOMETRY
     * value unless told not to. Tries the plain column first, then asks for WKB explicitly; keeps
     * the way that worked once a query has returned buildings to check it on.
     */
    private List<Raw> pickGeometrySql(Connection con, List<String> files, double w, double s, double e, double n)
            throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute("SET enable_geoparquet_conversion = false");
        } catch (SQLException ignored) {
            // Not a setting in this version.
        }
        SQLException last = null;
        for (String sql : List.of("geometry", "ST_AsWKB(geometry)", "spatial:ST_AsWKB(geometry)")) {
            String expr = sql.startsWith("spatial:") ? sql.substring(8) : sql;
            try {
                if (sql.startsWith("spatial:")) {
                    try (Statement st = con.createStatement()) {
                        st.execute("INSTALL spatial");
                        st.execute("LOAD spatial");
                    }
                }
                List<Raw> rows = query(con, expr, files, w, s, e, n);
                if (!rows.isEmpty()) {
                    geometrySql = expr;
                    log.info("Buildings: outlines read as {}", expr);
                }
                return rows;
            } catch (SQLException | IllegalArgumentException ex) {
                log.info("Buildings: outlines not readable as {}: {}", expr, ex.getMessage());
                last = ex instanceof SQLException se ? se : new SQLException(ex.getMessage(), ex);
            }
        }
        throw last;
    }

    private List<Raw> query(Connection con, String geometry, List<String> files,
                            double w, double s, double e, double n) throws SQLException {
        StringBuilder list = new StringBuilder("[");
        for (String f : files) {
            if (list.length() > 1) {
                list.append(", ");
            }
            list.append('\'').append(sqlString(f)).append('\'');
        }
        list.append(']');
        String sql = "SELECT id, " + geometry + " AS wkb, height, num_floors, roof_shape, roof_direction, \"class\""
                + " FROM read_parquet(" + list + ", hive_partitioning = false)"
                + " WHERE bbox.xmin <= ? AND bbox.xmax >= ? AND bbox.ymin <= ? AND bbox.ymax >= ?";
        List<Raw> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, e);
            ps.setDouble(2, w);
            ps.setDouble(3, n);
            ps.setDouble(4, s);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byte[] wkb = bytes(rs.getObject(2));
                    // Fails here, on the first row, when the column is not WKB.
                    if (out.isEmpty()) {
                        Wkb.polygons(wkb);
                    }
                    out.add(new Raw(rs.getString(1), wkb, number(rs.getObject(3)), integer(rs.getObject(4)),
                            rs.getString(5), number(rs.getObject(6)), rs.getString(7)));
                }
            }
        }
        return out;
    }

    private static byte[] bytes(Object o) throws SQLException {
        if (o instanceof byte[] b) {
            return b;
        }
        if (o instanceof Blob blob) {
            return blob.getBytes(1, (int) blob.length());
        }
        if (o instanceof java.nio.ByteBuffer bb) {
            byte[] b = new byte[bb.remaining()];
            bb.duplicate().get(b);
            return b;
        }
        throw new IllegalArgumentException("outline column is " + (o == null ? "null" : o.getClass().getName()));
    }

    private static Double number(Object o) {
        return o instanceof Number num ? num.doubleValue() : null;
    }

    private static Integer integer(Object o) {
        return o instanceof Number num ? num.intValue() : null;
    }

    private static String sqlString(String s) {
        return s.replace("'", "''");
    }

    private synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // Already gone.
            }
            connection = null;
        }
        geometrySql = null;
    }

    @jakarta.annotation.PreDestroy
    void shutdown() {
        close();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "DuckDB building reader (%s)", memoryLimit);
    }
}
