# LUA STUDIO

Native Android IDE for writing, editing, and **running** Lua/Luau code —
**fully offline**. No AI API, no account, no API key, no network calls of
any kind. Your code never leaves the device.

## What changed from earlier builds

This project originally explored a Claude-powered AI assistant. That has
been **fully removed**:

- No Claude API, no Anthropic API, no API key of any kind
- No code is ever sent to a server
- `ClaudeService`/`SecureKeyStore`/AI settings/AI request-context plumbing
  have been deleted outright (not just hidden)
- The **AI Assistant** tab now shows a plain, honest message —
  *"AI features are currently disabled. LUA STUDIO works completely
  offline."* — instead of faking a response

In their place, this build adds a **real, offline Lua Runtime + Console**
(▶ Run / ⏹ Stop) so you can execute the Lua you write directly on-device.

## Status

- ✅ Project skeleton, theme, navigation, Home screen (Recent Files + Recent Projects)
- ✅ Editor: syntax highlighting, Lua/Luau toolbar, multi-tab, find/replace,
  go-to-line, undo/redo, auto-indent, auto-save, Save/Save As via SAF
- ✅ File Manager: SAF folder browsing, New File/Folder, Rename, Delete,
  Duplicate, Move, Copy (files), Import (files & ZIPs), Export (file or
  whole project as ZIP), Share
- ✅ **Lua Runtime + Console**: ▶ Run executes the current tab's code with
  LuaJ (pure-JVM, no native code, no network); ⏹ Stop and an execution
  timeout both guard against infinite loops; Console panel with
  Clear/Copy/Close and auto-scroll
- ⬜ Settings screen (Editor/Appearance/File/Runtime settings — the
  preference storage already exists in `PreferencesManager`, just no UI yet)
- ⬜ Polish: autocomplete, error highlighting, code formatting, command palette, code folding

## ⚠️ Two things to know before you build

**1. No Gradle Wrapper is committed in this zip.** A normal Android Studio
project ships `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`
so the build works identically everywhere. Generating that wrapper requires
either running Gradle once locally or downloading the wrapper jar — neither
was possible in the sandbox this project was assembled in (no internet
access). **This is fixed automatically the first time you open the folder
in Android Studio** — it detects the missing wrapper and offers to
generate one (accept the prompt, or run `gradle wrapper` yourself from a
terminal if you have Gradle installed). The included GitHub Actions
workflow (`.github/workflows/build.yml`) doesn't need this at all — it
installs Gradle directly on the CI runner and calls `gradle` (not
`./gradlew`).

**2. I could not run a build myself to confirm it compiles.** I edited
every source file directly and checked them carefully for consistency
(imports, types, call sites, deleted-symbol references), but this sandbox
has a JDK, no Android SDK, no Gradle, and no internet access, so it can't
resolve the Android Gradle Plugin, Kotlin compiler artifacts, or the
`org.luaj:luaj-jse:3.0.1` dependency this phase adds. Once you build it
for real — in Android Studio or via the GitHub Actions workflow below —
if anything fails, paste the error back and I'll fix it immediately. I'd
rather tell you this than claim a build success I never actually observed.

One specific area worth watching: `services/LuaRuntimeService.kt` overrides
`DebugLib.onInstruction(...)` to implement cooperative timeout/cancellation
for the Lua runtime. This is a standard, widely-used LuaJ technique, but I
wrote it from memory of the LuaJ 3.0.1 API rather than against a compiler —
if the build flags that specific override, it's almost certainly a minor
signature mismatch that's quick to correct.

## Requirements

- Android Studio Koala (2024.1) or newer
- JDK 17
- Android SDK Platform 34
- A physical device or emulator running Android 8.0 (API 26) or newer
- Internet access (once) for Android Studio to resolve Gradle dependencies
  and generate the wrapper

## Getting started

1. Open this folder in Android Studio (`File > Open`).
2. If prompted to generate the Gradle Wrapper, accept — this only happens once.
3. Let Gradle sync — it downloads Compose, Material 3, Navigation Compose,
   DataStore, `androidx.documentfile`, and `org.luaj:luaj-jse:3.0.1` (the
   offline Lua interpreter) from Maven Central.
