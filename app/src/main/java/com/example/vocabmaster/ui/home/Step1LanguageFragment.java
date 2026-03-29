package com.example.vocabmaster.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.vocabmaster.R;

public class Step1LanguageFragment extends Fragment {
    private RadioGroup radioGroup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_step_language, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        radioGroup = view.findViewById(R.id.radio_group_language);
    }

    public String getSelectedLanguage() {
        int id = radioGroup.getCheckedRadioButtonId();
        if (id != -1) {
            RadioButton rb = getView().findViewById(id);
            return rb.getText().toString();
        }
        return "";
    }
}