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

## Next up (not yet done)
- Commit Phase 3.
- Phase 4: the reading game screen — parse `content` (stringified JSON array) into sentences, time the
  read, track accuracy + difficult words, then `POST api/sessions/`.
