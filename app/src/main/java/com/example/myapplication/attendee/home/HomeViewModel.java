package com.example.myapplication.attendee.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.model.Event;
import com.example.myapplication.data.local.EventEntity;
import com.example.myapplication.data.repo.EventRepository;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * HomeViewModel
 * ------------------------
 * - Lấy dữ liệu từ local Room (EventEntity) -> chuyển sang model Event để hiển thị.
 * - Có LiveData loading để theo dõi trạng thái tải.
 * - Có hàm refresh() để đồng bộ Firestore -> Room.
 */
public class HomeViewModel extends ViewModel {

    private final EventRepository repo;
    private final LiveData<List<Event>> events;
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);
    public LiveData<Boolean> loading = _loading;

    public HomeViewModel(EventRepository repo) {
        this.repo = repo;

        // Chuyển đổi EventEntity -> Event (để thống nhất kiểu với ExploreFragment)
        this.events = Transformations.map(repo.getAllLocal(), entities -> {
            if (entities == null) return Collections.emptyList();
            List<Event> list = new ArrayList<>();
            for (EventEntity e : entities) {
                Event ev = new Event();
                ev.setId(e.getId());
                ev.setTitle(e.getTitle());
                ev.setLocation(e.getLocation());
                ev.setCategory(e.getCategory());
                ev.setThumbnail(e.getThumbnail());

                // convert millis -> Timestamp
                if (e.getStartTime() != null) {
                    ev.setStartTime(new Timestamp(new Date(e.getStartTime())));
                }

                ev.setPrice(e.getPrice());
                ev.setAvailableSeats(e.getAvailableSeats());
                ev.setTotalSeats(e.getTotalSeats());
                list.add(ev);
            }
            return list;
        });
    }

    /** Trả về LiveData danh sách Event (đã map từ Room) */
    public LiveData<List<Event>> getEvents() {
        return events;
    }

    /** Đồng bộ lại dữ liệu từ Firestore xuống Room */
    public void refresh() {
        _loading.postValue(true);
        repo.refreshAll(
                () -> _loading.postValue(false),           // onDone
                e -> _loading.postValue(false)             // onError
        );
    }
}
