package com.example.myapplication.organizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.Event;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class OrganizerHomeFragment extends Fragment {

    private RecyclerView rv;
    private TextView tvEmpty;
    private OrganizerEventAdapter adapter;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_organizer_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        super.onViewCreated(v, b);

        rv = v.findViewById(R.id.recyclerMyEvents);
        tvEmpty = v.findViewById(R.id.tvEmpty);

        db = FirebaseFirestore.getInstance();

        adapter = new OrganizerEventAdapter(new OrganizerEventAdapter.Listener() {
            @Override
            public void onEdit(@NonNull Event e) {
                if (e.getId() == null) {
                    Toast.makeText(requireContext(), "Event thiếu id", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(requireContext(), CreateEventActivity.class);
                it.putExtra(com.example.myapplication.attendee.detail.EventDetailActivity.EXTRA_EVENT_ID, e.getId());
                startActivity(it);
            }

            @Override
            public void onViewAttendees(@NonNull Event e) {
                if (e.getId() == null) {
                    Toast.makeText(requireContext(), "Event thiếu id", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(requireContext(), OrganizerAttendeesActivity.class);
                it.putExtra(OrganizerAttendeesActivity.EXTRA_EVENT_ID, e.getId());
                startActivity(it);
            }

            @Override
            public void onBroadcast(@NonNull Event e) {
                if (e.getId() == null) {
                    Toast.makeText(requireContext(), "Event thiếu id", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(requireContext(), OrganizerBroadcastActivity.class);
                it.putExtra(OrganizerBroadcastActivity.EXTRA_EVENT_ID, e.getId());
                it.putExtra(OrganizerBroadcastActivity.EXTRA_EVENT_TITLE, e.getTitle());
                startActivity(it);
            }

        });
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);


        View btnCreate = v.findViewById(R.id.btnCreateEvent);
        if (btnCreate != null) {
            btnCreate.setOnClickListener(x ->
                    NavHostFragment.findNavController(this)
                            .navigate(R.id.organizerCreateEventFragment)
            );
        }

        v.findViewById(R.id.btnProfile).setOnClickListener(view ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.organizerProfileFragment)
        );


        loadMyEvents();
    }

    private void loadMyEvents() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Bạn cần đăng nhập lại", Toast.LENGTH_SHORT).show();
            adapter.submit(new ArrayList<>());
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("events")
                .whereEqualTo("ownerId", user.getUid()) // KHÔNG orderBy để tránh lỗi index lúc này
                .get()
                .addOnSuccessListener(snap -> {
                    List<Event> list = new ArrayList<>();
                    for (var doc : snap.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            e.setId(doc.getId());
                            list.add(e);
                        }
                    }
                    adapter.submit(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Không tải được sự kiện: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    tvEmpty.setVisibility(View.VISIBLE);
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMyEvents();   // 👈 quay lại màn là load lại list
    }

}
