package com.example.myapplication.common;

import com.example.myapplication.common.model.Event;
import java.util.*;

public class DummyData {
    public static List<Event> events(){
        return Arrays.asList(
                new Event("1","Cold Night Live 2025","24 Nov 2025","QK7 Stadium",
                        "https://picsum.photos/1000/600?1","from 299k","Âm nhạc"),
                new Event("2","Tech Summit HCMC","10 Dec 2025","SECC Q7",
                        "https://picsum.photos/1000/600?2","FREE","Hội thảo"),
                new Event("3","Swan Lake Ballet","30 Nov 2025","Saigon Opera House",
                        "https://picsum.photos/1000/600?3","from 499k","Sân khấu"),
                new Event("4","City Marathon","05 Jan 2026","District 1",
                        "https://picsum.photos/1000/600?4","from 199k","Thể thao")
        );
    }
}
