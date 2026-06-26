# Claude Watch — IntelliJ plugin design

Date: 2026-06-26
Author: Hemendra (brainstormed with Claude)

## Goal
Cursor-like "Claude changed this file" experience inside IntelliJ. Watch the
project directory, detect changes made by an external agent (Claude Code, CLI,
git), and react in real time: open + jump to the first changed line, highlight
changed lines, offer a diff, log to a timeline.

## Decisions (locked)
- **Detection scope:** external changes only (`VFileEvent.isFromRefresh() == true`),
  not in-IDE typing.
- **Auto-open:** open file, focus its tab, jump caret to first changed line
  (steal focus). Togglable to background mode via settings.
- **Diff feel:** fading gutter/line highlight on changed lines + a "Show Diff"
  action (IDE diff: captured pre-change content vs current).
- **Language:** Java. Build: IntelliJ Platform Gradle Plugin 2.x, target IC 2024.2+.

## The core problem: VFS latency (and the fix)
IntelliJ's VFS is a cached snapshot of disk. External writes are not visible
until VFS refreshes. Refresh triggers: frame activation (focus) and the native
file watcher (FSEvents on macOS). The native watcher lags/batches and is
unreliable on non-boot volumes — the user's code lives on `/Volumes/Java`, a
weak spot. A passive VFS listener alone would only fire on focus, defeating the
"steal focus when Claude edits while I'm elsewhere" goal.

**Fix:** `RefreshDriver` — a background `Alarm` that force-refreshes the project
content roots every ~1.5s (configurable) via
`VfsUtil.markDirtyAndRefresh(async=true, recursive=true, reloadChildren=true, roots)`.
This drives VFS to sync with disk regardless of focus or volume, so the listener
fires within ~1-2s. The poller is the heartbeat; the listener stays reactive.

## Architecture
```
ClaudeWatchService (project service, Disposable)  -- orchestrator
  ├─ RefreshDriver        : interval markDirtyAndRefresh on content roots
  ├─ AsyncFileListener    : prepareChange (bg) reads OLD content
  │                         ChangeApplier.afterVfsChange (EDT) -> route NEW content
  ├─ ChangeRouter         : burst-coalesce (Alarm ~400ms), filter, dedupe
  ├─ EditorOpener         : open + jump + fading highlight
  ├─ DiffPresenter        : DiffManager old-vs-current
  ├─ HistoryService       : ring buffer (last N), feeds tool window + status bar
  └─ Notifier             : balloon [Open] [Show Diff] [Mute]
```

### Why AsyncFileListener
`prepareChange(events)` runs on a background thread BEFORE the change applies, so
we read the old bytes for free (`LoadTextUtil.loadText`) — exact diff without
pre-snapshotting the whole project. `ChangeApplier.afterVfsChange` runs on EDT
after apply; we read new content there and dispatch.

### External vs internal
Filter `isFromRefresh() == true`. IDE saves write *through* VFS (not from
refresh); external writes are *discovered by* refresh. Plus: path under a project
content root, not ignored by globs, content actually changed (hash dedupe).

### Burst grouping
A VFS refresh delivers all changed files in one event batch -> one prepareChange
call -> natural grouping. Across separate poller cycles, `ChangeRouter` coalesces
batches within ~400ms. N files -> one "Claude edited N files" notification; open
up to `maxOpenPerBurst`, focus the last.

## Components / files
- `ClaudeWatchSettings` (app service, PersistentStateComponent) + `Configurable`.
- `ClaudeWatchService` (project service, Disposable) — registers listener, owns
  RefreshDriver Alarm, routing, dispatch.
- `ChangeRecord` — immutable: path, name, type (CREATE/MODIFY/DELETE), added,
  removed, firstChangedLine, oldText, timestamp.
- `HistoryService` (project service) — ring buffer + change listeners.
- `EditorOpener` — open + jump + fading highlight (stepped alpha via Alarm).
- `DiffPresenter` — DiffManager.
- `Notifier` — balloon.
- `IgnoreMatcher` — glob/segment match.
- `ClaudeWatchToolWindowFactory` + panel (JBTable, double-click open, ctx Show Diff,
  toolbar: Pause/Clear/Settings).
- `ClaudeStatusBarWidgetFactory` + widget (count + paused state, click toggles pause).
- `ClaudeWatchStartup` (ProjectActivity, Java impl pattern) — eager-init the service.
- `plugin.xml`.

## Settings (defaults)
autoOpen=true, stealFocus=true, highlight=true, fadeMs=5000,
refreshIntervalMs=1500, burstWindowMs=400, maxHistory=100, maxOpenPerBurst=5,
diffSizeCapKb=512, paused=false,
ignore = `.git node_modules target build .idea out dist .gradle *.class`.

Pause = skip open + balloon, still record history (heads-down "what changed" list).

## Error handling
- `getBasePath()` null -> no-op. contentsToByteArray IOException -> skip diff,
  still open. File > diffSizeCapKb -> open + jump, no diff/highlight.
- All UI on EDT via invokeLater; all IO off EDT.
- Everything parented to the service Disposable (Alarms auto-cancel on project close).

## Risk (honest)
"External only" also catches git pull, on-save formatters, other tools. Inherent
to the chosen heuristic. Mitigations: Pause toggle + ignore globs. No clean way
around it without Claude Code emitting an explicit signal.

## Out of scope (YAGNI)
Accept/Revert of changes (Claude already wrote disk), multi-root coloring schemes,
remote/WSL, telemetry.
