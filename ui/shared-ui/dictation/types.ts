/**
 * Shared dictation contracts — platform-wide voice input (narrative assist).
 * Consumers: one-ui-shell, future shells; implementations wrap Web Speech, native, or server STT.
 */

/** BCP-47 or platform-specific tag (e.g. en-US, en-GB). */
export type DictationLanguage = string;

/** Confidence score 0–1 when provided by engine. */
export type TranscriptionConfidence = number;

export interface TranscriptionAlternative {
  /** Raw text for this alternative. */
  transcript: string;
  /** Optional per-alternative confidence. */
  confidence?: TranscriptionConfidence;
}

export interface TranscriptionResult {
  /** Best-effort primary transcript. */
  transcript: string;
  /** Other hypotheses when engine exposes them. */
  alternatives?: TranscriptionAlternative[];
  /** Overall confidence when engine exposes it. */
  confidence?: TranscriptionConfidence;
  /** True when the engine considers this segment final (vs interim). */
  isFinal: boolean;
  /** Language used for this result (may differ from requested). */
  language?: DictationLanguage;
}

export interface DictationSession {
  /** Opaque session id (client-generated UUID or provider token). */
  id: string;
  requestedLanguage: DictationLanguage;
  startedAtUtc: string;
  /** Implementation-specific tags (e.g. engine = browser | cloud). */
  metadata?: Record<string, string | number | boolean | null>;
}

export type DictationErrorCode =
  | "NOT_SUPPORTED"
  | "PERMISSION_DENIED"
  | "NETWORK"
  | "SERVICE_UNAVAILABLE"
  | "ENGINE_ABORTED"
  | "UNKNOWN";

export interface DictationError {
  code: DictationErrorCode;
  message: string;
  cause?: unknown;
}

/** Boundaries for optional server-side STT or logging (no PII in keys). */
export interface DictationAuditMetadata {
  tenantId?: string;
  actorId?: string;
  actorType?: string;
  purposeOfUse?: string;
  /** Stable field or form identifier (not patient name). */
  fieldKey?: string;
  /** FHIR resource type + logical id when applicable. */
  clinicalContext?: string;
  correlationId?: string;
}

export interface DictationConfig {
  language: DictationLanguage;
  /** When true, engine may stream interim results (caller still must not auto-save). */
  interimResults?: boolean;
  /** When true, keep listening until user stops (browser engines). */
  continuous?: boolean;
  /** Optional engine hint — implementations map to Web Speech / native / HTTP. */
  preferredEngine?: "auto" | "browser" | "server";
  audit?: DictationAuditMetadata;
  /** If true, raw audio may be sent to server STT — requires explicit product + Mvumo consent. */
  allowServerAudio?: boolean;
}

/**
 * Pluggable dictation backend. Shells implement with Web Speech API, native bridges, or BFF-mediated STT.
 */
export interface DictationProvider {
  readonly id: string;
  /** Whether this provider can start a session in the current environment (sync or async). */
  isAvailable(): boolean | Promise<boolean>;
  /** Begin capture / recognition; must not persist clinical content by itself. */
  startSession(config: DictationConfig): Promise<DictationSession>;
  /** End capture / recognition and release mic. */
  endSession(session: DictationSession): Promise<void>;
  /** Subscribe to transcript events until endSession (implementation-defined subscription pattern). */
  subscribe?(handler: (result: TranscriptionResult) => void): () => void;
}
