package com.example.vocabmaster.data.repository;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
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
    private static final String PREFS_NAME = "VocabMasterPrefs";
    private static final String KEY_LAST_USER_ID = "last_user_id";
    
    private final CourseDao courseDao;
    private final FlashcardDao flashcardDao;
    private final ExecutorService executorService;
    private final FirebaseFirestore firestore;
    private final CollectionReference coursesRef;
    private CollectionReference personalFlashcardsRef;
    private CollectionReference personalCoursesRef;
    
    private final MutableLiveData<List<Course>> firestoreCoursesLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Course>> personalCoursesLiveData = new MutableLiveData<>();
    
    private ListenerRegistration coursesListener;
    private ListenerRegistration personalFlashcardsListener;
    private ListenerRegistration personalCoursesListener;

    public CourseRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        courseDao = db.courseDao();
        flashcardDao = db.flashcardDao();
        executorService = Executors.newFixedThreadPool(4);
        firestore = FirebaseFirestore.getInstance();
        coursesRef = firestore.collection("courses");
        
        checkUserChanged(application);
        setupRefs();
        startListeningToCourses();
        startListeningToPersonalFlashcards();
        startListeningToPersonalCourses();
    }

    private void checkUserChanged(Context context) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastUid = prefs.getString(KEY_LAST_USER_ID, null);

        if (currentUid != null && !currentUid.equals(lastUid)) {
            Log.d(TAG, "User changed from " + lastUid + " to " + currentUid + ". Clearing local data.");
            executorService.execute(() -> {
                courseDao.deleteAll();
                flashcardDao.deleteAll();
            });
            prefs.edit().putString(KEY_LAST_USER_ID, currentUid).apply();
        }
    }

    private void setupRefs() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            personalFlashcardsRef = firestore.collection("users").document(uid).collection("personal_flashcards");
            personalCoursesRef = firestore.collection("users").document(uid).collection("personal_courses");
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

        if (personalCoursesListener != null) personalCoursesListener.remove();

        personalCoursesListener = personalCoursesRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e(TAG, "Listen personal courses failed.", error);
                return;
            }
            if (value != null) {
                List<Course> courses = new ArrayList<>();
                for (QueryDocumentSnapshot document : value) {
                    Course course = document.toObject(Course.class);
                    course.setFirestoreId(document.getId());
                    courses.add(course);
                    // Sync to local
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

    public LiveData<List<Course>> getPersonalCoursesFromFirestore() {
        return personalCoursesLiveData;
    }

    public Task<Void> deleteCourseFromFirestore(String firestoreId) {
        if (firestoreId != null) {
            // Check in global first, then personal
            return coursesRef.document(firestoreId).delete()
                    .continueWithTask(task -> {
                        if (personalCoursesRef != null) {
                            return personalCoursesRef.document(firestoreId).delete();
                        }
                        return task;
                    });
        }
        return Tasks.forException(new Exception("Invalid ID"));
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

    public void addCourseAndSync(Course course) {
        addCourseToFirestore(course);
    }
}
