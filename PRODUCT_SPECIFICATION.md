# AI Universal Hub — Product Specification

This document is the canonical product and design brief. All implementation milestones must preserve these requirements. A feature is not considered complete merely because it appears in a catalog or UI: it must have a working adapter, persistence, error handling, permissions, and tests appropriate to the feature.

## 1. Product vision
Build an Android-first universal AI workspace that acts as the user's primary hub for AI models, conversations, memory, files, tools, development workflows, and long-running tasks. It should work online and support local/offline capabilities where a configured local runtime makes that technically possible. Do not claim that it contains all knowledge or capabilities of every AI model; capabilities depend on connected providers, tools, device resources, and network access.

## 2. Brand and visual design — approved direction
- App identity: a distinctive **golden gear** as the central brand mark. The gear is not merely a small settings icon; it is the visual identity of the application.
- Main palette: deep black / charcoal backgrounds with elegant muted metallic gold accents, gold outlines, and restrained highlights.
- Chat workspace: white or warm-white conversation canvas for readability, while the surrounding app shell remains black and gold.
- Settings entry: a clearly visible gold gear icon.
- Design language: premium, modern, precise, uncluttered, polished spacing, consistent cards, clear hierarchy, subtle gold separators, accessible contrast, and responsive Android layouts.
- Navigation and controls must use consistent iconography; avoid mixing random text glyphs as permanent icons.
- Arabic is the default language with correct RTL layout; provide a complete English toggle with LTR layout. Do not translate only headings while leaving key controls inaccessible.
- Motion should be restrained and purposeful; provide loading, empty, error, offline, permission, and success states.
- The user has approved the design direction as complete and delegated creative execution within these constraints. Do not repeatedly ask for design approval unless a genuinely new product decision is required.

## 3. Core app and conversation experience
- Unified chat with multiple persistent conversations, titles, timestamps, search, rename, archive/delete, and conversation-specific model selection.
- Visible model selector in each conversation; selection must be explicit and retained per conversation.
- Send the relevant conversation history to the model, not only the newest message. Support context limits, truncation/summarization, and clear handling of provider limits.
- Attach images and general files, with previews, type/size validation, progress, retry, and clear provider compatibility feedback.
- Voice dictation for Arabic and English; do not imply voice input is available when the device has no compatible speech service.
- Persist conversations and attachments safely; protect private data and provide export/delete controls.
- Persistent memory and file library with user visibility and controls; distinguish stored user facts from model-generated assumptions.
- Show model/provider status, errors, latency where available, and which model answered.

## 4. Model and provider center
Support configurable integrations for:
- OpenRouter
- FreeLLMAPI
- LiteLLM
- Ollama
- OpenAI-compatible endpoints
- Termux-hosted/local bridges where technically supported
- FreeModel at https://freemodel.in/v1

Requirements:
- Provider configuration includes endpoint, authentication method, API key/credential, connection status, and supported capabilities.
- Fetch available models dynamically from the provider's model-list API where available. Users should not have to type model IDs manually as the normal setup path.
- Show only models actually returned or otherwise verified for that provider; explain when a provider does not expose model discovery.
- Support per-provider credential groups and multiple keys where the provider permits.
- Store secrets encrypted using Android Keystore-backed storage; never commit keys, log them, expose them in UI after save, or send them to unrelated services.
- Validate endpoint and credentials with a safe test request; handle invalid keys, unavailable endpoints, rate limits, quotas, and provider-specific errors.
- Support model capability metadata (text, image, file, audio, tool calling), context limits, availability, and pricing when known.
- Keep provider-specific protocol/auth adapters separate. OpenAI-compatible support must not be misrepresented as native support for every provider.

## 5. Smart routing and quota management
- Offer automatic smart routing and manual provider/model selection.
- Routing can consider task type and required capability, model availability, credential validity, quota remaining/reset, speed, reliability, and cost.
- User can set priorities and exclusions; show why a model/provider was selected.
- Free-model ordering should use newest verified availability and current quota state, with each key tracked independently.
- Track quotas and reset times only from reliable provider APIs. If no API exists, clearly label user-entered estimates/manual reset dates; never fabricate quota.
- Avoid silently switching models mid-conversation; disclose and request/record user permission according to settings.

