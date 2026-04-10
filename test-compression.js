/**
 * Quick test — run this to prove compression works BEFORE touching Android.
 * Usage: node test-compression.js
 */
const http = require('http');

const SERVER = 'http://localhost:3000';

const TEST_IMAGES = [
  'https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png',
  'https://picsum.photos/id/237/800/600.jpg',
  'https://picsum.photos/id/1/800/600.jpg',
  'https://picsum.photos/id/10/800/600.jpg',
];

async function testUrl(url) {
  return new Promise((resolve, reject) => {
    const proxyUrl = `${SERVER}/proxy?url=${encodeURIComponent(url)}&quality=40`;
    http.get(proxyUrl, (res) => {
      const chunks = [];
      res.on('data', chunk => chunks.push(chunk));
      res.on('end', () => {
        const originalSize = parseInt(res.headers['x-original-size'] || '0');
        const compressedSize = parseInt(res.headers['x-compressed-size'] || '0');
        const saved = res.headers['x-data-saved'] || '0%';
        resolve({ url, originalSize, compressedSize, saved, status: res.statusCode });
      });
    }).on('error', reject);
  });
}

async function main() {
  console.log('=== DataSaver Compression Test ===\n');
  console.log('Testing server at', SERVER, '\n');

  // Health check
  try {
    await new Promise((resolve, reject) => {
      http.get(`${SERVER}/health`, res => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => { console.log('Health:', data, '\n'); resolve(); });
      }).on('error', reject);
    });
  } catch (e) {
    console.error('❌ Server not running! Start it with: cd server && npm start');
    process.exit(1);
  }

  // Test each image
  let totalOriginal = 0, totalCompressed = 0;

  for (const url of TEST_IMAGES) {
    try {
      const result = await testUrl(url);
      totalOriginal += result.originalSize;
      totalCompressed += result.compressedSize;

      const shortUrl = url.split('/').pop().substring(0, 40);
      console.log(`✅ ${shortUrl}`);
      console.log(`   Original:   ${(result.originalSize / 1024).toFixed(1)} KB`);
      console.log(`   Compressed: ${(result.compressedSize / 1024).toFixed(1)} KB`);
      console.log(`   Saved:      ${result.saved}\n`);
    } catch (e) {
      console.log(`❌ Failed: ${url}\n   ${e.message}\n`);
    }
  }

  const totalSaved = totalOriginal - totalCompressed;
  const pct = totalOriginal > 0 ? ((totalSaved / totalOriginal) * 100).toFixed(1) : 0;
  console.log('=== TOTAL ===');
  console.log(`Original:   ${(totalOriginal / 1024).toFixed(1)} KB`);
  console.log(`Compressed: ${(totalCompressed / 1024).toFixed(1)} KB`);
  console.log(`Saved:      ${(totalSaved / 1024).toFixed(1)} KB (${pct}%)`);
  console.log('\n🎉 If you see savings above, the compression server is WORKING!');
}

main().catch(console.error);
