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
import com.example.vocabmaster.data.model.Post;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CourseRepository {
    private final CourseDao courseDao;
    private final FlashcardDao flashcardDao;
    private final ExecutorService executorService;
    private final FirebaseFirestore firestore;
    private final CollectionReference coursesRef;
    private final CollectionReference postsRef;
    private CollectionReference personalCoursesRef;
    private CollectionReference personalFlashcardsRef;
    
    private final MutableLiveData<List<Course>> personalCoursesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Course>> firestoreCoursesLiveData = new MutableLiveData<>();

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
        firestore = FirebaseFirestore.getInstance();
        coursesRef = firestore.collection("courses");
        postsRef = firestore.collection("posts");
        
        setupRefs();
        startListeningToPersonalCourses();
    }

    private void setupRefs() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            personalCoursesRef = firestore.collection("users").document(uid).collection("personal_courses");
            personalFlashcardsRef = firestore.collection("users").document(uid).collection("personal_flashcards");
        }
    }

    private void startListeningToPersonalCourses() {
        if (personalCoursesRef == null) setupRefs();
        if (personalCoursesRef == null) return;
        personalCoursesRef.addSnapshotListener((value, error) -> {
            if (value != null) {
                List<Course> courses = new ArrayList<>();
                for (QueryDocumentSnapshot document : value) {
                    Course course = document.toObject(Course.class);
                    course.setFirestoreId(document.getId());
                    courses.add(course);
                    executorService.execute(() -> courseDao.insert(course));
                }
                personalCoursesLiveData.setValue(courses);
            }
        });
    }

    public LiveData<List<Course>> getPersonalCoursesFromFirestore() { return personalCoursesLiveData; }
    public LiveData<List<Course>> getAllCourses() { return courseDao.getAllCourses(); }
    public LiveData<List<Course>> getCoursesFromFirestore() { return firestoreCoursesLiveData; }
    public LiveData<List<Flashcard>> getPersonalFlashcards() { return flashcardDao.getPersonalFlashcards(); }

    public void insertCourseLocal(Course course) { executorService.execute(() -> courseDao.insert(course)); }

    // TÌM KIẾM TOÀN CẦU: Lấy từ collection "courses" dùng chung
    public Task<List<Course>> searchGlobalCourses(String query) {
        return coursesRef.whereEqualTo("isPublic", true)
                .get()
                .continueWith(task -> {
                    List<Course> list = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        String q = query.toLowerCase();
                        for (QueryDocumentSnapshot doc : task.getResult()) {
                            Course c = doc.toObject(Course.class);
                            c.setFirestoreId(doc.getId());
                            if (c.getTitle() != null && c.getTitle().toLowerCase().contains(q)) {
                                list.add(c);
                            }
                        }
                    }
                    return list;
                });
    }

    public Task<Void> shareCoursePublicly(Course course) {
        course.setPublic(true);
        if (course.getFirestoreId() == null) return Tasks.forException(new Exception("Course not synced"));
        // Ghi vào collection chung để mọi tài khoản đều thấy
        return coursesRef.document(course.getFirestoreId()).set(course)
                .continueWithTask(t -> personalCoursesRef.document(course.getFirestoreId()).update("isPublic", true));
    }

    public Task<Void> copyCourseToLibrary(Course course) {
        String uid = FirebaseAuth.getInstance().getUid();
        Course copy = new Course();
        copy.setTitle(course.getTitle() + " (Copy)");
        copy.setCreatorId(uid);
        copy.setPublic(false);
        copy.setFlashcardCount(course.getFlashcardCount());
        copy.setLanguage(course.getLanguage());
        copy.setTargetLanguageId(course.getTargetLanguageId());
        return personalCoursesRef.add(copy).continueWith(t -> null);
    }

    public Task<Void> shareCourseToFeed(Course course, String content, String userName, String userAvatar) {
        String uid = FirebaseAuth.getInstance().getUid();
        Post post = new Post(uid, userName, userAvatar, content, course.getFirestoreId(), course.getTitle(), course.getFlashcardCount());
        return postsRef.add(post).continueWith(t -> null);
    }

    public void addCourseToFirestore(Course course) {
        if (personalCoursesRef != null) {
            personalCoursesRef.add(course).addOnSuccessListener(doc -> {
                course.setFirestoreId(doc.getId());
                personalCoursesRef.document(doc.getId()).update("firestoreId", doc.getId());
            });
        }
    }

    public void updateCourseInFirestore(Course course) {
        if (course.getFirestoreId() != null && personalCoursesRef != null) {
            personalCoursesRef.document(course.getFirestoreId()).set(course);
            if (course.isPublic()) {
                coursesRef.document(course.getFirestoreId()).set(course);
            }
        }
    }

    public void addCourseAndSync(Course course) { addCourseToFirestore(course); }

    public Task<Void> deleteCourseFromFirestore(String id) {
        if (id == null) return Tasks.forResult(null);
        return personalCoursesRef.document(id).delete().continueWithTask(t -> coursesRef.document(id).delete());
    }

    public void addPersonalFlashcard(Flashcard flashcard) {
        if (personalFlashcardsRef != null) {
            personalFlashcardsRef.add(flashcard).addOnSuccessListener(doc -> {
                flashcard.setFirestoreId(doc.getId());
                executorService.execute(() -> flashcardDao.insert(flashcard));
            });
        }
    }

    public void deleteFlashcard(Flashcard flashcard) {
        executorService.execute(() -> flashcardDao.delete(flashcard));
        if (personalFlashcardsRef != null && flashcard.getFirestoreId() != null) {
            personalFlashcardsRef.document(flashcard.getFirestoreId()).delete();
        }
    }
}
