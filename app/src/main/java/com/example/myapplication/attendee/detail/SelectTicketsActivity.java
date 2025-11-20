package com.example.myapplication.attendee.detail;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SelectTicketsActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "EXTRA_EVENT_ID";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private RecyclerView recyclerTickets;
    private TextView tvSummary;
    private MaterialButton btnContinue;
    private TicketSelectAdapter adapter;

    private String eventId;
    private int totalQuantity = 0;
    private double totalPrice = 0d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_tickets);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Toast.makeText(this, "Thiếu ID sự kiện", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerTickets = findViewById(R.id.recyclerTickets);
        tvSummary = findViewById(R.id.tvSummary);
        btnContinue = findViewById(R.id.btnContinue);

        adapter = new TicketSelectAdapter(this::onSelectionChanged);
        recyclerTickets.setLayoutManager(new LinearLayoutManager(this));
        recyclerTickets.setAdapter(adapter);

        btnContinue.setOnClickListener(v -> {
            if (totalQuantity <= 0) {
                Toast.makeText(this, "Vui lòng chọn vé", Toast.LENGTH_SHORT).show();
                return;
            }
            // TODO: ở đây gọi API/Firestore để tạo đơn đặt vé + lưu chi tiết từng loại
            Toast.makeText(this,
                    "Tiếp tục với " + totalQuantity + " vé",
                    Toast.LENGTH_SHORT).show();
        });

        loadTickets();
    }

    private void loadTickets() {
        db.collection("events")
                .document(eventId)
                .collection("ticketTypes")
                .get()
                .addOnSuccessListener(snap -> {
                    List<TicketSelectAdapter.TicketType> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        TicketSelectAdapter.TicketType t = d.toObject(TicketSelectAdapter.TicketType.class);
                        if (t != null) {
                            t.id = d.getId();
                            list.add(t);
                        }
                    }
                    adapter.submit(list);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Không tải được loại vé: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void onSelectionChanged(List<TicketSelectAdapter.TicketType> list) {
        int qty = 0;
        double total = 0d;

        for (TicketSelectAdapter.TicketType t : list) {
            if (t.selected > 0 && t.price != null) {
                qty += t.selected;
                total += t.selected * t.price;
            }
        }

        totalQuantity = qty;
        totalPrice = total;

        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));

        if (qty == 0) {
            tvSummary.setText("Vui lòng chọn vé");
            btnContinue.setText("Tiếp tục");
            btnContinue.setEnabled(false);
        } else {
            String qtyStr = "x" + qty + " vé";
            String priceStr = total <= 0 ? getString(R.string.free) : nf.format(total) + " đ";
            tvSummary.setText(qtyStr);
            btnContinue.setText("Tiếp tục - " + priceStr);
            btnContinue.setEnabled(true);
        }
    }

    // ================== ADAPTER ==================
    private static class TicketSelectAdapter extends RecyclerView.Adapter<TicketSelectAdapter.VH> {

        interface OnSelectionChanged {
            void onChanged(List<TicketType> list);
        }

        static class TicketType {
            public String id;
            public String name;
            public Double price;
            public Long quota;
            public Long sold;
            public int selected = 0;

            public TicketType() {}
        }

        private final List<TicketType> data = new ArrayList<>();
        private final OnSelectionChanged callback;

        TicketSelectAdapter(OnSelectionChanged callback) {
            this.callback = callback;
        }

        void submit(List<TicketType> list) {
            data.clear();
            if (list != null) data.addAll(list);
            notifyDataSetChanged();
            notifyChanged();
        }

        private void notifyChanged() {
            if (callback != null) {
                callback.onChanged(new ArrayList<>(data));
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_select_ticket, null);
            return new VH(v, this);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvPrice, tvSoldOut, tvQty, btnMinus, btnPlus;
            View layoutCounter;

            VH(@NonNull View itemView, TicketSelectAdapter adapter) {
                super(itemView);
                tvName    = itemView.findViewById(R.id.tvTicketName);
                tvPrice   = itemView.findViewById(R.id.tvTicketPrice);
                tvSoldOut = itemView.findViewById(R.id.tvSoldOut);
                tvQty     = itemView.findViewById(R.id.tvQuantity);
                btnMinus  = itemView.findViewById(R.id.btnMinus);
                btnPlus   = itemView.findViewById(R.id.btnPlus);
                layoutCounter = itemView.findViewById(R.id.layoutCounter);

                btnMinus.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    TicketType t = adapter.data.get(pos);
                    if (t.selected > 0) {
                        t.selected--;
                        adapter.notifyItemChanged(pos);
                        adapter.notifyChanged();
                    }
                });

                btnPlus.setOnClickListener(v -> {
                    int pos = getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    TicketType t = adapter.data.get(pos);

                    long quota = t.quota == null ? 0 : t.quota;
                    long sold  = t.sold == null ? 0 : t.sold;
                    long available = quota - sold;

                    if (available <= 0) return;
                    if (t.selected >= available) return;

                    t.selected++;
                    adapter.notifyItemChanged(pos);
                    adapter.notifyChanged();
                });
            }

            void bind(TicketType t) {
                tvName.setText(t.name == null ? "Loại vé" : t.name);

                String priceStr;
                if (t.price == null || t.price == 0d) {
                    priceStr = "Miễn phí";
                } else {
                    priceStr = NumberFormat
                            .getNumberInstance(new Locale("vi", "VN"))
                            .format(t.price) + " đ";
                }
                tvPrice.setText(priceStr);

                long quota = t.quota == null ? 0 : t.quota;
                long sold  = t.sold == null ? 0 : t.sold;
                long available = quota - sold;

                boolean soldOut = (quota > 0 && available <= 0);

                if (soldOut) {
                    tvSoldOut.setVisibility(View.VISIBLE);
                    layoutCounter.setVisibility(View.GONE);
                } else {
                    tvSoldOut.setVisibility(View.GONE);
                    layoutCounter.setVisibility(View.VISIBLE);
                }

                tvQty.setText(String.valueOf(t.selected));
            }
        }
    }
}
