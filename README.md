# Srt Translator

Native Android app for translating `.srt` subtitles with the user's own Google Gemini API key.

## Features

- Select an `.srt` file with Android's document picker.
- Choose a target language and Gemini model.
- Split subtitles into exactly three ordered parts.
- Launch part 1, part 2, and part 3 three seconds apart without waiting for earlier requests to finish.
- Enforce one global rolling RPM limiter: at most **4 request starts per 60 seconds**. Initial calls and retries share the same pending queue, so a fifth request waits for a slot.
- Preserve subtitle numbers, timestamps, order, and multi-line structure.
- Save the result as `Translated.srt`.
- Persist the API key in Android encrypted shared preferences and persist the selected model locally.

## Build locally

```bash
./gradlew test
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

`.github/workflows/build-apk.yml` builds the debug APK on pushes to `main`, pull requests, and manual workflow dispatch. The APK is uploaded as the `srt-translator-debug-apk` artifact.

## API key and privacy

The app does not hard-code an API key. The user enters their own key in Settings; it is stored using `EncryptedSharedPreferences` and sent directly to Gemini for translation. Do not commit keys to the repository.

## Notes

Gemini model names are kept in the Settings dropdown so they can be updated in code as Google's API catalog changes. The default is `gemini-3.1-pro-preview`.
