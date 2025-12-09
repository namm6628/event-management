package com.example.myapplication.attendee.home;

import com.example.myapplication.common.model.Event;
import java.util.List;

public class DynamicCategory {
    private final String categoryName;
    private final List<Event> events;

    public DynamicCategory(String categoryName, List<Event> events) {
        this.categoryName = categoryName;
        this.events = events;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public List<Event> getEvents() {
        return events;
    }
}