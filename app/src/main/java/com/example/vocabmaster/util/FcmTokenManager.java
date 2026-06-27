package com.example.vocabmaster.util;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FcmTokenManager {
    private static final String TAG = "FcmTokenManager";

    private FcmTokenManager() {}

    public static void syncCurrentUserToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(FcmTokenManager::saveTokenForCurrentUser)
                .addOnFailureListener(e -> Log.w(TAG, "Could not get FCM token", e));
    }

    public static void saveTokenForCurrentUser(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || token == null || token.trim().isEmpty()) return;

        Map<String, Object> update = new HashMap<>();
        update.put("fcmToken", token);
        update.put("fcmTokens", FieldValue.arrayUnion(token));
        update.put("fcmTokenUpdatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update(update)
                .addOnFailureListener(e -> Log.w(TAG, "Could not save FCM token", e));
    }
}
