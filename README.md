# Bin Watan Caller (Android)

Companion app for the Bin Watan Calling Queue. It asks your website for the next
customer, calls them from **SIM slot 1**, and reports the result back.

## Get the APK (no Android Studio needed)

1. Create a free account at github.com and make a **new private repository**.
2. Upload everything in this folder to it (including the hidden `.github` folder).
   If the `.github` folder does not upload, create the file `.github/workflows/build.yml`
   in GitHub ("Add file" > "Create new file") and paste in the contents of `build.yml`.
3. Open the repository's **Actions** tab. The "Build APK" workflow runs by itself after the upload
   (or press **Run workflow**). It takes about 5 minutes.
4. Open the finished run and download **bin-watan-caller-apk** (a zip containing `app-debug.apk`).
5. Copy `app-debug.apk` to the phone and install it (allow "Install unknown apps" when asked).

## First-time setup on the phone

1. Open **Bin Watan Caller**.
2. Enter your website address (must start with `https://`) and the `CALLING_API_KEY` value.
3. Tap **Save settings**, then **Allow permissions** and accept everything.
4. Tap **Battery: set to No restrictions** and choose this app, then **No restrictions**.
5. Tap **Autostart settings** and switch the app on (Xiaomi / POCO).
6. In Recents, long-press the app card and **lock** it so it is not cleared.
7. Tap **Start service**.

Then use the Calling Queue page in the admin panel: add orders and press **Start Calling**.

## How calls are judged

- Call ends within the ring window (default 60s) and the call history shows no talk time: **No answer**, next customer.
- Call history shows talk time: **Answered**; the queue stops so you can handle the order.
- Call is still connected when the ring window ends: **Answered**; the queue stops.

Voicemail pickup looks like an answered call. This is an Android limitation.
