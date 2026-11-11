# AI Universal Hub

Android-first AI workspace starter. The initial release provides a bilingual Arabic/English shell, OpenAI-compatible provider configuration, model discovery, encrypted API-key storage, and a chat screen.

## Build
Open this project in Android Studio (JDK 17, Android SDK 35), or push it to GitHub and run **Build Android APK** from Actions.

The workflow builds a debug APK and uploads it as an artifact. A signed release APK requires repository secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Security
- API keys are stored using AndroidX Security Crypto encrypted preferences.
- Keys are never written to source files, logs, or Git.
- The first version supports OpenAI-compatible chat-completions endpoints. Provider-specific auth, OAuth, Chrome integration, Termux bridge, GitHub project agent, MCP, and automatic free-quota discovery are staged for later milestones.
- Do not import credentials from browser password stores without explicit Android/Chrome user consent and supported APIs.

## Current scope
This is a working foundation, not a finished AI platform. Free quota and reset dates are only reliable when the provider exposes them through an API; otherwise the user can set a manual reset date.


## Background AI task execution (required product capability)

User-initiated long-running AI tasks must continue when the user leaves the app or turns the screen off, subject to Android OS limits and device power/network conditions.

Planned architecture:
- Use an Android foreground service for actively running, user-visible long tasks, with a persistent notification and clear Stop/Pause controls. Declare the appropriate foreground-service type and permissions for the target Android version; do not start restricted service types or bypass Android background-start rules.
- Use WorkManager for deferrable, persistent work such as retries, queued steps, cleanup, and resumable follow-up jobs.
- Persist task state, project files, step checkpoints, logs, timestamps, and cancellation state locally so work can recover after process death or device restart where Android permits.
- Provide a task center with queued/running/paused/needs-attention/completed/failed states, progress, current step, elapsed time, and controls to pause, resume, cancel, or open the result.
- Apply network constraints and backoff; handle connectivity loss, provider rate limits, expired API keys, low storage, and battery restrictions without losing completed work.
- Keep user consent explicit for repository writes, installs, deployments, and other consequential actions. Background execution must not silently perform newly discovered privileged actions.
- Explain that Android may stop or delay work under force-stop, severe battery restrictions, reboot, or OEM policies; the app must report interruptions and resume safely when allowed rather than promise unlimited execution.

This capability is a core requirement and must be tested with screen-off, app-switched, network-loss, process-recreation, and task-cancellation scenarios before release.


## In-app tool and model library

The app now includes a local persistent library for user-registered tools and models. Each entry stores its name, type, launch URL, notes/settings, status label, and last-updated timestamp in app-private SharedPreferences. Entries can be opened by URL or removed from the library.

Important distinction: adding an entry registers metadata; it does not install or sandbox an arbitrary third-party package. Actual installation and in-app execution require a compatible, reviewed adapter (for example an HTTP API, MCP client, or explicitly configured Termux bridge). The library is the management surface for those adapters, not a claim that every Linux/Termux tool can run natively inside Android.

The foreground service added in this milestone is an execution shell and notification control only. WorkManager is included as a dependency, but durable task orchestration, checkpoint recovery, per-tool adapters, and real background project generation remain future implementation work.


## Runtime toolchain catalog

The app now includes a curated in-app catalog for Python, pip, uv, Node.js/npm, Git, Clang, CMake, Rust, FFmpeg, proot-distro, and the Termux pkg/apt package manager. Each entry documents its purpose and a suggested package command. The catalog is informational at this stage: it does not claim these runtimes are installed or updated.

The planned package backend must target an explicitly configured compatible environment (for example, a supported Termux installation/bridge or a remote Linux host). Android app-private storage is sandboxed; the app cannot silently install arbitrary Linux packages into the Android OS. The future updater must read installed versions, check trusted upstream/package repositories, show release notes and compatibility, ask before upgrades, preserve configuration, and support rollback where the package ecosystem allows it.

Docker is listed as a compatibility target, not a native Android package: a conventional Docker daemon generally requires a compatible Linux host or remote container service.

## Canonical product and design specification

The complete approved requirements, including the golden-gear identity, black-and-gold app shell, white chat workspace, Arabic/English support, providers, routing, tools, MCP, GitHub agent, runtime management, Chrome workflows, background continuity, security, and acceptance tests are maintained in [`PRODUCT_SPECIFICATION.md`](PRODUCT_SPECIFICATION.md). This specification is the source of truth for implementation and must not be reduced to the currently implemented feature set.

## Claude Code-first integration status

The active development direction is a focused Claude Code workspace inside the Android app, using Termux as its helper runtime. The current repository contains a Termux command bridge foundation, encrypted provider-key storage, provider chat support for OpenAI-compatible endpoints, and a runtime catalog. These pieces do **not** yet constitute a verified Claude Code launcher or a working Anthropic Messages gateway.

Claude Code routing requires an Anthropic Messages-compatible endpoint or a reviewed protocol gateway. An OpenAI-compatible `/chat/completions` endpoint is not sufficient by itself. Provider credentials must remain encrypted and must never be placed in Git, chat transcripts, shell history, process arguments, or diagnostic logs.

The intended provider pool is deliberately small and compatibility-tested. Multiple credentials may be used for authorized availability/fallback only where provider terms permit; quotas and credits must not be merged across unrelated accounts or bypassed. The user-provided third-party reference URL is unverified and must not be executed or receive credentials until reviewed.

**Current milestone:** requirements and product direction documented on the `feature/claude-code-termux` branch. No successful Claude Code installation, Termux execution test, compatible gateway test, or APK build is claimed yet.

