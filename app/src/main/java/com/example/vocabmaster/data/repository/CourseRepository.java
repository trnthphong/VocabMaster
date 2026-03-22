package com.example.vocabmaster.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.CourseDao;
import com.example.vocabmaster.data.local.FlashcardDao;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Flashcard;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CourseRepository {
    private final CourseDao courseDao;
    private final FlashcardDao flashcardDao;
    private final ExecutorService executorService;

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
    }

    public LiveData<List<Course>> getAllCourses() {
        return courseDao.getAllCourses();
    }

    public void insertCourse(Course course) {
        executorService.execute(() -> courseDao.insert(course));
    }

    public void insertFlashcard(Flashcard flashcard) {
        executorService.execute(() -> flashcardDao.insert(flashcard));
    }

    public LiveData<List<Flashcard>> getFlashcardsForCourse(int courseId) {
        return flashcardDao.getFlashcardsByCourse(courseId);
    }
}