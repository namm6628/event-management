package com.example.myapplication.data.local;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "events",
        indices = {
                @Index(value = {"startTime"}),
                @Index(value = {"category"}),
                @Index(value = {"title"})
        }
)
public class EventEntity {

    @PrimaryKey
    @NonNull
    private String id;

    @Nullable private String title;
    @Nullable private String location;
    @Nullable private String category;
    @Nullable private String thumbnail;

    /** Lưu millis (epoch). Null nếu chưa có */
    @Nullable private Long startTime;

    @Nullable private Double price;
    @Nullable private Integer availableSeats;
    @Nullable private Integer totalSeats;

    // --- getters / setters ---
    @Nullable public String getId() { return id; }
    public void setId(@Nullable String id) { this.id = id; }

    @Nullable public String getTitle() { return title; }
    public void setTitle(@Nullable String title) { this.title = title; }

    @Nullable public String getLocation() { return location; }
    public void setLocation(@Nullable String location) { this.location = location; }

    @Nullable public String getCategory() { return category; }
    public void setCategory(@Nullable String category) { this.category = category; }

    @Nullable public String getThumbnail() { return thumbnail; }
    public void setThumbnail(@Nullable String thumbnail) { this.thumbnail = thumbnail; }

    @Nullable public Long getStartTime() { return startTime; }
    public void setStartTime(@Nullable Long startTime) { this.startTime = startTime; }

    @Nullable public Double getPrice() { return price; }
    public void setPrice(@Nullable Double price) { this.price = price; }

    @Nullable public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(@Nullable Integer availableSeats) { this.availableSeats = availableSeats; }

    @Nullable public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(@Nullable Integer totalSeats) { this.totalSeats = totalSeats; }
}
