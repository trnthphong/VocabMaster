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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CourseRepository {
    private static final String TAG = "CourseRepository";
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
    
    private ListenerRegistration coursesListener;
    private ListenerRegistration personalCoursesListener;

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
        firestore = FirebaseFirestore.getInstance();
        coursesRef = firestore.collection("courses");
        postsRef = firestore.collection("posts");
        
        setupRefs();
        startListeningToCourses();
        startListeningToPersonalCourses();
    }

    private void setupRefs() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            personalCoursesRef = firestore.collection("users").document(uid).collection("personal_courses");
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

    private void startListeningToPersonalCourses() {
        if (personalCoursesRef == null) setupRefs();
        if (personalCoursesRef == null) return;
        personalCoursesListener = personalCoursesRef.addSnapshotListener((value, error) -> {
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

    private void startListeningToPersonalFlashcards() {
        if (personalFlashcardsRef == null) setupRefs();
        if (personalFlashcardsRef == null) return;
        
        if (personalFlashcardsListener != null) personalFlashcardsListener.remove();

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

                        Flashcard existing = flashcardDao.getFlashcardByFirestoreId(firestoreFlashcard.getFirestoreId());
                        if (existing != null) {
                            firestoreFlashcard.setId(existing.getId());
                            
                            // Bảo toàn trạng thái học tập cục bộ nếu Firestore chưa cập nhật
                            if (!firestoreFlashcard.isMastered() && existing.isMastered()) {
                                firestoreFlashcard.setMastered(true);
                            }
                            if (firestoreFlashcard.getLastReviewTime() == 0 && existing.getLastReviewTime() > 0) {
                                firestoreFlashcard.setLastReviewTime(existing.getLastReviewTime());
                                firestoreFlashcard.setNextReviewAt(existing.getNextReviewAt());
                                firestoreFlashcard.setInterval(existing.getInterval());
                            }
                        }
                        flashcardDao.insert(firestoreFlashcard); 
                    }
                }
            }
            return list;
        });
    }

    // CHIA SẺ CÔNG KHAI (Sao chép cả Units/Lessons/Challenges lên Global)
    public Task<Void> shareCoursePublicly(Course course) {
        if (course.getFirestoreId() == null) return Tasks.forException(new Exception("ID missing"));
        course.setPublic(true);
        
        return coursesRef.document(course.getFirestoreId()).set(course).continueWithTask(t -> {
            // Lấy Units từ cá nhân -> đẩy lên Global root collection "units"
            return personalCoursesRef.document(course.getFirestoreId()).collection("units").get()
                    .continueWithTask(unitTask -> {
                        List<Task<Void>> tasks = new ArrayList<>();
                        for (DocumentSnapshot unitDoc : unitTask.getResult()) {
                            Map<String, Object> uData = unitDoc.getData();
                            if (uData != null) {
                                uData.put("courseId", course.getFirestoreId());
                                tasks.add(firestore.collection("units").document(unitDoc.getId()).set(uData));
                                
                                // Lấy Lessons từ cá nhân -> đẩy lên Global root collection "lessons"
                                tasks.add(unitDoc.getReference().collection("lessons").get().continueWithTask(lessonTask -> {
                                    List<Task<Void>> lTasks = new ArrayList<>();
                                    for (DocumentSnapshot lessonDoc : lessonTask.getResult()) {
                                        Map<String, Object> lData = lessonDoc.getData();
                                        if (lData != null) {
                                            lData.put("unitId", unitDoc.getId());
                                            lTasks.add(firestore.collection("lessons").document(lessonDoc.getId()).set(lData));
                                            
                                            // Lấy Challenges từ cá nhân -> đẩy lên Global root collection "challenges"
                                            lTasks.add(lessonDoc.getReference().collection("challenges").get().continueWithTask(cTask -> {
                                                List<Task<Void>> cSubTasks = new ArrayList<>();
                                                for (DocumentSnapshot cDoc : cTask.getResult()) {
                                                    Map<String, Object> cData = cDoc.getData();
                                                    if (cData != null) {
                                                        cData.put("lessonId", lessonDoc.getId());
                                                        cSubTasks.add(firestore.collection("challenges").document(cDoc.getId()).set(cData));
                                                    }
                                                }
                                                return Tasks.whenAll(cSubTasks);
                                            }));
                                        }
                                    }
                                    return Tasks.whenAll(lTasks);
                                }));
                            }
                        }
                        return Tasks.whenAll(tasks);
                    });
        }).continueWithTask(t -> personalCoursesRef.document(course.getFirestoreId()).update("isPublic", true));
    }

    public void addPersonalFlashcard(Flashcard flashcard) {
        flashcard.setCourseId(-1);
        if (personalFlashcardsRef != null) {
            personalFlashcardsRef.add(flashcard)
                    .addOnSuccessListener(doc -> {
                        flashcard.setFirestoreId(doc.getId());
                        executorService.execute(() -> flashcardDao.insert(flashcard));
                    });
        }
    }

    public LiveData<List<Flashcard>> getPersonalFlashcards() {
        return flashcardDao.getPersonalFlashcards();
    }

    public void updateFlashcard(Flashcard flashcard) {
        executorService.execute(() -> flashcardDao.update(flashcard));
        if (personalFlashcardsRef != null && flashcard.getFirestoreId() != null) {
            personalFlashcardsRef.document(flashcard.getFirestoreId()).set(flashcard);
        }
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
            if (personalCoursesRef != null) {
                personalCoursesRef.document(course.getFirestoreId()).set(course);
            }
        }
    }

    public void addCourseToFirestore(Course course) {
        if (personalCoursesRef != null) {
            personalCoursesRef.add(course).addOnSuccessListener(doc -> course.setFirestoreId(doc.getId()));
        } else {
            coursesRef.add(course).addOnSuccessListener(doc -> course.setFirestoreId(doc.getId()));
        }
    }

    public Task<Void> shareCourseToFeed(Course course, String content, String userName, String userAvatar) {
        String uid = FirebaseAuth.getInstance().getUid();
        Post post = new Post(uid, userName, userAvatar, content, course.getFirestoreId(), course.getTitle(), course.getFlashcardCount());
        return postsRef.add(post).continueWith(t -> null);
    }
    
    public Task<Void> copyCourseToLibrary(Course course) { return copyCourseById(course.getFirestoreId()); }
    public void addCourseToFirestore(Course course) { if (personalCoursesRef != null) personalCoursesRef.add(course).addOnSuccessListener(doc -> { course.setFirestoreId(doc.getId()); personalCoursesRef.document(doc.getId()).update("firestoreId", doc.getId()); }); }
    public void updateCourseInFirestore(Course course) { if (course.getFirestoreId() != null) personalCoursesRef.document(course.getFirestoreId()).set(course); }
    public void addCourseAndSync(Course course) { addCourseToFirestore(course); }
    public void addPersonalFlashcard(Flashcard flashcard) { personalFlashcardsRef.add(flashcard).addOnSuccessListener(doc -> { flashcard.setFirestoreId(doc.getId()); executorService.execute(() -> flashcardDao.insert(flashcard)); }); }
    public void deleteFlashcard(Flashcard flashcard) { executorService.execute(() -> flashcardDao.delete(flashcard)); if (flashcard.getFirestoreId() != null) personalFlashcardsRef.document(flashcard.getFirestoreId()).delete(); }
    public Task<Void> deleteCourseFromFirestore(String id) { return personalCoursesRef.document(id).delete(); }
}