## 6. Tools marketplace and discovery
- Discover tools from GitHub, MCP registries, APIs, and reputable open-source sources.
- Display purpose, source, version, compatibility, permissions requested, security considerations, dependencies, maintenance/update state, and cost where known.
- Separate model library from tool library.
- A saved catalog card is metadata only; it must not be described as installed or executable until a real compatible adapter and installation/execution verification exist.
- Require explicit user approval before installing tools, granting permissions, connecting accounts, or executing consequential actions.
- Keep a full audit record of discovery, approval, install, update, enable/disable, execution, and removal.

## 7. MCP and external tool integration
- Implement an actual MCP client/transport for supported server types, with capability discovery and schema validation.
- Let users add, inspect, enable, disable, and remove MCP servers.
- Show each server's requested permissions and tools before connection/use.
- Require approval before granting access or executing sensitive tool calls.
- Apply timeouts, output limits, input validation, error handling, and isolation boundaries.
- Never treat untrusted tool output as system instructions.

## 8. GitHub project agent
Provide approved GitHub account connection and practical repository workflows:
- List and open repositories; inspect branches, files, commits, issues, and pull requests within granted scopes.
- Read files, explain projects, create project scaffolds, create/edit files, debug and propose fixes, and track changes.
- Show diffs and a clear action summary before writing.
- Require user approval for repository writes and always require explicit approval for sensitive actions such as merge, release, deployment, permission changes, or destructive operations.
- Use least-privilege OAuth/app scopes; never ask users to paste personal access tokens into source code or commit secrets.
- Record operation history and provide recoverable change context where possible.

## 9. App as the primary tool manager
- Manage discovery, installation, configuration, launch, stop, update, and removal from the Hub UI wherever a supported adapter permits.
- Keep tool/model metadata, settings, credentials references, permissions, version, installation state, last update, and history.
- Present a unified Hub interface; do not force users into a Termux screen for ordinary management.
- Termux may operate as a hidden/helper execution environment only when technically required and explicitly configured; be honest when a task must open Termux or a remote host.
- Do not claim Android can silently install arbitrary Linux packages into its own sandbox or run unsupported daemons.

## 10. Runtime and dependency management
Manage compatible execution environments for Python, pip, uv, Node.js/npm, Git, Clang, CMake, Rust, FFmpeg, proot-distro, and Termux pkg/apt where configured. Docker is a compatibility target requiring a suitable Linux host or remote container service, not a guaranteed native Android runtime.
- Detect installed runtime versions and actual availability.
- Install/update only through trusted compatible package sources and explicit user approval.
- Show version, source, changelog/release notes, compatibility, dependency plan, and storage impact before updates.
- Preserve user configuration and support rollback when the ecosystem permits.
- Provide logs and clear failure recovery. Never display a catalog item as installed without verification.

## 11. Chrome and web workflow integration
- Integrate with the user's existing Chrome/browser sessions through supported Android intents, share flows, or approved browser APIs; do not install a second browser as a substitute.
- Assist with websites and control panels only through user-approved sessions and supported mechanisms.
- Never extract, read, or transmit saved browser passwords or autofill secrets to models.
- Ask before submitting forms, changing account settings, purchasing, publishing, deleting, or performing other consequential actions.
- Respect site security, authentication, and access controls.

## 12. Long-running tasks and continuity
- User-started long tasks should continue when the app is backgrounded or the screen is off, within Android OS, battery, network, and device limits.
- Use a foreground service for active user-visible work and WorkManager for deferrable persistent work, retries, and resumable steps.
- Persist task state, checkpoints, inputs, outputs, project files, logs, timestamps, progress, and cancellation state.
- Provide a task center with queued, running, paused, needs-attention, completed, and failed states; show current step, progress, elapsed time, and pause/resume/cancel/open-result actions.
- Recover safely after process death or reboot where Android permits. Explain interruptions caused by force-stop, battery restrictions, OEM policies, or network loss; never promise unlimited background execution.
- Handle rate limits, expired credentials, low storage, connectivity loss, and cancellation without losing completed work.
- Background tasks must not silently acquire new permissions or perform newly discovered consequential actions.

