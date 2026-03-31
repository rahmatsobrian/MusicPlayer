# 🎵 Music Player – Android (Kotlin + Material You)

Android music player dengan support FLAC, Material You UI, dan CI/CD via GitHub Actions.

## ✨ Fitur

- 🎵 Memutar semua format audio termasuk **FLAC**
- 🎨 **Material You** (Material 3) dengan Dynamic Color
- 🔔 **Foreground Service** dengan notifikasi kontrol
- 🔀 Shuffle & Repeat mode
- 🔍 Search lagu, artis, album
- 📂 Filter khusus **FLAC**
- 🤖 **CI/CD GitHub Actions** – auto build & release APK

## 📋 Spesifikasi

| Item | Nilai |
|------|-------|
| Bahasa | Kotlin |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 (Android 14) |
| Build System | Gradle KTS |
| AGP | 8.3.2 |
| Kotlin | 1.9.23 |
| Gradle | 8.6 |

## ⚠️ WAJIB: Download `gradle-wrapper.jar`

File `gradle/wrapper/gradle-wrapper.jar` **tidak ikut dalam repo** karena binary.
Jalankan salah satu perintah berikut setelah clone:

### Opsi 1 – Pakai curl
```bash
curl -L https://raw.githubusercontent.com/gradle/gradle/v8.6.0/gradle/wrapper/gradle-wrapper.jar \
  -o gradle/wrapper/gradle-wrapper.jar
```

### Opsi 2 – Pakai Gradle (jika sudah terinstall)
```bash
gradle wrapper --gradle-version 8.6
```

### Opsi 3 – Pakai Android Studio
Buka project di Android Studio → ia akan otomatis download wrapper.

> **Catatan:** GitHub Actions sudah handle ini secara otomatis via `actions/setup-java` + cache Gradle.

## 🚀 Build Lokal

```bash
# Clone
git clone https://github.com/USERNAME/MusicPlayer.git
cd MusicPlayer

# Download gradle-wrapper.jar (lihat section di atas)

# Build
chmod +x gradlew
./gradlew assembleDebug

# APK output:
# app/build/outputs/apk/debug/app-debug.apk
```

## 🤖 GitHub Actions

Workflow otomatis berjalan saat:
- Push ke branch `main` atau `master`
- Pull request

### Alur workflow:
1. ✅ Checkout repo
2. ☕ Setup JDK 17 (Temurin)
3. 🤖 Setup Android SDK + accept licenses
4. 📦 Install `platforms;android-34` & `build-tools;34.0.0`
5. 💾 Cache Gradle
6. 🔨 Build dengan `--stacktrace --info`
7. 📤 Upload APK sebagai artifact
8. 🚀 Buat GitHub Release + upload APK

### Auto Release:
Setiap push sukses ke `main`/`master` → otomatis buat release dengan tag `v1.0.{run_number}`.

## 📁 Struktur Project

```
MusicPlayer/
├── .github/
│   └── workflows/
│       └── android.yml          ← CI/CD workflow
├── app/
│   ├── src/main/
│   │   ├── java/com/musicplayer/app/
│   │   │   ├── MainActivity.kt
│   │   │   ├── model/
│   │   │   │   ├── Song.kt
│   │   │   │   └── PlayerState.kt
│   │   │   ├── service/
│   │   │   │   └── MusicService.kt
│   │   │   ├── ui/
│   │   │   │   └── SongAdapter.kt
│   │   │   └── utils/
│   │   │       ├── MusicScanner.kt
│   │   │       └── FormatUtils.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   └── item_song.xml
│   │   │   ├── drawable/       ← Vector icons
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── themes.xml
│   │   │   └── menu/
│   │   │       └── main_menu.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── libs.versions.toml       ← Version catalog
│   └── wrapper/
│       ├── gradle-wrapper.jar   ← DOWNLOAD MANUAL (lihat README)
│       └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
└── gradlew.bat
```

## 🔧 Troubleshooting CI

Jika build gagal di GitHub Actions:
1. Cek tab **Actions** → klik run yang gagal
2. Lihat log step **Build Debug APK** (ada `--stacktrace --info`)
3. Jika ada error SDK → cek step **Install required SDK packages**
4. Download artifact `build-logs-XX` dari tab Summary jika perlu

## 📜 License

```
Copyright 2024 RahmatSobrian

Licensed under the Apache License, Version 2.0
```
