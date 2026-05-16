# Nompilo Search Voice Command Architecture

## Intent

Position Nompilo as the command and intelligence layer spanning search, voice, guidance, and action orchestration across the Health Operating System.

## Surface Model

- Global search bar and command palette
- Floating assistant and inline journey prompts
- Voice dictation and mobile voice
- Provider workspace, client app, dashboard assistant, Fundo assistant, support/help interface

## Core Runtime Components

- Intent classifier: maps free text and voice transcripts to command intents.
- Permission filter: filters search sources and tools by role, consent, purpose, policy, and workspace.
- Tool orchestrator: invokes approved source systems and composes governed responses.
- Response formatter: labels data classes and uncertainty, then renders user-facing cards and prompts.
- Handoff controller: routes unresolved cases to human support safely with context.

## Safety Boundaries

- No direct mutation of source records without explicit approved action path.
- No unsupported clinical confidence claims.
- Mandatory source attribution in command results.
- Explicit labeling for external-source content and uncertain output.
