const express = require("express");
const dotenv = require("dotenv");
const cors = require("cors");
const path = require("path");
const { GoogleGenerativeAI } = require("@google/generative-ai");

dotenv.config();
dotenv.config({ path: path.join(__dirname, ".env.local"), override: true });

const app = express();
app.use(cors());
app.use(express.json());

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY || "");

const LANGUAGE_LABELS = {
  english: "English",
  japanese: "Japanese",
  russian: "Russian",
  chinese: "Chinese",
};

const LEVEL_LABELS = {
  A1: "Beginner",
  A2: "Elementary",
  B1: "Intermediate",
  B2: "Upper Intermediate",
  beginner: "Beginner",
  elementary: "Elementary",
  intermediate: "Intermediate",
  advanced: "Advanced",
};

const TOPIC_BANK = {
  Career: {
    themes: ["workplace communication", "meetings", "emails", "interviews"],
    words: ["resume", "deadline", "meeting", "colleague", "project", "salary", "interview", "task"],
  },
  School: {
    themes: ["classroom basics", "assignments", "campus life", "exams"],
    words: ["lesson", "homework", "teacher", "student", "library", "exam", "grade", "subject"],
  },
  Culture: {
    themes: ["traditions", "festivals", "customs", "daily etiquette"],
    words: ["festival", "tradition", "custom", "museum", "music", "history", "art", "celebration"],
  },
  Travel: {
    themes: ["airport phrases", "hotel check-in", "directions", "local transport"],
    words: ["ticket", "passport", "hotel", "station", "map", "luggage", "reservation", "direction"],
  },
  Food: {
    themes: ["ordering food", "ingredients", "restaurants", "cooking"],
    words: ["menu", "breakfast", "dinner", "rice", "vegetable", "drink", "spicy", "delicious"],
  },
  Technology: {
    themes: ["devices", "apps", "online safety", "technical support"],
    words: ["computer", "phone", "password", "website", "download", "software", "message", "battery"],
  },
  General: {
    themes: ["daily phrases", "people", "places", "common actions"],
    words: ["hello", "friend", "home", "city", "learn", "speak", "listen", "practice"],
  },
};

function normalizeLanguage(language) {
  const raw = String(language || "English").trim();
  return LANGUAGE_LABELS[raw.toLowerCase()] || raw || "English";
}

function normalizeLevel(level) {
  const raw = String(level || "A1").trim();
  return LEVEL_LABELS[raw] || LEVEL_LABELS[raw.toLowerCase()] || raw || "Beginner";
}

function normalizeTopics(topics) {
  if (!Array.isArray(topics) || topics.length === 0) return ["General"];
  return topics.map((topic) => String(topic || "").trim()).filter(Boolean);
}

function pickTopicData(topic) {
  return TOPIC_BANK[topic] || TOPIC_BANK.General;
}

function rotatePick(items, start, count) {
  const result = [];
  for (let i = 0; i < count; i += 1) {
    result.push(items[(start + i) % items.length]);
  }
  return result;
}

function buildLesson(topic, unitIndex, lessonIndex, language, levelName) {
  const topicData = pickTopicData(topic);
  const theme = topicData.themes[(unitIndex + lessonIndex - 2) % topicData.themes.length];
  const vocabWords = rotatePick(topicData.words, (unitIndex - 1) * 2 + lessonIndex - 1, 5);
  const lessonTypes = ["intro", "vocabulary", "listening", "speaking", "quiz"];
  const type = lessonTypes[(lessonIndex - 1) % lessonTypes.length];

  return {
    title: `${topic}: ${theme}`,
    type,
    durationMinutes: type === "quiz" ? 8 : 12,
    xpPoints: type === "quiz" ? 25 : 15,
    isCompleted: false,
    orderNum: lessonIndex,
    vocabWords,
    note: `${language} ${levelName} lesson generated locally`,
  };
}

function generateTemplateCourse(profile = {}) {
  const language = normalizeLanguage(profile.language);
  const levelName = normalizeLevel(profile.level);
  const topics = normalizeTopics(profile.topics);
  const unitCount = Math.max(10, Math.min(12, topics.length * 3));

  const units = [];
  for (let unitIndex = 1; unitIndex <= unitCount; unitIndex += 1) {
    const topic = topics[(unitIndex - 1) % topics.length];
    const topicData = pickTopicData(topic);
    const focus = topicData.themes[(unitIndex - 1) % topicData.themes.length];

    units.push({
      title: `Unit ${unitIndex}: ${topic} - ${focus}`,
      orderNum: unitIndex,
      isUnlocked: unitIndex === 1,
      lessons: [1, 2, 3, 4, 5].map((lessonIndex) =>
        buildLesson(topic, unitIndex, lessonIndex, language, levelName)
      ),
    });
  }

  return {
    units,
    source: "template-generator",
  };
}

