package com.example.vocabmaster.ui.library;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.repository.CourseRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.List;

public class LibraryViewModel extends AndroidViewModel {
    private final CourseRepository repository;
    private final LiveData<List<Course>> allCoursesLocal;
    private final MutableLiveData<Boolean> courseDeletedEvent = new MutableLiveData<>(false);

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

    public void addCourseAndSync(Course course) {
        repository.addCourseAndSync(course);
    }

    public void addCourseToFirestore(Course course) {
        repository.addCourseToFirestore(course);
    }

    public void updateCourseInFirestore(Course course) {
        repository.updateCourseInFirestore(course);
    }

    public Task<Void> deleteCourseFromFirestore(String firestoreId) {
        return repository.deleteCourseFromFirestore(firestoreId);
    }

    public Task<Void> deleteCoursesFromFirestore(List<Course> courses) {
        List<Task<Void>> tasks = new ArrayList<>();
        for (Course course : courses) {
            if (course.getFirestoreId() != null) {
                tasks.add(repository.deleteCourseFromFirestore(course.getFirestoreId()));
            }
        }
        return Tasks.whenAll(tasks);
    }
    
    public void addPersonalFlashcard(Flashcard flashcard) {
        repository.addPersonalFlashcard(flashcard);
    }

    public LiveData<List<Flashcard>> getPersonalFlashcards() {
        return repository.getPersonalFlashcards();
    }

    public void deleteFlashcard(Flashcard flashcard) {
        repository.deleteFlashcard(flashcard);
    }

    public LiveData<Boolean> getCourseDeletedEvent() {
        return courseDeletedEvent;
    }

    public void notifyCourseDeleted() {
        courseDeletedEvent.setValue(true);
    }

    public void resetCourseDeletedEvent() {
        courseDeletedEvent.setValue(false);
    }
}