4. Select a device/emulator and press **Run**.

### Building an APK from the command line

Once Android Studio has generated the wrapper (step 2 above):

```
./gradlew clean assembleDebug
```

The output APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Building an APK without Android Studio — GitHub Actions

If you don't have a computer to run Android Studio on, push this project to
a GitHub repo and let GitHub's servers build it for you:

1. Create a new repo on GitHub (the mobile app or github.com both work) and
   push/upload this entire folder to it.
2. Go to the **Actions** tab — `Build Debug APK` runs automatically on
   push, or click **Run workflow** to trigger it manually.
3. When it finishes (a few minutes), open the completed run and download
   the **LuaStudio-debug-apk** artifact from the *Artifacts* section at the
   bottom of the run summary — that's a zip containing `app-debug.apk`.
4. Unzip it on your phone and install it (you'll need to allow "install
   from unknown sources" the first time).

This workflow installs its own Gradle on the runner, so it works even
though this repo doesn't have a wrapper committed.

### Running the Lua Runtime tests

```
./gradlew test
```

`app/src/test/java/.../LuaRuntimeServiceTest.kt` covers print, variables,
arithmetic, if/elseif/else, for loops, functions, tables, a syntax error, a
runtime error, an infinite-loop timeout, a Stop request mid-execution,
output-limit truncation, and the Roblox-stub-globals behavior — all as
plain JVM unit tests (no emulator needed). This also runs automatically as
part of the GitHub Actions build above (Gradle runs `test` before
`assembleDebug` completes).

## Architecture

MVVM + Repository pattern:

```
ui/            Compose screens + ViewModels (Home, Editor, Files done; Settings stubbed;
               AI Assistant shows a static "disabled" screen)
domain/model/  Plain data classes shared across layers (LuaFile, FileEntry,
               EditorSettings/AppearanceSettings/FileSettings/RuntimeSettings...)
data/storage/  PreferencesManager — DataStore only; no secure/encrypted storage exists
               anymore because there's no API key to protect
data/files/    PendingFileOpenHolder — hands a LuaFile from Home/Files to the Editor screen
services/      FileService (text/byte I/O over SAF Uris), FileManagerService (DocumentFile
               tree operations), ZipService (import/export ZIPs),
               LuaRuntimeService (offline Lua execution via LuaJ)
```

## Lua Runtime — what it is and isn't

- Backed by **LuaJ**, a pure-JVM Lua interpreter. No native `.so` files, no
  NDK, no ABI concerns.
- Supports standard Lua: variables, numbers/strings/booleans/nil, tables,
  functions, `if/elseif/else`, `for`, `while`, `repeat`, `local`, `return`,
  and the safe parts of the standard library.
- **This is not Roblox Luau.** Roblox-only globals (`game`, `workspace`,
  `Instance`, `Players`, `LocalPlayer`, `ReplicatedStorage`, `RemoteEvent`,
  `RemoteFunction`) are stubbed to fail with a clear message —
  *"Roblox APIs are not available in LUA STUDIO."* — instead of silently
  doing nothing or pretending to emulate Roblox. This is a standalone Lua
  runtime for writing and testing plain Lua, not a Roblox executor.
- Safety: a per-instruction hook enforces an execution timeout (default
  5s, configurable once the Settings screen lands) and lets ⏹ Stop
  interrupt a running script cooperatively; output is capped (default
  20,000 characters) with a clear "Output limit exceeded." message.
- Threading: execution runs on a dedicated background thread, never the UI
  thread; the ViewModel also wraps it in a coroutine timeout as a second
  safety net so the UI never hangs even in a worst case.

## Known limitations (by design)

- File Manager Move/Copy currently supports files only (not folders);
  Import ZIP and Export Project ZIP already handle nested folders fully.
- Line numbers are computed from `\n` count, so with Word Wrap on, a long
  logical line that wraps visually won't get extra gutter numbers.
- The Lua/Luau syntax highlighter is a regex tokenizer, not a full grammar.
- No Settings UI yet (Runtime timeout/output-limit/auto-clear values are
  wired to `PreferencesManager` with sensible defaults, just not editable
  from the UI yet).

## License

Not yet decided — placeholder for the project owner to fill in.
