# My Offline Music Player (Android, Kotlin + Compose + Media3)

## Biên dịch APK
1. Cài Android Studio (Koala 2024.1 trở lên, JDK 17 đi kèm).
2. File ▸ Open ▸ chọn thư mục `OfflineMusic`. Đợi Gradle Sync (cần Internet LẦN ĐẦU để tải thư viện).
3. Build ▸ Build Bundle(s)/APK(s) ▸ Build APK(s).
   APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`
   (hoặc dòng lệnh: `gradle assembleDebug` / `gradle assembleRelease`).
4. Copy APK sang điện thoại, bật "Cài từ nguồn không rõ" và cài đặt (Android 8.0+).

## Cấu trúc
- `audio/BiquadEqProcessor.kt` – DSP EQ 6 dải (Low shelf, 4 Peaking, High shelf) chèn vào pipeline ExoPlayer
- `PlaybackService.kt` – MediaSessionService: phát nền, màn hình khóa, thông báo, lưu vị trí
- `MainViewModel.kt`, `Data.kt` – logic, MediaStore, DataStore (EQ, yêu thích, playlist, hàng đợi)
- `ui/` – Jetpack Compose: Home, Library, Now Playing, Equalizer, Settings
