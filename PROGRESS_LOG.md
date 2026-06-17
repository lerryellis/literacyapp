# LiteracyApp — Progress Log

A running log of every change made, in order, with challenges caught and how they were fixed.
Newest entries are appended at the bottom. (Updated after every change.)

**Project:** `ayola.literacyapp.game` | Pure Kotlin + Jetpack Compose | min SDK 23 / target 36
**Backend:** local Django at `http://10.0.2.2:8001/` (emulator -> host loopback)

---

## Entry 1 — Repo connected to GitHub

**What was done**
- Initialized git in the local Android Studio project (it was not a repo).
- Connected remote `origin` -> `https://github.com/lerryellis/literacyapp.git` (private).
- Committed all existing project files and pushed `main`.

**Challenges & fixes**
- The repo URL first returned 404 via web fetch -> confirmed it was *private*, not missing. Verified
  access with `git ls-remote` (exit 0, empty repo) before pushing.
- `gh` CLI is not installed on this machine -> used plain `git` over HTTPS (cached credentials worked).
- User requested no "Co-Authored-By: Claude" line -> recommitted without it.

**Build status:** N/A (no code change)

---

## Entry 2 — Phase 1: Networking layer, models, session storage

**What was done**
- `app/build.gradle.kts`: added Retrofit 2.9.0, converter-gson 2.9.0, okhttp logging-interceptor 4.11.0,
  navigation-compose 2.7.7.
- `AndroidManifest.xml`: added `INTERNET` permission + `android:usesCleartextTraffic="true"`.
- New `api/LiteracyApiService.kt`: Retrofit interface for `POST api/students/`, `GET api/stories/`
  (optional `age_group` query), `POST api/sessions/`. Uses `Response<T>` so the 200-vs-201 student
  case can be read via `.code()`.
- New `api/RetrofitClient.kt`: singleton, base URL `http://10.0.2.2:8001/`, OkHttp logging interceptor.
- New models: `Student`, `Story`, `SessionRequest` with `@SerializedName` snake_case mapping.
- New `utils/SessionManager.kt`: SharedPreferences for `student_id`; reads `Settings.Secure.ANDROID_ID`
  as `device_id`.

**Challenges & fixes**
- **Manifest build failure** (`ManifestMerger2$MergeFailureException: Error parsing AndroidManifest.xml`).
  Cause: an edit left a blank line *before* the `<?xml ...?>` declaration, which is illegal in XML.
  Fix: rewrote the file so `<?xml` is the very first byte. Rebuild succeeded.
- Project uses a Gradle **version catalog** (`libs.versions.toml`); dependencies were added as direct
  version strings to match the prompt. Noted as a future cleanup (could migrate into the catalog).

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL (16 MB debug APK).

---

## Entry 3 — Story model corrected (life-skill fields)

**What was done**
- `models/Story.kt`: added `@SerializedName("life_skill") lifeSkill` and
  `@SerializedName("life_skill_lesson") lifeSkillLesson` to match the Django model.

**Challenges & fixes**
- Original `Story` was an explicit assumption flagged in the Phase 1 report; user confirmed two extra
  fields existed on the backend. Fixed before building any UI that consumes stories.
- Confirmed `difficult_words` as `List<String>` is correct (maps to Django `JSONField` array).

**Build status:** verified together with Entry 4.

---

## Entry 4 — Phase 2: Onboarding UI, ViewModel & navigation

**What was done**
- New `ui/onboarding/OnboardingViewModel.kt`: holds `OnboardingUiState` (`isLoading`, `error`,
  `loginSuccess`) as a `StateFlow`. `loginStudent(name, school, ageGroup)` validates input, reads
  `deviceId` from `SessionManager`, calls `createStudent()`, and on success (200/201 with a non-null id)
  saves the id and flips `loginSuccess` to trigger navigation. Includes a `factory()` to inject
  `SessionManager`.
- New `ui/onboarding/OnboardingScreen.kt`: Material 3 form — Name + School text fields, Age-group radio
  buttons (`5-7`, `8-10`), a `Start Reading` button that shows a `CircularProgressIndicator` while
  loading, and an inline error message.
