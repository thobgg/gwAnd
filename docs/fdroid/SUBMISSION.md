# F-Droid submission (main repo)

Recipe: `de.bgghome.gwand.yml` in this folder. Store texts and screenshots:
`fastlane/metadata/android/{en-US,de-DE,fr-FR,nl-NL,es-ES}/` (F-Droid reads them from the repository; icon and
screenshots are present in every language folder, as the reviewers ask for).

## How a release reaches F-Droid

1. Tag `vX.Y` on `main`, pushed. Tags are never moved afterwards – F-Droid builds the commit hash in the recipe.
   The submodule `app4webtrees` must point to a commit that is reachable on GitHub (`submodules: true`).
2. The reference APK is built in the F-Droid build-server image (`tools/fdroid-reference.sh X.Y`, not in the
   repository; needs Docker, build-tools 34.0.0 and apksigcopier), signed with the project key and attached to the
   GitHub release as `gwAnd-vX.Y.apk` (field `Binaries:` in the recipe). F-Droid builds the same commit, copies the
   signature over and only ships the APK if both are byte-identical – so F-Droid users get the same signature as
   GitHub users and can switch between the two without reinstalling.
   `tools/fdroid-reference.sh X.Y --check <apk>` only compares an existing signed APK with the container build.
3. The recipe is updated by F-Droid's checkupdates bot (`UpdateCheckMode: Tags`, version from `gradle.properties`),
   or by hand with the new `Builds:` entry (versionName, versionCode, commit hash) and `CurrentVersion`.
   The recipe keeps a single `Builds:` entry (requested by the reviewer for wtAnd).

Reproducibility relies on `vcsInfo { include = false }` and `dependenciesInfo { includeInApk = false }` in
`app/build.gradle.kts`; a release build without `keystore.properties` is unsigned.

## Merge request

Fork `thobgg/fdroiddata` on GitLab, branch `gwand`, one commit „New App: de.bgghome.gwand“, file
`metadata/de.bgghome.gwand.yml`. Open the MR against `fdroid/fdroiddata` `master`.

### Merge request text

> **New App: gwAnd (de.bgghome.gwand)**
>
> Native Android client for Gramps Web, the free web-based genealogy software (AGPL): hourglass and fan chart,
> person profiles with timeline, family and map, editing of people, events and relatives, photos with the server's
> face detection, anniversaries with an optional daily reminder. The app talks to the user's own Gramps Web server
> through its public REST API (3.0 or later); it has no server part of its own. GPL-3.0-or-later.
>
> Source: https://github.com/thobgg/gwAnd – Kotlin/Compose Multiplatform. The `app` module is the Android app,
> `core` the shared client; the Git submodule `app4webtrees` (same author, GPL-3.0-or-later) provides UI building
> blocks, hence `submodules: true`. Pure Kotlin, no native code, dependencies from Maven Central and Google only
> (Compose, OkHttp, Coil, osmdroid).
>
> Reproducible build: `Binaries:` points to the release APK on GitHub, signed with the project key
> (`AllowedAPKSigningKeys`). The APK was built in `fdroidserver:buildserver-trixie` and verified with
> `apksigcopier compare` against the unsigned build.
>
> Network: only the user's own Gramps Web server (address entered by the user) and OpenStreetMap tiles for the map.
> No analytics, no ads, no Google services; permissions INTERNET and POST_NOTIFICATIONS (optional daily reminder).
> Privacy policy: https://github.com/thobgg/gwAnd/blob/main/docs/DATENSCHUTZ.md
>
> The author also maintains wtAnd (`de.bgghome.webtrees.nativ`, MR !51425), built the same way.
