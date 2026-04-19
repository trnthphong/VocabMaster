const express = require('express');
const dotenv = require('dotenv');
const cors = require('cors');
const { GoogleGenerativeAI } = require("@google/generative-ai");

dotenv.config();
const app = express();
app.use(cors());
app.use(express.json());

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY || "");

async function generateContentAI(prompt) {
    if (!process.env.GEMINI_API_KEY) throw new Error("MISSING_KEY");
    const models = ["gemini-1.5-flash", "gemini-pro"];
    for (const modelName of models) {
        try {
            console.log(`--- Thử gọi AI: ${modelName} ---`);
            const model = genAI.getGenerativeModel({ model: modelName });
            const result = await model.generateContent(prompt);
            const text = result.response.text().replace(/```json|```/g, "").trim();
            return JSON.parse(text);
        } catch (error) {
            console.warn(`⚠️ ${modelName} lỗi:`, error.message);
            if (error.message.includes("429") || error.message.includes("quota")) throw new Error("QUOTA_EXCEEDED");
        }
    }
    throw new Error("ALL_MODELS_FAILED");
}

app.post('/assessment/placement/start', async (req, res) => {
    try {
        const data = await generateContentAI("Tạo 1 câu hỏi trắc nghiệm tiếng Anh. JSON: { 'test_id': 't1', 'first_question': { 'id': 'q1', 'skill': 'Grammar', 'text': 'I ___ a student.', 'options': ['am','is','are','be'] } }");
        res.json(data);
    } catch (error) {
        res.json({
            test_id: "fb_" + Date.now(),
            first_question: { id: "q1", skill: "Grammar", text: "Choose the correct word: I ___ from Vietnam.", options: ["am", "is", "are", "be"] }
        });
    }
});

app.post('/assessment/placement/answer', (req, res) => {
    const { questionId } = req.body;
    const nextNum = parseInt((questionId || "q1").replace("q", "")) + 1;
    if (nextNum > 10) return res.json({ is_finished: true });
    res.json({
        is_finished: false,
        next_question: { id: "q" + nextNum, skill: "General", text: "Fill in the blank: She ___ to school every day.", options: ["go", "goes", "went", "gone"] }
    });
});

app.post('/courses/generate', async (req, res) => {
    try {
        const { language, level } = req.body;
        const prompt = `Tạo lộ trình ${language} ${level} (4 Units, 4 Lessons). JSON: { 'units': [{ 'title': 'U1', 'orderNum': 1, 'lessons': [{ 'lessonId': 'L1', 'title': 'L1', 'type': 'vocabulary', 'durationMinutes': 10, 'xpPoints': 20, 'orderNum': 1 }] }] }`;
        const data = await generateContentAI(prompt);
        res.json(data);
    } catch (error) {
        // QUAN TRỌNG: Dữ liệu mẫu phải có lessonId ngẫu nhiên
        res.json({
            units: [
                {
                    title: "Chương 1: Khởi động", orderNum: 1,
                    lessons: [
                        { lessonId: "ls_" + Math.random(), title: "Chào hỏi & Giới thiệu", type: "vocabulary", durationMinutes: 10, xpPoints: 20, orderNum: 1 },
                        { lessonId: "ls_" + Math.random(), title: "Gia đình & Bạn bè", type: "vocabulary", durationMinutes: 10, xpPoints: 20, orderNum: 2 }
                    ]
                }
            ]
        });
    }
});

app.post('/assessment/placement/complete', (req, res) => {
    res.json({ userId: "user", profileId: "prof", cefrLevel: "B1", isActive: true });
});

app.listen(3000, () => console.log("🚀 Server LIVE (Hỗ trợ lessonId đầy đủ)"));
