package com.example.myapplication.common.model;

import java.io.Serializable;

public class Event implements Serializable {
    public String id, title, date, venue, coverUrl, price, category;
    public Event(String id, String title, String date, String venue,
                 String coverUrl, String price, String category) {
        this.id = id; this.title = title; this.date = date; this.venue = venue;
        this.coverUrl = coverUrl; this.price = price; this.category = category;
    }
}
