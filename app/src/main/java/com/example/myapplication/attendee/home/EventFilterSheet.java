// app/src/main/java/com/example/myapplication/attendee/home/EventFilterSheet.java
package com.example.myapplication.attendee.home;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;

public class EventFilterSheet extends BottomSheetDialogFragment {

    public interface OnApply { void onApply(String city, String category, long from, long to); }

    private OnApply callback; // ✅ không dùng constructor có tham số

    public EventFilterSheet() { /* empty required constructor */ }

    public EventFilterSheet setOnApply(OnApply cb) { // chainable
        this.callback = cb;
        return this;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.view_event_filter, container, false);

        TextInputEditText edtCity = v.findViewById(R.id.edtCity);
        MaterialAutoCompleteTextView autoCat = v.findViewById(R.id.autoCategory);
        Button btnFrom = v.findViewById(R.id.btnFrom);
        Button btnTo   = v.findViewById(R.id.btnTo);
        Button btnApply= v.findViewById(R.id.btnApply);

        final long[] from = {0L};
        final long[] to   = {0L};

        btnFrom.setOnClickListener(_v -> pickDate(btnFrom, from));
        btnTo  .setOnClickListener(_v -> pickDate(btnTo,   to));

        btnApply.setOnClickListener(_v -> {
            String city = edtCity.getText() == null ? null : edtCity.getText().toString().trim();
            String cat  = autoCat.getText() == null ? null : autoCat.getText().toString().trim();
            if (callback != null) callback.onApply(city, cat, from[0], to[0]);
            dismiss();
        });

        return v;
    }

    private void pickDate(Button btn, long[] store) {
        Calendar c = Calendar.getInstance();
        DatePickerDialog d = new DatePickerDialog(requireContext(),
                (datePicker, y, m, day) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(y, m, day, 0, 0, 0);
                    store[0] = cal.getTimeInMillis();
                    btn.setText(day + "/" + (m+1) + "/" + y);
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.show();
    }
}
