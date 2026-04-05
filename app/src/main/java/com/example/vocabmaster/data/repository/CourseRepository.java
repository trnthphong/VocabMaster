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
    private CollectionReference personalFlashcardsRef;
    
    private final MutableLiveData<List<Course>> firestoreCoursesLiveData = new MutableLiveData<>();
    private ListenerRegistration coursesListener;
    private ListenerRegistration personalFlashcardsListener;

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
        firestore = FirebaseFirestore.getInstance();
        coursesRef = firestore.collection("courses");
        
        setupPersonalFlashcardsRef();
        startListeningToCourses();
        startListeningToPersonalFlashcards();
    }

    private void setupPersonalFlashcardsRef() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            personalFlashcardsRef = firestore.collection("users").document(uid).collection("personal_flashcards");
        }
    }

    private void startListeningToCourses() {
        if (coursesListener != null) return;
        coursesListener = coursesRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen courses failed.", error);
                return;
            }
            if (value != null) {
                List<Course> courses = new ArrayList<>();
                for (QueryDocumentSnapshot document : value) {
                    Course course = document.toObject(Course.class);
                    course.setFirestoreId(document.getId());
                    courses.add(course);
                }
                firestoreCoursesLiveData.setValue(courses);
            }
        });
    }

    private void startListeningToPersonalFlashcards() {
        if (personalFlashcardsRef == null) {
            setupPersonalFlashcardsRef();
        }
        if (personalFlashcardsRef == null || personalFlashcardsListener != null) return;

        personalFlashcardsListener = personalFlashcardsRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen personal flashcards failed.", error);
                return;
            }
            if (value != null) {
                executorService.execute(() -> {
                    for (QueryDocumentSnapshot document : value) {
                        Flashcard firestoreFlashcard = document.toObject(Flashcard.class);
                        firestoreFlashcard.setFirestoreId(document.getId());
                        firestoreFlashcard.setCourseId(-1);

                        // Kiểm tra xem đã tồn tại local chưa dựa trên firestoreId
                        Flashcard existing = flashcardDao.getFlashcardByFirestoreId(firestoreFlashcard.getFirestoreId());
                        if (existing != null) {
                            // Nếu đã tồn tại, cập nhật ID local để Room thực hiện UPDATE thay vì INSERT mới
                            firestoreFlashcard.setId(existing.getId());
                        }
                        flashcardDao.insert(firestoreFlashcard); 
                    }
                });
            }
        });
    }

    public LiveData<List<Course>> getAllCourses() {
        return courseDao.getAllCourses();
    }

    public void insertCourseLocal(Course course) {
        executorService.execute(() -> courseDao.insert(course));
    }

    public LiveData<List<Course>> getCoursesFromFirestore() {
        return firestoreCoursesLiveData;
    }

    public Task<Void> deleteCourseFromFirestore(String firestoreId) {
        if (firestoreId != null) {
            return coursesRef.document(firestoreId).delete();
        }
        return Tasks.forException(new Exception("Invalid ID"));
    }

    public void addPersonalFlashcard(Flashcard flashcard) {
        flashcard.setCourseId(-1);
        // Luôn đồng bộ lên Firebase Firestore trực tiếp
        if (personalFlashcardsRef != null) {
            personalFlashcardsRef.add(flashcard)
                    .addOnSuccessListener(doc -> {
                        flashcard.setFirestoreId(doc.getId());
                        // Sau khi lưu thành công lên Firebase, cập nhật Local (thông qua listener sẽ tự động sync)
                        // Hoặc có thể chèn trực tiếp vào Room ở đây để hiển thị Offline
                        executorService.execute(() -> flashcardDao.insert(flashcard));
                    });
        }
    }

    public LiveData<List<Flashcard>> getPersonalFlashcards() {
        // Trả về từ Room để có LiveData phản hồi nhanh, việc Sync từ Firebase đã được quản lý bởi Listener
        return flashcardDao.getPersonalFlashcards();
    }

    public void deleteFlashcard(Flashcard flashcard) {
        executorService.execute(() -> flashcardDao.delete(flashcard));
        if (personalFlashcardsRef != null && flashcard.getFirestoreId() != null) {
            personalFlashcardsRef.document(flashcard.getFirestoreId()).delete();
        }
    }

    public void updateCourseInFirestore(Course course) {
        if (course.getFirestoreId() != null) {
            coursesRef.document(course.getFirestoreId()).set(course);
        }
    }

    public void addCourseToFirestore(Course course) {
        coursesRef.add(course).addOnSuccessListener(doc -> course.setFirestoreId(doc.getId()));
    }

    public void addCourseAndSync(Course course) {
        addCourseToFirestore(course);
    }
}
