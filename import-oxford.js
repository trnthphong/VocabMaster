const admin = require('firebase-admin');
const fs = require('fs');
const csv = require('csv-parser');

const serviceAccount = require('./your-firebase-service-account.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

async function importRussian() {
  const data = [];

  console.log('--- Đang đọc russian_vocab_with_topics.csv ---');

  fs.createReadStream('russian_vocab_with_topics.csv')
    .pipe(csv())
    .on('data', (row) => {
      const word = (row['word'] || '').trim();
      if (!word) return;

      data.push({
        word,
        accented: (row['accented'] || '').trim(),
        translations_en: (row['translations_en'] || '').trim(),
        translations_de: (row['translations_de'] || '').trim(),
        part_of_speech: (row['part_of_speech'] || '').trim(),
        extra: (row['extra'] || '').trim(),
        topic: (row['topic'] || 'khác').trim(),
        lang: (row['lang'] || 'ru').trim(),
        phonetic: (row['phonetic'] || '').trim(),
        audio: (row['audio'] || '').trim(),
        created_at: admin.firestore.FieldValue.serverTimestamp(),
      });
    })
    .on('end', async () => {
      console.log(`Đã đọc xong ${data.length} từ hợp lệ.`);

      if (data.length === 0) {
        console.error('❌ Không tìm thấy dữ liệu. Kiểm tra lại file CSV.');
        return;
      }

      // Dedup by docId (word_partOfSpeech), giữ lại bản cuối
      const seen = new Map();
      for (const item of data) {
        const docId = `${item.word}_${item.part_of_speech}`;
        seen.set(docId, item);
      }
      const unique = Array.from(seen.entries()); // [[docId, item], ...]
      console.log(`Sau dedup: ${unique.length} từ (đã loại ${data.length - unique.length} trùng).`);

      let count = 0;
      let totalImported = 0;
      let batch = db.batch();

      for (const [docId, item] of unique) {
        const docRef = db.collection('russian_vocabularies').doc(docId);
        batch.set(docRef, item, { merge: true });
        count++;
        totalImported++;

        if (count === 200) {
          await batch.commit();
          console.log(`Tiến độ: ${totalImported}/${unique.length} từ...`);
          batch = db.batch();
          count = 0;
        }
      }

      if (count > 0) {
        await batch.commit();
      }

      console.log(`✅ Thành công! Đã import ${totalImported} từ vựng tiếng Nga.`);
      process.exit(0);
    })
    .on('error', (err) => {
      console.error('❌ Lỗi đọc file:', err.message);
      process.exit(1);
    });
}

importRussian();
