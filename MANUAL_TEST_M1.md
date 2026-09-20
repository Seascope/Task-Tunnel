# M1 YouTube surface detection — physical phone matrix

Status: all cases **NOT RUN**. Record results on a physical phone with the installed YouTube version; candidate resource IDs can vary by release, account, locale, and experiment.

## Install and run

1. Set `JAVA_HOME=D:\jdk-17.0.20.101-hotspot`, `ANDROID_HOME=F:\sdk`, and `GRADLE_USER_HOME=F:\coding\android-saas\.gradle-user-home`.
2. Build with `gradlew.bat --no-daemon :app:assembleDebug` and install `app\build\outputs\apk\debug\app-debug.apk` using `F:\sdk\platform-tools\adb.exe install -r ...`.
3. Open Task Tunnel, enable its accessibility service, then exercise each YouTube surface below.
4. Return to Task Tunnel after each case and record the “Last observed YouTube detection,” confidence, time, and strongest signals.
5. For tuning, open the debug Sanitized Inspector, arm capture, switch to YouTube, then return to inspect resource IDs plus class/structure/boolean metadata. Copy only these sanitized values. Never capture text, content descriptions, screenshots, or raw trees.

## Matrix

| Case | Expected | Actual | Confidence | UNKNOWN? | False positive? | Pass / notes |
|---|---|---|---|---|---|---|
| YouTube Home | YOUTUBE_OTHER or UNKNOWN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Search screen | YOUTUBE_SEARCH | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Search results | YOUTUBE_SEARCH | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Long video watch page | YOUTUBE_VIDEO | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Long video fullscreen | YOUTUBE_VIDEO | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Shorts tab | YOUTUBE_SHORTS | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Short opened from Home | YOUTUBE_SHORTS | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Back from Short/video | Destination surface or UNKNOWN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Repeated video → Shorts switching | Each current surface or UNKNOWN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Background and resume YouTube | Resumed surface or UNKNOWN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| YouTube cold restart (force stop/fully close YouTube, then reopen) | No stale current during restart; newly captured reopened surface | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN |

The most severe failure is any legitimate non-Shorts surface classified as `YOUTUBE_SHORTS`. `UNKNOWN` is a safe fail-open result while signals are being validated, but repeated `UNKNOWN` results on the key target surfaces fail M1 usefulness.

## Go / no-go decision (user)

After completing the matrix: **Are false positives rare enough that I would trust this detector during normal daily YouTube use?** Record GO or NO-GO and the YouTube app version here: **NOT RUN / undecided**. Every false positive is severe and must be recorded even if the final decision is GO.