- New `ui/navigation/AppNavigation.kt`: `NavHost` with `Onboarding` as the start destination; on success
  navigates to a placeholder `Story List` route (with `popUpTo(onboarding, inclusive=true)` so the child
  can't go back into onboarding).
- `MainActivity.kt`: now sets `AppNavigation()` inside `LiteracyAppTheme`; removed the default Greeting.

**Challenges & fixes**
- **Missing Compose-ViewModel integration.** `viewModel(factory = ...)` requires
  `androidx.lifecycle:lifecycle-viewmodel-compose`, which wasn't on the classpath. Fix: added
  `lifecycle-viewmodel-compose:2.6.1` to `app/build.gradle.kts`.
- **SessionManager needs a Context** but ViewModels can't take one directly without leaking. Fix: created
  a `ViewModelProvider.Factory` (`OnboardingViewModel.factory`) and built `SessionManager(LocalContext)`
  in the NavHost composable.
- Removed an unused `ImeAction` import to keep the build warning-clean.

**Open items to verify on backend**
- `StudentSerializer` must return a non-null `id` in the create response, or the success path treats it
  as an error.
- `Story` fields beyond the six now modeled are still unconfirmed (e.g. cover image / difficulty).

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 5 — Smoke test on emulator (PASSED)

**What was done**
- Booted Pixel_7a AVD, `./gradlew installDebug`, launched the app.
- Verified Onboarding renders, then drove the UI via adb: typed name "Kofi", school "Accra Primary",
  age group "5-7", tapped "Start Reading".
- Captured the OkHttp interceptor log and confirmed the full round trip.

**Result (from Logcat `okhttp` tag):**
```
--> POST http://10.0.2.2:8001/api/students/
{"age_group":"5-7","device_id":"a6f0da14d0012509","name":"Kofi","school":"Accra Primary"}
<-- 201 Created (616ms)   Server: WSGIServer/0.2 CPython/3.9.6
{"id":7,"name":"Kofi","school":"Accra Primary","age_group":"5-7","device_id":"...","created_at":...}
```
- App then navigated to the "Story List — coming soon" placeholder. End-to-end chain confirmed working.

**Challenges & fixes**
- First screenshot after the initial `monkey` launch showed the Story List placeholder instead of
  Onboarding (stale navigation/activity state from the launch). A clean `force-stop` + relaunch correctly
  showed Onboarding as the start destination. No code change needed; behavior is correct.
- Confirmed the Django create response includes extra fields (`created_at`, `updated_at`) the `Student`
  model doesn't declare — harmless, Gson ignores unknown keys. `id` is present, so the success path works.

**Build status:** installed & ran on Pixel_7a (API 16/Android emulator); login returns 201.

---

## Entry 6 — Phase 1 & 2 committed + pushed

- `git commit -m "Phase 1 & 2: Networking and Onboarding UI"` -> pushed to `origin/main` (b482887).
- Includes `PROGRESS_LOG.md` and `PHASE1_REPORT.md` (intentional, for hackathon tracking).

---

## Entry 7 — Phase 3: Dynamic Story Selection

**What was done**
- `utils/SessionManager.kt`: added `saveAgeGroup()` / `getAgeGroup()` (key `age_group`).
- `ui/onboarding/OnboardingViewModel.kt`: now calls `saveAgeGroup(student.ageGroup)` right after
  `saveStudentId()` on a successful login.
- New `ui/storylist/StoryListViewModel.kt`: takes `SessionManager` (via factory); on `init` reads the
  saved age group and calls `getStories(ageGroup)`. Exposes `StoryListUiState(isLoading, stories, error)`
  as a `StateFlow`.
- New `ui/storylist/StoryListScreen.kt`: Material 3. Shows `CircularProgressIndicator` while loading,
  an error/empty message otherwise, and a `LazyColumn` of elevated `Card`s (title = headlineSmall,
  lifeSkill = bodyMedium). Card click invokes `onStoryClick(story.id)`.
- `ui/navigation/AppNavigation.kt`: replaced the Story List placeholder with the real screen; added a
  typed `game/{storyId}` route (NavType.IntType) with a `GamePlaceholder`.

**Live verification on emulator**
- Re-onboarded as "Ama" / "Accra Primary" / 5-7 -> `201` student created.
- `GET http://10.0.2.2:8001/api/stories/?age_group=5-7` -> `200 OK`, 2 stories returned and rendered as
  cards ("Kofi Shares His Toys" / Interpersonal, "Whose Turn Is It?" / Communication).
- Tapped first card -> navigated to "Game for story #1 — coming soon" (storyId arg passed correctly).

**Challenges & fixes / observations**
- **`content` arrives as a JSON-encoded string**, not a plain paragraph: e.g.
  `"[\"Kofi had a big box...\", \"His friend Ama had none...\"]"`. Our `Story.content: String` deserializes
  fine (no crash), but the future reading game must parse this string into a `List<String>` of sentences.
  Flagged for Phase 4 — do NOT treat `content` as display-ready text.
- The age filter works: only the two 5-7 stories came back (the backend has others for 8-10).

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; installed & verified live on Pixel_7a.

---

## Entry 8 — Phase 3 committed + pushed

- `git commit -m "Phase 3: Dynamic Story Selection"` -> pushed to `origin/main` (15e8ee5).

---

## Entry 9 — Phase 4 (Part 1): Game Screen + sentence navigation (Vosk simulated by a button)

**What was done**
- `models/Story.kt`: added `fun Story.getSentences(): List<String>` — parses the stringified-JSON
  `content` via `Gson().fromJson(content, Array<String>::class.java)`, with a graceful fallback to a
  single-item list if the content isn't valid JSON.
- New `ui/game/GameViewModel.kt`: `GameState(story, currentSentenceIndex, isFinished, error)`.
  `loadStory(storyId)` fetches stories (age-filtered), finds the matching id, records the start time
  (`System.currentTimeMillis()`). `advanceSentence()` increments the index; when it passes the last
  sentence it sets `isFinished`, computes `durationSeconds`, and POSTs a session
  (accuracy hardcoded 100.0, empty difficult words for now — Vosk fills these in Part 2).
- New `ui/game/GameScreen.kt`: shows the current sentence in `headlineLarge` (centered); a centered
  Mic `FloatingActionButton` advances sentences; on `isFinished` it navigates to `lesson/{storyId}`.
- `ui/navigation/AppNavigation.kt`: replaced the game placeholder with `GameScreen` (storyId arg passed
  into the ViewModel); added a typed `lesson/{storyId}` route + `LessonPlaceholder`.

**Live verification on emulator**
- Onboarded "Yaw" (student id 9) -> opened "Kofi Shares His Toys" (id 1).
- Game screen showed the FIRST PARSED sentence "Kofi had a big box of shiny toy cars." (NOT the raw JSON
  array) — confirms `getSentences()` works.
- Tapped the Mic FAB 5 times to read all sentences. On the last tap:
  ```
  --> POST http://10.0.2.2:8001/api/sessions/
  {"accuracy_percent":100.0,"difficult_words":[],"duration_seconds":31,"story":1,"student":9}
  <-- 201 Created (31ms)
  {"id":2,"student":9,"story":1,"duration_seconds":31,...}
  ```
- App then navigated to "Lesson for story #1 — coming soon". Full loop confirmed.

**Challenges & fixes**
- **Mic icon not in the core icon set.** `Icons.Filled.Mic` lives in `material-icons-extended`, which
  wasn't on the classpath. Fix: added `implementation("androidx.compose.material:material-icons-extended")`
  (versioned by the Compose BOM). Build then succeeded.
- **Out-of-bounds guard:** when finished, `currentSentenceIndex` equals the sentence count. The UI reads
  the sentence with `getOrNull(index).orEmpty()` so the brief pre-navigation recomposition can't crash.
- **Re-load guard:** `loadStory()` early-returns if the story with that id is already loaded, so screen
  recomposition doesn't refetch or reset progress.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; verified live on Pixel_7a.

---

## Entry 10 — Phase 4 Part 1 committed + pushed

- `git commit -m "Phase 4 (Part 1): Game screen and session tracking"` -> pushed (580bca3).

---

## Entry 11 — Phase 4 (Part 2, infra): Vosk offline speech — assets, deps, wrapper, permission

**What was done**
- **Model asset (normally a manual step) downloaded automatically:** fetched the official
  `vosk-model-small-en-us-0.15` (~39 MB zip) and unpacked its contents into
  `app/src/main/assets/model/` (`am/ conf/ graph/ ivector/ README`, ~68 MB on disk).
- `app/build.gradle.kts`: added `net.java.dev.jna:jna:5.13.0@aar` and the Vosk Android AAR.
- `AndroidManifest.xml`: added `RECORD_AUDIO` permission.
- New `utils/SpeechRecognizerManager.kt`: takes a `Context`; `initModel()` unpacks via
  `StorageService.unpack("model","model", ...)` and builds `org.vosk.Model`; `startListening()` /
  `stopListening()` drive `Recognizer` + `SpeechService`; implements `RecognitionListener` and parses
  Vosk's JSON (`partial` / `text`) into a `SpeechState` StateFlow (`isReady`, `isListening`,
  `partialText`, `resultText`, `error`). `destroy()` releases native resources.
