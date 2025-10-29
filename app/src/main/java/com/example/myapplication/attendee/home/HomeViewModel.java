// app/src/main/java/com/example/myapplication/attendee/home/HomeViewModel.java
package com.example.myapplication.attendee.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.common.DummyData;
import com.example.myapplication.common.model.Event;

import java.util.ArrayList;
import java.util.List;

public class HomeViewModel extends ViewModel {
    private final MutableLiveData<List<Event>> events = new MutableLiveData<>();
    private List<Event> cached = new ArrayList<>();
    private String q = "";
    private String cat = "Tất cả";

    public LiveData<List<Event>> getEvents(){ return events; }

    public void load(){
        if (cached.isEmpty()) cached = DummyData.getEvents();
        apply();
    }

    public void setQuery(String s){ q = s==null?"":s; apply(); }
    public void setCategory(String c){ cat = (c==null||c.isEmpty())?"Tất cả":c; apply(); }

    private void apply(){
        List<Event> out = new ArrayList<>();
        for (Event e : cached) {
            boolean ok = true;
            if (!"Tất cả".equalsIgnoreCase(cat)) ok &= e.category != null && e.category.equalsIgnoreCase(cat);
            if (!q.isEmpty()) ok &= e.title != null && e.title.toLowerCase().contains(q.toLowerCase());
            if (ok) out.add(e);
        }
        events.setValue(out);
    }
}
