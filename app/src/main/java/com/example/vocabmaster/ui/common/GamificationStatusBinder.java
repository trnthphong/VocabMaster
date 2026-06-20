package com.example.vocabmaster.ui.common;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.gamification.GamificationConstants;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.data.repository.GamificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

public class GamificationStatusBinder {
    private final GamificationRepository repository;
    private final View root;
    private ListenerRegistration listenerRegistration;

    public GamificationStatusBinder(Context context, View root) {
        this.repository = new GamificationRepository(context);
        this.root = root;
    }

    public void start() {
        start(FirebaseAuth.getInstance().getUid());
    }

    public void start(@Nullable String uid) {
        View container = root.findViewById(R.id.gamification_status_container);
        if (uid == null) {
            if (container != null) container.setVisibility(View.GONE);
            return;
        }
        if (container != null) container.setVisibility(View.VISIBLE);

        repository.recoverHeartsIfDue(uid);
        stop();
        listenerRegistration = repository.observeUser(uid, (snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;
            User user = snapshot.toObject(User.class);
            if (user == null) return;
            bindUser(user);
        });
    }

    public void stop() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    private void bindUser(User user) {
        TextView hearts = root.findViewById(R.id.text_status_hearts);
        TextView xp = root.findViewById(R.id.text_status_xp);
        TextView streak = root.findViewById(R.id.text_status_streak);

        if (hearts != null) {
            hearts.setText(user.isActivePremium()
                    ? "\u221E"
                    : String.valueOf(Math.min(user.getHearts(), GamificationConstants.MAX_HEARTS)));
        }
        if (xp != null) {
            xp.setText(String.valueOf(user.getXp()));
        }
        if (streak != null) {
            streak.setText(String.valueOf(user.getStreak()));
        }
    }
}
