#!/data/data/com.termux/files/usr/bin/bash
# Experimental FreeLLMAPI local router setup for AI Universal Hub.
# No provider keys are requested, copied, or stored by this script.
set -Eeuo pipefail

if ! command -v pkg >/dev/null 2>&1; then
  echo "Run this script inside Termux."
  exit 1
fi

APP_DIR="${HOME}/AI-Universal-Hub"
ROUTER_DIR="${HOME}/freellmapi"

echo "AI Universal Hub - FreeLLMAPI (Termux)"
echo "This installs the upstream experimental Android-compatible router."
echo "Provider credentials must be entered only in its local dashboard."
printf "Continue? [y/N] "
read -r answer
case "$answer" in y|Y|yes|YES) ;; *) echo "Cancelled."; exit 0 ;; esac

pkg update -y
pkg install -y nodejs-lts git

node -e 'const [a,b]=process.versions.node.split(".").map(Number); if(a<22 || (a===22 && b<13)){console.error("Node.js 22.13+ is required; update Termux nodejs-lts.");process.exit(1)}'
if [ ! -d "$ROUTER_DIR/.git" ]; then
  git clone https://github.com/tashfeenahmed/freellmapi.git "$ROUTER_DIR"
else
  git -C "$ROUTER_DIR" pull --ff-only
fi

cd "$ROUTER_DIR"
npm install --no-audit --no-fund

echo
echo "Installation complete."
echo "Start the local dashboard and API with:"
echo "  cd ~/freellmapi && npm run dev"
echo
echo "On this phone open:"
echo "  Dashboard: http://127.0.0.1:5173"
echo "  API:       http://127.0.0.1:3001/v1"
echo
echo "Keep the dashboard bound to localhost. Do not expose it to public networks."
echo "Do not paste provider keys into chats, source files, or GitHub."
