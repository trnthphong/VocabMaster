/**
 * Script thêm nghĩa tiếng Việt vào Firestore dùng Google Translate (miễn phí, không cần key)
 * 
 * Bước 1: Tải serviceAccountKey.json từ Firebase Console → Project Settings → Service accounts
 * Bước 2: Copy serviceAccountKey.json vào cùng thư mục này
 * Bước 3: cd "app\src\main\java\com\example\vocabmaster\data"
 * Bước 4: node add_vietnamese.js
 */

const admin = require("firebase-admin");
const https = require("https");

const SERVICE_ACCOUNT_PATH = "./serviceAccountKey.json";
const DELAY_MS = 500;
const START_FROM_TOPIC = "";  // Dịch tất cả
const SKIP_TOPICS = ["khác"]; // Bỏ qua topic này

// Force dịch lại các topic này (xóa nghĩa cũ bị sai)
const FORCE_RETRANSLATE = [
  "giải trí", "giao thông", "hình dạng", "học tập",
  "liên lạc", "màu sắc", "mua sắm", "ngôn ngữ",
  "nhà ở", "pháp luật", "quần áo", "số học",
  "sức khỏe", "thể dục", "thể thao", "thời tiết",
  "thức ăn", "gia đình"
];

const serviceAccount = require(SERVICE_ACCOUNT_PATH);
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/** Dịch EN → VI dùng Google Translate unofficial API */
function translateWord(text) {
  return new Promise((resolve) => {
    if (!text || text.trim() === "") { resolve(""); return; }

    const encoded = encodeURIComponent(text.trim().substring(0, 200));
    const path = `/translate_a/single?client=gtx&sl=en&tl=vi&dt=t&q=${encoded}`;

    const options = {
      hostname: "translate.googleapis.com",
      path,
      method: "GET",
    };

    const req = https.request(options, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => {
        try {
          const json = JSON.parse(data);
          // Response format: [[[translated, original, ...]]]
          const translation = json?.[0]?.[0]?.[0] || "";
          resolve(translation.trim());
        } catch {
          resolve("");
        }
      });
    });

    req.on("error", () => resolve(""));
    req.end();
  });
}

async function processCollection(collectionRef, topicName, forceRetranslate = false) {
  const snapshot = await collectionRef.get();

  // Nếu force retranslate → dịch lại tất cả, không check vietnamese_translation cũ
  const docs = forceRetranslate
    ? snapshot.docs
    : snapshot.docs.filter((doc) => {
        const d = doc.data();
        return !d.vietnamese_translation || d.vietnamese_translation.trim() === "";
      });

  if (docs.length === 0) {
    console.log(`  ✓ ${topicName}: Đã có đủ nghĩa tiếng Việt`);
    return;
  }

  console.log(`  → ${topicName}: ${forceRetranslate ? "Dịch lại" : "Dịch"} ${docs.length} từ...`);

  for (let i = 0; i < docs.length; i++) {
    const doc = docs[i];
    const data = doc.data();
    const word = data.word || "";
    const definition = data.definition || "";

    // Ưu tiên dịch definition ngắn, nếu quá dài thì dịch word
    const textToTranslate = definition.length > 0 && definition.length < 100
      ? definition
      : word;

    const vi = await translateWord(textToTranslate);

    if (vi && vi.length > 0) {
      await doc.ref.update({ vietnamese_translation: vi });
      console.log(`    [${i + 1}/${docs.length}] ${word} → ${vi}`);
    } else {
      console.log(`    [${i + 1}/${docs.length}] ${word} → (bỏ qua)`);
    }

    await sleep(DELAY_MS);
  }

  console.log(`  ✓ ${topicName}: Xong!\n`);
}

async function main() {
  console.log("🚀 Bắt đầu thêm nghĩa tiếng Việt vào Firestore...\n");
  console.log("📚 Đang xử lý public topics...");

  const topicsSnap = await db.collection("topics").get();
  console.log(`   Tìm thấy ${topicsSnap.docs.length} topics\n`);

  let started = !START_FROM_TOPIC;

  for (const topicDoc of topicsSnap.docs) {
    const name = topicDoc.data().name || topicDoc.id;
    const nameLower = name.toLowerCase();

    if (!started) {
      if (nameLower === START_FROM_TOPIC.toLowerCase()) {
        started = true;
      } else {
        console.log(`  ⏭ Bỏ qua (chưa tới): ${name}`);
        continue;
      }
    }

    if (SKIP_TOPICS.some(s => nameLower === s.toLowerCase())) {
      console.log(`  ⏭ Bỏ qua (blacklist): ${name}`);
      continue;
    }

    const forceRetranslate = FORCE_RETRANSLATE.some(s => nameLower === s.toLowerCase());
    await processCollection(topicDoc.ref.collection("vocabularies"), name, forceRetranslate);
  }

  console.log("\n✅ Hoàn thành! Xóa bộ từ trong app rồi tải lại để thấy nghĩa tiếng Việt.");
  process.exit(0);
}

main().catch((err) => {
  console.error("❌ Lỗi:", err.message);
  if (err.message.includes("serviceAccountKey") || err.code === "MODULE_NOT_FOUND") {
    console.error("\n👉 Hướng dẫn lấy serviceAccountKey.json:");
    console.error("   1. Vào https://console.firebase.google.com");
    console.error("   2. Chọn project vocabmaster-66946");
    console.error("   3. ⚙️ Project Settings → Service accounts");
    console.error("   4. Generate new private key → Download");
    console.error("   5. Đổi tên thành serviceAccountKey.json");
    console.error("   6. Copy vào thư mục này (cùng với add_vietnamese.js)");
  }
  process.exit(1);
});
