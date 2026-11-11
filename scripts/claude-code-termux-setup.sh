#!/data/data/com.termux/files/usr/bin/bash
# Claude Code setup for Android via Termux + Ubuntu (proot-distro).
# Installs the official Claude Code CLI inside Ubuntu's glibc environment.
set -Eeuo pipefail

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
ROOTFS="$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu"

if ! command -v pkg >/dev/null 2>&1; then
  echo "Run this script inside Termux, not Android shell."
  exit 1
fi

echo "== Claude Code for AI Universal Hub =="
echo "Route: Termux -> Ubuntu (proot-distro) -> official Claude Code CLI"
echo "Android/Termux is not an officially supported Claude Code OS; this is a community compatibility route."
printf "Continue with the Ubuntu runtime setup? [y/N] "
read -r answer
case "$answer" in y|Y|yes|YES) ;; *) echo "Cancelled."; exit 0 ;; esac

if ! command -v proot-distro >/dev/null 2>&1; then
  pkg update -y
  pkg install -y proot-distro
fi

if [ ! -d "$ROOTFS" ]; then
  proot-distro install ubuntu
fi

proot-distro login ubuntu -- bash -s <<'UBUNTU_SETUP'
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git python3

if ! command -v claude >/dev/null 2>&1; then
  echo "Installing Claude Code from Anthropic's official installer..."
  curl -fsSL https://claude.ai/install.sh -o /tmp/claude-install.sh
  bash /tmp/claude-install.sh
  rm -f /tmp/claude-install.sh
fi

if ! command -v claude >/dev/null 2>&1; then
  export PATH="$HOME/.local/bin:$PATH"
fi
if ! command -v claude >/dev/null 2>&1; then
  echo "Claude Code installation did not produce a runnable claude command."
  exit 2
fi

echo
claude --version
echo
echo "Optional provider setup:"
echo "- Leave the base URL blank to use Anthropic's normal endpoint."
echo "- Enter a base URL only for a provider/gateway that supports the Anthropic Messages API."
echo "- OpenAI chat-completions endpoints are not compatible here."
printf "Anthropic-compatible base URL (blank = official Anthropic): "
read -r base_url
printf "API token (input hidden; leave blank to configure login later): "
read -r -s api_token
printf "\n"

if [ -n "$api_token" ]; then
  umask 077
  printf '%s' "$api_token" | ANTHROPIC_BASE_URL="$base_url" python3 -c '
import json, os, pathlib, sys, tempfile
token = sys.stdin.read()
base = os.environ.get("ANTHROPIC_BASE_URL", "").strip().rstrip("/")
home = pathlib.Path.home()
cfg_dir = home / ".claude"
cfg_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
cfg = cfg_dir / "settings.json"
try:
    data = json.loads(cfg.read_text()) if cfg.exists() else {}
except Exception:
    raise SystemExit("Existing ~/.claude/settings.json is not valid JSON; left unchanged.")
env = data.setdefault("env", {})
env["ANTHROPIC_AUTH_TOKEN"] = token
if base:
    env["ANTHROPIC_BASE_URL"] = base
else:
    env.pop("ANTHROPIC_BASE_URL", None)
fd, tmp = tempfile.mkstemp(prefix=".settings.", dir=str(cfg_dir))
try:
    with os.fdopen(fd, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\\n")
    os.chmod(tmp, 0o600)
    os.replace(tmp, cfg)
finally:
    if os.path.exists(tmp):
        os.unlink(tmp)
print("Saved provider settings to ~/.claude/settings.json (mode 600).")
'
  unset api_token
else
  echo "No API token saved. You can configure login later with: claude"
fi

echo
if [ ! -d "$HOME/AI-Universal-Hub/.git" ]; then
  git clone --branch feature/claude-code-termux https://github.com/amramr01200/AI-Universal-Hub.git "$HOME/AI-Universal-Hub"
fi

echo "Setup complete. Start with:"
echo "  proot-distro login ubuntu"
echo "  claude"
echo "For a project, cd to its folder first. Claude Code will ask before protected actions."
UBUNTU_SETUP
