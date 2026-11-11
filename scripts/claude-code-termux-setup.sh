#!/data/data/com.termux/files/usr/bin/bash
# Claude Code setup for Android through Termux + Ubuntu (proot-distro).
# Claude Code runs as an unprivileged Ubuntu user, not root.
set -Eeuo pipefail

PREFIX="\${PREFIX:-/data/data/com.termux/files/usr}"
ROOTFS="$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu"
BOOTSTRAP="$HOME/AI-Universal-Hub-claude-bootstrap"

if ! command -v pkg >/dev/null 2>&1; then
  echo "Run this script inside Termux, not Android shell."
  exit 1
fi

echo "== AI Universal Hub / Claude Code setup =="
echo "Runtime: Termux -> Ubuntu (proot-distro) -> official Claude Code CLI"
echo "Android/Termux is an experimental community route, not an officially supported Claude Code OS."
printf "Continue with setup? [y/N] "
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
apt-get install -y ca-certificates curl git python3 util-linux

if ! id hub >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash hub
fi
HUB_HOME="$(getent passwd hub | cut -d: -f6)"
mkdir -p "$HUB_HOME/AI-Universal-Hub"
chown -R hub:hub "$HUB_HOME"

if [ ! -d "$HUB_HOME/AI-Universal-Hub/.git" ]; then
  su - hub -s /bin/bash -c 'git clone --branch feature/claude-code-termux https://github.com/amramr01200/AI-Universal-Hub.git "$HOME/AI-Universal-Hub"'
fi

if ! su - hub -s /bin/bash -c 'command -v claude >/dev/null 2>&1'; then
  echo "Installing Claude Code with Anthropic's official installer for the unprivileged 'hub' user..."
  curl -fsSL https://claude.ai/install.sh -o /tmp/claude-install.sh
  chown hub:hub /tmp/claude-install.sh
  su - hub -s /bin/bash -c 'bash /tmp/claude-install.sh'
  rm -f /tmp/claude-install.sh
fi

if ! su - hub -s /bin/bash -c 'command -v claude >/dev/null 2>&1'; then
  echo "Claude Code command was not found after installation."
  exit 2
fi

echo
su - hub -s /bin/bash -c 'claude --version'
echo
echo "Optional provider setup:"
echo "- Leave base URL blank for Anthropic's official endpoint."
echo "- Use only an endpoint that implements the Anthropic Messages API."
echo "- An OpenAI /chat/completions endpoint is not sufficient."
printf "Anthropic-compatible base URL (blank = official Anthropic): "
read -r base_url
printf "API token (hidden; blank = configure login later): "
read -r -s api_token
printf "\n"

if [ -n "$api_token" ]; then
  printf '%s' "$api_token" | runuser -u hub -- env HOME="$HUB_HOME" ANTHROPIC_BASE_URL="$base_url" python3 -c '
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
    raise SystemExit("Existing ~/.claude/settings.json is invalid; left unchanged.")
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
        f.write("\n")
    os.chmod(tmp, 0o600)
    os.replace(tmp, cfg)
finally:
    if os.path.exists(tmp):
        os.unlink(tmp)
print("Saved provider settings in the hub user's private Claude config (mode 600).")
'
else
  echo "No API token saved. You can configure authentication when Claude Code starts."
fi
unset api_token

echo
echo "Setup complete. Open Claude Code from AI Universal Hub or run:"
echo "  proot-distro login ubuntu --user hub"
echo "  cd ~/AI-Universal-Hub && claude"
echo "Permission confirmations remain enabled."
UBUNTU_SETUP