- `ui/game/GameScreen.kt`: requests `RECORD_AUDIO` at runtime via `rememberLauncherForActivityResult`;
  mic FAB shows `Mic` (enabled/primary) when granted and `MicOff` (dimmed) when not, re-requesting on tap.
  Speech is NOT yet wired into the game loop (still calls `advanceSentence()` — per plan, stop before wiring).

**Challenges & fixes**
- **Wrong Maven coordinates.** `com.alphacep:vosk-android:0.3.32` does NOT exist -> build failed
  ("Could not find com.alphacep:vosk-android:0.3.32"). The real group id is **`com.alphacephei`**
  (with "phei"). Verified available versions on Maven Central and switched to
  `com.alphacephei:vosk-android:0.3.47` (latest). jna `5.13.0@aar` was correct.
- **Working-directory drift.** A `cd /tmp` used for the model download left the shell there, so
  `./gradlew` wasn't found. Fixed by invoking gradle with an explicit `-p <project>` path.
- **`libjnidispatch.so` strip warning** during build — harmless (debug symbols not stripped; lib is still
  packaged). Native JNA bridge bundled correctly.
- **No native crash** on install/launch (the SIGSEGV risk). Confirmed clean Logcat.
- **Could not complete the runtime mic-permission check:** by this run the Django backend was down, so the
  app showed "Failed to connect to /10.0.2.2:8001" on onboarding and never reached the game screen. Build,
  install, dependency resolution, and crash-free launch are all verified; the on-screen permission prompt
  still needs a visual check once Django is running again.

