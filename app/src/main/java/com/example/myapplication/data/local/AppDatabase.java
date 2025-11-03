package com.example.myapplication.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {EventEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract EventDao eventDao();

    public static AppDatabase create(Context ctx) {
        return Room.databaseBuilder(ctx.getApplicationContext(),
                AppDatabase.class, "event-db").build();
    }
}
