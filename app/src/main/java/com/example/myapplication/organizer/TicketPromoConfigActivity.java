package com.example.myapplication.organizer;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.DatePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.TicketType;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TicketPromoConfigActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "eventId";

    private String eventId;
    private FirebaseFirestore db;

    private MaterialToolbar toolbar;
    private RecyclerView rvTicketPromo;
    private MaterialButton btnSave;

    private final List<TicketType> ticketTypes = new ArrayList<>();
    private TicketPromoAdapter adapter;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ticket_promo_config);

        db = FirebaseFirestore.getInstance();
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) eventId = "";

        toolbar = findViewById(R.id.toolbar);
        rvTicketPromo = findViewById(R.id.rvTicketPromo);
        btnSave = findViewById(R.id.btnSavePromo);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Ưu đãi vé");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new TicketPromoAdapter(ticketTypes, new TicketPromoAdapter.OnPickDate() {
            @Override
            public void onPickDate(int position) {
                showDatePickerFor(position);
            }
        });
        rvTicketPromo.setLayoutManager(new LinearLayoutManager(this));
        rvTicketPromo.setAdapter(adapter);

        btnSave.setOnClickListener(v -> saveAll());

        loadTicketTypes();
    }

    private void loadTicketTypes() {
        if (eventId.isEmpty()) return;

        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    ticketTypes.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketType t = d.toObject(TicketType.class);
                        if (t == null) continue;
                        t.setId(d.getId());
                        ticketTypes.add(t);
                    }
                    adapter.notifyDataSetChanged();

                    if (ticketTypes.isEmpty()) {
                        Toast.makeText(this,
                                "Sự kiện chưa có loại vé nào.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Lỗi tải loại vé: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }

    private void showDatePickerFor(int position) {
        if (position < 0 || position >= ticketTypes.size()) return;

        TicketType t = ticketTypes.get(position);
        Calendar cal = Calendar.getInstance();

        if (t.getEarlyBirdUntil() != null) {
            cal.setTime(t.getEarlyBirdUntil().toDate());
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, month);
                    cal.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    Timestamp ts = new Timestamp(cal.getTime());
                    t.setEarlyBirdUntil(ts);
                    adapter.notifyItemChanged(position);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private void saveAll() {
        if (eventId.isEmpty()) return;

        if (ticketTypes.isEmpty()) {
            Toast.makeText(this, "Không có loại vé để lưu.", Toast.LENGTH_SHORT).show();
            return;
        }

        for (TicketType t : ticketTypes) {
            if (t.getId() == null) continue;

            com.google.firebase.firestore.DocumentReference ref =
                    db.collection("events")
                            .document(eventId)
                            .collection("ticketTypes")
                            .document(t.getId());

            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("earlyBirdPrice", t.getEarlyBirdPrice());
            map.put("earlyBirdUntil", t.getEarlyBirdUntil());
            map.put("memberPrice", t.getMemberPrice());
            map.put("earlyBirdLimit", t.getEarlyBirdLimit());

            ref.update(map);
        }

        Toast.makeText(this, "Đã lưu ưu đãi vé.", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ================== ADAPTER ==================
    public static class TicketPromoAdapter
            extends RecyclerView.Adapter<TicketPromoAdapter.TicketVH> {

        public interface OnPickDate {
            void onPickDate(int position);
        }

        private final List<TicketType> data;
        private final OnPickDate dateListener;
        private final NumberFormat nf =
                NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        public TicketPromoAdapter(List<TicketType> data, OnPickDate dateListener) {
            this.data = data;
            this.dateListener = dateListener;
        }

        @NonNull
        @Override
        public TicketVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ticket_promo, parent, false);
            return new TicketVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull TicketVH holder, int position) {
            holder.bind(data.get(position), dateListener, nf);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class TicketVH extends RecyclerView.ViewHolder {

            private final com.google.android.material.textview.MaterialTextView tvName;
            private final com.google.android.material.textview.MaterialTextView tvBasePrice;

            private final TextInputEditText edtEarlyPrice;
            private final TextInputEditText edtEarlyLimit;
            private final TextInputEditText edtEarlyDate;
            private final TextInputEditText edtMemberPrice;
            private final MaterialButton btnPickDate;

            TicketVH(@NonNull android.view.View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvTicketName);
                tvBasePrice = itemView.findViewById(R.id.tvBasePrice);

                edtEarlyPrice = itemView.findViewById(R.id.edtEarlyBirdPrice);
                edtEarlyLimit = itemView.findViewById(R.id.edtEarlyBirdLimit);
                edtEarlyDate = itemView.findViewById(R.id.edtEarlyBirdDate);
                edtMemberPrice = itemView.findViewById(R.id.edtMemberPrice);
                btnPickDate = itemView.findViewById(R.id.btnPickDate);
            }

            void bind(TicketType t, OnPickDate dateListener, NumberFormat nf) {
                tvName.setText(t.getName());

                String baseStr = nf.format(t.getPrice()) + " đ";
                tvBasePrice.setText(baseStr);

                if (t.getEarlyBirdPrice() != null && t.getEarlyBirdPrice() > 0) {
                    edtEarlyPrice.setText(String.valueOf((long) t.getEarlyBirdPrice().doubleValue()));
                } else {
                    edtEarlyPrice.setText("");
                }

                if (t.getEarlyBirdLimit() != null && t.getEarlyBirdLimit() > 0) {
                    edtEarlyLimit.setText(String.valueOf(t.getEarlyBirdLimit()));
                } else {
                    edtEarlyLimit.setText("");
                }

                if (t.getEarlyBirdUntil() != null) {
                    SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy",
                            new Locale("vi", "VN"));
                    edtEarlyDate.setText(df.format(t.getEarlyBirdUntil().toDate()));
                } else {
                    edtEarlyDate.setText("");
                }

                if (t.getMemberPrice() != null && t.getMemberPrice() > 0) {
                    edtMemberPrice.setText(String.valueOf((long) t.getMemberPrice().doubleValue()));
                } else {
                    edtMemberPrice.setText("");
                }

                edtEarlyPrice.addTextChangedListener(simpleWatcher(s -> {
                    if (s.isEmpty()) {
                        t.setEarlyBirdPrice(null);
                    } else {
                        try {
                            t.setEarlyBirdPrice(Double.parseDouble(s));
                        } catch (NumberFormatException e) {
                            t.setEarlyBirdPrice(null);
                        }
                    }
                }));

                edtEarlyLimit.addTextChangedListener(simpleWatcher(s -> {
                    if (s.isEmpty()) {
                        t.setEarlyBirdLimit(null);
                    } else {
                        try {
                            t.setEarlyBirdLimit(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            t.setEarlyBirdLimit(null);
                        }
                    }
                }));

                edtMemberPrice.addTextChangedListener(simpleWatcher(s -> {
                    if (s.isEmpty()) {
                        t.setMemberPrice(null);
                    } else {
                        try {
                            t.setMemberPrice(Double.parseDouble(s));
                        } catch (NumberFormatException e) {
                            t.setMemberPrice(null);
                        }
                    }
                }));

                edtEarlyDate.setKeyListener(null);
                edtEarlyDate.setOnClickListener(v -> {
                    if (dateListener != null) {
                        dateListener.onPickDate(getBindingAdapterPosition());
                    }
                });

                btnPickDate.setOnClickListener(v -> {
                    if (dateListener != null) {
                        dateListener.onPickDate(getBindingAdapterPosition());
                    }
                });
            }

            private TextWatcher simpleWatcher(SimpleWatcher watcher) {
                return new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        watcher.afterTextChanged(s.toString().trim());
                    }
                };
            }

            interface SimpleWatcher {
                void afterTextChanged(String s);
            }
        }
    }
}
