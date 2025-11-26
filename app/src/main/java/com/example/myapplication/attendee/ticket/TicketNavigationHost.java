package com.example.myapplication.attendee.ticket;

/**
 * Activity nào chứa TicketsFragment và muốn xử lý nút "Mua vé ngay"
 * thì implement interface này, ví dụ: chuyển sang tab Explore.
 */
public interface TicketNavigationHost {
    void onBuyTicketClicked();
}
