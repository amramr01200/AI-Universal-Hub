#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "AI Universal Hub - Claude Code + FreeLLMAPI setup"
echo "Claude Code will run in Debian under Termux (proot-distro)."

pkg update -y
pkg install -y proot-distro curl

if ! proot-distro list-installed | grep -qE '^[[:space:]]*debian([[:space:]]|$)'; then
  proot-distro install debian
fi

# Install system dependencies and create a non-root account.
proot-distro login debian -- bash -lc '
set -euo pipefail
apt-get update
apt-get install -y ca-certificates curl git
if ! id claudeuser >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash claudeuser
fi
install -d -o claudeuser -g claudeuser -m 700 /home/claudeuser/.config/claude-code-free
install -d -o claudeuser -g claudeuser -m 700 /home/claudeuser/.local/bin
'

# Run the official installer and save the existing gateway key as the
# unprivileged Debian user. The key is entered invisibly and never echoed.
proot-distro login debian --user claudeuser -- bash -lc '
set -euo pipefail
read -r -p "FreeLLMAPI server origin [http://127.0.0.1:3001]: " api_origin
api_origin="${api_origin:-http://127.0.0.1:3001}"
case "$api_origin" in
  http://*|https://*) ;;
  *) echo "Origin must begin with http:// or https://"; exit 1 ;;
esac
api_origin="${api_origin%/}"

read -r -s -p "Paste your existing unified FreeLLMAPI key (input hidden): " api_token
printf "\n"
if [ -z "$api_token" ]; then echo "The key cannot be empty."; exit 1; fi

printf "%s\n" "$api_origin" > "$HOME/.config/claude-code-free/base-url"
printf "%s\n" "$api_token" > "$HOME/.config/claude-code-free/token"
chmod 600 "$HOME/.config/claude-code-free/base-url" "$HOME/.config/claude-code-free/token"
unset api_token

if ! command -v claude >/dev/null 2>&1; then
  curl -fsSL https://claude.ai/install.sh | bash
fi

cat > "$HOME/.local/bin/claude-free" <<'"'"'LAUNCHER'"'"'
#!/usr/bin/env bash
set -euo pipefail
CONFIG_DIR="$HOME/.config/claude-code-free"
TOKEN_FILE="$CONFIG_DIR/token"
BASE_FILE="$CONFIG_DIR/base-url"
if [ ! -s "$TOKEN_FILE" ] || [ ! -s "$BASE_FILE" ]; then
  echo "Gateway credentials are missing. Re-run the setup script."
  exit 1
fi
export ANTHROPIC_BASE_URL="$(cat "$BASE_FILE")"
export ANTHROPIC_AUTH_TOKEN="$(cat "$TOKEN_FILE")"
unset ANTHROPIC_API_KEY
exec claude "$@"
LAUNCHER
chmod 700 "$HOME/.local/bin/claude-free"

echo
echo "Setup complete. Start FreeLLMAPI first, then run claude-free in Debian."
'
echo
echo "To open Debian later:"
echo "  proot-distro login debian --user claudeuser"
