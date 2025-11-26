package com.example.myapplication.common.model;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import java.io.Serializable;
import com.google.firebase.firestore.PropertyName;

public class Event implements Serializable {
    private String id;               // set từ document id
    private String title;
    private String location;
    private String category;
    private String thumbnail;

    private String description;
    private String status;

    private String addressDetail;

    private String ownerId;
    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    // ✅ Firestore Timestamp
    private Timestamp startTime;

    private Double price;
    private Integer availableSeats;
    private Integer totalSeats;

    // Các field bên dưới CHƯA có trong DB, để null (mở rộng sau)
    private String artist;           // optional
    private String venue;            // optional
    private Timestamp endTime;       // optional
    private Double lat;              // optional
    private Double lng;              // optional

    public Event() {}

    // getters/setters...
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Nullable public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @Nullable public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    @Nullable public String getAddressDetail() { return addressDetail; }
    public void setAddressDetail(String addressDetail) { this.addressDetail = addressDetail; }

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

    @Nullable
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Nullable
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }


    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
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
