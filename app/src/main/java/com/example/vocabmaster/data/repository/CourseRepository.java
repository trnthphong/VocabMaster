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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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
    private final CollectionReference personalFlashcardsRef;
    
    private final MutableLiveData<List<Course>> firestoreCoursesLiveData = new MutableLiveData<>();
    private ListenerRegistration coursesListener;

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
        firestore = FirebaseFirestore.getInstance();
        coursesRef = firestore.collection("courses");
        
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            personalFlashcardsRef = firestore.collection("users").document(uid).collection("personal_flashcards");
        } else {
            personalFlashcardsRef = null;
        }
        
        startListeningToCourses();
    }

    private void startListeningToCourses() {
        if (coursesListener != null) return;
        
        coursesListener = coursesRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen failed.", error);
                return;
            }

            if (value != null) {
                List<Course> courses = new ArrayList<>();
                for (QueryDocumentSnapshot document : value) {
                    try {
                        Course course = document.toObject(Course.class);
                        course.setFirestoreId(document.getId());
                        courses.add(course);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing course: " + document.getId(), e);
                    }
                }
                firestoreCoursesLiveData.setValue(courses);
            }
        });
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

    public void addCourseAndSync(Course course) {
        coursesRef.add(course)
                .addOnSuccessListener(documentReference -> {
                    String firestoreId = documentReference.getId();
                    course.setFirestoreId(firestoreId);
                    insertCourseLocal(course);
                })
                .addOnFailureListener(e -> insertCourseLocal(course));
    }

    public void addCourseToFirestore(Course course) {
        coursesRef.add(course)
                .addOnSuccessListener(documentReference -> {
                    String firestoreId = documentReference.getId();
                    course.setFirestoreId(firestoreId);
                    if (course.getId() > 0) {
                        updateCourseLocal(course);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error adding course to Firestore", e));
    }

    public LiveData<List<Course>> getCoursesFromFirestore() {
        return firestoreCoursesLiveData;
    }

    public void updateCourseInFirestore(Course course) {
        if (course.getFirestoreId() != null) {
            coursesRef.document(course.getFirestoreId()).set(course)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Course updated in Firestore"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error updating course in Firestore", e));
        }
    }

    public Task<Void> deleteCourseFromFirestore(String firestoreId) {
        if (firestoreId != null) {
            return coursesRef.document(firestoreId).delete();
        }
        return Tasks.forException(new Exception("Invalid Course ID"));
    }

    public void insertFlashcard(Flashcard flashcard) {
        executorService.execute(() -> {
            flashcardDao.insert(flashcard);
            Log.d(TAG, "Flashcard inserted locally: " + flashcard.getTerm());
        });
    }

    public void addPersonalFlashcard(Flashcard flashcard) {
        Log.d(TAG, "Adding personal flashcard: " + flashcard.getTerm());
        flashcard.setCourseId(-1); // Luôn đặt là Personal
        
        // Bước 1: Lưu Local ngay lập tức để UI cập nhật
        insertFlashcard(flashcard);
        
        // Bước 2: Thử đồng bộ lên Firestore (nếu có mạng và không hết quota)
        if (personalFlashcardsRef != null) {
            personalFlashcardsRef.add(flashcard)
                    .addOnSuccessListener(documentReference -> {
                        Log.d(TAG, "Flashcard synced to Firestore: " + documentReference.getId());
                        flashcard.setFirestoreId(documentReference.getId());
                        executorService.execute(() -> flashcardDao.update(flashcard));
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Firestore sync failed (expected if quota exceeded)", e));
        }
    }

    public LiveData<List<Flashcard>> getFlashcardsForCourse(int courseId) {
        return flashcardDao.getFlashcardsByCourse(courseId);
    }

    public LiveData<List<Flashcard>> getPersonalFlashcards() {
        return flashcardDao.getPersonalFlashcards();
    }
}
