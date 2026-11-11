# AI Universal Hub — Claude Code Edition

AI Universal Hub is now focused on one primary workflow: run the **official Claude Code CLI** from the app's chat screen, routed through the user's local FreeLLMAPI unified API key.

## How it works

- The Android app is the primary interface. The old standalone Claude setup page and its separate setup script have been removed.
- Termux is used as a background execution bridge through its documented `RUN_COMMAND` service; the user does not need to switch to a separate Termux chat session for each request.
- Claude Code is installed in a Debian `proot-distro` environment because the native Android Termux platform is not a supported Claude Code target.
- The app stores the unified key using AndroidX encrypted preferences. During setup, it transfers the key to Debian's private Claude configuration directory with file permissions restricted to the Debian user.
- The default FreeLLMAPI origin is `http://127.0.0.1:3001`. Claude Code receives `ANTHROPIC_BASE_URL` (without `/v1`) and `ANTHROPIC_AUTH_TOKEN`, then calls the Anthropic Messages API path `/v1/messages`.
- Setup checks the authenticated `/v1/models` endpoint before reporting that the gateway is reachable. Actual Claude Code requests still require FreeLLMAPI to route Anthropic Messages API requests correctly.
- The active `feature/claude-code-freellmapi-termux` project branch is cloned into `/home/claudeuser/AI-Universal-Hub` inside Debian by the setup flow. The project directory can be changed in the app.
- Each chat request invokes the real Claude Code CLI in print mode and returns its completed output to the in-app chat. Claude Code's CLI session ID is retained between prompts.
- File read/edit tools are enabled for the coding agent. Shell-command access is off by default and can be explicitly enabled in the app for a task.

## First run

1. Install and open Termux from a trusted source.
2. Start the existing FreeLLMAPI server on the phone at `http://127.0.0.1:3001`.
3. Open AI Universal Hub, enter the existing unified FreeLLMAPI key in the Claude Code connection panel, and tap **Save key & set up Claude**.
4. Grant the app Termux `RUN_COMMAND` permission when Android asks.
5. Wait for Debian, Claude Code, and the local project checkout to finish installing. Then send a coding task from the in-app chat.

Do not paste API keys into GitHub, source files, logs, or ChatGPT. The key is stored encrypted by the app and is never committed to the repository.

## Important implementation boundary

This version runs the actual Claude Code CLI as a background, non-interactive task for each chat turn and returns the result when the process finishes. It is an in-app agent workflow, not a continuously streaming interactive terminal/PTY. Android, Termux, network availability, FreeLLMAPI's Anthropic Messages API compatibility, and upstream provider limits can affect execution. A successful `/v1/models` check alone does not prove that `/v1/messages` is compatible.

## Build

Open the project in Android Studio (JDK 17, Android SDK 35), or use the **Build Android APK** GitHub Actions workflow. A signed release APK requires the repository's Android signing secrets; the debug APK is uploaded as a workflow artifact.
