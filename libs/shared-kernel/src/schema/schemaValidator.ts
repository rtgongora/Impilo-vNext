/**
 * SchemaValidator — Companion Spec section 2.2 envelope gate.
 *
 * Blocks non-compliant events BEFORE they are emitted.
 * Validates required envelope fields and meta.schema_version enforcement.
 * Does NOT implement JSON Schema registry calls.
 */

/** Required top-level envelope fields per Companion Spec 2.2. */
const REQUIRED_ENVELOPE_FIELDS: readonly string[] = [
  "event_id",
  "event_type",
  "schema_version",
  "correlation_id",
  "idempotency_key",
  "producer",
  "tenant_id",
  "pod_id",
  "occurred_at",
  "emitted_at",
  "subject_type",
  "subject_id",
  "payload",
] as const;

export class SchemaValidationError extends Error {
  public readonly code: string;
  public readonly details: {
    missing_fields?: string[];
    invalid_fields?: string[];
  };

  constructor(
    message: string,
    details: { missing_fields?: string[]; invalid_fields?: string[] },
  ) {
    super(message);
    this.name = "SchemaValidationError";
    this.code = "SCHEMA_VALIDATION_FAILED";
    this.details = details;
  }
}

/**
 * Validate that all required envelope fields are present and non-nullish.
 * Throws SchemaValidationError if any are missing.
 */
export function validateEventEnvelope(event: any): void {
  if (event == null || typeof event !== "object") {
    throw new SchemaValidationError("Event must be a non-null object", {
      missing_fields: [...REQUIRED_ENVELOPE_FIELDS],
    });
  }

  const missing: string[] = [];
  for (const field of REQUIRED_ENVELOPE_FIELDS) {
    if (event[field] === undefined || event[field] === null) {
      missing.push(field);
    }
  }

  if (missing.length > 0) {
    throw new SchemaValidationError(
      `Missing required envelope fields: ${missing.join(", ")}`,
      { missing_fields: missing },
    );
  }
}

/**
 * If event.meta exists, enforce that meta.schema_version is a positive integer (>= 1).
 * If meta is absent, validation passes (meta is optional).
 * Throws SchemaValidationError on invalid meta.schema_version.
 */
export function assertMetaSchemaVersion(event: any): void {
  if (event == null || typeof event !== "object") {
    return; // envelope validation catches this
  }

  if (!("meta" in event) || event.meta === undefined || event.meta === null) {
    return; // meta is optional
  }

  const meta = event.meta;
  if (typeof meta !== "object") {
    throw new SchemaValidationError(
      "meta must be an object when present",
      { invalid_fields: ["meta"] },
    );
  }

  if (
    !("schema_version" in meta) ||
    meta.schema_version === undefined ||
    meta.schema_version === null
  ) {
    throw new SchemaValidationError(
      "meta.schema_version is required when meta is present",
      { missing_fields: ["meta.schema_version"] },
    );
  }

  const ver = meta.schema_version;
  if (typeof ver !== "number" || !Number.isInteger(ver) || ver < 1) {
    throw new SchemaValidationError(
      `meta.schema_version must be an integer >= 1, got: ${JSON.stringify(ver)}`,
      { invalid_fields: ["meta.schema_version"] },
    );
  }
}

/**
 * Full event validation: envelope fields + meta.schema_version enforcement.
 * Calls both validateEventEnvelope and assertMetaSchemaVersion.
 */
export function validateEvent(event: any): void {
  validateEventEnvelope(event);
  assertMetaSchemaVersion(event);
}