## 13. Files, memory, settings, and audit
- Provide persistent user file storage with import, preview, organization, search, export, and delete.
- Preserve tool/model settings, dependency state, last update, task history, and conversation history.
- Provide a complete activity/audit log with timestamps and clear actor/action/result information.
- Include controls to review and delete stored conversations, files, memory, credentials, and task history.
- Sensitive local data must be protected at rest; minimize retained data and explain any cloud transmission.

## 14. Security and permissions
- Least privilege by default. Explain each permission at the point it is needed.
- Explicit approval before connecting accounts, granting tool permissions, installing software, repository writes, deployment, merge, publishing, purchases, or destructive operations.
- Keep credentials in encrypted storage and out of source control, logs, analytics, model prompts, and unrelated tools.
- Validate network endpoints and tool inputs; use TLS for remote connections; protect against untrusted tool output and prompt injection.
- Provide disconnect/revoke controls and clear audit history.

## 15. Delivery, quality, and acceptance
- Deliver a complete Android project and a buildable APK through GitHub Actions.
- Arabic and English must both be usable; verify RTL/LTR, accessibility, small screens, loading/error/empty/offline states.
- Automated checks should cover unit logic, provider adapters, persistence, permission/approval gates, and task recovery.
- End-to-end tests should cover provider setup/model discovery/chat, per-conversation model retention, image/file attachment, voice dictation availability, GitHub approval flow, MCP permissions, tool/runtime installation gates, backgrounding/screen-off, network interruption, process recreation, pause/resume/cancel, and audit records.
- Do not ask the user to test every partial increment. Complete implementation and run the available build/integration tests first, then arrange one consolidated acceptance test with the user.
- Clearly label anything blocked by external credentials, provider APIs, Android restrictions, or missing compatible hardware/host.
- Never call the product complete while major agreed capabilities remain catalog-only, mocked, or unimplemented.

## 16. Provider and project configuration
- The configured FreeModel endpoint is https://freemodel.in/v1.
- Repository: https://github.com/amramr01200/AI-Universal-Hub
- Keep all private credentials out of this specification and repository.

## 17. Termux AI launch and in-app conversation handoff — explicit requirement
- When the user starts an AI agent/model configured to run through Termux from inside AI Universal Hub, the intended experience is to open directly into that agent's dedicated conversation inside the Hub, not leave the user stranded at a Termux shell.
- The Hub must retain the Termux AI configuration: display name, command/entry point, working directory, endpoint/host and port when applicable, startup arguments, environment-variable references, connection mode, and last known status. Secrets must be stored only in encrypted storage and must never be written into source files, chat history, logs, or plain-text configuration exports.
- Provide a persistent mapping between each Termux AI profile and its Hub conversation. Reopening the profile returns to the same conversation with its selected provider/model and history, while allowing a new conversation when the user asks.
- On launch, the Hub checks whether the configured Termux service/agent is reachable. If stopped, it may request launch through an explicitly configured and authorized Termux bridge; then it waits for readiness with a bounded timeout and clear progress/error state. When ready, the Hub opens the linked in-app chat and routes requests/replies through the configured local HTTP/API bridge.
- Prefer a secure localhost bridge with a random per-install pairing secret, loopback-only binding, bounded request sizes, timeouts, and explicit pairing/permission setup. Do not assume arbitrary Termux shell execution is available. If using Termux RUN_COMMAND or another Android intent/plugin bridge, require the relevant Termux version, exported permission, user-granted permission, and compatible plugin setup; explain when manual setup is needed.
- Do not expose a Termux terminal screen during ordinary chat. Only open Termux when the user explicitly asks to inspect/debug it or when Android/Termux requires a one-time permission or setup action.
- If the app is offline, keep the Termux profile and conversation visible, report whether the local service is reachable, and queue only those requests the user explicitly chooses to queue. Never imply an offline cloud model is running locally.
- Persist profile configuration and conversation association across app restarts and updates. Provide edit, test connection, start, stop, reconnect, and remove controls, plus a visible status indicator and an audit record for launch/stop/configuration actions.
- Acceptance test: configure a Termux-hosted AI once; approve the bridge; start it from the Hub; verify the correct saved Hub conversation opens automatically; send a message and receive a reply without manually navigating Termux; close/reopen the Hub and verify the same profile, settings, model, and conversation are restored; test stopped service, unavailable bridge, timeout, and revoked permission states.



