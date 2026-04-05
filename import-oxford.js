const admin = require('firebase-admin');
const fs = require('fs');
const csv = require('csv-parser');

const serviceAccount = require('./your-firebase-service-account.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

async function importOxford() {
  const data = [];

  console.log('--- Đang đọc oxford3000_final.csv ---');

  fs.createReadStream('oxford3000_final.csv')
    .pipe(csv())
    .on('data', (row) => {
      const word = (row['Word'] || '').trim();
      if (!word) return;

      data.push({
        word: word.toLowerCase(),
        definition: (row['Definition'] || '').trim(),
        turkish_translation: (row['Turkish Translation'] || '').trim(),
        example_sentence: (row['Example Sentence'] || '').trim(),
        part_of_speech: (row['Part of Speech'] || '').trim(),
        related_forms: (row['Related Forms'] || '').trim(),
        synonyms: (row['Synonyms'] || '').trim(),
        antonyms: (row['Antonyms'] || '').trim(),
        collocations: (row['Collocations'] || '').trim(),
        topic: (row['Topic'] || 'khác').trim(),
        lang: (row['Lang'] || 'en').trim(),
        phonetic: (row['Phonetic'] || '').trim(),
        audio: (row['Audio'] || '').trim(),
        cefr: (row['CEFR'] || '').trim(),
        created_at: admin.firestore.FieldValue.serverTimestamp(),
      });
    })
    .on('end', async () => {
      console.log(`Đã đọc xong ${data.length} từ hợp lệ.`);

      if (data.length === 0) {
        console.error('❌ Không tìm thấy dữ liệu. Kiểm tra lại file CSV.');
        return;
      }

      // Dedup by word
      const seen = new Map();
      for (const item of data) seen.set(item.word, item);
      const unique = Array.from(seen.entries());
      console.log(`Sau dedup: ${unique.length} từ (đã loại ${data.length - unique.length} trùng).`);

      let count = 0;
      let totalImported = 0;
      let batch = db.batch();

      for (const [word, item] of unique) {
        const docRef = db.collection('vocabularies').doc(word);
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

      if (count > 0) await batch.commit();

      console.log(`✅ Thành công! Đã import ${totalImported} từ vựng tiếng Anh.`);
      process.exit(0);
    })
    .on('error', (err) => {
      console.error('❌ Lỗi đọc file:', err.message);
      process.exit(1);
    });
}

importOxford();
