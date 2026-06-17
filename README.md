# LiteracyApp

An offline-first Android reading app for early readers (ages 5–10). Pure Kotlin + Jetpack Compose,
talking to a local Django backend, with **offline speech recognition via Vosk**.

- **Package:** `ayola.literacyapp.game`
- **Min SDK:** 23 · **Target SDK:** 36
- **Backend:** Django REST API at `http://10.0.2.2:8001/` (host loopback as seen from the emulator)

## Flow

Onboarding (register student) → Story List (age-filtered) → Game (read each sentence aloud; Vosk grades
accuracy) → Lesson (life-skill takeaway) → back to stories. Reading sessions (`duration`, `accuracy`,
`difficult_words`) are posted to the backend.

## ⚠️ Required setup: the Vosk acoustic model

The model is **not** committed to git (it's ~68 MB and lives under `app/src/main/assets/model/`, which is
in `.gitignore`). After cloning, you must add it manually:

1. Download the small English model from https://alphacephei.com/vosk/models
   (`vosk-model-small-en-us-0.15.zip`).
2. Unzip it and copy its **contents** (`am/`, `conf/`, `graph/`, `ivector/`, `README`) into
   `app/src/main/assets/model/`.
3. **Create a file named `uuid`** in `app/src/main/assets/model/` containing any short string
   (e.g. `v1`).

> **Why the `uuid` file matters:** Vosk's `StorageService.unpack()` reads `model/uuid` to version the
> copied model. If it is missing, `unpack()` throws a `FileNotFoundException` **on a background executor
> that silently swallows the exception** — so the app hangs forever on "Loading Speech Engine…" with no
> visible error. The generic model zip from the website does **not** include this file, so you must add it.

## Backend

Run the Django server bound so the emulator can reach it:

```bash
python manage.py runserver 0.0.0.0:8001
```

(The app uses cleartext HTTP to `10.0.2.2:8001`; `usesCleartextTraffic="true"` is set for local dev.)

## Build & run

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install to a running emulator/device
```

Grant the microphone permission when prompted (required for the reading game).
