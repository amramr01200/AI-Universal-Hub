# FreeLLMAPI on Android (Termux)

This is the first integration milestone for AI Universal Hub. It installs the upstream FreeLLMAPI router as a separate local service in Termux.

## What this does

- Installs Node.js LTS and Git in Termux.
- Clones or fast-forward updates the upstream `tashfeenah/freellmapi` repository.
- Installs its dependencies.
- Documents the local dashboard (127.0.0.1:5173) and API (127.0.0.1:3001/v1).

## What this does not do yet

- It does not embed the FreeLLMAPI dashboard inside the Android app.
- It does not automatically start the router as an Android foreground service.
- It does not connect a coding agent to the router.
- It does not import, read, or reuse any API keys from prior exports or chat messages.
- It does not make paid models free or remove provider quotas/age restrictions.

## Install

Run from Termux:

```bash
curl -fsSL https://raw.githubusercontent.com/amramr01200/AI-Universal-Hub/feature/claude-code-termux/scripts/freellmapi-termux-setup.sh -o freellmapi-termux-setup.sh
bash freellmapi-termux-setup.sh
```

The upstream Android guide requires Android 7+, approximately 1 GB free space, Termux from F-Droid, and Node.js 22.13+ (Node 24 LTS recommended).

## Start

```bash
cd ~/freellmapi
npm run dev
```

Open `http://127.0.0.1:5173` on the same phone. The local API is `http://127.0.0.1:3001/v1`.

Use only localhost access for the administrative dashboard. Do not expose it to a public network. Enter provider credentials directly in the local dashboard; never commit them to GitHub or share them in chat.

## Next integration stages

1. Add app-side service status/start/stop controls using Termux RUN_COMMAND.
2. Add an authenticated local health-check client in Android.
3. Embed the dashboard in a restricted WebView only after reviewing its origin, navigation, and local-network security.
4. Add a coding-agent integration only after validating the provider's supported API protocol and applicable service terms.
