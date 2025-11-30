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
                if (r <= 1) {          // A,B
                    zone = "VIP";
                } else if (r <= 3) {   // C,D
                    zone = "STANDARD";
                } else {               // E
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

    // Sân khấu vừa: A..H, 1..12, bỏ cột 7 làm lối đi (8x11≈88 ghế)
    // A-B: VIP, C-F: Standard, G-H: Economy
    private static SeatTemplate buildMediumTemplate() {
        int rows = 8;
        int cols = 12;
        int aisleCol = 7; // cột làm lối đi

        Set<String> seats = new LinkedHashSet<>();
        Map<String, String> zones = new HashMap<>();

        for (int r = 0; r < rows; r++) {
            char rowChar = (char) ('A' + r);
            for (int c = 1; c <= cols; c++) {
                if (c == aisleCol) {
                    // bỏ hẳn cột 7 -> tạo lối đi
                    continue;
                }
                String label = rowChar + String.valueOf(c);

                seats.add(label);

                String zone;
                if (r <= 1) {          // A,B
                    zone = "VIP";
                } else if (r <= 5) {   // C..F
                    zone = "STANDARD";
                } else {               // G,H
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

    // Sân khấu lớn: A..L (12 hàng), 1..18 (18 cột)
    // bỏ cột 7 & 12 làm 2 lối đi → mỗi hàng còn 16 ghế → 12*16 = 192 ghế
    // A-C: VIP, D-H: Standard, I-L: Economy
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
                if (r <= 2) {          // A..C
                    zone = "VIP";
                } else if (r <= 7) {   // D..H
                    zone = "STANDARD";
                } else {               // I..L
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

    /** Chọn template nhỏ nhất có sức chứa >= totalSeats */
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
