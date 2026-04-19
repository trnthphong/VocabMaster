package com.example.vocabmaster.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.vocabmaster.data.model.Course;
import com.example.vocabmaster.data.model.Flashcard;
import com.example.vocabmaster.data.model.LearningProfile;
import com.example.vocabmaster.data.model.StudyPlan;
import com.example.vocabmaster.data.model.CourseScheduleDay;
import com.example.vocabmaster.data.model.Vocabulary;

@Database(entities = {Course.class, Flashcard.class, LearningProfile.class, StudyPlan.class, CourseScheduleDay.class, Vocabulary.class}, version = 12, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract CourseDao courseDao();
    public abstract FlashcardDao flashcardDao();
    public abstract LearningProfileDao learningProfileDao();
    public abstract StudyPlanDao studyPlanDao();
    public abstract CourseScheduleDayDao courseScheduleDayDao();
    public abstract VocabularyDao vocabularyDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "vocab_master_db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