**Open decision**
- The 68 MB model now lives in `assets/` (and will bloat both the APK and the git repo if committed).
  NOT committed yet — need a call: commit it, or gitignore `assets/model/` and keep it local-only.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL (after coordinate fix); installs & launches
without native crash.

---

## Entry 12 — Vosk model gitignored + Part-2 infra committed

- Added `app/src/main/assets/model/` to `.gitignore` (per decision: do NOT commit the 68 MB blob —
  kept local, re-downloadable). Confirmed it no longer appears in `git status`.
- `git commit -m "Phase 4 (Part 2 infra): Vosk deps, RECORD_AUDIO, SpeechRecognizerManager, mic permission UI"`
  -> pushed (1724af2).

---

## Entry 13 — Phase 4 (Part 2, wiring): speech graded into the game loop

**What was done**
- `ui/game/GameViewModel.kt`:
  - Constructor now also takes `SpeechRecognizerManager`; factory updated to provide both managers.
  - `initSpeechEngine()` calls `speechManager.initModel()` on `Dispatchers.IO`.
  - `GameState` gained `isSpeechEngineReady`, `isListening`, `partialText`.
  - `observeSpeech()` collects `speechManager.state`, mirrors it into `GameState`, and fires
    `onSentenceRecognized()` once per new final result (guarded by `lastEvaluatedResult`).
  - `evaluateReading(spoken, expected)`: lowercases + strips punctuation via regex, splits into words,
    `accuracyPercent = matched/expected*100`, `difficultWords = expected words not spoken`. Accumulates
    `accuracySum`, `sentencesEvaluated`, and a `linkedSetOf` of session-wide difficult words.
  - On finish, `submitSession()` POSTs the REAL average accuracy (rounded 2 d.p.) and the master
    difficult-words list.
  - `onMicPressed()` toggles listening; `onCleared()` calls `speechManager.destroy()` (no native leak).
  - Current UX: auto-advances to the next sentence as soon as a final result is graded (per the prompt).
