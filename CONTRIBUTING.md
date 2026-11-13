# Contributing to MARAGRAM

Start with the [development setup](README.md#build-locally) and [roadmap](ROADMAP.md). Discuss substantial changes in an issue before implementation so the intended behavior and scope are clear.

## Bug reports

Include the commit tested, Android version, device architecture, reproduction steps, expected behavior, actual behavior, and a minimal redacted log. For build failures include the failing Gradle task and JDK version.

Use synthetic data in screenshots. Remove phone numbers, message content, authentication codes, session files, and credentials from reports. A dedicated private security-reporting channel remains a release-readiness task; do not include live secrets or private account data in public issues.

## Changes and validation

Keep each pull request focused on one problem. Explain the resulting behavior and include interface screenshots where relevant. For substantive fixes, add a regression test when it can demonstrate the failure and correction.

Suggested checks from the root project are:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :easytdlib:testDebugUnitTest
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

Existing library tests exercise session close/restart behavior, account switching, stale callbacks, message merging, cache snapshots, and deletion. These checks do not replace device testing of the native TDLib client and Compose gestures. State exactly which commands and device scenarios you tested, including checks you could not run. The documentation editing environment was unable to execute Gradle.

Do not commit `local.properties`, keystores, account/session data, generated build output, or private logs. Avoid including unrelated local experiments and nested source copies in a pull request.

## Attribution

Identify the origin and license of third-party code and preserve notices. Root project licensing and contribution terms must be established before accepting outside code for a public release. Do not assume an upstream module's license covers every file in this repository.
