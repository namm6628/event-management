package com.example.myapplication.common;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.EventEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

// Mapper.java
public class Mapper {

    public static Event toEvent(EventEntity entity) {
        if (entity == null) return null;
        Event e = new Event();
        e.setId(entity.getId());
        e.setTitle(entity.getTitle());
        e.setLocation(entity.getLocation());
        e.setCategory(entity.getCategory());
        e.setThumbnail(entity.getThumbnail());
        e.setPrice(entity.getPrice());
        e.setAvailableSeats(entity.getAvailableSeats());
        e.setTotalSeats(entity.getTotalSeats());

        // ✅ startTime trong Entity là Long → đổi sang Timestamp
        if (entity.getStartTime() != null) {
            e.setStartTime(new com.google.firebase.Timestamp(
                    new java.util.Date(entity.getStartTime())
            ));
        } else {
            e.setStartTime(null);
        }
        return e;
    }

    public static EventEntity toEntity(Event e) {
        if (e == null) return null;
        EventEntity entity = new EventEntity();
        entity.setId(e.getId());
        entity.setTitle(e.getTitle());
        entity.setLocation(e.getLocation());
        entity.setCategory(e.getCategory());
        entity.setThumbnail(e.getThumbnail());
        entity.setPrice(e.getPrice());
        entity.setAvailableSeats(e.getAvailableSeats());
        entity.setTotalSeats(e.getTotalSeats());

        // ✅ startTime trong Event là Timestamp → đổi sang millis (Long)
        com.google.firebase.Timestamp ts = e.getStartTime();
        entity.setStartTime(ts != null ? ts.toDate().getTime() : null);
        return entity;
    }
}

