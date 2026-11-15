# LD0801 Native Bridge

Android bridge for the Josh/SEZNIK LD0801 Bluetooth thermal printer.

## Build without Android Studio

1. Create a new GitHub repository.
2. Upload the **contents of this ZIP** to the repository root.
3. GitHub Actions will automatically start because the workflow runs on `push`.
4. Open **Actions** and wait for **Build LD0801 Native Bridge APK** to finish.
5. Open the completed run and download the artifact **LD0801-Native-Bridge-debug**.
6. Extract the artifact and install `app-debug.apk` on the Android phone.

You can also manually start the workflow from **Actions → Build LD0801 Native Bridge APK → Run workflow**.

## Use

1. Pair the LD0801 printer in Android Bluetooth settings.
2. Open **LD0801 Native Bridge**.
3. Select the paired printer, normally `LD0801-Y5085220641`.
4. Tap **Connect Printer**.
5. Tap **Start Local Print Server**.
6. Keep the bridge app running while using the WordPress attendance page.

The WordPress native version sends the raster label to `http://127.0.0.1:9101/print`.

## Important

The printer protocol in this project is a prototype based on the DeTong/LD0801 raster protocol. It must be tested with the physical printer before relying on it for production printing, especially the die-cut label advance behavior.


## Build fix in FINAL package

The Android Java and Kotlin compiler targets are both explicitly set to JVM 17. This avoids the Gradle/Kotlin error where Java was targeting 1.8 while Kotlin targeted 17.

The GitHub Actions workflow uses current GitHub action versions for checkout, Java setup, and Gradle setup.
