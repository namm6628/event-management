package com.example.myapplication.organizer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SeatTemplateStore {

    private static final SeatTemplate TEMPLATE_SMALL;
    private static final SeatTemplate TEMPLATE_MEDIUM;
    private static final SeatTemplate TEMPLATE_LARGE;

    private static final Map<String, SeatTemplate> TEMPLATES = new LinkedHashMap<>();

    static {
        TEMPLATE_SMALL  = buildSmallTemplate();
        TEMPLATE_MEDIUM = buildMediumTemplate();
        TEMPLATE_LARGE  = buildLargeTemplate();

        TEMPLATES.put(TEMPLATE_SMALL.getId(), TEMPLATE_SMALL);
        TEMPLATES.put(TEMPLATE_MEDIUM.getId(), TEMPLATE_MEDIUM);
        TEMPLATES.put(TEMPLATE_LARGE.getId(), TEMPLATE_LARGE);
    }

    private static SeatTemplate buildSmallTemplate() {
        int rows = 5;
        int cols = 10;

        Set<String> seats = new LinkedHashSet<>();
        Map<String, String> zones = new HashMap<>();

        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                String label = rowChar + String.valueOf(c);
                seats.add(label);

                String zone;
                if (r <= 1) {
                    zone = "VIP";
                } else if (r <= 3) {
                    zone = "STANDARD";
                } else {
                    zone = "ECONOMY";
                }
                zones.put(label, zone);
            }
        }

        return new SeatTemplate(
                "S",
                "Sơ đồ nhỏ (tối đa ~50 ghế)",
                rows,
                cols,
                seats,
                zones
        );
    }


    private static SeatTemplate buildMediumTemplate() {
        int rows = 8;
        int cols = 12;
        int aisleCol = 7;

        Set<String> seats = new LinkedHashSet<>();
        Map<String, String> zones = new HashMap<>();

        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                if (c == aisleCol) {
                    continue;
                }
                String label = rowChar + String.valueOf(c);

                seats.add(label);

                String zone;
                if (r <= 1) {
                    zone = "VIP";
                } else if (r <= 5) {
                    zone = "STANDARD";
                } else {
                    zone = "ECONOMY";
                }
                zones.put(label, zone);
            }
        }

        return new SeatTemplate(
                "M",
                "Sơ đồ chuẩn (lối đi giữa, ~88 ghế)",
                rows,
                cols,
                seats,
                zones
        );
    }

    private static SeatTemplate buildLargeTemplate() {
        int rows = 12;
        int cols = 18;
        int aisleCol1 = 7;
        int aisleCol2 = 12;

        Set<String> seats = new LinkedHashSet<>();
        Map<String, String> zones = new HashMap<>();

        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                if (c == aisleCol1 || c == aisleCol2) {
                    continue; // lối đi
                }
                String label = rowChar + String.valueOf(c);
                seats.add(label);

                String zone;
                if (r <= 2) {
                    zone = "VIP";
                } else if (r <= 7) {
                    zone = "STANDARD";
                } else {
                    zone = "ECONOMY";
                }
                zones.put(label, zone);
            }
        }

        return new SeatTemplate(
                "L",
                "Sơ đồ lớn (2 lối đi, ~192 ghế)",
                rows,
                cols,
                seats,
                zones
        );
    }

    public static SeatTemplate getTemplate(String id) {
        if (id == null) return null;
        return TEMPLATES.get(id);
    }

    public static SeatTemplate pickTemplateForCapacity(int totalSeats) {
        if (totalSeats <= 0) {
            return TEMPLATE_SMALL;
        }
        SeatTemplate best = null;
        for (SeatTemplate t : TEMPLATES.values()) {
            if (t.getCapacity() >= totalSeats) {
                if (best == null || t.getCapacity() < best.getCapacity()) {
                    best = t;
                }
            }
        }
        return best;
    }

    public static SeatTemplate getDefaultTemplate() {
        return TEMPLATE_MEDIUM;
    }

    public static int getMaxCapacity() {
        int max = 0;
        for (SeatTemplate t : TEMPLATES.values()) {
            if (t.getCapacity() > max) max = t.getCapacity();
        }
        return max;
    }
}
