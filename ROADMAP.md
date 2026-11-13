# Proposed release roadmap

These are proposed milestones, not completed release claims or promised dates. The immediate objective is a reproducible preview that other developers can evaluate.

## Reproducible preview

- [ ] Verify the documented setup on a fresh checkout with JDK 21 and SDK 36.
- [ ] Run compilation, unit tests, lint, and debug APK assembly; record the tested commit.
- [ ] Document native library versions, provenance, checksums, and rebuild instructions.
- [ ] Verify Telegram authentication with contributor-owned credentials on an ARM64 device.
- [ ] Test and document behavior without Turso configuration.
- [ ] Add CI after establishing a working baseline, without exposing private credentials to pull-request jobs.

## Reliability and usability

- [ ] Exercise interrupted authentication, network recovery, logout, and account switching on a device.
- [ ] Verify workspace persistence after process recreation and app upgrades.
- [ ] Test source navigation and message destination clarity in spatial views.
- [ ] Evaluate gesture accessibility, text scaling, right-to-left layouts, and large workspaces.
- [ ] Capture real screenshots with synthetic or redacted data.
- [ ] Publish reproducible bug reports and document known limitations.

## Release foundations

- [ ] Establish root project licensing and preserve upstream and native-library notices.
- [ ] Publish a private vulnerability-reporting channel and supported-version policy.
- [ ] Review analytics disclosure, user control, backend authorization, logs, backups, and session storage.
- [ ] Review tracked generated Kotlin error files and exclude unnecessary generated output.
- [ ] Publish a tagged preview with installation instructions, checksums, and a device test matrix.

## Adoption evidence

- [ ] Invite real feedback from developers and people who regularly use Telegram.
- [ ] Record actual release downloads, external contributions, and downstream use with dated evidence.
- [ ] Validate whether spatial organization helps people retrieve conversation context.

Documentation and releases improve the project's usability. Ecosystem reach must be demonstrated through actual use and contributions; this roadmap does not establish eligibility for a funding or subscription program.
