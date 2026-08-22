# Release checklist — MVP

Use this checklist before creating a production Play Store release.

## Build
- [ ] `./gradlew verifyCi` passes.
- [ ] `./gradlew :app:bundleRelease` passes.
- [ ] R8/minify and resource shrinking are enabled for release.
- [ ] Release AAB is signed with the production upload/release keystore.
- [ ] No release credential or keystore is committed to Git.

## Version
- [ ] `versionCode` is greater than the previously published build.
- [ ] `versionName` matches the intended release.
- [ ] `CHANGELOG.md` describes the release.

## Device smoke test
- [ ] Install the release build on a real Android device.
- [ ] Open the main flow.
- [ ] Create a recommendation.
- [ ] Save/review history where applicable.
- [ ] Photo Try-On works without crash.
- [ ] Live Try-On starts, tracks the hand and recovers from temporary loss.
- [ ] No obvious regression in accessibility or navigation.

## Store readiness
- [ ] Privacy policy URL is public and current.
- [ ] LGPD/privacy disclosures are reviewed.
- [ ] App name, description and category are ready.
- [ ] Screenshots are current and from the release build.
- [ ] Play Console listing is prepared.
- [ ] **Do not publish until the release decision is explicit.**
