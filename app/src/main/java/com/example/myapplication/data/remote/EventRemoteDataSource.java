package com.example.myapplication.data.remote;

import com.example.myapplication.common.Mapper;
import com.example.myapplication.common.model.Event;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventRemoteDataSource {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void fetchAll(Consumer<List<Event>> onOk, Consumer<Exception> onErr){
        db.collection("events").get()
                .addOnSuccessListener(snap -> {
                    List<Event> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap) out.add(Mapper.fromSnapshot(d));
                    onOk.accept(out);
                })
                .addOnFailureListener(onErr::accept);
    }
}