- `ui/game/GameScreen.kt`: shows "Loading Speech Engine…" spinner until `isSpeechEngineReady`; mic FAB
  toggles listening (Mic / Stop / MicOff icons, red while recording); shows live `partialText` under the
  sentence.
- `ui/navigation/AppNavigation.kt`: game route now builds `SpeechRecognizerManager(context)` for the VM.

**Challenges & fixes**
- **Duplicate-result guard:** Vosk's `state` is a hot StateFlow holding the latest value, so a naive
  `collect` would re-grade the same `resultText` on every emission. Added `lastEvaluatedResult` and reset
  it when a new listen starts, so each utterance is graded exactly once.
- **Accuracy rounding:** average accuracy rounded to 2 d.p. via `(x*100).roundToInt()/100.0` so the JSON
  sends e.g. `83.33`, not `83.33333`.

**Not yet verified at runtime (blocked):**
- Django was down this session, so the game screen (reached only after onboarding+story-list network
  calls) could not be opened to confirm: model unpack/init with NO native SIGSEGV, the "Loading Speech
  Engine…" state, live partial text, and a real graded `POST /api/sessions/`.
- Emulators have no physical mic; Vosk will run on the synthetic audio stream without crashing but will
  likely transcribe nothing. A real device (or piped audio) is needed to see non-zero accuracy.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 14 — Phase 5: manual "Next" UX + Lesson screen (front-to-back complete)

**What was done**
- **UX decision: manual "Next" button** (chosen over auto-advance — early readers pause unpredictably).
  - `GameViewModel`: `onSentenceRecognized()` now grades + stops listening but NO LONGER auto-advances.
    `advanceSentence()` stops listening, resets `lastEvaluatedResult`, and clears `partialText`.
  - `GameScreen`: added a "Next ▶" Button under the live transcript that calls `advanceSentence()`.
- New `ui/lesson/LessonViewModel.kt`: loads the story by id (age-filtered `getStories()` + filter),
  exposes `LessonUiState(isLoading, story, error)`.
- New `ui/lesson/LessonScreen.kt`: celebratory Material 3 finish screen — "🎉 Great reading!", the story
  title, an elevated `primaryContainer` card showing "Today's skill: <lifeSkill>" + the `lifeSkillLesson`
  text, and a full-width "Back to Stories" button. Scrollable for long lessons.
- `AppNavigation`: replaced the lesson placeholder with `LessonScreen`; "Back to Stories" navigates to
  `STORY_LIST` with `popUpTo(STORY_LIST){inclusive=true}` to clear game+lesson from the backstack.
  Removed the now-unused placeholder + its imports.

**Challenges & fixes**
- Cleaned up dead imports (`Box`, `fillMaxSize`, `Alignment`, `Modifier`, `Text`) left behind when the
  last placeholder was deleted, to keep the build warning-clean.
- Used `Icons.AutoMirrored.Filled.ArrowForward` (not the deprecated `Icons.Filled.ArrowForward`) for RTL
  correctness.

**Not yet verified at runtime (blocked):** Django still down (`curl localhost:8001` -> HTTP 000), so the
full loop (Onboarding -> Story List -> Game -> Lesson) hasn't been exercised this session. Needs Django
up; then a manual Next walk-through will confirm navigation + a session POST, and the virtual mic will
confirm real transcription/accuracy.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 15 — FULL END-TO-END LOOP VERIFIED on emulator (Django up)

**Result: the entire pipeline works.** Onboarding -> Story List -> Game (Vosk model loads, "Next" button)
-> session POST `201` -> Lesson screen (life-skill card) -> Back to Stories (backstack cleared).

**Two real bugs caught and fixed during this run:**

1. **Stale APK — I was testing an old build.** After the Phase 4-wiring and Phase 5 code changes I had run
   `assembleDebug` but NOT `installDebug`, so the emulator was still running an older APK (no "Next"
   button; the old auto-advance behaviour made the sentence jump on its own). Fix: always `installDebug`
   before a runtime test. Re-installing showed the correct Phase 5 UI.

