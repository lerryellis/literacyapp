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

## Entry 23 — Game screen delight: live word highlighting, reactive buddy, motion, ding

**Added (all asset-free, themed):**
- **Reactive "Reading Buddy"** (`ui/game/ReadingBuddy.kt`): emoji that reacts to live confidence —
  😄 ≥70% / 😊 ≥50% / 👂 listening / 🙂 idle. Gentle infinite bob; pops bigger when reading well; tinted
  circle background. Sits BESIDE the sentence (the "character beside the text" idea, placeholder art).
- **Live word highlighting:** each expected word turns green+bold the moment Vosk recognizes it
  (`highlightSentence()` builds an AnnotatedString from `heardText`).
- **Swoosh:** `AnimatedContent` slides+fades the sentence on advance.
- **Mic pulse:** the mic button gently pulses (infinite scale) while listening.
- **Ding:** `rememberDing()` uses the system `ToneGenerator` (no audio asset) to chime once when
  confidence first crosses 70% per sentence.

**Verified:** built + installed; navigated to the game screen; uiautomator dump confirms the buddy (🙂)
and all elements render with no crash. NOTE: image-screenshot reading was still erroring this session, so
the MOTION quality (bob/pulse/swoosh) and the green-highlight effect were NOT visually verified by me —
needs an eyeball on the emulator/device.

**Upgrade path (needs user):** swap the emoji buddy for real art — a **Lottie `.json`** (add
`com.airbnb.android:lottie-compose`) or a **PNG set per expression**. Also an optional custom "ding"
`.ogg`/`.wav` instead of the system tone.

**Build status:** BUILD SUCCESSFUL.

---

## Entry 24 — Avatar expression logic + age→character mapping (art still pending)

HONEST scope note: the avatar PNGs do NOT exist yet, so this entry implements the *architecture and
reactive logic only*; the visual is still the emoji buddy. (I did not commit the pre-written "Phase 6"
entry that claimed 15 PNGs bundled / pensive-swap verified — none of that happened.)

**What was actually done**
- `GameViewModel`: reads `SessionManager.getAgeGroup()` in `init` and maps it to a character via
  `characterFor()` — "5-7"→Kofi, "8-10"→Ama (11-13→Yaw, 14-15→Esi, 16-18→Musa wired for a FUTURE
  age-group expansion; not reachable today). Added `characterName` + `isStruggling` to `GameState`.
- **Struggle timer:** a `struggleJob` coroutine sets `isStruggling=true` after `STRUGGLE_DELAY_MS`
  (10 s) of listening with no recognized words; cleared the instant words are heard / on stop / on
  advance.
- `ReadingBuddy`: now also shows 🤔 (struggling) and is documented with the EXACT 3-step swap to real
  art (drop PNGs in res/drawable → add `avatarRes(character, expression)` → replace `Text(emoji)` with
  `Image(painterResource(...))`). Reactive state is already computed, so the swap is purely visual.
- `GameScreen`: shows the buddy + the character name ("Kofi") beside the sentence.

**Verified (UI dump, not pixels — image-screenshot reading still erroring this session):** onboarding
"Abena" age 5-7 → game screen shows the 🙂 buddy and the label **"Kofi"**, all elements present, builds &
runs, no crash. The struggle/expression SWITCHING and motion were NOT visually verified by me.

**⚠️ Data-model mismatch to resolve before the 5-character set makes sense:** the app + backend currently
support only TWO age groups (5-7, 8-10). The 5-character plan (up to 16-18) needs onboarding + backend +
seeded stories for the extra groups first. Today only Kofi & Ama are reachable.

**Asset prompts:** user supplied detailed image-gen prompts for 5 characters × 3 expressions; art to be
generated by the user and dropped into res/drawable (transparent PNGs).

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 25 — Expanded to 5 age groups (5-7, 8-10, 11-13, 14-15, 16-18)

**What was done**
- No migration needed: `Student.age_group` / `Story.age_group` are free `CharField`s (no choices).
- **Backend seed:** added 6 new stories to `backend/api/fixtures/stories.json` (now 9 total) — 2 each for
  11-13 (Teamwork, Critical Thinking), 14-15 (Leadership, Time Management), 16-18 (Integrity, Civic
  Responsibility), with reading level scaled up by age. Validated locally (all `content` fields parse as
  sentence arrays).
- **Onboarding:** age selector expanded from 2 to 5 options; switched from a 2-pill Row to a Column of
  full-width "Ages X-Y" pills (`OnboardingScreen.kt`).
