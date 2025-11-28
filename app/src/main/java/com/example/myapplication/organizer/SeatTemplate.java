package com.example.myapplication.organizer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class SeatTemplate {

    private final String id;
    private final String name;
    private final int rows;
    private final int cols;
    private final Set<String> activeSeats;    // danh sách ghế tồn tại
    private final Map<String, String> zoneBySeat; // A1 -> "VIP", ...

    public SeatTemplate(String id,
                        String name,
                        int rows,
                        int cols,
                        Set<String> activeSeats,
                        Map<String, String> zoneBySeat) {
        this.id = id;
        this.name = name;
        this.rows = rows;
        this.cols = cols;
        this.activeSeats = activeSeats;
        this.zoneBySeat = zoneBySeat;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public Set<String> getActiveSeats() {
        return Collections.unmodifiableSet(activeSeats);
    }

    public int getCapacity() {
        return activeSeats.size();
    }

    public String getZoneForSeat(String label) {
        if (zoneBySeat == null) return null;
        return zoneBySeat.get(label);
    }

    public Map<String, String> getZoneMap() {
        return Collections.unmodifiableMap(zoneBySeat);
    }
}
