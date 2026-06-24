package com.example.vocabmaster.data.repository;

import android.app.Application;

import com.example.vocabmaster.data.local.AppDatabase;
import com.example.vocabmaster.data.local.CourseDao;
import com.example.vocabmaster.data.local.FlashcardDao;
import com.example.vocabmaster.data.local.VocabularyDao;
import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.CourseFlashcardCount;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.StudySet;
import com.example.vocabmaster.data.model.TopicStudySetCount;
import com.example.vocabmaster.data.model.UserProgress;
import com.example.vocabmaster.data.model.Vocabulary;
import com.example.vocabmaster.data.srs.SpacedRepetitionCalculator;
import com.example.vocabmaster.data.srs.SpacedRepetitionConstants;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FlashcardStudyRepository {
    public enum StudySetSource {
        PERSONAL_FLASHCARDS,
        LOCAL_TOPIC,
        LOCAL_COURSE,
        FIRESTORE_STUDY_SET
    }

    public static final class StudySetSummary {
        private final String setId;
        private final String title;
        private final String subtitle;
        private final int totalCards;
        private final StudySetSource source;

        public StudySetSummary(String setId, String title, String subtitle, int totalCards, StudySetSource source) {
            this.setId = setId;
            this.title = title;
            this.subtitle = subtitle;
            this.totalCards = totalCards;
            this.source = source;
        }

        public String getSetId() {
            return setId;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public int getTotalCards() {
            return totalCards;
        }

        public StudySetSource getSource() {
            return source;
        }
    }

    public static final class StudyCard {
        private final Flashcard flashcard;
        private final String cardId;
        private final String setId;
        private final StudySetSource source;
        private final UserProgress progress;

        public StudyCard(Flashcard flashcard, String cardId, String setId, StudySetSource source, UserProgress progress) {
            this.flashcard = flashcard;
            this.cardId = cardId;
            this.setId = setId;
            this.source = source;
            this.progress = progress;
        }

        public Flashcard getFlashcard() {
            return flashcard;
        }

        public String getCardId() {
            return cardId;
        }

        public String getSetId() {
            return setId;
        }

        public StudySetSource getSource() {
            return source;
        }

        public UserProgress getProgress() {
            return progress;
        }
    }

    private final CourseDao courseDao;
    private final FlashcardDao flashcardDao;
    private final VocabularyDao vocabularyDao;
    private final FirestoreRepository firestoreRepository;
    private final OfflineProgressRepository offlineProgressRepository;
    private final FirebaseFirestore firestore;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public FlashcardStudyRepository(Application application) {
        AppDatabase database = AppDatabase.getDatabase(application);
        this.courseDao = database.courseDao();
        this.flashcardDao = database.flashcardDao();
        this.vocabularyDao = database.vocabularyDao();
        this.firestoreRepository = new FirestoreRepository();
        this.offlineProgressRepository = new OfflineProgressRepository(application);
        this.firestore = FirebaseFirestore.getInstance();
    }

    public Task<List<StudySetSummary>> loadAvailableStudySets(String uid) {
        TaskCompletionSource<List<StudySetSummary>> source = new TaskCompletionSource<>();
        executorService.execute(() -> {
            List<StudySetSummary> localSets = buildLocalStudySets();
            firestoreRepository.getMyStudySets(uid)
                    .addOnSuccessListener(snapshot -> {
                        List<StudySetSummary> result = new ArrayList<>(localSets);
                        for (QueryDocumentSnapshot doc : snapshot) {
                            StudySet set = doc.toObject(StudySet.class);
                            if (set == null) continue;
                            String setId = set.getSetId() != null ? set.getSetId() : doc.getId();
                            int cardCount = set.getCardCount();
                            if (cardCount <= 0) cardCount = getInt(doc.getLong("cardCount"));
                            if (cardCount <= 0) continue;
                            result.add(new StudySetSummary(
                                    setId,
                                    nonEmpty(set.getTitle(), "Bộ thẻ"),
                                    "Bộ thẻ đã lưu hoặc tự tạo",
                                    cardCount,
                                    StudySetSource.FIRESTORE_STUDY_SET
                            ));
                        }
                        source.setResult(result);
                    })
                    .addOnFailureListener(e -> source.setResult(localSets));
        });
        return source.getTask();
    }

    public Task<List<StudyCard>> loadDuePersonalSession(String uid) {
        StudySetSummary personal = new StudySetSummary(
                SpacedRepetitionConstants.PERSONAL_FLASHCARDS_SET_ID,
                "Personal Flashcards",
                "Bộ thẻ cá nhân",
                0,
                StudySetSource.PERSONAL_FLASHCARDS
        );
        return loadDueSession(uid, personal);
    }

    public Task<Integer> loadDueStudyCardCount(String uid) {
        TaskCompletionSource<Integer> source = new TaskCompletionSource<>();
        loadAvailableStudySets(uid)
                .addOnSuccessListener(summaries -> {
                    if (summaries == null || summaries.isEmpty()) {
                        source.setResult(0);
                        return;
                    }

                    List<Task<List<StudyCard>>> tasks = new ArrayList<>();
                    for (StudySetSummary summary : summaries) {
                        tasks.add(loadDueSession(uid, summary));
                    }

                    Tasks.whenAllComplete(tasks).addOnSuccessListener(completedTasks -> {
                        int count = 0;
                        for (Task<?> task : completedTasks) {
                            if (task.isSuccessful() && task.getResult() instanceof List) {
                                count += ((List<?>) task.getResult()).size();
                            }
                        }
                        source.setResult(count);
                    }).addOnFailureListener(source::setException);
                })
                .addOnFailureListener(source::setException);
        return source.getTask();
    }

    public Task<List<StudyCard>> loadDueSession(String uid, StudySetSummary summary) {
        TaskCompletionSource<List<StudyCard>> source = new TaskCompletionSource<>();
        loadCardsForSummary(uid, summary)
                .addOnSuccessListener(cards -> loadProgressAndBuildSession(uid, summary, cards, source))
                .addOnFailureListener(source::setException);
        return source.getTask();
    }

    public Task<UserProgress> saveReview(String uid, StudyCard studyCard, SpacedRepetitionCalculator.Rating rating) {
        TaskCompletionSource<UserProgress> source = new TaskCompletionSource<>();
        UserProgress progress = studyCard.getProgress() != null ? studyCard.getProgress() : new UserProgress();
        progress.setUserId(uid);
        progress.setCardId(studyCard.getCardId());
        progress.setSetId(studyCard.getSetId());

        SpacedRepetitionCalculator.ReviewResult result =
                SpacedRepetitionCalculator.calculate(studyCard.getProgress(), rating, System.currentTimeMillis());
        SpacedRepetitionCalculator.applyResult(progress, result);
        progress.setProgressId(uid + "_" + studyCard.getCardId());
        progress.setLastReviewed(Timestamp.now());
        offlineProgressRepository.enqueue(progress);

        firestoreRepository.saveUserProgress(progress)
                .addOnSuccessListener(unused -> source.setResult(progress))
                .addOnFailureListener(e -> source.setResult(progress));
        return source.getTask();
    }

    public StudyCard withProgress(StudyCard studyCard, UserProgress progress) {
        return new StudyCard(
                studyCard.getFlashcard(),
                studyCard.getCardId(),
                studyCard.getSetId(),
                studyCard.getSource(),
                progress
        );
    }

    private List<StudySetSummary> buildLocalStudySets() {
        List<StudySetSummary> result = new ArrayList<>();

        int personalCount = safeSize(flashcardDao.getPersonalFlashcardsSync());
        if (personalCount > 0) {
            result.add(new StudySetSummary(
                    SpacedRepetitionConstants.PERSONAL_FLASHCARDS_SET_ID,
                    "Personal Flashcards",
                    "Bộ thẻ cá nhân",
                    personalCount,
                    StudySetSource.PERSONAL_FLASHCARDS
            ));
        }

        for (TopicStudySetCount topicCount : vocabularyDao.getTopicStudySetCounts()) {
            if (topicCount == null || topicCount.getTopic() == null || topicCount.getCount() <= 0) continue;
            result.add(new StudySetSummary(
                    topicSetId(topicCount.getTopic()),
                    displayName(topicCount.getTopic()),
                    "Bộ từ đã tải hoặc tự tạo",
                    topicCount.getCount(),
                    StudySetSource.LOCAL_TOPIC
            ));
        }

        Map<Integer, Course> coursesById = new HashMap<>();
        for (Course course : courseDao.getAllCoursesSync()) {
            coursesById.put(course.getId(), course);
        }
        for (CourseFlashcardCount courseCount : flashcardDao.getCourseFlashcardCounts()) {
            if (courseCount == null || courseCount.getCount() <= 0) continue;
            Course course = coursesById.get(courseCount.getCourseId());
            String title = course != null ? course.getTitle() : null;
            result.add(new StudySetSummary(
                    courseSetId(courseCount.getCourseId()),
                    nonEmpty(title, "Bộ thẻ đã lưu"),
                    "Bộ thẻ đã lưu hoặc tải về",
                    courseCount.getCount(),
                    StudySetSource.LOCAL_COURSE
            ));
        }
        return result;
    }

    private Task<List<Flashcard>> loadCardsForSummary(String uid, StudySetSummary summary) {
        TaskCompletionSource<List<Flashcard>> source = new TaskCompletionSource<>();
        executorService.execute(() -> {
            switch (summary.getSource()) {
                case PERSONAL_FLASHCARDS:
                    List<Flashcard> personalCards = flashcardDao.getPersonalFlashcardsSync();
                    if (personalCards != null && !personalCards.isEmpty()) {
                        source.setResult(personalCards);
                    } else {
                        loadPersonalCardsFromFirestore(uid, source);
                    }
                    break;
                case LOCAL_TOPIC:
                    source.setResult(vocabulariesToFlashcards(topicFromSetId(summary.getSetId())));
                    break;
                case LOCAL_COURSE:
                    source.setResult(flashcardDao.getFlashcardsByCourseSync(courseIdFromSetId(summary.getSetId())));
                    break;
                case FIRESTORE_STUDY_SET:
                default:
                    loadStudySetCards(summary.getSetId(), source);
                    break;
            }
        });
        return source.getTask();
    }

    private void loadProgressAndBuildSession(String uid, StudySetSummary summary, List<Flashcard> cards,
                                             TaskCompletionSource<List<StudyCard>> source) {
        if (cards == null || cards.isEmpty()) {
            source.setResult(new ArrayList<>());
            return;
        }

        firestore.collection("user_progress")
                .whereEqualTo("userId", uid)
                .whereEqualTo("setId", summary.getSetId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, UserProgress> progressByCard = new HashMap<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        UserProgress progress = doc.toObject(UserProgress.class);
                        if (progress != null && progress.getCardId() != null) {
                            progressByCard.put(progress.getCardId(), progress);
                        }
                    }
                    source.setResult(buildSession(summary, cards, progressByCard, System.currentTimeMillis()));
                })
                .addOnFailureListener(source::setException);
    }

    private List<StudyCard> buildSession(StudySetSummary summary, List<Flashcard> flashcards,
                                         Map<String, UserProgress> progressByCard, long nowMillis) {
        List<StudyCard> due = new ArrayList<>();
        List<StudyCard> fresh = new ArrayList<>();

        for (Flashcard flashcard : flashcards) {
            String cardId = getStableCardId(summary, flashcard);
            UserProgress progress = progressByCard.get(cardId);
            StudyCard studyCard = new StudyCard(flashcard, cardId, summary.getSetId(), summary.getSource(), progress);

            if (progress == null || progress.getNextReview() == null) {
                fresh.add(studyCard);
            } else if (progress.getNextReview().toDate().getTime() <= nowMillis) {
                due.add(studyCard);
            }
        }

        due.sort(Comparator.comparingLong(card -> nextReviewMillis(card.getProgress())));

        List<StudyCard> session = new ArrayList<>();
        for (StudyCard card : due) {
            if (session.size() >= SpacedRepetitionConstants.SESSION_LIMIT) break;
            session.add(card);
        }
        for (StudyCard card : fresh) {
            if (session.size() >= SpacedRepetitionConstants.SESSION_LIMIT) break;
            session.add(card);
        }
        return session;
    }

    private void loadPersonalCardsFromFirestore(String uid, TaskCompletionSource<List<Flashcard>> source) {
        firestore.collection("users").document(uid).collection("personal_flashcards")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Flashcard> cards = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Flashcard card = doc.toObject(Flashcard.class);
                        if (card != null) {
                            card.setFirestoreId(doc.getId());
                            cards.add(card);
                        }
                    }
                    source.setResult(cards);
                })
                .addOnFailureListener(source::setException);
    }

    private void loadStudySetCards(String setId, TaskCompletionSource<List<Flashcard>> source) {
        firestoreRepository.getFlashcards(setId)
                .addOnSuccessListener(snapshot -> {
                    List<Flashcard> cards = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Flashcard card = doc.toObject(Flashcard.class);
                        if (card != null) {
                            card.setFirestoreId(doc.getId());
                            cards.add(card);
                        }
                    }
                    source.setResult(cards);
                })
                .addOnFailureListener(source::setException);
    }

    private List<Flashcard> vocabulariesToFlashcards(String topic) {
        List<Vocabulary> vocabularies = vocabularyDao.getVocabulariesByTopic(topic);
        List<Flashcard> cards = new ArrayList<>();
        for (Vocabulary vocabulary : vocabularies) {
            Flashcard card = new Flashcard(vocabulary.getWord(), vocabulary.getDefinition());
            card.setFirestoreId(vocabulary.getVocabularyId());
            card.setExample(vocabulary.getExample_sentence());
            card.setImageUrl(vocabulary.getImage_url());
            card.setAudioUrl(vocabulary.getAnyAudioUrl());
            card.setPhonetic(vocabulary.getPhonetic());
            card.setTag(displayName(topic));
            cards.add(card);
        }
        return cards;
    }

    private long nextReviewMillis(UserProgress progress) {
        Timestamp nextReview = progress != null ? progress.getNextReview() : null;
        return nextReview != null ? nextReview.toDate().getTime() : 0L;
    }

    private String getStableCardId(StudySetSummary summary, Flashcard flashcard) {
        if (flashcard.getFirestoreId() != null && !flashcard.getFirestoreId().trim().isEmpty()) {
            return flashcard.getFirestoreId();
        }
        return summary.getSetId() + "_local_" + flashcard.getId();
    }

    private int safeSize(List<?> list) {
        return list != null ? list.size() : 0;
    }

    private int getInt(Long value) {
        return value != null ? value.intValue() : 0;
    }

    private String nonEmpty(String value, String fallback) {
        return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
    }

    private String displayName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Bộ từ";
        String spaced = raw.replace("_", " ").replace("-", " ").trim();
        return spaced.substring(0, 1).toUpperCase(Locale.getDefault()) + spaced.substring(1);
    }

    private String topicSetId(String topic) {
        return "topic_" + topic;
    }

    private String topicFromSetId(String setId) {
        return setId != null && setId.startsWith("topic_") ? setId.substring("topic_".length()) : setId;
    }

    private String courseSetId(int courseId) {
        return "course_" + courseId;
    }

    private int courseIdFromSetId(String setId) {
        if (setId == null || !setId.startsWith("course_")) return 0;
        try {
            return Integer.parseInt(setId.substring("course_".length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
