# Changelog

All notable changes to Claude Watch are documented here.
This project adheres to [Semantic Versioning](https://semver.org/).

## [0.1.2] - 2026-06-26

### Changed
- Replaced the 3 deprecated `ReadAction.compute(ThrowableComputable)` calls with
  `Application.runReadAction(Computable)` — clean against IntelliJ 2026.1, no
  behavior change. (JetBrains Marketplace verifier flagged these as deprecated.)

## [0.1.1] - 2026-06-26

First public release.

### Added
- **External-change detection** — reacts to disk changes made outside the IDE
  (Claude Code, other agents, CLIs, git) via `AsyncFileListener` +
  `isFromRefresh()`, not your own typing.
- **RefreshDriver** — interval-based `markDirtyAndRefresh` of content roots so
  changes are detected even while the IDE is in the background (works around
  VFS refreshing only on frame activation; reliable on non-boot volumes).
- **Open + jump + steal focus** to the first changed line (toggleable).
- **Fading line highlight** of changed lines (green new / blue modified).
- **Show Diff** — IDE diff of captured pre-change content vs current.
- **Burst grouping** — many simultaneous edits collapse into one notification.
- **Timeline tool window** — last N changes; double-click to open, right-click to diff.
- **Status-bar widget** — live count + paused indicator; click to pause/resume.
- **Pause** (record-only) mode and a full settings panel.

### Notes
- Detection is "external, not me typing"; it also sees git pull / formatters.
  Use Pause and the ignore globs to manage noise.
- Not affiliated with Anthropic. See NOTICE.
