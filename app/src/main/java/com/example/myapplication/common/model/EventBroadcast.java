package com.example.myapplication.common.model;

import com.google.firebase.Timestamp;

public class EventBroadcast {

    private String id;          // document id (set bằng tay)
    private String eventId;
    private String eventTitle;  // tên sự kiện (optional nhưng nên lưu)
    private String title;       // tiêu đề thông báo
    private String message;     // nội dung
    private Timestamp createdAt;

    public EventBroadcast() {
        // Firestore cần constructor rỗng
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
