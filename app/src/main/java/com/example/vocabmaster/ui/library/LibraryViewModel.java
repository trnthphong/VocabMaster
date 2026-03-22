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
    private final LiveData<List<Course>> allCourses;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        repository = new CourseRepository(application);
        allCourses = repository.getAllCourses();
    }

    public LiveData<List<Course>> getAllCourses() {
        return allCourses;
    }

    public void insert(Course course) {
        repository.insertCourse(course);
    }
}