# Claude Code on Android (Termux + Ubuntu)

This is the first executable runtime milestone for AI Universal Hub.

## Why Ubuntu inside Termux?

Current Claude Code releases use Linux binaries that expect glibc. Plain Termux uses Android's Bionic libc, so a normal npm install can leave a non-runnable launcher on current releases. This project therefore uses proot-distro with Ubuntu as a compatibility environment. This is a community workaround, not an Anthropic-supported Android target.

## Install

1. Install Termux from a maintained source (F-Droid or the official Termux GitHub releases; avoid the obsolete Play Store build).
2. Open Termux and run:

   pkg install -y git
   git clone https://github.com/amramr01200/AI-Universal-Hub.git
   cd AI-Universal-Hub
   bash scripts/claude-code-termux-setup.sh

3. The script asks before installing Ubuntu, then installs curl, git and Python inside Ubuntu and downloads Anthropic's installer from https://claude.ai/install.sh.
4. At the optional provider prompt:
   - Leave the base URL blank for the official Anthropic endpoint.
   - Only enter a gateway URL if it implements the Anthropic Messages API expected by Claude Code.
   - Enter the provider token at the hidden prompt. It is saved only inside Ubuntu at ~/.claude/settings.json, with restrictive file permissions (0600). It is never written to this repository.
   - A provider that only exposes OpenAI /v1/chat/completions is not compatible without a separately configured protocol gateway.
5. Start:

   proot-distro login ubuntu
   claude --version
   claude

   To work on a repository, enter its directory inside Ubuntu before starting Claude Code.

## Provider and key handling

- One active provider endpoint/token is configured for this Claude Code profile. Multiple unrelated keys are not combined into a synthetic balance and are not rotated to evade provider quotas.
- A gateway may provide legitimate routing/fallback only within its documented terms and only when it speaks the Anthropic Messages API.
- Never commit API keys, paste them into GitHub issues, or place them in shell command arguments/history.
- If a key was previously exposed in an uploaded JSON file, revoke it and create a replacement before use.
- Third-party setup pages and installers are not executed automatically. The user-provided ikhtiardhaki.com/v/claude-code-free page could not be retrieved for review; no code or key should be sent to it until its exact contents and trustworthiness are verified.

## Current limitation

This script installs and configures the CLI in Termux's Ubuntu environment. It does not yet provide an embedded interactive terminal or live bidirectional streaming inside the Android chat screen. The existing app bridge currently submits background commands and receives completed output; it is not a PTY. The next app milestone must add an explicit terminal/session transport before claiming in-chat interactive Claude Code.

## Recovery

- Enter Ubuntu: proot-distro login ubuntu
- Check CLI: claude --version
- Check environment: claude doctor
- Update CLI: claude update
- Remove Ubuntu only if you intentionally want to erase that Linux environment: proot-distro remove ubuntu

Do not run the removal command as part of routine maintenance.
