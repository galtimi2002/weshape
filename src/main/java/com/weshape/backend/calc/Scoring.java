package com.weshape.backend.calc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The expert's scoring numbers, read from {@code calc/scoring.xlsx}: reference values that turn each
 * criterion into 0–100 points, the weight of each criterion for each priority, and the limits that
 * exclude an option on their own. Edit the Excel file to change them; the file is checked when it is
 * read and refused with the reason when something is wrong (docs/decisions/2026-11-17-scoring-inputs.md).
 */
public record Scoring(
        Map<Criterion, Reference> references,
        Map<Priority, Map<Criterion, Double>> weights,
        Map<Limit, Double> limits,
        Map<Segment, Reference> otherSegments) {

    /** The resource the app ships. */
    public static final String RESOURCE = "/calc/scoring.xlsx";

    /** The criteria; the key is the one in column A of the Excel file. */
    public enum Criterion {
        PAYBACK("payback"),
        SAVINGS("savings"),
        COVERAGE("coverage"),
        UPFRONT("upfront"),
        MAINTENANCE("maintenance"),
        ENVIRONMENT("environment"),
        RELIABILITY("reliability"),
        SITE_FIT("siteFit");

        final String key;

        Criterion(String key) {
            this.key = key;
        }
    }

    /** The household's answer to "What matters most to you?"; the key is the app's. */
    public enum Priority {
        UPFRONT("upfront"),
        RUNNING("running"),
        ENVIRONMENT("environment"),
        RELIABLE("reliable"),
        BALANCED("balanced");

        final String key;

        Priority(String key) {
            this.key = key;
        }
    }

    /** Limits that exclude an option on their own (sheet "Exclusions"). */
    public enum Limit {
        /** Payback over this many years: not recommended. */
        PAYBACK_MAX_YEARS("payback", "max"),
        /** Savings under this share of the bills the user gave us: not recommended. */
        SAVINGS_MIN_SHARE("savings", "min"),
        /** Under this share of the need: not a main option, an add-on at most. */
        COVERAGE_MIN_SHARE("coverage", "min"),
        /** Under this many hours a day without backup: not recommended without storage. */
        RELIABILITY_MIN_HOURS("reliability", "min"),
        /** Maintenance tier above this when no one could fix it: not recommended. */
        MAINTENANCE_NO_REPAIR_MAX("maintenanceNoRepair", "max");

        final String key;
        final String rule;

        Limit(String key, String rule) {
            this.key = key;
            this.rule = rule;
        }
    }

    /** Who the assessment is for, besides households (sheet "Other segments"; for later). */
    public enum Segment {
        MICRO("micro"),
        SME("sme"),
        COMMUNITY("community");

        final String key;

        Segment(String key) {
            this.key = key;
        }
    }

    /** Best value (100 points) and worst value (0 points), on a linear or log scale. */
    public record Reference(double best, double worst, boolean log) {
        public Reference {
            if (best == worst) throw new IllegalArgumentException("best and worst are the same");
            if (log && (best <= 0 || worst <= 0)) throw new IllegalArgumentException("a log scale needs values above 0");
        }

        /** 0–100 points for a value: 100 at best or beyond, 0 at worst or beyond, proportional between. */
        public double points(double value) {
            double share = log
                    ? (Math.log(Math.max(value, Double.MIN_NORMAL)) - Math.log(worst)) / (Math.log(best) - Math.log(worst))
                    : (value - worst) / (best - worst);
            return 100 * Math.max(0, Math.min(1, share));
        }
    }

    /** Weights in a column may be off by this much from 100% (rounding in Excel). */
    static final double WEIGHT_TOLERANCE = 0.001;

    /** Points of one criterion for a value. */
    public double points(Criterion c, double value) {
        return references.get(c).points(value);
    }

    /**
     * The total score, 0–100, for a priority: the weighted sum of each criterion's points. A criterion
     * that cannot be worked out for an option (missing from {@code values}) counts with 0 points.
     */
    public double score(Priority p, Map<Criterion, Double> values) {
        double total = 0;
        for (Map.Entry<Criterion, Double> w : weights.get(p).entrySet()) {
            Double v = values.get(w.getKey());
            if (v != null) total += w.getValue() * points(w.getKey(), v);
        }
        return total;
    }

    public double limit(Limit l) {
        return limits.get(l);
    }

    /** The file the app ships. */
    public static Scoring bundled() {
        try (InputStream in = Scoring.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is missing from the app");
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads and checks the workbook; throws IllegalArgumentException with the reason when it is not usable. */
    public static Scoring parse(InputStream xlsx) {
        Xlsx book = Xlsx.read(xlsx);

        Map<Criterion, Reference> refs = new EnumMap<>(Criterion.class);
        List<List<Object>> crit = book.sheet("Criteria");
        header(crit, "Criteria", "Key", "Criterion", "Unit", "Best value (100 points)", "Worst value (0 points)", "Scale");
        for (Criterion c : Criterion.values()) {
            List<Object> row = row(crit, "Criteria", c.key);
            String scale = text(row, 5, "Criteria", c.key).strip().toLowerCase();
            if (!scale.equals("linear") && !scale.equals("log")) {
                throw new IllegalArgumentException("Criteria, " + c.key + ": scale must be linear or log, not \"" + scale + "\"");
            }
            refs.put(c, reference(number(row, 3, "Criteria", c.key), number(row, 4, "Criteria", c.key), scale.equals("log"), "Criteria, " + c.key));
        }

        Map<Priority, Map<Criterion, Double>> weights = new EnumMap<>(Priority.class);
        List<List<Object>> w = book.sheet("Weights");
        if (w.isEmpty()) throw new IllegalArgumentException("Weights: the sheet is empty");
        List<Object> head = w.get(0);
        for (Priority p : Priority.values()) {
            int col = head.indexOf(p.key);
            if (col < 0) throw new IllegalArgumentException("Weights: no column for priority \"" + p.key + "\" in row 1");
            Map<Criterion, Double> column = new EnumMap<>(Criterion.class);
            double sum = 0;
            for (Criterion c : Criterion.values()) {
                double v = number(row(w, "Weights", c.key), col, "Weights", c.key + " / " + p.key);
                if (v < 0 || v > 1) {
                    throw new IllegalArgumentException("Weights, " + c.key + " / " + p.key + ": " + v + " is not between 0% and 100%");
                }
                column.put(c, v);
                sum += v;
            }
            if (Math.abs(sum - 1) > WEIGHT_TOLERANCE) {
                throw new IllegalArgumentException("Weights: the column \"" + p.key + "\" adds up to "
                        + Math.round(sum * 1000) / 10.0 + "%, not 100%");
            }
            weights.put(p, column);
        }

        Map<Limit, Double> limits = new EnumMap<>(Limit.class);
        List<List<Object>> ex = book.sheet("Exclusions");
        header(ex, "Exclusions", "Key", "Criterion", "Rule", "Limit");
        for (Limit l : Limit.values()) {
            List<Object> row = row(ex, "Exclusions", l.key);
            String rule = text(row, 2, "Exclusions", l.key).strip();
            if (!rule.equals(l.rule)) {
                throw new IllegalArgumentException("Exclusions, " + l.key + ": the rule must be \"" + l.rule + "\", not \"" + rule + "\"");
            }
            limits.put(l, number(row, 3, "Exclusions", l.key));
        }

        Map<Segment, Reference> segments = new EnumMap<>(Segment.class);
        List<List<Object>> seg = book.sheet("Other segments");
        for (Segment s : Segment.values()) {
            List<Object> row = row(seg, "Other segments", s.key);
            segments.put(s, reference(number(row, 2, "Other segments", s.key), number(row, 3, "Other segments", s.key), true, "Other segments, " + s.key));
        }
        return new Scoring(refs, weights, limits, segments);
    }

    private static Reference reference(double best, double worst, boolean log, String where) {
        try {
            return new Reference(best, worst, log);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage());
        }
    }

    private static void header(List<List<Object>> rows, String sheet, String... names) {
        List<Object> h = rows.isEmpty() ? List.of() : rows.get(0);
        for (int i = 0; i < names.length; i++) {
            Object v = i < h.size() ? h.get(i) : null;
            if (!(v instanceof String s) || !s.strip().equals(names[i])) {
                throw new IllegalArgumentException(sheet + ": column " + (char) ('A' + i) + " of row 1 must be \"" + names[i] + "\"");
            }
        }
    }

    /** The row whose column A is the key. */
    private static List<Object> row(List<List<Object>> rows, String sheet, String key) {
        List<Object> found = null;
        for (List<Object> r : rows) {
            if (!r.isEmpty() && r.get(0) instanceof String s && s.strip().equals(key)) {
                if (found != null) throw new IllegalArgumentException(sheet + ": the key \"" + key + "\" is there twice");
                found = r;
            }
        }
        if (found == null) throw new IllegalArgumentException(sheet + ": no row with the key \"" + key + "\" in column A");
        return found;
    }

    private static double number(List<Object> row, int col, String sheet, String what) {
        Object v = col < row.size() ? row.get(col) : null;
        if (v instanceof Double d && Double.isFinite(d)) return d;
        throw new IllegalArgumentException(sheet + ", " + what + ": column " + (char) ('A' + col) + " must be a number");
    }

    private static String text(List<Object> row, int col, String sheet, String what) {
        Object v = col < row.size() ? row.get(col) : null;
        if (v instanceof String s) return s;
        throw new IllegalArgumentException(sheet + ", " + what + ": column " + (char) ('A' + col) + " must be text");
    }
}
