# Project working instructions

## Release policy

For every application update, build and verify all three distributions: Windows x64,
macOS Apple Silicon arm64, and macOS Intel x86_64. Publish them together under the
same version on GitHub Releases. Do not report an update as fully delivered after
updating only Windows. Preserve the existing macOS preview/notarization disclosure.

## Teaching preference

Explain the reason behind meaningful implementation choices briefly so the user can learn.

## Development directory and deliverables

The user prefers desktop and Android development together in
`C:/Users/zpw/Desktop/myApps/standUp`. Work directly in this directory by default;
do not create a separate worktree unless the user explicitly requests one.
Preserve existing uncommitted changes when integrating work.

Keep desktop sources at the project root and Android sources under `android/`.
Place current distributable packages in the root `dist/` directory, and keep
versioned release copies under `archive/releases/<version>/`. Verify package
checksums before copying; preserve existing archives instead of overwriting them.
