package com.example.vocabmaster.ui.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.remote.AIService;
import com.example.vocabmaster.databinding.DialogCreateFlashcardBinding;
import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.example.vocabmaster.ui.common.UiFeedback;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private AIService aiService;
    private CourseAdapter adapter;
    private final List<Course> allCourses = new ArrayList<>();
    
    private Uri selectedImageUri;
    private ImageView dialogImagePreview;
    private View dialogImagePrompt;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (dialogImagePreview != null) {
                        dialogImagePreview.setImageURI(selectedImageUri);
                        dialogImagePreview.setVisibility(View.VISIBLE);
                        dialogImagePrompt.setVisibility(View.GONE);
                    }
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(LibraryViewModel.class);
        aiService = new AIService("AIzaSyDEw4mFQqqOzwXDlYoMDwecIURPNkdO4Ko");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        setupSelectionBar();
        
        binding.fabAddCourse.setOnClickListener(v -> showCreateFlashcardDialog());
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

        binding.layoutPersonalCards.getRoot().setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), PersonalCardsActivity.class);
            startActivity(intent);
        });

        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new CourseAdapter(course -> {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra("course_id", course.getFirestoreId());
            intent.putExtra("course_title", course.getTitle());
            startActivity(intent);
        }, this::showCourseActionMenu, count -> {
            updateSelectionBar(count);
        });
        
        binding.recyclerCourses.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerCourses.setAdapter(adapter);
    }

    private void setupSelectionBar() {
        binding.btnCancelSelection.setOnClickListener(v -> {
            adapter.setSelectionMode(false);
        });

        binding.btnDeleteSelected.setOnClickListener(v -> {
            List<Course> selected = adapter.getSelectedCourses();
            if (!selected.isEmpty()) {
                confirmDeleteMultiple(selected);
            }
        });
    }

    private void updateSelectionBar(int count) {
        if (count > 0) {
            binding.cardSelectionBar.setVisibility(View.VISIBLE);
            binding.layoutBottomActions.setVisibility(View.GONE);
            binding.fabAddCourse.hide();
            binding.textSelectionCount.setText(count + " selected");
        } else {
            binding.cardSelectionBar.setVisibility(View.GONE);
            binding.layoutBottomActions.setVisibility(View.VISIBLE);
            binding.fabAddCourse.show();
            if (adapter.isSelectionMode()) {
                adapter.setSelectionMode(false);
            }
        }
    }

    private void confirmDeleteMultiple(List<Course> courses) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete " + courses.size() + " courses?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteCoursesFromFirestore(courses).addOnSuccessListener(aVoid -> {
                        UiFeedback.showSnack(binding.getRoot(), "Deleted " + courses.size() + " courses");
                        adapter.setSelectionMode(false);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCreateFlashcardDialog() {
        DialogCreateFlashcardBinding dialogBinding = DialogCreateFlashcardBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogBinding.getRoot())
                .create();

        dialogImagePreview = dialogBinding.imagePreview;
        dialogImagePrompt = dialogBinding.layoutAddImagePrompt;
        selectedImageUri = null;

        dialogBinding.cardSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        dialogBinding.btnAiGenerateImage.setOnClickListener(v -> {
            String term = dialogBinding.editTerm.getText().toString().trim();
            String definition = dialogBinding.editDefinition.getText().toString().trim();
            
            if (TextUtils.isEmpty(term)) {
                Toast.makeText(requireContext(), "Vui lòng nhập từ vựng trước", Toast.LENGTH_SHORT).show();
                return;
            }

            dialogBinding.progressImage.setVisibility(View.VISIBLE);
            dialogBinding.btnAiGenerateImage.setEnabled(false);

            aiService.generateImageFromText(term, definition, new AIService.ImageGenerationCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        dialogBinding.progressImage.setVisibility(View.GONE);
                        dialogBinding.btnAiGenerateImage.setEnabled(true);
                        
                        selectedImageUri = Uri.parse(imageUrl);
                        
                        Glide.with(LibraryFragment.this)
                                .load(imageUrl)
                                .centerCrop()
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.stat_notify_error)
                                .into(dialogImagePreview);
                                
                        dialogImagePreview.setVisibility(View.VISIBLE);
                        dialogImagePrompt.setVisibility(View.GONE);
                    });
                }

                @Override
                public void onError(Throwable t) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        dialogBinding.progressImage.setVisibility(View.GONE);
                        dialogBinding.btnAiGenerateImage.setEnabled(true);
                        Toast.makeText(requireContext(), "Lỗi AI: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        dialogBinding.btnSaveFlashcard.setOnClickListener(v -> {
            String term = dialogBinding.editTerm.getText().toString().trim();
            String definition = dialogBinding.editDefinition.getText().toString().trim();
            String tag = dialogBinding.editTag.getText().toString().trim();

            if (TextUtils.isEmpty(term) || TextUtils.isEmpty(definition)) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            Flashcard card = new Flashcard(term, definition);
            card.setTag(tag);
            if (selectedImageUri != null) {
                card.setImageUrl(selectedImageUri.toString());
            }
            
            viewModel.addPersonalFlashcard(card);
            UiFeedback.showSnack(binding.getRoot(), "Đã lưu Flashcard mới");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void observeViewModel() {
        if (binding == null) return;
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

        viewModel.getPersonalFlashcards().observe(getViewLifecycleOwner(), flashcards -> {
            if (flashcards != null && !flashcards.isEmpty()) {
                binding.layoutPersonalCards.getRoot().setVisibility(View.VISIBLE);
                TextView textCount = binding.layoutPersonalCards.getRoot().findViewById(com.example.vocabmaster.R.id.text_personal_count);
                if (textCount != null) {
                    textCount.setText(flashcards.size() + " cards");
                }
            } else {
                binding.layoutPersonalCards.getRoot().setVisibility(View.GONE);
            }
        });
    }

    private void filterCourses(String query) {
        if (binding == null) return;
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Course> result = new ArrayList<>();
        for (Course c : allCourses) {
            String t = c.getTitle() == null ? "" : c.getTitle().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || t.contains(q)) result.add(c);
        }
        adapter.submitList(new ArrayList<>(result));
        
        boolean noCourses = result.isEmpty();
        boolean noPersonal = viewModel.getPersonalFlashcards().getValue() == null || viewModel.getPersonalFlashcards().getValue().isEmpty();
        binding.layoutEmptyState.setVisibility(noCourses && noPersonal ? View.VISIBLE : View.GONE);
        
        if (!q.isEmpty()) {
            binding.layoutPersonalCards.getRoot().setVisibility(View.GONE);
        } else if (viewModel.getPersonalFlashcards().getValue() != null && !viewModel.getPersonalFlashcards().getValue().isEmpty()) {
            binding.layoutPersonalCards.getRoot().setVisibility(View.VISIBLE);
        }
    }

    private void showCourseActionMenu(Course course) {
        String[] actions = {"Sửa", "Xóa", "Nhân bản"};
        new AlertDialog.Builder(requireContext())
                .setTitle(course.getTitle())
                .setItems(actions, (dialog, which) -> {
                    if (which == 1) confirmDeleteCourse(course);
                })
                .show();
    }

    private void confirmDeleteCourse(Course course) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa khóa học")
                .setPositiveButton("Xóa", (dialog, which) -> deleteCourse(course))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteCourse(Course course) {
        viewModel.deleteCourseFromFirestore(course.getFirestoreId());
        UiFeedback.showSnack(binding.getRoot(), "Đã xóa");
    }

    private void createCourseByAi() {
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
