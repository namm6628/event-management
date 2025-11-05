package com.example.myapplication.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity cho bảng "events".
 * Lưu ý: startTime lưu dạng millis (Long) để dễ ORDER BY trong SQL.
 */
@Entity(tableName = "events")
public class EventEntity {

    @PrimaryKey @NonNull
    private String id;

    private String title;
    private String location;
    private String category;
    private String thumbnail;

    /** Thời gian sự kiện (millis since epoch) */
    private Long startTime;

    private Double price;
    private Integer availableSeats;
    private Integer totalSeats;

    @NonNull public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

    public Long getStartTime() { return startTime; }
    public void setStartTime(Long startTime) { this.startTime = startTime; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }
}
