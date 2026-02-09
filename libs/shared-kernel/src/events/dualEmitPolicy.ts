/**
 * DualEmitPolicy — Wave migration emit-mode resolution.
 *
 * During migration, services may emit BOTH v1.0 (legacy full-state)
 * and v1.1 (delta-first envelope) events. This module decides what
 * to emit; it does NOT send to Kafka.
 */

export type EmitMode = "V1_ONLY" | "V1_1_ONLY" | "DUAL";

const VALID_MODES = new Set<string>(["V1_ONLY", "V1_1_ONLY", "DUAL"]);

/**
 * Resolve the emit mode from environment variables.
 *
 * Reads `EMIT_MODE` from the provided env (defaults to `process.env`).
 * Returns "DUAL" if the value is missing or invalid (safe migration default).
 */
export function resolveEmitMode(env?: NodeJS.ProcessEnv): EmitMode {
  const source = env ?? process.env;
  const raw = source.EMIT_MODE;
  if (raw && VALID_MODES.has(raw)) {
    return raw as EmitMode;
  }
  return "DUAL";
}

/** Whether legacy v1.0 full-state events should be emitted. */
export function shouldEmitV1(mode: EmitMode): boolean {
  return mode === "V1_ONLY" || mode === "DUAL";
}

/** Whether v1.1 delta-first envelope events should be emitted. */
export function shouldEmitV1_1(mode: EmitMode): boolean {
  return mode === "V1_1_ONLY" || mode === "DUAL";
}
