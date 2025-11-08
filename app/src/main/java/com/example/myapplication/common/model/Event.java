package com.example.myapplication.common.model;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;

/** Model map trực tiếp với collection "events" trên Firestore. */
public class Event {

    private String id;                 // gán từ documentId
    private String title;
    private String location;
    private String category;
    private String thumbnail;

    // ✅ Firestore Timestamp
    private Timestamp startTime;

    private Double price;
    private Integer availableSeats;
    private Integer totalSeats;

    // --- getters / setters ---

    @Nullable public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Nullable public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @Nullable public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Nullable public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Nullable public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    @Nullable public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    @Nullable public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    @Nullable public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    @Nullable public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    // Dán code này vào BÊN TRONG lớp Event.java

    @com.google.firebase.firestore.PropertyName("videoUrl")
    private String videoUrl;

    @com.google.firebase.firestore.PropertyName("videoUrl")
    public String getVideoUrl() {
        return videoUrl;
    }

    @com.google.firebase.firestore.PropertyName("videoUrl")
    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
    // Dán code này vào BÊN TRONG lớp Event.java

    @com.google.firebase.firestore.PropertyName("hasVideo")
    private boolean hasVideo = false; // Mặc định là false

    @com.google.firebase.firestore.PropertyName("hasVideo")
    public boolean getHasVideo() {
        return hasVideo;
    }

    @com.google.firebase.firestore.PropertyName("hasVideo")
    public void setHasVideo(boolean hasVideo) {
        this.hasVideo = hasVideo;
    }
    // Bạn có thể override equals/hashCode nếu muốn dùng trong DiffUtil theo id
}
