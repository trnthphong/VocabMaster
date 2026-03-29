package com.example.vocabmaster.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.R;

public class Step2ThemeFragment extends Fragment {
    private String selectedTheme = "";
    private View lastSelectedView = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step_theme, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        setupThemeClick(view.findViewById(R.id.chip_career), "Nghề nghiệp");
        setupThemeClick(view.findViewById(R.id.chip_school), "Trường học");
        setupThemeClick(view.findViewById(R.id.chip_culture), "Văn hóa");
        setupThemeClick(view.findViewById(R.id.chip_travel), "Du lịch");
        setupThemeClick(view.findViewById(R.id.chip_food), "Thức ăn");
        setupThemeClick(view.findViewById(R.id.chip_brain), "Luyện trí não");
    }

    private void setupThemeClick(View view, String theme) {
        view.setOnClickListener(v -> {
            if (lastSelectedView != null) {
                lastSelectedView.setBackgroundResource(R.drawable.bg_toggle_pill);
            }
            view.setBackgroundResource(R.drawable.button_primary); // Giả sử dùng màu primary khi chọn
            selectedTheme = theme;
            lastSelectedView = view;
        });
    }

    public String getSelectedTheme() {
        return selectedTheme;
    }
}