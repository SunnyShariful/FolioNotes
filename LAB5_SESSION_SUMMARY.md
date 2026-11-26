# MADT Lab 5 — AI-Assisted Development Session Summary

**Project:** FolioNotes — Dual-Storage Local Notebook  
**Repository:** [https://github.com/SunnyShariful/FolioNotes](https://github.com/SunnyShariful/FolioNotes)  
**Session Reference:** <@ListOfsessions>

---

## 1. Project Overview

**FolioNotes** is a dual-storage local Android notebook application built with **Kotlin** and **Jetpack Compose**. The project was developed across four separate AI-assisted sessions combined with my own architectural planning, documentation review, local Android Studio work, and Git/GitHub management.

The application implements:
- Full note CRUD (Create, Read, Update, Delete)
- Dual local persistence using `SQLiteOpenHelper` as primary storage and `SharedPreferences` as secondary storage
- User-selectable storage destination and note filtering
- Manual SQLite-to-SharedPreferences synchronization with deletion tracking
- Input validation and localization-ready UI text using English string resources

---

## 2. Session List (<@ListOfsessions>)

1. **Code Generation:** [https://aistudio.google.com/apps/5060594c-042e-4fd2-bd29-a42b4fe4c49d?showPreview=true&showAssistant=true](https://aistudio.google.com/apps/5060594c-042e-4fd2-bd29-a42b4fe4c49d?showPreview=true&showAssistant=true)
2. **Code Review and Improvements:** [https://aistudio.google.com/apps/0967b737-2bf7-48bd-b3e4-a726fd03c651?showPreview=true&showAssistant=true](https://aistudio.google.com/apps/0967b737-2bf7-48bd-b3e4-a726fd03c651?showPreview=true&showAssistant=true)
3. **Documentation Generation:** [https://aistudio.google.com/prompts/1sF5ZAYKlcKjGM3vwUltCqsIsxZ_5TXlF](https://aistudio.google.com/prompts/1sF5ZAYKlcKjGM3vwUltCqsIsxZ_5TXlF)
4. **GitHub Interaction:** This current AI Studio conversation (subsequent to my initial local Git setup and GitHub push)

---

## 3. Session-by-Session Breakdown

### Session 1: Code Generation
**Link:** [https://aistudio.google.com/apps/5060594c-042e-4fd2-bd29-a42b4fe4c49d?showPreview=true&showAssistant=true](https://aistudio.google.com/apps/5060594c-042e-4fd2-bd29-a42b4fe4c49d?showPreview=true&showAssistant=true)

**Purpose:**  
Plan and generate the initial implementation of the FolioNotes Android application.

**My Input, Decisions, and Participation:**
- I started the development process using the `/grill-me` instruction to plan and structure the application before code generation.
- I selected **Google AI Studio** and chose **Kotlin with Jetpack Compose**.
- I participated directly in the planning decisions, including:
    - `SQLiteOpenHelper` as primary storage
    - `SharedPreferences` as secondary storage
    - Storage selection and note filtering
    - Manual synchronization
    - Input validation
    - English string resources

**AI Output and Outcome:**  
The AI generated the FolioNotes application implementing note CRUD, dual local storage, storage selection, filtering, SQLite-to-SharedPreferences synchronization, deletion tracking, and localization-ready UI text.

---

### Session 2: Code Review and Improvements
**Link:** [https://aistudio.google.com/apps/0967b737-2bf7-48bd-b3e4-a726fd03c651?showPreview=true&showAssistant=true](https://aistudio.google.com/apps/0967b737-2bf7-48bd-b3e4-a726fd03c651?showPreview=true&showAssistant=true)

**Purpose:**  
Review the generated FolioNotes codebase, address the highest-priority reliability and synchronization defects, resolve the build-signing issue, and add automated persistence tests.

**Two Highest-Priority Improvements Implemented:**

1. **Improvement 1 — Sync State Integrity, Dirty-Edit Tracking, and SharedPreferences Mirror Accuracy**
    - Reset `is_synced` to `0` when a SQLite note is edited.
    - Corrected synchronization badges and mirror display.
    - Protected independent `SharedPreferences` notes from origin hijacking.
    - Corrected orphaned tombstone handling.
    - Ensured the authoritative SQLite record is used when editing SQLite-owned notes.

2. **Improvement 2 — Repository-Level Validation, Exception-Safe Persistence, ViewModel Recovery, SharedPreferences Key/JSON Integrity, and Real Persistence Tests**
    - Added repository-level blank title/content validation.
    - Added SQLite exception handling.
    - Added exception-safe ViewModel recovery.
    - Made the `SharedPreferences` storage key authoritative and rejected malformed JSON fields.
    - Added unit and Robolectric tests for actual storage behaviour.

3. **Separate Build-Signing Correction (`app/build.gradle.kts`)**
    - Updated `app/build.gradle.kts` because the imported project initially required a missing `debug.keystore`; the configuration now falls back to Android's standard debug signing.

**Testing and Verification Distinction:**
- **Automated Tests Reported in Session 2 (AI Code-Review Environment):** **13/13 passed** (**9 unit tests** and **4 Robolectric tests**).
- **Android Emulator Instrumentation Tests in the AI Review Environment:** **Not executed** in the AI review environment.

**My Local Android Studio Build and Emulator Testing:**

After receiving the reviewed project, I opened it in Android Studio, successfully built the application, and installed it on the Pixel 4a emulator. I manually tested the following functionality:

- Android Studio project build — successful.
- SQLite note creation — passed.
- SharedPreferences note creation — passed.
- Editing an existing SQLite note — passed.
- SQLite-to-SharedPreferences synchronization and synced-status verification — passed.
- Empty title validation — passed.
- Empty content validation — passed.
- SharedPreferences note deletion — passed.
- Note persistence after closing and reopening the application — passed.

---

### Session 3: Documentation Generation
**Link:** [https://aistudio.google.com/prompts/1sF5ZAYKlcKjGM3vwUltCqsIsxZ_5TXlF](https://aistudio.google.com/prompts/1sF5ZAYKlcKjGM3vwUltCqsIsxZ_5TXlF)

**Purpose:**  
Generate a `README.md` grounded in the reviewed FolioNotes source code.

**My Input, Decisions, and Participation:**
- I requested a `README` based on the actual reviewed source code.
- I personally reviewed the generated documentation and requested a correction to the stated JDK requirement.

**AI Output and Outcome:**  
The final `README.md` describes the application architecture, dual storage, implemented features, setup instructions (with the corrected JDK requirement), tests, and limitations.

---

### Session 4: GitHub Interaction, Repository Verification, and Summary Preparation
**Reference:** This current AI Studio conversation

**My Personal Git and GitHub Actions (Prior to This AI Session):**  
All initial repository creation, Git initialization, committing, and pushing were performed personally by me—not by the AI:
1. I personally created the public GitHub repository at [https://github.com/SunnyShariful/FolioNotes](https://github.com/SunnyShariful/FolioNotes).
2. I initialized Git in **Android Studio**, checked the project files and `.gitignore` exclusions, made the initial commit, and pushed the reviewed project:
    - **Initial Commit:** `71322e3 — Initial commit: FolioNotes Android application`
    - **Branch:** `master`
    - **Remote:** `origin`
    - **Repository:** `https://github.com/SunnyShariful/FolioNotes`
3. I added the repository description through GitHub.

**Subsequent GitHub Interaction AI Session (This Conversation):**
- This separate AI session was subsequently used for **repository verification** and **preparation of `LAB5_SESSION_SUMMARY.md`**.
- **Pre-Summary Repository and Git Status (Before Creating `LAB5_SESSION_SUMMARY.md`):**
    - Verified that the GitHub repository contains the project source code and `README.md`.
    - Confirmed in the local terminal before creating the summary file:
        - `On branch master`
        - `Branch up to date with origin/master`
        - `nothing to commit, working tree clean`
- Application source code was not modified during this session.

---

## 4. Pending Final Steps (Not Yet Completed)

The following actions remain pending and will be recorded once actually completed:
1. **Create, Commit, and Push `LAB5_SESSION_SUMMARY.md`:**
    - Status: **Pending** (awaiting final approval of this summary content; not yet created, committed, or pushed to `origin/master`).
2. **Moodle Submission:**
    - Status: **Pending** — upload the finalized `LAB5_SESSION_SUMMARY.md` and provide the public GitHub repository link: [https://github.com/SunnyShariful/FolioNotes](https://github.com/SunnyShariful/FolioNotes).

---

## 5. Summary of User Input vs. AI Assistance

| Stage | My Personal Input, Decisions, & Actions | AI Assistance & Reported Output |
| :--- | :--- | :--- |
| **1. Code Generation** | Selected Google AI Studio, Kotlin, and Jetpack Compose; started with `/grill-me` to plan and structure the app; participated in planning decisions (dual storage, storage selection, filtering, manual sync, validation, English string resources) | Generated initial FolioNotes app implementing CRUD, dual storage, selection, filtering, sync, deletion tracking, and localization-ready UI text |
| **2. Code Review & Testing** | Conducted separate local Android Studio build and Pixel 4a emulator testing | Implemented 2 priority improvements and `app/build.gradle.kts` debug-signing fallback; reported 13/13 tests passed (9 unit, 4 Robolectric); instrumentation tests not run in AI environment |
| **3. Documentation** | Requested `README` from actual reviewed source code; reviewed output and requested JDK requirement correction | Generated and revised `README.md` covering architecture, storage, features, setup, tests, and limitations |
| **4. GitHub & Summary** | Personally created GitHub repo, initialized Git in Android Studio, verified `.gitignore`/files, made initial commit (`71322e3`), pushed `master` to `origin`, added repo description | Subsequently used for repository verification and preparing `LAB5_SESSION_SUMMARY.md` for review (did not perform initial commit or push) |