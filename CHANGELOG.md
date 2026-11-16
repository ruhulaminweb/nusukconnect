# Changelog

## 0.4.23 (versionCode 32)

- Resume door selection even if the accessibility key counter is unreadable.
- Resume an already-open question or door confirmation after a lost code result.
- Close no-key popups during code-result recovery before generating another code.
- After 15 seconds on a code form or Keys page without a result, leave the submission unconfirmed and generate a different code. Never resubmit that code or credit it as accepted.
- Retry category selection when the door page has not opened after three seconds.
- Retain tabs, optional daily limits and manual answer confirmation.

## 0.4.22 (versionCode 31)

- Continue from a stale pending-code state to the door page when its key counter is readable.

## 0.4.21 (versionCode 30)

- Navigate from Home through Start the challenge while recovering a pending code result.
- Stop blind result-wait scrolling on unrelated pages.

## 0.4.20 (versionCode 29)

- Recognize “The entered code is not correct” and generate a fresh code.

## 0.4.19 (versionCode 28)

- Add Home, Task, Dashboard, Settings and About tabs, with disclaimer.

## 0.4.18 (versionCode 27)

- Return to Nusuk for manual answer submission after No in the confirmation dialog, then continue after observing the result.

## 0.4.15–0.4.17

- Add optional daily limits and update requested title/banner text.

## 0.4.14 (versionCode 23)

- Add hidden-button scrolling, key checks, no-key/used-code recovery, long-question handling and answer confirmation.
- Remove Teach an answer.
- User-reported working baseline. Original source/APK snapshot is unavailable in this repository. The file supplied under a 0.4.14 filename actually declares 0.4.18.

## Versioning

- Increment versionCode for every new APK; never reuse a version code for changed code.
- Increment the patch version for fixes and minor version for new features.
- Update app/build.gradle, README title and this changelog together.
- Run powershell -File scripts/check-version.ps1 to verify version metadata and archived APK checksums.
- Build and run regression checks before committing; tag the verified commit as vX.Y.Z.
- Keep a version-named APK with its SHA-256 checksum for each published release.
- Never move an existing release tag. Fixes get a new version and tag.
- Builds, device logs, screenshots, local SDK paths and credentials are excluded from source control.