2. **Vosk model missing its `uuid` file (the real blocker).** `StorageService.unpack` was hanging on
   "Loading Speech Engine…" forever — no `files/model`, no logs. The executor inside `unpack` SWALLOWS
   exceptions, so nothing surfaced. Added `Log` + `LibVosk.setLogLevel(INFO)` + a try/catch in
   `SpeechRecognizerManager.initModel()`, which revealed:
   `java.io.FileNotFoundException: model/uuid at StorageService.sync(StorageService.java:76)`.
   The generic `vosk-model-small-en-us-0.15` zip from the website does NOT include the `uuid` file that
   Vosk's Android `sync()` requires to version the copied model. Fix: created
   `app/src/main/assets/model/uuid` (contents: the model name). After that, VoskAPI loaded the model
   (`unpack onComplete: model ready`) and the game screen became interactive.
   - NOTE: the model unpacks to the EXTERNAL files dir
     (`/storage/emulated/0/Android/data/<pkg>/files/model/model`), which is why `run-as ls files/model`
     (internal) showed nothing — that was a red herring, not a failure.

**Verified live (Logcat `okhttp`):**
```
--> POST http://10.0.2.2:8001/api/sessions/
{"accuracy_percent":0.0,"difficult_words":[],"duration_seconds":886,"story":1,"student":10}
<-- 201 Created
{"id":3,"student":10,"story":1,"duration_seconds":886,"accuracy_percent":0.0,...}
```
(accuracy 0.0 / empty difficult_words is EXPECTED — I drove the loop with the "Next" button and cannot
speak into the mic. duration 886s reflects debugging time, not a real read.)

**⚠️ IMPORTANT for a fresh clone:** the model dir is gitignored, so anyone re-downloading the Vosk model
MUST also create `app/src/main/assets/model/uuid` (any short string), or the app hangs on "Loading Speech
Engine…". Documented here + in `.gitignore`.

**Build status:** installs & runs; full loop green.

---

## Entry 16 — Polish: README, sentence progress indicator, log cleanup

**What was done**
- **README.md** created at project root: setup, flow, backend command, and a prominent section on the
  Vosk model + the REQUIRED `uuid` file (with the explanation of the silent-hang trap).
- **Game progress indicator:** `GameScreen.kt` restructured into a top-level Column — a
  `LinearProgressIndicator(progress = { ... })` + "Sentence X of N" counter at the top, with the sentence
  area in a weighted Box below. Shown only once the story is loaded and the engine is ready.
- **Log cleanup:** in `SpeechRecognizerManager.initModel()` removed `LibVosk.setLogLevel(INFO)` (verbose
  native logs) and the routine `Log.i` lines (+ their imports). KEPT the try/catch and a single `Log.e`
  on the genuine failure paths — logging real failures is good practice and is exactly what guards against
  the silent-executor-swallow trap recurring; it never fires on the happy path.

**Verified live on emulator**
- Re-ran onboarding ("Abena") -> game screen now shows the progress bar at ~1/5 and "Sentence 1 of 5"
  above "Kofi had a big box of shiny toy cars.", with the Next button and blue mic. Model still loads
  fine after the log cleanup.

**Challenges & fixes**
- Used the current Material3 `LinearProgressIndicator(progress = { progress }, ...)` lambda overload
  (the `progress: Float` overload is deprecated in the BOM in use).
- Guarded the counter with `coerceAtMost(total)` / progress `coerceIn(0f,1f)` so the brief
  finished-state (index == size) can't render "Sentence 6 of 5" before navigation fires.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; verified live.

---

## Entry 17 — Accuracy score on the Lesson screen (gamification loop closed)

**What was done**
- `GameViewModel`: added `finalAccuracy: Float` to `GameState`; on finish it computes the rounded average
  accuracy once and stores it in state (and passes the same value to `submitSession`, which no longer
  recomputes — single source of truth).
- `GameScreen`: `onFinished` is now `(storyId, accuracy) -> Unit`; fires `onFinished(storyId, finalAccuracy)`.
- `AppNavigation`: lesson route is now `lesson/{storyId}/{accuracy}` (IntType + FloatType); game's
  `onFinished` passes the accuracy through.
- `LessonViewModel`: `loadStory(storyId, accuracy)` stores accuracy in `LessonUiState`.
- `LessonScreen`: takes an `accuracy` arg and shows "Reading Accuracy: X%" (`headlineMedium`,
  `colorScheme.tertiary`) above the life-skill card.

**Verified live on emulator**
- Full loop as "Adwoa"; Lesson screen shows "Reading Accuracy: 0%" in tertiary color above the card
  (0% expected — drove with Next, no speech). UI scoring loop confirmed end-to-end.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; verified live.

