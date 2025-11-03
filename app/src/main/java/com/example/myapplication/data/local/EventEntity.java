package com.example.myapplication.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "events")
public class EventEntity {
    @PrimaryKey @NonNull public String id;
    public String title;
    public String location;
    public Long startTime;
    public Integer price;
    public Integer availableSeats;
    public Integer totalSeats;
    public String thumbnail;
    public String category;
}
