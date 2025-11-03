package com.example.myapplication.data.repo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.myapplication.common.Mapper;
import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.EventDao;
import com.example.myapplication.data.local.EventEntity;
import com.example.myapplication.data.remote.EventRemoteDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class EventRepository {
    private final EventDao dao;
    private final EventRemoteDataSource remote;

    public EventRepository(EventDao dao, EventRemoteDataSource remote) {
        this.dao = dao;
        this.remote = remote;
    }

    // UI quan sát Room
    public LiveData<List<Event>> observe() {
        return Transformations.map(dao.getAll(), list -> {
            List<Event> out = new ArrayList<>();
            for (EventEntity x : list) out.add(Mapper.fromEntity(x));
            return out;
        });
    }

    // Kéo Firestore → Room
    public void refresh(Consumer<Exception> onError) {
        remote.fetchAll(events -> Executors.newSingleThreadExecutor().execute(() -> {
            List<EventEntity> xs = new ArrayList<>();
            for (Event e : events) xs.add(Mapper.toEntity(e));
            dao.upsertAll(xs);
        }), e -> { if (onError != null) onError.accept(e); });
    }
}