---

## Status: feature-complete for the hackathon demo
Full pipeline verified on the emulator: Onboarding -> Story List -> Game (Vosk, progress bar, Next) ->
session POST -> Lesson (accuracy score + life skill) -> Back to Stories.

## Entry 18 — Backend prepped for Railway deployment (so a physical phone can reach it over HTTPS)

NOTE: this work is in the SEPARATE Django repo `/Users/ellis/Documents/GitHub/literacyappBackend`
(project package `backend`, i.e. `backend.wsgi` / `backend.settings`), located by searching the disk —
it is NOT in the Android project workspace.

**What was done (Django backend)**
- `backend/settings.py`:
  - `SECRET_KEY` and `DEBUG` now read from env (fallbacks for local dev).
  - `ALLOWED_HOSTS` scoped to local hosts + `.up.railway.app` / `.railway.app` (NOT a blanket `*`);
    added matching `CSRF_TRUSTED_ORIGINS`.
  - Added `whitenoise.middleware.WhiteNoiseMiddleware` right below SecurityMiddleware.
  - `DATABASES` via `dj_database_url.config(default=sqlite…)` — uses Railway `DATABASE_URL` (Postgres)
    in prod, SQLite locally.
  - Static: `STATIC_ROOT=staticfiles`, `STATICFILES_STORAGE=whitenoise…CompressedManifestStaticFilesStorage`.
- `Procfile`: `web: python manage.py migrate && (python manage.py loaddata stories || true) && gunicorn backend.wsgi --bind 0.0.0.0:$PORT`
- `requirements.txt`: regenerated via venv `pip freeze` (added dj-database-url, psycopg2-binary, whitenoise).
- `.gitignore` added (venv, db.sqlite3, __pycache__, staticfiles, .env); `git init` + first commit done.
- Verified: `manage.py check` → 0 issues; `loaddata stories` → "Installed 3 object(s)".

