package com.example.myapplication.attendee.ticket;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.util.QrUtils;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TicketAdapter extends ListAdapter<TicketAdapter.TicketItem, TicketAdapter.VH> {

    public interface OnTicketClick {
        void onClick(@NonNull TicketItem item);
    }

    private final OnTicketClick onTicketClick;

    public TicketAdapter(OnTicketClick onTicketClick) {
        super(DIFF);
        this.onTicketClick = onTicketClick;
    }

    private static final DiffUtil.ItemCallback<TicketItem> DIFF =
            new DiffUtil.ItemCallback<TicketItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull TicketItem a, @NonNull TicketItem b) {
                    return a.orderId != null && a.orderId.equals(b.orderId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull TicketItem a, @NonNull TicketItem b) {
                    return a.equals(b);
                }
            };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_ticket_timeline, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), onTicketClick);
    }

    // ================== ViewHolder ==================
    class VH extends RecyclerView.ViewHolder {

        TextView tvDay, tvMonthYear, tvEventTitle,
                tvStatusChip, tvTicketTypeChip,
                tvOrderCode, tvTime, tvVenue,
                tvAddressDetail, tvPrice;

        VH(@NonNull View itemView) {
            super(itemView);
            tvDay = itemView.findViewById(R.id.tvDay);
            tvMonthYear = itemView.findViewById(R.id.tvMonthYear);
            tvEventTitle = itemView.findViewById(R.id.tvEventTitle);
            tvStatusChip = itemView.findViewById(R.id.tvStatusChip);
            tvTicketTypeChip = itemView.findViewById(R.id.tvTicketTypeChip);
            tvOrderCode = itemView.findViewById(R.id.tvOrderCode);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvVenue = itemView.findViewById(R.id.tvVenue);
            tvAddressDetail = itemView.findViewById(R.id.tvAddressDetail);
            tvPrice = itemView.findViewById(R.id.tvPrice);
        }

        void bind(TicketItem item, OnTicketClick onClick) {

            // ===== Title =====
            tvEventTitle.setText(item.title != null ? item.title : "(Sự kiện không tên)");

            // ===== Date column =====
            if (item.startTimeMillis > 0) {
                Date d = new Date(item.startTimeMillis);

                tvDay.setText(new SimpleDateFormat("dd", Locale.getDefault()).format(d));
                tvMonthYear.setText(
                        new SimpleDateFormat("'Tháng' MM\nyyyy", new Locale("vi", "VN"))
                                .format(d)
                );
            }

            // ===== Chips =====
            tvStatusChip.setText("Thành công");


            tvTicketTypeChip.setText("Vé điện tử");


            // Click vào chip "Vé điện tử" -> show QR
            tvTicketTypeChip.setOnClickListener(v -> {
                showQrDialog(v.getContext(), item);
            });

            // ===== Order code =====
            tvOrderCode.setText("Mã đơn hàng: " + item.orderId);

            // ===== Time =====
            String timeText = "";
            if (item.startTimeMillis > 0) {
                Date start = new Date(item.startTimeMillis);

                SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                SimpleDateFormat df = new SimpleDateFormat("dd 'Tháng' MM, yyyy", new Locale("vi", "VN"));

                if (item.endTimeMillis > 0) {
                    Date end = new Date(item.endTimeMillis);
                    timeText = tf.format(start) + " - " + tf.format(end) + ", " + df.format(start);
                } else {
                    timeText = new SimpleDateFormat("HH:mm, dd 'Tháng' MM, yyyy", new Locale("vi", "VN"))
                            .format(start);
                }
            }
            tvTime.setText(timeText);

            // ===== Địa điểm + Địa chỉ chi tiết =====
            if (tvVenue != null && tvAddressDetail != null) {
                boolean hasVenue = item.venue != null && !item.venue.isEmpty();
                boolean hasAddr  = item.addressDetail != null && !item.addressDetail.isEmpty();

                if (hasVenue && hasAddr) {
                    tvVenue.setText(item.venue);
                    tvVenue.setVisibility(View.VISIBLE);

                    tvAddressDetail.setText(item.addressDetail);
                    tvAddressDetail.setVisibility(View.VISIBLE);

                } else if (hasVenue) {
                    tvVenue.setText(item.venue);
                    tvVenue.setVisibility(View.VISIBLE);

                    tvAddressDetail.setText("");
                    tvAddressDetail.setVisibility(View.GONE);

                } else if (hasAddr) {
                    tvVenue.setText(item.addressDetail);
                    tvVenue.setVisibility(View.VISIBLE);

                    tvAddressDetail.setText("");
                    tvAddressDetail.setVisibility(View.GONE);

                } else {
                    tvVenue.setText("");
                    tvVenue.setVisibility(View.GONE);
                    tvAddressDetail.setText("");
                    tvAddressDetail.setVisibility(View.GONE);
                }
            }

            // ===== Price =====
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));

            if (item.ticketPrice > 0) {
                String label = (item.ticketTypeName != null ? item.ticketTypeName + " – " : "");
                tvPrice.setText(label + nf.format(item.ticketPrice) + " đ");
                tvPrice.setVisibility(View.VISIBLE);
            } else if (item.minPrice > 0) {
                tvPrice.setText("Từ " + nf.format(item.minPrice) + " đ");
                tvPrice.setVisibility(View.VISIBLE);
            } else {
                tvPrice.setVisibility(View.GONE);
            }

            // Click cả item -> mở EventDetail
            itemView.setOnClickListener(v -> {
                if (onClick != null) onClick.onClick(item);
            });
        }
    }

    // ================== QR dialog ==================
    private void showQrDialog(Context context, TicketItem item) {
        // Nội dung QR: cho BTC quét check-in
        String qrContent = "eventId=" + item.eventId
                + ";orderId=" + item.orderId
                + ";userId=" + item.userId;

        Bitmap qrBitmap = QrUtils.generateQr(qrContent, 800);
        if (qrBitmap == null) {
            Toast.makeText(context, "Không tạo được mã QR", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_qr_ticket, null, false);

        ImageView imgQr   = dialogView.findViewById(R.id.imgQr);
        TextView tvTitle  = dialogView.findViewById(R.id.tvQrTitle);
        TextView tvSub    = dialogView.findViewById(R.id.tvQrSubtitle);
        TextView tvInfo   = dialogView.findViewById(R.id.tvQrInfo);

        imgQr.setImageBitmap(qrBitmap);
        tvTitle.setText("Vé điện tử");
        tvSub.setText(item.title != null ? item.title : "Sự kiện");
        tvInfo.setText("Mã đơn: " + item.orderId);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.show();

    }

    // ================== Model ==================
    public static class TicketItem {
        public String orderId;
        public String eventId;
        public String userId;        // dùng cho QR
        public String title;

        public String venue;          // địa điểm ngắn
        public String addressDetail;  // địa chỉ chi tiết

        public String ticketTypeName; // tên loại vé
        public long ticketPrice;      // giá đúng loại vé
        public long ticketQuantity;   // 3, 2, 1...

        public long startTimeMillis;
        public long endTimeMillis;

        public long minPrice;

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TicketItem)) return false;
            TicketItem o = (TicketItem) obj;
            return safeEquals(orderId, o.orderId)
                    && safeEquals(eventId, o.eventId)
                    && safeEquals(userId, o.userId)
                    && safeEquals(title, o.title)
                    && safeEquals(venue, o.venue)
                    && safeEquals(addressDetail, o.addressDetail)
                    && safeEquals(ticketTypeName, o.ticketTypeName)
                    && ticketPrice == o.ticketPrice
                    && ticketQuantity == o.ticketQuantity
                    && startTimeMillis == o.startTimeMillis
                    && endTimeMillis == o.endTimeMillis
                    && minPrice == o.minPrice;
        }

        private boolean safeEquals(Object a, Object b) {
            if (a == null) return b == null;
            return a.equals(b);
        }
    }
}
