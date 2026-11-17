# PDF Focus Reader 1.0 stable releases

This branch assembles every approved improvement from the tested 0.10.11-test
prerelease: corrected reading proportions and seams, Auto toggle, grouped menu,
isolated interactive tutorial, top-bar PDF picker, OCR spinner and ordered reveal.
No source feature PR or main was merged by the assistant.

The stable application is `com.pdffocus.reader`, labelled PDF Focus Reader.
It uses a NEW stable signing certificate, authorized on November 17, 2026.
It cannot update the installed 0.9 app in place. The user chose to uninstall
that old app themselves after the 1.0 APK is ready. Uninstalling removes private
local reading state; original PDFs outside the app are not changed. This code
never uninstalls, deletes or migrates another installation. PDF Focus Test keeps
its separate package and signing identity.

The dedicated encrypted GitHub secret is `PDF_FOCUS_STABLE_SIGNING_BUNDLE`.
Only the public certificate fingerprint is pinned in code. Neither the old
original certificate nor the Test key is accepted as a fallback. No private
signing material is stored in this repository.

Initial stable release: 1.0.0, Android versionCode 100000. The candidate CI builds
release-mode ARM64 and x86_64 APKs, runs JVM/lint checks, waits for all 36 device
tests, signs with the stable secret and uploads signed artifacts without
publishing. The initial release is published only after these checks and review.

After this PR is merged by the owner, a merged PR into main starts the Android
checks. Only a successful push build for the exact merge commit can publish.
Direct pushes and failed builds do not publish. Automatic versions are 1.0.N,
where N is that successful build workflow run number; code is 100000 + N.
Gaps are expected. Retrying a source build uses the same tag. Each release is
drafted first and made visible only after every asset is uploaded.

An existing Release is never overwritten. Incomplete uploads remain drafts
for inspection, not silent replacement. The workflow must first be on main;
an unmerged PR cannot exercise its automatic publication path.
