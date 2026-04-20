/**
 * Script thêm ảnh vào vocabulary trong Firestore dùng LoremFlickr API (miễn phí)
 * Tìm ảnh theo word + definition để ảnh chính xác hơn
 * 
 * Chạy: node add_images.js
 * Cần: serviceAccountKey.json cùng thư mục
 */

const admin = require("firebase-admin");
const https = require("https");

const SERVICE_ACCOUNT_PATH = "./serviceAccountKey.json";
const DELAY_MS = 800;

const ONLY_TOPICS = [];
const SKIP_TOPICS = ["khác"];

const serviceAccount = require(SERVICE_ACCOUNT_PATH);
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Trích xuất từ khóa chính từ definition
 * VD: "a round red fruit" → "red fruit"
 */
function extractKeywords(word, definition) {
  if (!definition) return word;

  // Bỏ các từ phổ biến không mang nghĩa ảnh
  const stopWords = new Set([
    "a","an","the","of","to","in","is","are","was","were","be","been",
    "have","has","had","do","does","did","will","would","could","should",
    "may","might","shall","can","that","which","who","whom","whose",
    "this","these","those","it","its","they","them","their","there",
    "when","where","how","what","why","with","for","from","by","at",
    "on","or","and","but","not","no","nor","so","yet","both","either",
    "used","relating","referring","especially","usually","often","also",
    "one","two","three","any","all","each","every","some","such","more"
  ]);

  // Lấy các danh từ/tính từ quan trọng từ definition (tối đa 3 từ)
  const defWords = definition
    .toLowerCase()
    .replace(/[^a-z\s]/g, "")
    .split(/\s+/)
    .filter(w => w.length > 3 && !stopWords.has(w))
    .slice(0, 2);

  // Kết hợp: word + từ khóa từ definition
  const keywords = [word, ...defWords].join(",");
  return keywords;
}

/**
 * Lấy URL ảnh từ LoremFlickr theo word + definition
 * Thử nhiều lần với từ khóa đơn giản dần nếu không tìm được ảnh
 */
function getImageUrl(word, definition) {
  return new Promise(async (resolve) => {
    // Danh sách từ khóa thử theo thứ tự: phức tạp → đơn giản
    const keywordSets = [
      extractKeywords(word, definition),  // word + keywords từ definition
      word,                                // chỉ word
      word.split(/\s+/)[0],               // từ đầu tiên của word
    ];

    for (const keywords of keywordSets) {
      const encoded = encodeURIComponent(keywords);
      const url = `https://loremflickr.com/400/300/${encoded}`;
      const result = await fetchImageUrl(url);

      // Bỏ qua nếu là ảnh default (không tìm được)
      if (result && !result.includes("defaultImage")) {
        resolve(result);
        return;
      }
    }

    // Fallback: dùng URL gốc với word đơn giản nhất
    resolve(`https://loremflickr.com/400/300/${encodeURIComponent(word.split(/\s+/)[0])}`);
  });
}

function fetchImageUrl(url) {
  return new Promise((resolve) => {
    const req = https.request(url, { method: "GET" }, (res) => {
      if (res.statusCode === 302 || res.statusCode === 301) {
        resolve(res.headers.location || null);
      } else if (res.statusCode === 200) {
        resolve(url);
      } else {
        resolve(null);
      }
      res.resume();
    });
    req.on("error", () => resolve(null));
    req.end();
  });
}

async function processCollection(collectionRef, topicName) {
  const snapshot = await collectionRef.get();
  const docs = snapshot.docs.filter((doc) => {
    const d = doc.data();
    const url = d.image_url || "";
    return !url || url.trim() === "" ||
           url.includes("defaultImage") ||        // ảnh default = không tìm được
           url.includes("loremflickr.com/600") ||
           url.includes("source.unsplash.com");
  });

  if (docs.length === 0) {
    console.log(`  ✓ ${topicName}: Đã có đủ ảnh`);
    return;
  }

  console.log(`  → ${topicName}: Thêm ảnh cho ${docs.length} từ...`);

  for (let i = 0; i < docs.length; i++) {
    const doc = docs[i];
    const data = doc.data();
    const word = data.word || "";
    const definition = data.definition || "";
    if (!word) continue;

    const imageUrl = await getImageUrl(word, definition);
    await doc.ref.update({ image_url: imageUrl });

    const keywords = extractKeywords(word, definition);
    console.log(`    [${i + 1}/${docs.length}] "${word}" (${keywords}) → OK`);

    await sleep(DELAY_MS);
  }

  console.log(`  ✓ ${topicName}: Xong!\n`);
}

async function main() {
  console.log("🖼️  Bắt đầu thêm ảnh vào Firestore...\n");

  const topicsSnap = await db.collection("topics").get();
  console.log(`📚 Tìm thấy ${topicsSnap.docs.length} topics\n`);

  for (const topicDoc of topicsSnap.docs) {
    const name = topicDoc.data().name || topicDoc.id;
    const nameLower = name.toLowerCase();

    if (SKIP_TOPICS.some(s => nameLower === s.toLowerCase())) {
      console.log(`  ⏭ Bỏ qua: ${name}`);
      continue;
    }

    if (ONLY_TOPICS.length > 0 && !ONLY_TOPICS.some(s => nameLower === s.toLowerCase())) {
      console.log(`  ⏭ Bỏ qua: ${name}`);
      continue;
    }

    await processCollection(topicDoc.ref.collection("vocabularies"), name);
  }

  console.log("✅ Hoàn thành! Xóa bộ từ trong app rồi tải lại để thấy ảnh mới.");
  process.exit(0);
}

main().catch((err) => {
  console.error("❌ Lỗi:", err.message);
  process.exit(1);
});
