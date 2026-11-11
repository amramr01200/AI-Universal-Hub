# Claude Code on Android (Termux + Ubuntu)

This is an experimental Android compatibility route for the official Claude Code CLI. Android/Termux is not an officially supported Claude Code platform.

## Runtime design

- Termux is the Android-side launcher and terminal host.
- Ubuntu runs inside Termux through proot-distro to provide a glibc-based Linux userspace.
- Claude Code is installed as the unprivileged Ubuntu user `hub`, not as root.
- The Android app opens a visible Termux terminal. Interactive prompts and Claude Code permission confirmations remain visible to the user.
- The app Runtime Center is intentionally focused on Claude Code; generic model scanning and unrelated package catalogs are not part of this workspace.

## Install

1. Install a maintained Termux build and open it.
2. In Termux, enable **Allow external apps** in Termux settings. The Android app also needs the Termux RUN_COMMAND permission.
3. Open AI Universal Hub and choose **Install & configure Claude Code**, or run the setup script from a Termux checkout:

   ```bash
   pkg install -y git
   git clone --branch feature/claude-code-termux https://github.com/amramr01200/AI-Universal-Hub.git
   cd AI-Universal-Hub
   bash scripts/claude-code-termux-setup.sh
   ```

4. Approve the Ubuntu installation. The script installs the Ubuntu userspace, creates a non-root `hub` user, clones the project into that user's home directory, and runs Anthropic's official installer for Claude Code.
5. At the optional provider prompt:
   - Leave the base URL blank to use Anthropic's normal endpoint.
   - Enter a base URL only if the provider implements the Anthropic Messages API expected by Claude Code.
   - An OpenAI `/v1/chat/completions` endpoint alone is not compatible.
   - The API token is entered at a hidden prompt and saved in `/home/hub/.claude/settings.json` with mode 0600. It is not written to Git or Android app logs.
6. Start from the app with **Launch Claude Code**, or manually:

   ```bash
   proot-distro login ubuntu --user hub
   cd ~/AI-Universal-Hub
   claude
   ```

## Provider and credential rules

- Configure one active Anthropic-compatible provider endpoint/token for this Claude Code profile.
- Do not merge separate accounts' quotas into a fake shared balance or rotate credentials to evade provider limits.
- A legitimate gateway may perform provider routing only within its terms and only if it implements the Anthropic Messages API and tool-use behavior.
- Never commit API keys or paste them into chat, GitHub issues, or shell command arguments.
- Any credentials previously exposed in an uploaded JSON bundle must be revoked and replaced before use.

## Free-Pi link clarification

The supplied link describes Free-Pi, a separate Pi-based coding agent. It is not Claude Code and does not use the user's API keys; its requests go through Free-Pi's own service and account. It is not part of this Claude Code runtime.

## Current limitations

- This is an experimental Android/proot compatibility route and must be tested on the target phone.
- The app opens a visible Termux terminal; it does not yet embed an interactive PTY or stream Claude Code's live session into the Hub chat.
- A successful CI APK build does not prove that Claude Code has installed or run on the user's device.
- Do not disable Claude Code permission prompts or run the CLI with permission bypass flags.

## Recovery and diagnostics

- Enter Ubuntu as the Claude Code user: `proot-distro login ubuntu --user hub`
- Check CLI version: `claude --version`
- Run diagnostics: `claude doctor`
- Update CLI: `claude update`

Do not remove the Ubuntu environment as part of routine maintenance; that would erase the installed runtime and user configuration.
