package com.example.vocabmaster;

import android.app.Application;
import android.util.Log;

import com.example.vocabmaster.data.sync.OfflineSyncWorker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class VocabMasterApplication extends Application {
    private static final String TAG = "VocabMasterApp";

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        configureFirestoreOfflineCache();
        OfflineSyncWorker.enqueue(this);
    }

    private void configureFirestoreOfflineCache() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            db.setFirestoreSettings(settings);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Firestore settings were already initialized.", e);
        }
    }
}
