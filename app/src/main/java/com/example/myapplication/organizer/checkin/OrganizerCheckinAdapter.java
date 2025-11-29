package com.example.myapplication.organizer.checkin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrganizerCheckinAdapter extends RecyclerView.Adapter<OrganizerCheckinAdapter.VH> {

    private final List<DocumentSnapshot> data = new ArrayList<>();

    public void submit(List<DocumentSnapshot> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_checkin_order, parent, false);
        return new VH(v);
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

        TextView tvOrderId, tvCheckinTime, tvUserInfo, tvTicketSummary, tvSeats, tvTicketDetail;

        VH(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvCheckinTime = itemView.findViewById(R.id.tvCheckinTime);
            tvUserInfo = itemView.findViewById(R.id.tvUserInfo);
            tvTicketSummary = itemView.findViewById(R.id.tvTicketSummary);
            tvSeats = itemView.findViewById(R.id.tvSeats);
            tvTicketDetail = itemView.findViewById(R.id.tvTicketDetail);
        }

        void bind(DocumentSnapshot doc) {
            String orderId = doc.getId();
            String userName = doc.getString("userName");
            String userEmail = doc.getString("userEmail");
            Long totalTickets = doc.getLong("totalTickets");
            Double totalAmount = doc.getDouble("totalAmount");
            Timestamp checkedInAt = doc.getTimestamp("checkedInAt");

            tvOrderId.setText("Mã đơn: " + orderId);

            if (checkedInAt != null) {
                Date d = checkedInAt.toDate();
                String timeStr = new SimpleDateFormat("HH:mm dd/MM/yyyy", new Locale("vi", "VN"))
                        .format(d);
                tvCheckinTime.setText("Check-in lúc: " + timeStr);
            } else {
                tvCheckinTime.setText("Check-in lúc: (không có dữ liệu)");
            }

            // user info
            StringBuilder userSb = new StringBuilder("Khách: ");
            if (userName != null && !userName.isEmpty()) userSb.append(userName);
            else userSb.append("(Không tên)");

            if (userEmail != null && !userEmail.isEmpty()) {
                userSb.append(" (").append(userEmail).append(")");
            }

            tvUserInfo.setText(userSb.toString());

            // tổng vé + tiền
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            String summary = "Số vé: "
                    + (totalTickets != null ? totalTickets : 0)
                    + " • Tổng tiền: "
                    + (totalAmount != null ? nf.format(totalAmount.longValue()) : "0")
                    + " đ";
            tvTicketSummary.setText(summary);

            // seats (nếu có)
            Object seatsObj = doc.get("seats");
            if (seatsObj instanceof List) {
                List<?> seats = (List<?>) seatsObj;
                if (!seats.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Ghế: ");
                    for (int i = 0; i < seats.size(); i++) {
                        Object s = seats.get(i);
                        if (s != null) {
                            sb.append(s.toString());
                            if (i < seats.size() - 1) sb.append(", ");
                        }
                    }
                    tvSeats.setVisibility(View.VISIBLE);
                    tvSeats.setText(sb.toString());
                } else {
                    tvSeats.setVisibility(View.GONE);
                }
            } else {
                tvSeats.setVisibility(View.GONE);
            }

            // chi tiết từng vé: tickets[]
            Object ticketsObj = doc.get("tickets");
            if (ticketsObj instanceof List) {
                List<?> tickets = (List<?>) ticketsObj;
                if (!tickets.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Object t : tickets) {
                        if (!(t instanceof Map)) continue;
                        Map<?, ?> map = (Map<?, ?>) t;

                        Object type = map.get("type");
                        Object label = map.get("label");
                        Object price = map.get("price");

                        sb.append("- ");
                        if (type != null) sb.append(type.toString());
                        if (label != null) sb.append(" (").append(label.toString()).append(")");
                        if (price instanceof Number) {
                            sb.append(" – ")
                                    .append(nf.format(((Number) price).longValue()))
                                    .append(" đ");
                        }
                        sb.append("\n");
                    }
                    tvTicketDetail.setVisibility(View.VISIBLE);
                    tvTicketDetail.setText(sb.toString().trim());
                } else {
                    tvTicketDetail.setVisibility(View.GONE);
                }
            } else {
                tvTicketDetail.setVisibility(View.GONE);
            }
        }
    }
}
