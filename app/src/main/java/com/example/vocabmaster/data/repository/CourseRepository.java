package com.example.vocabmaster.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.CourseDao;
import com.example.vocabmaster.data.local.FlashcardDao;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Flashcard;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CourseRepository {
    private static final String TAG = "CourseRepository";
    private final CourseDao courseDao;
    private final FlashcardDao flashcardDao;
    private final ExecutorService executorService;
    private final FirebaseFirestore firestore;
    private final CollectionReference coursesRef;

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
        firestore = FirebaseFirestore.getInstance();
        coursesRef = firestore.collection("courses");
    }

    // --- Local Room DB methods ---
    public LiveData<List<Course>> getAllCourses() {
        return courseDao.getAllCourses();
    }

    public void insertCourseLocal(Course course) {
        executorService.execute(() -> courseDao.insert(course));
    }

    // --- Firebase Firestore CRUD methods ---

    public void addCourseToFirestore(Course course) {
        coursesRef.add(course)
                .addOnSuccessListener(documentReference -> {
                    String id = documentReference.getId();
                    course.setFirestoreId(id);
                    // Update local if needed or sync back
                    Log.d(TAG, "Course added to Firestore with ID: " + id);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error adding course", e));
    }

    public LiveData<List<Course>> getCoursesFromFirestore() {
        MutableLiveData<List<Course>> liveData = new MutableLiveData<>();
        coursesRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Course> courses = new ArrayList<>();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Course course = document.toObject(Course.class);
                    course.setFirestoreId(document.getId());
                    courses.add(course);
                }
                liveData.setValue(courses);
            } else {
                Log.e(TAG, "Error getting courses: ", task.getException());
            }
        });
        return liveData;
    }

    public void updateCourseInFirestore(Course course) {
        if (course.getFirestoreId() != null) {
            coursesRef.document(course.getFirestoreId()).set(course)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Course updated in Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating course", e));
        }
    }

    public void deleteCourseFromFirestore(String firestoreId) {
        if (firestoreId != null) {
            coursesRef.document(firestoreId).delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Course deleted from Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error deleting course", e));
        }
    }

    // --- Flashcards ---
    public void insertFlashcard(Flashcard flashcard) {
        executorService.execute(() -> flashcardDao.insert(flashcard));
    }

    public LiveData<List<Flashcard>> getFlashcardsForCourse(int courseId) {
        return flashcardDao.getFlashcardsByCourse(courseId);
    }
}