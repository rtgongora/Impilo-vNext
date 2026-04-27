# Cross-repo voice dictation integration

This document describes how sibling repositories (for example `impilo-structure`, telemedicine shells, or other Next/React apps) should align with Impilo vNext voice dictation contracts and avoid divergent implementations.

## Canonical contracts

Types and the long-term `DictationProvider` shape live in the **`shared-ui`** package:

- Path in monorepo: `ui/shared-ui` (package name `shared-ui`).
- Import surface: `DictationLanguage`, `TranscriptionResult`, `DictationConfig`, `DictationProvider`, `createNoopDictationProvider`, etc. (see `ui/shared-ui/dictation/` and `ui/shared-ui/index.ts`).

Shells **`one-ui-shell`** and **`experience`** wire the UI hook `useSpeechToText` to those types and expose:

- `onTranscriptionResult` — structured segments (`TranscriptionResult`) for audit, Mvumo, or analytics (no PHI in event keys; treat transcript as clinical content under existing retention rules).
- `allowCloudStt` — default **`false`**. When `false`, the hook does **not** start `MediaRecorder` or call server transcription; only browser Web Speech runs when available.

## Dependency pattern

1. Add a **file** or **workspace** dependency on `shared-ui` (same as `one-ui-shell` / `experience`):

   ```json
   "dependencies": {
     "shared-ui": "file:../Impilo-vNext/ui/shared-ui"
   }
   ```

   Adjust the relative path to wherever `shared-ui` sits relative to your app root.

2. **Next.js 14**: set `transpilePackages: ["shared-ui"]` and, for `output: "standalone"`, set `experimental.outputFileTracingRoot` to the monorepo `ui` folder parent so the standalone bundle traces the linked package (see `ui/one-ui-shell/next.config.mjs`).

3. TypeScript: ensure `moduleResolution` can resolve the package (`bundler` or `node16` is typical for Next).

## Product and privacy alignment

- Do not set `allowCloudStt` (or `DictationConfig.allowServerAudio`) to true without explicit product decision and Mvumo / consent flows documented under `docs/privacy` and `docs/security`.
- External repos that today use ad hoc `MediaRecorder` + HTTP STT should migrate to the same gate and types so compliance review has a single contract.

## Suggested migration for duplicate components

If a sibling repo ships its own `DictationButton` or speech hook:

1. Import types from `shared-ui` and match `TranscriptionResult` emission on final and interim segments consistent with `useSpeechToText` in `one-ui-shell`.
2. Prefer **reusing** or thin-wrapping the shell implementations rather than forking the ElevenLabs chunking logic.

## Related docs

- `docs/architecture/voice-dictation-platform-integration.md`
- `docs/product/platform-wide-voice-dictation-doctrine.md`
- `docs/plan/SERVICE_CATALOG.md` (Experience adjunct — voice dictation)
