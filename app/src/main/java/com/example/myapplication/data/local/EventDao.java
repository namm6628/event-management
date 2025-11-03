package com.example.myapplication.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface EventDao {
    @Query("SELECT * FROM events ORDER BY startTime ASC")
    LiveData<List<EventEntity>> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<EventEntity> items);

    @Query("DELETE FROM events")
    void clear();
}
