package com.example.vocabmaster.ui.library;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.vocabmaster.R;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.User;
import com.example.vocabmaster.databinding.FragmentLibraryBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryFragment extends Fragment {
    private FragmentLibraryBinding binding;
    private LibraryViewModel viewModel;
    private CourseAdapter adapter;
    private final List<Course> allPersonalCourses = new ArrayList<>();
    private boolean isGlobalSearch = false;
    private User currentUser;

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
        
        loadCurrentUser();
        setupRecyclerView();
        setupSelectionBar();
        
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!TextUtils.isEmpty(query)) {
                    isGlobalSearch = true;
                    binding.progressSkeleton.setVisibility(View.VISIBLE);
                    viewModel.searchGlobal(query);
                }
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                if (TextUtils.isEmpty(newText)) {
                    isGlobalSearch = false;
                    filterLocalCourses("");
                } else {
                    filterLocalCourses(newText);
                }
                return true;
            }
        });

        binding.fabAddCourse.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreateFlashcardActivity.class)));
        binding.layoutPersonalCards.getRoot().setOnClickListener(v -> startActivity(new Intent(requireContext(), PersonalCardsActivity.class)));

        observeViewModel();
    }

    private void loadCurrentUser() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> currentUser = doc.toObject(User.class));
        }
    }

    private void setupRecyclerView() {
        adapter = new CourseAdapter(course -> {
            Intent intent = new Intent(requireContext(), CourseDetailActivity.class);
            intent.putExtra("course_id", course.getFirestoreId());
            intent.putExtra("course_title", course.getTitle());
            boolean isMine = course.getCreatorId() != null && course.getCreatorId().equals(FirebaseAuth.getInstance().getUid());
            intent.putExtra("is_personal", isMine);
            startActivity(intent);
        }, this::showCourseActionMenu, count -> updateSelectionBar(count));
        
        binding.recyclerCourses.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerCourses.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getPersonalCoursesFromFirestore().observe(getViewLifecycleOwner(), courses -> {
            allPersonalCourses.clear();
            if (courses != null) allPersonalCourses.addAll(courses);
            if (!isGlobalSearch) filterLocalCourses(binding.searchView.getQuery().toString());
            binding.progressSkeleton.setVisibility(View.GONE);
        });

        viewModel.getGlobalSearchResults().observe(getViewLifecycleOwner(), courses -> {
            if (isGlobalSearch) {
                binding.progressSkeleton.setVisibility(View.GONE);
                adapter.submitList(new ArrayList<>(courses));
                binding.layoutEmptyState.setVisibility(courses.isEmpty() ? View.VISIBLE : View.GONE);
                if (courses.isEmpty()) {
                    binding.textEmptySubtitle.setText("Không tìm thấy học phần nào khớp với \"" + binding.searchView.getQuery() + "\" trên toàn cầu.");
                }
            }
        });
    }

    private void filterLocalCourses(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Course> result = new ArrayList<>();
        for (Course c : allPersonalCourses) {
            if (q.isEmpty() || (c.getTitle() != null && c.getTitle().toLowerCase().contains(q))) result.add(c);
        }
        adapter.submitList(new ArrayList<>(result));
        binding.layoutEmptyState.setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
        binding.textEmptySubtitle.setText(q.isEmpty() ? "Tạo học phần đầu tiên để bắt đầu" : "Không tìm thấy học phần cá nhân khớp.");
    }

    private void showCourseActionMenu(Course course) {
        String uid = FirebaseAuth.getInstance().getUid();
        boolean isMine = course.getCreatorId() != null && course.getCreatorId().equals(uid);
        
        List<String> options = new ArrayList<>();
        if (isMine) {
            options.add("Sửa");
            options.add("Xóa");
            if (!course.isPublic()) options.add("Chia sẻ công khai");
            options.add("Chia sẻ lên bản tin");
        } else {
            options.add("Sao chép vào thư viện cá nhân");
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(course.getTitle())
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String selected = options.get(which);
                    if (selected.equals("Chia sẻ công khai")) sharePublicly(course);
                    else if (selected.equals("Sao chép vào thư viện cá nhân")) copyToLibrary(course);
                    else if (selected.equals("Chia sẻ lên bản tin")) showShareFeedDialog(course);
                    else if (selected.equals("Xóa")) confirmDelete(course);
                }).show();
    }

    private void sharePublicly(Course course) {
        viewModel.sharePublic(course).addOnSuccessListener(aVoid -> 
            Toast.makeText(getContext(), "Học phần này hiện đã công khai cho mọi người!", Toast.LENGTH_SHORT).show());
    }

    private void copyToLibrary(Course course) {
        viewModel.copyToLibrary(course).addOnSuccessListener(aVoid -> 
            Toast.makeText(getContext(), "Đã sao chép thành công vào thư viện của bạn", Toast.LENGTH_SHORT).show());
    }

    private void showShareFeedDialog(Course course) {
        View view = getLayoutInflater().inflate(R.layout.dialog_share_feed, null);
        EditText editContent = view.findViewById(R.id.edit_post_content);
        new AlertDialog.Builder(requireContext())
                .setTitle("Chia sẻ lên Bản tin")
                .setView(view)
                .setPositiveButton("Đăng", (dialog, which) -> {
                    String content = editContent.getText().toString();
                    String name = currentUser != null ? currentUser.getName() : "Người dùng";
                    String avatar = currentUser != null ? currentUser.getAvatar() : "bear";
                    viewModel.shareToFeed(course, content, name, avatar).addOnSuccessListener(v -> 
                        Toast.makeText(getContext(), "Đã chia sẻ học phần lên bản tin!", Toast.LENGTH_SHORT).show());
                }).setNegativeButton("Hủy", null).show();
    }

    private void confirmDelete(Course course) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa học phần?")
                .setMessage("Bạn có chắc muốn xóa \"" + course.getTitle() + "\"?")
                .setPositiveButton("Xóa", (d, w) -> viewModel.deleteCourseFromFirestore(course.getFirestoreId()))
                .setNegativeButton("Hủy", null).show();
    }

    private void updateSelectionBar(int count) {
        binding.cardSelectionBar.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.layoutBottomActions.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
        binding.textSelectionCount.setText(count + " học phần được chọn");
    }

    private void setupSelectionBar() {
        binding.btnCancelSelection.setOnClickListener(v -> adapter.setSelectionMode(false));
        binding.btnDeleteSelected.setOnClickListener(v -> {
            List<Course> selected = adapter.getSelectedCourses();
            if (!selected.isEmpty()) {
                for (Course c : selected) viewModel.deleteCourseFromFirestore(c.getFirestoreId());
                adapter.setSelectionMode(false);
            }
        });
    }
}