function ensureCourseShape(data, profile) {
  const fallback = generateTemplateCourse(profile);
  const rawUnits = Array.isArray(data?.units) ? data.units : Array.isArray(data) ? data : fallback.units;

  const units = rawUnits
    .filter(Boolean)
    .map((unit, unitIndex) => {
      const topic = normalizeTopics(profile.topics)[unitIndex % normalizeTopics(profile.topics).length];
      const lessons = Array.isArray(unit.lessons) && unit.lessons.length > 0
        ? unit.lessons
        : fallback.units[unitIndex % fallback.units.length].lessons;

      return {
        title: unit.title || fallback.units[unitIndex % fallback.units.length].title,
        orderNum: Number(unit.orderNum || unit.order || unitIndex + 1),
        isUnlocked: unit.isUnlocked !== undefined ? Boolean(unit.isUnlocked) : unitIndex === 0,
        lessons: lessons.map((lesson, lessonIndex) => ({
          title: lesson.title || buildLesson(topic, unitIndex + 1, lessonIndex + 1, normalizeLanguage(profile.language), normalizeLevel(profile.level)).title,
          type: lesson.type || "vocabulary",
          durationMinutes: Number(lesson.durationMinutes || 12),
          xpPoints: Number(lesson.xpPoints || 15),
          isCompleted: Boolean(lesson.isCompleted || lesson.completed || false),
          orderNum: Number(lesson.orderNum || lesson.order || lessonIndex + 1),
          vocabWords: Array.isArray(lesson.vocabWords) ? lesson.vocabWords : [],
        })),
      };
    });

  return { units: units.length > 0 ? units : fallback.units };
}

async function generateContentAI(prompt) {
  if (!process.env.GEMINI_API_KEY) throw new Error("MISSING_KEY");

  const models = ["gemini-3.5-flash", "gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite"];
  for (const modelName of models) {
    try {
      console.log(`Trying AI model: ${modelName}`);
      const model = genAI.getGenerativeModel({ model: modelName });
      const result = await model.generateContent(prompt);
      const text = result.response.text().replace(/```json|```/g, "").trim();
      return JSON.parse(text);
    } catch (error) {
      console.warn(`${modelName} failed:`, error.message);
    }
  }

  throw new Error("ALL_MODELS_FAILED");
}

app.post("/assessment/placement/start", async (req, res) => {
  try {
    const data = await generateContentAI(
      "Create 1 English multiple-choice question. Return JSON only: { \"test_id\": \"t1\", \"first_question\": { \"id\": \"q1\", \"skill\": \"Grammar\", \"text\": \"I ___ a student.\", \"options\": [\"am\",\"is\",\"are\",\"be\"] } }"
    );
    res.json(data);
  } catch (error) {
    res.json({
      test_id: `fb_${Date.now()}`,
      first_question: {
        id: "q1",
        skill: "Grammar",
        text: "Choose the correct word: I ___ from Vietnam.",
        options: ["am", "is", "are", "be"],
      },
    });
  }
});

app.post("/assessment/placement/answer", (req, res) => {
  const { questionId } = req.body;
  const nextNum = parseInt(String(questionId || "q1").replace("q", ""), 10) + 1;
  if (nextNum > 10) return res.json({ is_finished: true });

  return res.json({
    is_finished: false,
    next_question: {
      id: `q${nextNum}`,
      skill: "General",
      text: "Fill in the blank: She ___ to school every day.",
      options: ["go", "goes", "went", "gone"],
    },
  });
});

app.post("/assessment/placement/complete", (req, res) => {
  res.json({ userId: "user", profileId: "prof", cefrLevel: "B1", isActive: true });
});

app.post("/courses/generate", async (req, res) => {
  const profile = req.body || {};
  const language = normalizeLanguage(profile.language);
  const levelName = normalizeLevel(profile.level);
  const topics = normalizeTopics(profile.topics);

  const prompt = `
Create a personalized ${language} course for level ${levelName}.
Topics: ${topics.join(", ")}.
Return JSON only, with this exact shape:
{
  "units": [
    {
      "title": "Unit title",
      "orderNum": 1,
      "isUnlocked": true,
      "lessons": [
        {
          "title": "Lesson title",
          "type": "vocabulary",
          "durationMinutes": 12,
          "xpPoints": 15,
          "orderNum": 1,
          "vocabWords": ["word1", "word2"]
        }
      ]
    }
  ]
}
Make 10 units and 5 lessons per unit.
`;

  try {
    const aiData = await generateContentAI(prompt);
    res.json({ ...ensureCourseShape(aiData, profile), source: "gemini" });
  } catch (error) {
    console.warn("AI unavailable, using template course generator:", error.message);
    res.json(generateTemplateCourse(profile));
  }
});

app.post("/ai/generate-image-prompt", (req, res) => {
  const { term, definition } = req.body || {};
  const query = encodeURIComponent(`${term || "vocabulary"} ${definition || ""}`.trim());
  res.json({ imageUrl: `https://source.unsplash.com/800x600/?${query}` });
});

app.post("/ai/analyze-performance", (req, res) => {
  res.json({
    summary: "Practice is consistent.",
    strengths: ["Vocabulary recall"],
    suggestions: ["Review missed words daily", "Add one listening lesson per session"],
  });
});

app.post("/ai/tts", (req, res) => {
  res.json({ speechUrl: "" });
});

if (require.main === module) {
  const port = process.env.PORT || 3000;
  app.listen(port, () => {
    console.log(`VocabMaster backend running on http://localhost:${port}`);
    console.log("Course generation fallback is enabled, so Gemini quota is optional.");
  });
}

module.exports = {
  app,
  generateTemplateCourse,
  ensureCourseShape,
};
