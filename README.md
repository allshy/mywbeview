# WebImage Edit

Kotlin native Android WebView container for quickly switching between several AI image generation/editing web consoles.

## Included Providers

- ModelScope image generation
- Gitee AI FLUX.1 Kontext dev
- Volcengine Ark Seedream
- StepFun console tools
- Tencent Hunyuan image

## Open In Android Studio

1. Open this folder in Android Studio.
2. Let Android Studio sync Gradle.
3. Run the `app` configuration on an Android 8.0+ device.

Use a recent Android Studio release with its bundled JDK 17. This workspace does not include a Gradle wrapper jar, so Android Studio or a local Gradle installation should perform the first sync.

## GitHub Actions

The repository includes `.github/workflows/android-debug-apk.yml`. Push to `main` or run the workflow manually to build a debug APK. The APK is uploaded as the `webimage-edit-debug-apk` workflow artifact.

The app keeps each provider in its own WebView tab where possible, shares the standard WebView cookie store, supports web file uploads, and routes unknown external links to the system browser.