**Key gotcha caught:** the seeded stories live in LOCAL SQLite; Railway's Postgres starts EMPTY. The
fixture `api/fixtures/stories.json` (3 stories) is the reproducible seed, so the Procfile runs
`loaddata stories` on every boot (idempotent; `|| true` so a hiccup can't block startup).

**Build status:** Django system check clean; backend committed locally, push-ready.

---

## Entry 19 — Backend moved INTO the monorepo (decision: single repo)

**Decision:** the Django backend now lives inside the Android repo at `LiteracyApp/backend/` (monorepo),
instead of a separate repo. Reasons: one repo/history/PROGRESS_LOG for a solo hackathon; frontend +
API contract change together; no second GitHub repo to manage.

**What was done**
- `rsync`'d `~/Documents/GitHub/literacyappBackend/` → `LiteracyApp/backend/`, EXCLUDING `venv/`,
  `.git/`, `__pycache__/`, `*.pyc`, `db.sqlite3`, `staticfiles/`.
- The Django project's own `.gitignore` came along as a NESTED gitignore (`backend/.gitignore`), so its
  Python/venv/sqlite excludes apply within `backend/` without touching the Android root `.gitignore`.
- Committed under the existing `github.com/lerryellis/literacyapp` repo.

**Local-dev note:** the canonical backend location is now `LiteracyApp/backend/`. A venv was NOT copied —
recreate it there for local runs (`python -m venv venv && venv/bin/pip install -r requirements.txt`), and
run `python manage.py runserver 0.0.0.0:8001` from `LiteracyApp/backend/`. The OLD
`~/Documents/GitHub/literacyappBackend/` folder is now superseded — safe to delete once the monorepo is
confirmed working (left in place for now; not deleted automatically).

**Railway:** deploy the existing `literacyapp` repo with **Root Directory = `backend`** (so it finds
`backend/Procfile`, `requirements.txt`, `manage.py`). No new GitHub repo needed.

---

## Status: Android app feature-complete; backend in monorepo, ready to deploy

## Entry 20 — LIVE on Railway; app pointed at production HTTPS

**Deployed & verified.** Railway URL: `https://literacyapp-production-b4fe.up.railway.app/`
- `GET /api/stories/` from the host -> `200`, returns all 3 seeded stories (Procfile `loaddata` worked
  against the fresh Postgres). Confirms migrate + seed ran on deploy.
- `RetrofitClient.BASE_URL` switched from `http://10.0.2.2:8001/` to the Railway HTTPS URL (kept the
  emulator-loopback URL in a comment for local dev). Rebuilt.
- Ran onboarding on the emulator against production:
  ```
  --> POST https://literacyapp-production-b4fe.up.railway.app/api/students/  <-- 201 (server: railway-hikari)
  --> GET  .../api/stories/?age_group=5-7                                    <-- 200
  ```
  `railway-hikari` / `x-railway-edge` headers confirm it hit the live Postgres-backed service. Story list
  rendered from production data. Full cloud pipeline works (no localhost involved).

**Build status:** BUILD SUCCESSFUL; verified live against Railway.

---

## Status: FULLY DEPLOYED — Android app (HTTPS) + Django/Postgres on Railway

## Entry 21 — UI refresh (playful kid-friendly theme) + login screen redesign

**Direction chosen by user:** "Playful & kid-friendly" — sunny orange + teal + cream. Starting with the
login (onboarding) page first. (Game screen will later show a character with different expressions beside
the text — needs art assets, deferred.)

**What was done**
- New theme palette `ui/theme/Color.kt`: sunny orange primary, teal secondary, grape tertiary, warm cream
  background/surface, sand variant, soft outline.
- `ui/theme/Theme.kt`: single consistent light scheme — REMOVED dynamic color + dark mode so the playful
  palette is identical on every device. `LiteracyAppTheme` is now just `(content)`.
- `ui/theme/Type.kt`: large, bold, high-contrast type for early readers (display 40 / headlineLarge 34 /
  bodyLarge 18, etc.).
- `ui/onboarding/OnboardingScreen.kt` redesigned: 📚 header, big "Let's Read!" title, rounded text fields,
  two large tappable age "pills" (selected = orange, unselected = mint) replacing radio buttons, a big
  rounded "Start Reading 🚀" button; scrollable for the keyboard.

**Verified live:** rebuilt + installed; login screen renders the new playful look correctly on the emulator.

**Build status:** BUILD SUCCESSFUL.

---

## Status: deployed + login screen restyled; remaining UI screens pending

## Entry 22 — Game screen redesigned as "Story Flow" (from user's design reference)

**Adopted from the provided `com.literacyapp.design` reference** (Flutter-derived), themed to OUR
orange/teal/cream palette rather than its periwinkle/purple:
- New `GameScreen.kt` layout: "📖 Story Flow" top bar, progress + "Sentence X of N", a peach
  "Read this aloud:" card (big sentence), a sand "hearing" card with a listening dot + live heard text +
  a **Confidence Meter**, a feedback banner tinted green/amber/red, a wide mic button (orange → red while
  listening), a manual "Next" button (✅ when ≥70%), and a stats row (Done / Match%).
- **Confidence meter** (the key element): gradient fill (green ≥70 / amber ≥50 / red) with a fixed 70%
  pass-threshold marker; added the semantic ramp colors to `ui/theme/Color.kt`.
- `GameViewModel`: now computes a LIVE `matchConfidence` (0..1) of heard-vs-expected words on every
  speech emission, plus `heardText`; reuses one `wordMatchRatio()` for both the live meter and final
  grading. Reset on advance.

**NOT adopted (deliberately):** the bouncing 3D age-balls home screen — it collects a single age (5–18),
but our backend needs name + school + age-GROUP (5-7 / 8-10) and we just shipped that login. Flagged as
an optional separate splash. Game-screen "character with expressions beside the text" still pending art.

**Verified:** built + installed; navigated login → story list → game. Verified the full Story Flow layout
via uiautomator dump (image-screenshot reading was erroring transiently this session): all elements
present and correct ("Read this aloud", "Match Confidence 0%", mic button, Next, stats).

**Build status:** BUILD SUCCESSFUL; verified live on emulator.

---

## Next up
- Story List: color-coded cards + per-skill icons/emoji.
- Lesson: ⭐ stars scaled to accuracy + confetti.
- (Optional) game character-with-expressions beside the text — needs art assets.
- **Voice accuracy test (user-only):** physical phone, read aloud, confirm non-zero `accuracy_percent` +
  real `difficult_words`. I cannot do this (no mic/voice).
