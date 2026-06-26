# Claude Watch

[![Build](https://github.com/Hemendra1990/claude-watch/actions/workflows/build.yml/badge.svg)](https://github.com/Hemendra1990/claude-watch/actions/workflows/build.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

A Cursor-like **"the agent changed this file"** experience for IntelliJ. Watches your
project on disk, detects changes made by an **external** process — Claude Code, other
AI agents, CLIs, or `git` — **even while IntelliJ is in the background** — then opens
the file, jumps to the first changed line, highlights the change, offers a diff, and
logs a timeline.

> **Disclaimer:** Not affiliated with, endorsed by, or sponsored by Anthropic.
> "Claude" is a trademark of Anthropic, PBC. This is an independent, community-built
> tool that reacts to external file changes from **any** source. See [NOTICE](NOTICE).

## Why it works when a plain VFS listener doesn't

IntelliJ's Virtual File System is a *cached snapshot* of disk. External writes aren't
visible until VFS refreshes, and refresh normally only happens on **frame activation**
(you click the window) or via the native file watcher — which lags, batches, and is
unreliable on non-boot volumes (e.g. `/Volumes/...`). A passive listener alone would
only react when you focus the IDE, defeating the whole point.

**`RefreshDriver`** fixes this: a background `Alarm` force-refreshes the project
content roots every ~1.5s via `VfsUtil.markDirtyAndRefresh(async, recursive, …)`,
driving VFS to sync with disk regardless of focus or volume. The listener stays
reactive; the poller is the heartbeat.

## Features

- **External-only detection** — fires on disk changes (`VFileEvent.isFromRefresh()`),
  not your own typing.
- **Open + jump + steal focus** to the first changed line (toggleable to background).
- **Fading line highlight** — changed lines glow (green new / blue modified), fade ~5s.
- **Show Diff** — IDE diff of the captured pre-change content vs current.
- **Burst grouping** — many simultaneous edits → one notification, opens up to N.
- **Timeline tool window** — recent changes; double-click to open, right-click to diff.
- **Status-bar widget** — live count + paused indicator; click to pause/resume.
- **Pause** — record-only mode for heads-down work.
- **Settings** (Tools ▸ Claude Watch) — auto-open, focus, highlight + fade, refresh
  interval, burst window, history size, max files per burst, diff cap, ignore globs.

## Compatibility

IntelliJ IDEA (Community or Ultimate) **2024.2 – 2026.1.x** (`since-build 242`,
`until-build 261.*`). Works for any tool that writes files outside the IDE.

## Install

### From JetBrains Marketplace
Settings ▸ Plugins ▸ Marketplace ▸ search **"Claude Watch"** ▸ Install.
*(Pending first-time Marketplace review.)*

### From a release zip (now)
1. Download `claude-watch-<version>-signed.zip` from
   [Releases](https://github.com/Hemendra1990/claude-watch/releases).
2. Settings ▸ Plugins ▸ ⚙ ▸ **Install Plugin from Disk…** ▸ pick the zip ▸ restart.

### From a custom plugin repository (auto-updates)
Settings ▸ Plugins ▸ ⚙ ▸ **Manage Plugin Repositories…** ▸ add:
```
https://github.com/Hemendra1990/claude-watch/releases/latest/download/updatePlugins.xml
```
Then install **Claude Watch** from Marketplace tab.

## Build from source

```bash
./gradlew buildPlugin     # -> build/distributions/claude-watch-<version>.zip
./gradlew runIde          # launch a sandbox IDE with the plugin
./gradlew verifyPlugin    # run the JetBrains Plugin Verifier
```

JDK 21 toolchain (auto-provisioned). First build downloads the IntelliJ SDK.

## Test it

1. `./gradlew runIde`, open a project.
2. From a terminal **outside** the IDE, edit a file you do **not** have open:
   ```bash
   echo "// touched $(date)" >> src/SomeFile.java
   ```
3. Within ~1–2s: the file opens, caret jumps to the change, the line glows and fades,
   a balloon fires, and the **Claude Watch** tool window logs it.

## Limitation (honest)

"External only" also catches `git pull`, on-save formatters, and other outside tools —
anything that isn't you typing in the IDE. There's no way to single out one agent
without it emitting a dedicated signal. Mitigations: the **Pause** toggle and ignore globs.

## License

[Apache-2.0](LICENSE). See [NOTICE](NOTICE) and [CHANGELOG](CHANGELOG.md).
Publishing/maintainer guide: [PUBLISHING.md](PUBLISHING.md).
