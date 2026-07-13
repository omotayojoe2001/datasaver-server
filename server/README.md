# DataSaver — Mobile Data Compression App

Saves 20-30% of mobile data by compressing images and text through a proxy server.

## Architecture

```
[Android Phone] → VPN intercepts traffic → [Compression Server] → fetches content
                                                ↓
                                          compresses images (JPEG→WebP, lower quality)
                                          gzips text (HTML/CSS/JS)
                                                ↓
[Android Phone] ← smaller response ← [Compression Server]
```

## Quick Start

### Step 1: Start the compression server

```bash
cd server
npm install
npm start
```

You should see:
```
DataSaver compression server running on port 3000
```

### Step 2: Test compression works

```bash
cd server
node test-compression.js
```

You should see 20-60% savings on test images.

### Step 3: Test in browser

Open your browser:
```
http://localhost:3000/proxy?url=https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png&quality=30
```

Check response headers in DevTools (F12 → Network tab) for `X-Original-Size`, `X-Compressed-Size`, and `X-Data-Saved`.

### Step 4: Build the Android app (NO Android Studio needed)

Requirements: Android SDK command-line tools + JDK 17 (no Android Studio required).

```bash
cd android-lite
build.bat
```

The APK will be at `android-lite/out/datasaver.apk`.

### Step 5: Install on phone

**Via USB:**
```bash
adb install android-lite/out/datasaver.apk
```

**Without USB:**
Transfer `datasaver.apk` to your phone (email, Google Drive, WhatsApp to yourself, etc.) and open it to install. Enable "Install from unknown sources" if prompted.

### Step 6: Use the app

1. Make sure your phone and laptop are on the same WiFi
2. Find your laptop's IP: `ipconfig` (Windows)
3. Open DataSaver app on your phone
4. Enter your laptop's IP (e.g., `192.168.1.100`) and port `3000`
5. Tap **TEST COMPRESSION** to verify connectivity
6. Tap **CONNECT** to start saving data

## Testing on Emulator

The default `10.0.2.2:3000` should work — this maps to your laptop's localhost from the Android emulator.

## Server Endpoints

- `GET /health` — server status + total savings
- `GET /stats` — savings statistics (JSON)
- `GET /proxy?url=<URL>&quality=<1-100>` — fetch + compress a URL

## How Savings Work

| Content Type | Method | Typical Savings |
|-------------|--------|----------------|
| JPEG images | Re-encode as WebP at quality 40 | 30-60% |
| PNG images | Re-compress at max level | 10-30% |
| HTML/CSS/JS | gzip level 9 | 60-80% |
| Video | Not compressed (v2) | 0% |

## Project Structure

```
dataapp/
├── server/              # Node.js compression proxy server
│   ├── src/server.js    # Main server code
│   └── test-compression.js
├── android-lite/        # Android app (command-line build, no Android Studio)
│   ├── src/             # Java source files
│   ├── res/             # Android resources (layouts, strings)
│   ├── build.bat        # Build script
│   └── out/             # Built APK output
└── README.md
```
