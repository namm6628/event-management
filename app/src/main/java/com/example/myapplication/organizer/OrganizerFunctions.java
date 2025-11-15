package com.example.myapplication.organizer;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

public class OrganizerFunctions {

    private final FirebaseFunctions functions;

    public OrganizerFunctions() {
        // Phải trùng region với Cloud Function: asia-southeast1
        functions = FirebaseFunctions.getInstance("asia-southeast1");
    }

    /**
     * Gọi Cloud Function assignOrganizerRole để gán role "organizer" cho user.
     */
    public Task<Void> assignOrganizerRole(@NonNull String uid) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);

        return functions
                .getHttpsCallable("assignOrganizerRole")
                .call(data)
                .continueWith(task -> {
                    // task.getResult() chứa message từ function nếu cần
                    return null;
                });
    }
}
