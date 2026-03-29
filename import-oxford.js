const admin = require('firebase-admin');
const fs = require('fs');
const csv = require('csv-parser');

const serviceAccount = require('./your-firebase-service-account.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function importOxford() {
  const data = [];

  console.log('--- Đang đọc dữ liệu từ file CSV ---');

  fs.createReadStream('oxford3000_vocabulary_with_collocations_and_definitions_datasets.csv')
    .pipe(csv())
    .on('data', (row) => {
      // Log headers một lần để kiểm tra
      if (data.length === 0) {
        console.log('Headers thực tế trong file CSV:', Object.keys(row));
      }

      // Ánh xạ linh hoạt các tên cột (trim để tránh lỗi khoảng trắng)
      const word = (row.word || row.Word || row['﻿word'] || '').trim();
      const cefr = (row.cefr || row.cefr_level || row['CEFR Level'] || 'B1').trim();
      const pos = (row.pos || row.part_of_speech || row['Part of Speech'] || '').trim();
      const def = (row.definition || row.definition_en || row['Definition'] || '').trim();
      const example = (row.example || row.example_sentences || row['Example Sentence'] || '').trim();

      if (word) {
        data.push({
          word: word.toLowerCase(),
          cefr_level: cefr,
          part_of_speech: pos,
          definition_en: def,
          definition_vi: '',
          example_sentences: example ? [example] : [],
          difficulty: 3,
          created_at: admin.firestore.FieldValue.serverTimestamp()
        });
      }
    })
    .on('end', async () => {
      console.log(`Đã đọc xong ${data.length} dòng hợp lệ.`);

      if (data.length === 0) {
        console.error('❌ Không tìm thấy dữ liệu hợp lệ để import. Hãy kiểm tra lại tên cột trong file CSV.');
        return;
      }

      let count = 0;
      let totalImported = 0;
      let batch = db.batch();

      for (const item of data) {
        const docRef = db.collection('vocabularies').doc(item.word);
        batch.set(docRef, item, { merge: true });

        count++;
        totalImported++;

        if (count === 400) {
          await batch.commit();
          console.log(`Tiến độ: Đã import ${totalImported} từ...`);
          batch = db.batch();
          count = 0;
        }
      }

      if (count > 0) {
        await batch.commit();
      }

      console.log(`✅ Thành công! Đã import tổng cộng ${totalImported} từ vựng.`);
      process.exit(0);
    });
}

importOxford();
