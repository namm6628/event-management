package com.example.myapplication.common;

import com.example.myapplication.common.model.Event;
import java.util.*;

public class DummyData {

    private static final List<Event> EVENTS = new ArrayList<>();

    public static List<Event> getEvents() {
        if (EVENTS.isEmpty()) {
            EVENTS.clear();
            EVENTS.add(new Event("1","Hội chợ Âm nhạc mùa hè","29/10/2025 18:00","Hà Nội",
                    "https://picsum.photos/210","200.000","Âm nhạc"));
            EVENTS.add(new Event("2","Giải bóng đá sinh viên","30/10/2025 15:00","TP. Hồ Chí Minh",
                    "https://picsum.photos/220","150.000","Thể thao"));
            EVENTS.add(new Event("3","Vở kịch: Người tốt thành Tứ Xuyên","31/10/2025 19:30","Đà Nẵng",
                    "https://picsum.photos/230","120.000","Sân khấu"));
            EVENTS.add(new Event("4","Tech Summit HCMC","10/12/2025 09:00","SECC Q7",
                    "https://picsum.photos/240","Miễn phí","Hội thảo"));
            EVENTS.add(new Event("5","Live Music Show","12/11/2025 19:00","Hà Nội",
                    "https://picsum.photos/250","250.000","Âm nhạc"));

        }
        return new ArrayList<>(EVENTS);
    }
}
