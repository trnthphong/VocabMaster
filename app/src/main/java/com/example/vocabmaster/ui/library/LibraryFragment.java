package com.example.vocabmaster.ui.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.data.model.Course;

import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.example.vocabmaster.ui.home.CreateCourseFlowActivity;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private CourseAdapter adapter;
    private final List<Course> allCourses = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        adapter = new CourseAdapter(course -> {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra("course_id", course.getFirestoreId());
            intent.putExtra("course_title", course.getTitle());
            intent.putExtra("course_theme", course.getTheme());
            startActivity(intent);
        }, this::showCourseActionMenu);
        
        binding.recyclerCourses.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerCourses.setAdapter(adapter);
        
        binding.fabAddCourse.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CreateCourseFlowActivity.class);
            startActivity(intent);
        });

        binding.btnAiCreateCourse.setOnClickListener(v -> createCourseByAi());
        
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterCourses(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterCourses(newText);
                return true;
            }
        });

        observeViewModel();
    }

    private void observeViewModel() {
        binding.progressSkeleton.setVisibility(View.VISIBLE);
        viewModel.getCoursesFromFirestore().observe(getViewLifecycleOwner(), courses -> {
            allCourses.clear();
            String uid = FirebaseAuth.getInstance().getUid();
            for (Course c : courses) {
                if (TextUtils.equals(c.getCreatorId(), uid) || c.isPublic()) {
                    allCourses.add(c);
                }
            }
            if (binding != null) {
                binding.progressSkeleton.setVisibility(View.GONE);
                filterCourses(binding.searchView.getQuery().toString());
            }
        });
    }



    private void refreshData() {
        observeViewModel();
    }

    private void filterCourses(String query) {
        if (binding == null) return;
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            String t = c.getTitle() == null ? "" : c.getTitle().toLowerCase(Locale.ROOT);
            String d = c.getDescription() == null ? "" : c.getDescription().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || t.contains(q) || d.contains(q)) result.add(c);
        }
        adapter.submitList(new ArrayList<>(result));
        binding.layoutEmptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showCourseActionMenu(Course course) {
        String[] actions = {"Edit", "Delete", "Duplicate", "Share"};
        new AlertDialog.Builder(requireContext())
                .setTitle(course.getTitle())
                .setItems(actions, (dialog, which) -> {

                    if (which == 1) deleteCourse(course);
                    if (which == 2) duplicateCourse(course);
                    if (which == 3) shareCourse(course);
                })
                .show();
    }



    private void deleteCourse(Course course) {
        viewModel.deleteCourseFromFirestore(course.getFirestoreId());
        UiFeedback.showSnack(binding.getRoot(), "Study set deleted");
        refreshData();
    }

    private void duplicateCourse(Course course) {
        Course copied = new Course(course.getTitle() + " (copy)", course.getDescription(), course.getTheme(), FirebaseAuth.getInstance().getUid(), false);
        copied.setFlashcardCount(course.getFlashcardCount());
        viewModel.addCourseToFirestore(copied);
        UiFeedback.showSnack(binding.getRoot(), "Study set duplicated");
        refreshData();
    }

    private void shareCourse(Course course) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, "Study set: " + course.getTitle() + "\nDescription: " + course.getDescription());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share study set"));
    }

    private void createCourseByAi() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(snapshot -> {
            Boolean premium = snapshot.getBoolean("premium");
            if (!Boolean.TRUE.equals(premium)) {
                UiFeedback.showErrorDialog(requireContext(), "Premium required", "Upgrade to Premium to use AI generation.");
                return;
            }
            Course ai = new Course("AI Set: TOEIC High-Frequency Vocabulary", "Auto-generated 60 cards for fast review", "Education", uid, false);
            ai.setFlashcardCount(60);
            viewModel.addCourseToFirestore(ai);
            UiFeedback.showSnack(binding.getRoot(), "AI generated a new study set");
            refreshData();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