- Character mapping already present in `GameViewModel.characterFor()` → 11-13 Yaw, 14-15 Esi, 16-18 Musa.

**Verified live (against Railway):**
- Pushed → Railway AUTO-DEPLOYED in ~10s; Procfile `loaddata stories` re-seeded Postgres.
  `GET /api/stories/?age_group=` confirms 11-13, 14-15, 16-18 all return their 2 stories.
- App end-to-end (UI dump; image-screenshot reading still erroring): onboarding shows all 5 pills →
  picked "Ages 11-13" → story list shows the 11-13 stories → opened one → game header shows character
  **"Yaw"** + "Yaw's class entered the school science fair as a team." Age→stories→character all correct.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; verified live end-to-end.

---

## Entry 26 — Story List + Lesson playful redesign

**What was done**
- New `ui/theme/SkillVisuals.kt`: `skillVisual(skill)` → an emoji + accent color per life skill
  (🤝 Interpersonal, 🗣️ Communication, 🧩 Teamwork, 🧠 Critical Thinking, 🌟 Leadership, ⏰ Time
  Management, 🛡️ Integrity, 🌍 Civic Responsibility, 🪞 Self-awareness; deterministic color fallback).
- **Story List** (`StoryListScreen.kt`): cards redesigned — skill emoji in a colored circle, bold title,
  skill name in the skill's accent color, trailing arrow; "📚 Choose a Story" orange top bar.
- **Lesson** (`LessonScreen.kt`): added looping **confetti** (`ConfettiBurst.kt`, asset-free Canvas) and
  an **accuracy-scaled star row** (3 stars: ≥80% = 3, ≥50% = 2, else 1; stars pop in one-by-one with a
  bouncy spring). Skill emoji now prefixes "Today's skill".

**Verified (UI dump — image-screenshot reading STILL erroring this session):**
- Story list shows the new cards: 🤝 "Kofi Shares His Toys" / Interpersonal, 🗣️ "Whose Turn Is It?" /
  Communication.
- Completed a story → Lesson screen shows "🎉 Great reading!", "Reading Accuracy: 0%",
  "🤝 Today's skill: Interpersonal", the lesson text, and "Back to Stories". No crash.
- NOTE: stars + confetti are Canvas/Icon (no text nodes), so their VISUAL/motion was not eyeballed by me;
  they compile and render in the tree. Needs a glance on device.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 27 — Background images: splash screen + login background

**Assets:** user added `splashscreen.png` (1.5 MB) and `menu.png` (1.4 MB) to `res/drawable-nodpi/`.
- Converted both PNG→JPEG q85 (opaque backgrounds, no transparency needed): **splash_background.jpg**
  (388 K) and **menu_background.jpg** (325 K) — ~2.2 MB saved. Removed the originals and the
  `PUT_BACKGROUNDS_HERE.txt` (a non-image file in a drawable folder breaks aapt).
  (sips can't emit WebP and cwebp wasn't installed, so JPEG was the available + correct choice for
  opaque full-screen art.)

**Wiring (user choices):**
- **Splash screen** (new `ui/splash/SplashScreen.kt`): full-bleed `splash_background` (ContentScale.Crop),
  auto-advances after 1.6 s. Added `Routes.SPLASH` as the NavHost start destination → navigates to
  onboarding with `popUpTo(SPLASH, inclusive=true)`.
- **Login background**: `menu_background` painted full-bleed behind the onboarding form, with an 82%
  `background`-color scrim so the fields/text stay readable.

**Verified (UI dump; image-screenshot reading STILL erroring this session):** launch → splash (image, no
text nodes) at ~1s → onboarding ("Let's Read!") at ~4s; no crash. Image VISUALS not eyeballed by me —
needs a device glance.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 28 — Custom app launcher icon

- User supplied `iconmain.png` (760×762, ~square). Kept a source copy in `branding/iconmain.png`
  (removed the earlier portrait `AYOLAICON.png` — superseded).
- Center-cropped to a perfect square and generated legacy launcher icons at all densities
  (`mipmap-{mdpi..xxxhdpi}/ic_launcher.png` + `ic_launcher_round.png`); removed the old default
  `ic_launcher*.webp`.