## 18. Live token capacity and usage per model — explicit requirement
- Display token information beside every model in selectors, provider/model cards, and the active project assistant panel.
- Distinguish clearly between context window capacity (maximum tokens accepted in one context), current context usage, estimated remaining context capacity, and account/API quota or credits. These are different measurements and must never be conflated.
- Refresh counts after each message, model switch, attachment change, tool call, context compaction, or project update. Update immediately from local estimates, then reconcile with authoritative provider usage when returned.
- Show explicit numeric values and units. Label estimates as estimates and authoritative figures as provider-reported; show last-updated time when live refresh is unavailable.
- Use the model's actual tokenizer when available. Otherwise show a clearly labeled approximation; never present character or word counts as exact token counts.
- For project continuation, estimate the token budget needed from project files, conversation history, instructions, and planned output. Compare it with each compatible model's remaining context capacity and provider quota where available.
- Show which models can continue the project within budget, which may need summarization/compaction or file selection, and which cannot proceed due to context, quota, capability, or connectivity limits. Explain the reason and let the user choose; never silently discard project context or switch models.
- Some providers expose only per-request usage, some expose account credits/quota, and some expose neither. If unavailable, show 'غير متاح من المزود' / 'Not exposed by provider' rather than inventing a number.
- Acceptance checks: verify counts update after messages and file attachments; distinguish context capacity from account quota; compare models for continuing the same project; label estimates; never fabricate unavailable quota; verify refresh after provider responses.

## 19. Claude Code integration through the Termux runtime — approved feature

The Hub should provide a dedicated Claude Code workspace that can be launched from inside the Android app and linked to a persistent Hub conversation. This is a real CLI integration, not a web-page shortcut or a generic chat label.

### Runtime and compatibility
- Treat Termux as an explicitly configured helper runtime. Detect Node.js/npm and Claude Code availability before showing the feature as ready.
- Claude Code's documented installation/runtime requirements must be checked against the installed Termux environment. Android/Termux is not listed as an officially supported operating system in Anthropic's published system requirements; therefore the app must label it experimental until the actual device passes installation and runtime tests.
- Support the documented CLI installation path only after showing the package, source, required storage, and user approval. Do not silently install or update packages.
- Keep the Claude Code CLI version and runtime status visible, with test, install, update, start, stop, and diagnostic actions.

### Provider and protocol routing
- Add a dedicated Claude Code provider adapter using the Anthropic Messages API contract.
- Configure the CLI through supported environment settings such as ANTHROPIC_BASE_URL and the appropriate authentication variable. Keep model IDs and endpoint paths provider-specific; do not assume an OpenAI-compatible /chat/completions endpoint can serve Claude Code.
- A provider may be selected for Claude Code only after a compatibility test confirms the required Anthropic Messages API behavior, streaming, tool-use request/response format, and model-name mapping.
- If FreeLLMAPI exposes only OpenAI-compatible endpoints, use a separately configured, reviewed protocol-translation gateway; otherwise mark it incompatible for Claude Code instead of claiming it works.
- Preserve provider/model choice per Claude Code profile and conversation. Never silently route to another model or provider.
- Do not represent a third-party model as an Anthropic Claude model. Clearly show the actual upstream provider and model.

### Credentials and privacy
- Keep provider credentials in the Hub's encrypted credential store. Never commit them, include them in exported project files, place them in chat history, or print them in logs.
- Do not pass long-lived credentials in shell command text, command-line arguments, or broadcast extras. The bridge must use a short-lived, narrowly scoped handoff or a local authenticated channel with explicit pairing.
- Do not import credentials from the uploaded provider bundle unless the user has rotated any exposed keys and explicitly re-enters the replacement credentials into secure storage.
- Use only legitimate provider access and quotas; do not bypass account limits, subscription checks, or provider authentication.

