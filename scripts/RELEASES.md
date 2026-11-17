# Automatic app releases

After this PR is merged, a merged PR into `main` starts the usual Android build
and device tests. Only a successful `push` build for the exact merge commit can
start publication. Direct pushes and failed builds do not publish.

The automatic version is `0.11.N`, where N is the successful Android workflow's
run number. Its Android versionCode is `100000 + N`, above the earlier test
versions. Gaps are expected. Retrying the same source build uses the same tag.
The release package is the original `com.pdffocus.reader`, labelled PDF Focus
Reader, and its certificate must match the published 0.9.0-alpha APK. Test builds
keep their separate package and label.

BLOCKER: the original private key was not retained in the earlier development
session. No available replacement key can update that installed app. Publication
requires recovery of the original key into the separate encrypted secret
`PDF_FOCUS_ORIGINAL_SIGNING_BUNDLE`. If absent or mismatched, publication stops.
The Test signing secret is never used as a fallback. No signing material is stored in this repository.

The workflow builds versioned ARM64 and x86_64 APKs, repeats unit tests and lint,
checks their package/version, signs with the existing pinned certificate, and
adds SHA-256 checksums. Device tests have already passed on the same source SHA
in the gating workflow. Each release is drafted first and only made visible
after all uploads finish. Releases after merges are regular releases.

An existing tag's Release is never overwritten. If upload fails, the incomplete
Release stays a draft. Inspect that draft and remove it before retrying that
source run if appropriate; the workflow deliberately does not delete or replace
it. A version collision or a changed signing certificate stops publication.

The workflow must first exist on the default branch. Its publication path cannot
be exercised by an unmerged PR, which intentionally cannot publish a Release.
