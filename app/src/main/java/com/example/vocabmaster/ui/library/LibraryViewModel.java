package com.example.vocabmaster.ui.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.repository.CourseRepository;

import java.util.List;

public class LibraryViewModel extends AndroidViewModel {
    private final CourseRepository repository;
    private final LiveData<List<Course>> allCoursesLocal;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        repository = new CourseRepository(application);
        allCoursesLocal = repository.getAllCourses();
    }

    // Local Room methods
    public LiveData<List<Course>> getAllCoursesLocal() {
        return allCoursesLocal;
    }

    public void insertLocal(Course course) {
        repository.insertCourseLocal(course);
    }

    // Firebase Firestore methods
    public LiveData<List<Course>> getCoursesFromFirestore() {
        return repository.getCoursesFromFirestore();
    }

    /**
     * Đồng bộ khóa học lên Firestore sau đó mới lưu vào Local
     */
    public void addCourseAndSync(Course course) {
        repository.addCourseAndSync(course);
    }

    public void addCourseToFirestore(Course course) {
        repository.addCourseToFirestore(course);
    }

    public void updateCourseInFirestore(Course course) {
        repository.updateCourseInFirestore(course);
    }

    public void deleteCourseFromFirestore(String firestoreId) {
        repository.deleteCourseFromFirestore(firestoreId);
    }
}