### In-app conversation and project workspace
- Launch Claude Code from the Hub into a dedicated conversation associated with a selected project directory.
- Stream CLI events and final responses back into the Hub conversation; persist session identifiers and profile-to-conversation mappings so users can resume the same session.
- Scope file access to the selected project workspace. Show the active working directory and the files the agent can access.
- Require user approval for file edits, shell commands, installs, repository writes, and other consequential operations. Provide a visible diff/action summary before applying changes where practical.
- Never start the CLI with permission prompts disabled. Keep permission checks enabled and make denied actions visible in the conversation.
- Provide bounded execution time, output limits, cancellation, pause/reconnect behavior, and recoverable task state. Do not claim a background task continues unless the process and its status are verified.
- Integrate MCP servers only through the Hub's reviewed MCP configuration and permission flow; do not inherit unknown global MCP configuration without showing it to the user.

### Acceptance tests
- Install or detect Node.js and Claude Code in the configured Termux environment and run a version/diagnostic check.
- Verify whether the actual device/Termux version is compatible; clearly report unsupported or failed states.
- Test one Anthropic Messages-compatible provider and one OpenAI-only provider; the latter must be rejected or routed through an explicitly configured translator.
- Verify FreeLLMAPI only after protocol and tool-use compatibility tests pass.
- Start Claude Code from the Hub, open the correct linked conversation, send a prompt, stream the response, and resume the same session after closing/reopening the app.
- Verify credential secrecy, project-directory scoping, permission prompts, command cancellation, process failure, timeout, network loss, and audit records.

## 20. Product direction update — Claude Code is the primary experience

This update supersedes the broad multi-tool showcase direction for the primary user experience.

- The main product is Claude Code running as the coding agent inside AI Universal Hub. Do not build a large collection of unrelated tools, competing agents, or a crowded model catalogue as the primary workflow.
- Keep only the supporting components required to run Claude Code: the Termux runtime bridge, Node.js/npm prerequisite, Claude Code CLI, a small compatible-provider router, encrypted credential storage, project workspace access, GitHub integration, and only MCP servers explicitly enabled for the active project.
- The UI should foreground one Claude Code session and its current provider/model, runtime status, project, task output, approval requests, and remaining provider quota when the provider actually exposes it. Keep advanced provider and runtime settings behind the existing settings surface.
- Allow the user to register several API credentials and compatible upstreams behind one Claude Code gateway profile. The gateway may choose a healthy configured credential or perform a documented fallback only when the credential belongs to the same authorized provider/account policy and the provider permits it. Keep per-key usage, provider identity, limits, and errors separate and visible.
- Never combine credits from unrelated accounts into a fictitious balance, bypass per-key or per-account quotas, rotate identities to evade rate limits, or retry a rejected request through another account to defeat provider restrictions. On quota/rate-limit responses, honor Retry-After and provider policy; switch only to a separately authorized provider/model when the user has enabled that fallback.
- A credential can be used by Claude Code only if its endpoint implements the Anthropic Messages API behavior Claude Code requires, or is routed through a reviewed protocol adapter that correctly translates the required request, streaming, tool-use, and response semantics. OpenAI-compatible chat-completions support alone is insufficient.
- Prefer a short, curated set of providers that pass the Claude Code compatibility test. Do not automatically add every model discovered by a provider to the Claude Code selector.
- The user-supplied reference URL https://ikhtiardhaki.com/v/claude-code-free is an optional candidate integration reference only. Its contents and safety have not been verified. Do not execute remote scripts, install packages, or send credentials to it until its source, code, endpoint behavior, ownership, privacy policy, and provider terms have been reviewed and explicitly approved.
- Preserve existing provider records and encrypted credentials during migration. Do not import or reuse any API keys from the previously uploaded plaintext JSON; require the user to revoke exposed keys and enter replacement credentials directly into encrypted app storage.

### Acceptance criteria for the focused Claude Code experience
- The app opens directly into a Claude Code workspace, not a generic tool marketplace.
- A single health check reports Termux, Node.js, npm, Claude Code CLI, selected gateway, and selected model readiness independently.
- The user can choose one default compatible provider/model and optionally enable explicitly authorized fallbacks.
- All credential use is auditable without logging or displaying secret values.
- Rate limits and remaining quota are provider-reported, never inferred by summing unrelated keys.
- An incompatible provider is clearly disabled for Claude Code and is not silently translated or selected.
- Project file changes, shell commands, Git operations, installs, and MCP actions remain subject to visible user approval.

