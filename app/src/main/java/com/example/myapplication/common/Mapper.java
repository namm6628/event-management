package com.example.myapplication.common;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.EventEntity;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public final class Mapper {
    private Mapper() {}

    /** Map Firestore -> Domain Event (startTime là Timestamp chuẩn) */
    public static Event fromSnapshot(DocumentSnapshot doc) {
        Event e = new Event();
        e.setId(doc.getId());
        e.setTitle(doc.getString("title"));
        e.setLocation(doc.getString("location"));
        e.setThumbnail(doc.getString("thumbnail"));
        e.setCategory(doc.getString("category"));

        // startTime: Timestamp -> millis
        Timestamp ts = doc.getTimestamp("startTime");
        e.setStartTime(ts != null ? ts.toDate().getTime() : null);

        // price / seats: Number -> int
        Number priceNum = (Number) doc.get("price");
        e.setPrice(priceNum != null ? priceNum.intValue() : null);

        Number av = (Number) doc.get("availableSeats");
        Number tt = (Number) doc.get("totalSeats");
        e.setAvailableSeats(av != null ? av.intValue() : null);
        e.setTotalSeats(tt != null ? tt.intValue() : null);

        return e;
    }

    /** Domain -> Room Entity */
    public static EventEntity toEntity(Event e) {
        EventEntity x = new EventEntity();
        x.id = e.getId();
        x.title = e.getTitle();
        x.location = e.getLocation();
        x.startTime = e.getStartTime();
        x.price = e.getPrice();
        x.availableSeats = e.getAvailableSeats();
        x.totalSeats = e.getTotalSeats();
        x.thumbnail = e.getThumbnail();
        x.category = e.getCategory();
        return x;
    }

    /** Room Entity -> Domain */
    public static Event fromEntity(EventEntity x) {
        Event e = new Event();
        e.setId(x.id);
        e.setTitle(x.title);
        e.setLocation(x.location);
        e.setStartTime(x.startTime);
        e.setPrice(x.price);
        e.setAvailableSeats(x.availableSeats);
        e.setTotalSeats(x.totalSeats);
        e.setThumbnail(x.thumbnail);
        e.setCategory(x.category);
        return e;
    }
}
