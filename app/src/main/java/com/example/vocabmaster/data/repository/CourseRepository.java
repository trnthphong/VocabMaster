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

    public LiveData<List<Course>> getAllCourses() {
        return courseDao.getAllCourses();
    }

    public void insertCourseLocal(Course course) {
        executorService.execute(() -> {
            long id = courseDao.insert(course);
            course.setId((int) id);
            Log.d(TAG, "Course inserted locally with ID: " + id);
        });
    }

    public void updateCourseLocal(Course course) {
        executorService.execute(() -> courseDao.update(course));
    }

    /**
     * Thêm khóa học mới vào Firestore, sau đó lưu ngược lại vào Local Room với ID đồng bộ
     */
    public void addCourseAndSync(Course course) {
        Log.d(TAG, "Attempting to add course to Firestore: " + course.getTitle());
        coursesRef.add(course)
                .addOnSuccessListener(documentReference -> {
                    String firestoreId = documentReference.getId();
                    course.setFirestoreId(firestoreId);
                    // Sau khi có ID từ Firestore, mới lưu vào local Room
                    insertCourseLocal(course);
                    Log.d(TAG, "Successfully pushed to Firestore. ID: " + firestoreId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "FAILED to push to Firestore: " + e.getMessage(), e);
                    // Vẫn lưu local nếu fail network để người dùng không mất dữ liệu
                    insertCourseLocal(course); 
                });
    }

    public void addCourseToFirestore(Course course) {
        coursesRef.add(course)
                .addOnSuccessListener(documentReference -> {
                    String firestoreId = documentReference.getId();
                    course.setFirestoreId(firestoreId);
                    Log.d(TAG, "Course added to Firestore with ID: " + firestoreId);
                    // Cập nhật lại local với firestoreId nếu đã tồn tại local
                    if (course.getId() > 0) {
                        updateCourseLocal(course);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error adding course to Firestore", e));
    }

    public LiveData<List<Course>> getCoursesFromFirestore() {
        MutableLiveData<List<Course>> liveData = new MutableLiveData<>();
        coursesRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<Course> courses = new ArrayList<>();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    try {
                        Course course = document.toObject(Course.class);
                        course.setFirestoreId(document.getId());
                        courses.add(course);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing course: " + document.getId(), e);
                    }
                }
                liveData.setValue(courses);
            } else {
                Log.e(TAG, "Error getting courses from Firestore: ", task.getException());
            }
        });
        return liveData;
    }

    public void updateCourseInFirestore(Course course) {
        if (course.getFirestoreId() != null) {
            coursesRef.document(course.getFirestoreId()).set(course)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Course updated in Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating course in Firestore", e));
        } else {
            Log.w(TAG, "Cannot update Firestore: firestoreId is null");
        }
    }

    public void deleteCourseFromFirestore(String firestoreId) {
        if (firestoreId != null) {
            coursesRef.document(firestoreId).delete()
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Course deleted from Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error deleting course from Firestore", e));
        }
    }

    public void insertFlashcard(Flashcard flashcard) {
        executorService.execute(() -> flashcardDao.insert(flashcard));
    }

    public LiveData<List<Flashcard>> getFlashcardsForCourse(int courseId) {
        return flashcardDao.getFlashcardsByCourse(courseId);
    }
}