- Removed the adaptive `mipmap-anydpi-v26/ic_launcher*.xml` so the legacy PNGs are used everywhere
  (reliable when I can't preview adaptive-mask cropping). Manifest already points at `@mipmap/ic_launcher`.
- Verified: APK bundles all 5 density icons; reinstalled OK. (Icon VISUAL not eyeballed — image reading
  still down this session.) If it has transparency / needs an adaptive look, regenerate via Android
  Studio's Image Asset Studio.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

---

## Entry 29 — App icon: adaptive + sizing iterations

- User replaced the mipmaps with a 1254×1254 image (1.4 MB) copied into every density bucket (functional
  but adds ~14 MB; can be right-sized on request).
- Icon looked "small on white" → that's the legacy-icon fallback on Android 8+. Fix: restored an
  **adaptive icon** (`mipmap-anydpi-v26/ic_launcher*.xml`) with the image as a full-bleed foreground +
  white background color (`@color/ic_launcher_background`).
- Measured the source (PIL): content fills 100% (no transparent padding), so smallness was purely the
  legacy treatment.
- Sized per feedback: full-bleed → too big → inset foreground via transparent padding. Two "same-action"
  steps landed at **~63%** logo (20 px padding at mdpi, scaled per density: 20/30/40/60/80).
- (Icon VISUAL not eyeballed — image-screenshot reading down all session; verified via build + PIL only.)

---

## Entry 30 — Onboarding redesign over the background + back-nav to age selection

**Navigation**
- From the Story List you can now go **back to age selection**: removed the
  `popUpTo(ONBOARDING, inclusive)` on login→storylist so onboarding stays on the back stack, and added a
  back arrow in the Story List top bar (`onBack = popBackStack`).
- **Bug fixed:** returning to onboarding immediately bounced forward again because the retained
  `OnboardingViewModel` still had `loginSuccess = true`. Added `onLoginHandled()` to consume the one-time
  nav event; the screen calls it right after `onLoginSuccess()`. Verified: storylist → BACK → age
  selection (no bounce).

**Onboarding layout (per the background art)**
- Removed the "Let's Read!" title, the 📚 icon, the "Tell us about you…" subtitle, the "How old are you?"
  label, and the scrim — so the `menu_background` art shows.
- `menu_background` is now full-bleed (`ContentScale.Crop`). Name/school are semi-opaque white-filled
  `OutlinedTextField`s (readable over art); the 5 ages are a neat wrapping `FlowRow` of pill chips.
- **Vertical placement is a BLIND first pass** (image reading down): tunable weights at the top of
  `OnboardingScreen.kt` — `TOP_SPACE=44` (inputs/pointing-tip), `MID_SPACE=26` (ages/white-space),
  `BOTTOM_SPACE=16`. Needs the user's eyes to dial in.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; reinstalled; nav verified via UI dump.
**APK:** `app/build/outputs/apk/debug/app-debug.apk` (~115 MB) — current, ready to sideload/export.

---

## Entry 31 — Friendly error handling + proper logging

- New `utils/ErrorMessages.kt`:
  - `forException(context, t)` maps exceptions to child/parent-friendly text AND logs the real cause
    (`Log.e`, tag "LiteracyApp"): UnknownHostException → "No internet connection. Please check your Wi-Fi
    or mobile data and try again.", SocketTimeout → timed out, ConnectException → couldn't reach server,
    SSLException → secure-connection problem, other IOException → network problem, else generic.
  - `forHttp(context, code)` maps status codes to friendly text + logs (`Log.w`): 5xx → server problem,
    408/429 → busy, 404 → not found, 4xx → check details.
- Wired into all ViewModels (Onboarding, StoryList, Game, Lesson) — replaced raw `e.message` /
  `"HTTP <code>"` strings. GameViewModel's previously-silent `createSession` failure now logs too.
- **Verified live:** disabled emulator network → login showed "No internet connection. Please check your
  Wi-Fi or mobile data and try again." on screen, while Logcat showed
  `E LiteracyApp: createStudent failed: UnknownHostException: Unable to resolve host ...`.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; verified on emulator.

---

## Entry 32 — Remove splash, length-based listening window, real avatar art wired

**Splash removed** — `ui/splash/SplashScreen.kt` deleted, `Routes.SPLASH` gone, NavHost start is now
`ONBOARDING`. (`splash_background.jpg` left in drawable-nodpi, now unused — can delete later.)

**Listening window (gamified by sentence length)** — `GameViewModel`:
- Replaced the fixed 10 s struggle timer + "stop on first Vosk final" behaviour. Now a tap starts a
  listening WINDOW = `LISTEN_BASE_MS(6s) + LISTEN_PER_WORD_MS(1.5s) * wordCount`, capped 30 s — recomputed
  per sentence, so longer lines get more time (5 words ≈ 13.5 s, 12 words ≈ 24 s).
- Heard segments now ACCUMULATE in a buffer across the window (Vosk finals appended), so pausing
  mid-sentence no longer erases earlier words; confidence/highlight use the combined text.
- Grades once per sentence on stop (window elapsed or mic tapped again); `resetSentenceListening()` clears
  buffer/guards/timers on advance. Struggling face shows only after 60% of the window with nothing heard.

**Avatar art wired** (user added the PNGs):
- Renamed the dropped files (invalid CamelCase names broke the build) to a consistent scheme:
  `kofi/ama/yaw _ happy/struggling/excited.png` in res/drawable. Moved the stray `avatars*.png` sheets to
  `branding/avatars/`.
- `ReadingBuddy` now resolves `<character>_<expression>` via `resources.getIdentifier` and renders the
  real `Image` (bob/pop animation kept); falls back to the emoji face for characters without art yet
  (Esi/Musa). Expression: excited ≥70%, struggling, else happy.
- Verified live: game screen `content-desc` = "Yaw, happy" → real art rendering, no crash.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; verified on emulator.

---

## Entry 33 — Esi + Musa avatar art added (all 5 characters complete)

- User added Esi/Musa PNGs (CamelCase again → broke the build). Renamed to the scheme:
  `esi_{happy,struggling,excited}.png`, `musa_{happy,struggling,excited}.png` (Static→happy).
- No code change needed — `ReadingBuddy` resolves them by name. Now all 5 characters have full art
  (15 PNGs: kofi/ama/yaw/esi/musa × happy/struggling/excited).
- Verified live: onboarded at age 14-15 → game screen `content-desc` = "Esi, happy" (real art rendering).
- **APK rebuilt** (`app-debug.apk`, ~115 MB) — was failing on the invalid names; now BUILD SUCCESSFUL.

---

## Entry 34 — Django admin: student profile card + progress chart (user-authored)

- User enhanced `backend/api/admin.py` `StudentAdmin`:
  - **Profile card** (`profile_card`): avatar initial, name/school/age/device, session count, avg accuracy,
    last-read date, and a status badge (🌟 High Performer ≥80% / ⚠️ Needs Support <50% / 🔥 Regular >10
    sessions / 👍 On Track / 🆕 First Time).
  - **Progress chart** (`progress_chart` + `_progress_svg`): server-rendered inline **SVG** line chart of
    accuracy per session over time (green/amber/red dots by score, 70%-style gridlines) — no JS/CDN, so it
    always renders in the admin.
  - Organised into fieldsets (Profile / Details / Progress over time).
- Verified: `python manage.py check` → 0 issues. (Applies to the live Railway admin after deploy.)

---

## Entry 35 — Updated menu background + full app documentation

- User replaced `menu_background.jpg` (now 1684×2528). NOTE: it's actually a PNG saved with a `.jpg`
  extension, ~5 MB — loads fine (Android decodes by content) but bumped the APK to ~117 MB; offered to
  re-encode to a true JPEG to slim it.
- Removed 4 stray `.DS_Store` files from `res/` (would break aapt). Rebuilt + installed; committed (82f06ae).
- Wrote **`DOCUMENTATION.md`** — detailed technical docs: overview, user flow, **use-case diagram**
  (Mermaid + ASCII, actors = Child / Teacher-Admin / Backend), architecture + project structure, data
  model + REST API, Vosk speech + scoring + listening window, gamification, error handling, teacher admin
  dashboard, setup/build/run, deployment, and known constraints.

**Build status:** `./gradlew assembleDebug` -> BUILD SUCCESSFUL; APK ~117 MB installed.

---

## Next up
- USER: eyeball onboarding placement (TOP_SPACE/MID_SPACE), backgrounds, icon, all 5 avatars, game motion
  on a device (image reading is down on my side); tune listening window if still too short/long.
- (Optional) re-encode `menu_background` to true JPEG to slim the APK (~5 MB → a few hundred KB).
- **Voice accuracy test (user-only):** physical phone, read aloud, confirm non-zero `accuracy_percent`.
- USER: generate avatar PNGs (Kofi/Ama/Yaw/Esi/Musa × happy/struggling/excited) → I wire `avatarRes()`.
- **Voice accuracy test (user-only):** physical phone, read aloud, confirm non-zero `accuracy_percent`.
