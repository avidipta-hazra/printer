# LD0801 Native Bridge

Phone-only Android bridge for the Josh/SEZNIK LD0801 Bluetooth thermal label printer.

## What it does

- Finds paired Bluetooth Classic printers.
- Connects to the selected LD0801 over Bluetooth SPP.
- Runs a local HTTP server at `http://127.0.0.1:9101/print`.
- Accepts the same image JSON style used by the WordPress attendance page.
- Converts the incoming PNG to a 400 × 240 monochrome raster.
- Sends the current DeTong-style raster sequence and `0C` label-advance command.

## Important

This is a prototype native bridge. The exact DeTong command sequence still needs to be tested with the physical LD0801. The WordPress → local HTTP → bridge path is separate from Cleanter and does not depend on Cleanter.

Keep the app open while testing so Android does not stop the local server.

## Phone-only APK build

This project includes a GitHub Actions workflow. No Android Studio is required.

1. Create a GitHub repository from your phone.
2. Upload the contents of this project.
3. Open **Actions** → **Build LD0801 Native Bridge APK**.
4. Tap **Run workflow**.
5. After it finishes, open the workflow run and download the artifact named `LD0801-Native-Bridge-debug`.
6. Extract the ZIP and install `app-debug.apk` on the Android phone.

## WordPress endpoint

The WordPress plugin should POST the raster image to:

`http://127.0.0.1:9101/print`
