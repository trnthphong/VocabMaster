const fs = require('fs');
const csv = require('csv-parser');

fs.createReadStream('oxford3000_vocabulary_with_collocations_and_definitions_datasets.csv')
  .pipe(csv())
  .on('data', (row) => {
    console.log('Headers found:', Object.keys(row));
    console.log('First row sample:', row);
    process.exit(0);
  })
  .on('error', (err) => {
    console.error('Error:', err.message);
  });
