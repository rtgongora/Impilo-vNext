import { describe, it, expect } from "vitest";
import {
  validateEventEnvelope,
  assertMetaSchemaVersion,
  validateEvent,
  SchemaValidationError,
} from "../src/schema/schemaValidator.js";

/** Helper: returns a valid minimal event envelope. */
function validEvent(overrides?: Record<string, any>) {
  return {
    event_id: "evt-001",
    event_type: "test.event",
    schema_version: "1.1.0",
    correlation_id: "corr-001",
    idempotency_key: "idem-001",
    producer: "test-service",
    tenant_id: "tenant-1",
    pod_id: "pod-1",
    occurred_at: "2025-01-01T00:00:00Z",
    emitted_at: "2025-01-01T00:00:01Z",
    subject_type: "patient",
    subject_id: "cpid-123",
    payload: { data: "test" },
    ...overrides,
  };
}

describe("SchemaValidator", () => {
  describe("validateEventEnvelope", () => {
    it("passes for a valid minimal event", () => {
      expect(() => validateEventEnvelope(validEvent())).not.toThrow();
    });

    it("fails when event is null", () => {
      expect(() => validateEventEnvelope(null)).toThrow(SchemaValidationError);
    });

    it("fails with missing_fields listing all absent fields", () => {
      try {
        validateEventEnvelope({ event_id: "evt-001" });
        expect.fail("should have thrown");
      } catch (err) {
        expect(err).toBeInstanceOf(SchemaValidationError);
        const e = err as SchemaValidationError;
        expect(e.code).toBe("SCHEMA_VALIDATION_FAILED");
        expect(e.details.missing_fields).toBeDefined();
        expect(e.details.missing_fields).toContain("event_type");
        expect(e.details.missing_fields).toContain("payload");
        expect(e.details.missing_fields).not.toContain("event_id");
      }
    });

    it("fails when a required field is null", () => {
      try {
        validateEventEnvelope(validEvent({ payload: null }));
        expect.fail("should have thrown");
      } catch (err) {
        const e = err as SchemaValidationError;
        expect(e.details.missing_fields).toContain("payload");
      }
    });
  });

  describe("assertMetaSchemaVersion", () => {
    it("passes when meta is absent", () => {
      expect(() => assertMetaSchemaVersion(validEvent())).not.toThrow();
    });

    it("passes when meta.schema_version is a valid integer >= 1", () => {
      expect(() =>
        assertMetaSchemaVersion(validEvent({ meta: { schema_version: 1 } })),
      ).not.toThrow();
      expect(() =>
        assertMetaSchemaVersion(validEvent({ meta: { schema_version: 42 } })),
      ).not.toThrow();
    });

    it("fails when meta exists but meta.schema_version is missing", () => {
      try {
        assertMetaSchemaVersion(validEvent({ meta: {} }));
        expect.fail("should have thrown");
      } catch (err) {
        const e = err as SchemaValidationError;
        expect(e.code).toBe("SCHEMA_VALIDATION_FAILED");
        expect(e.details.missing_fields).toContain("meta.schema_version");
      }
    });

    it("fails when meta.schema_version is 0", () => {
      try {
        assertMetaSchemaVersion(validEvent({ meta: { schema_version: 0 } }));
        expect.fail("should have thrown");
      } catch (err) {
        const e = err as SchemaValidationError;
        expect(e.details.invalid_fields).toContain("meta.schema_version");
      }
    });

    it("fails when meta.schema_version is a non-number", () => {
      try {
        assertMetaSchemaVersion(
          validEvent({ meta: { schema_version: "1.0" } }),
        );
        expect.fail("should have thrown");
      } catch (err) {
        const e = err as SchemaValidationError;
        expect(e.details.invalid_fields).toContain("meta.schema_version");
      }
    });

    it("fails when meta.schema_version is a float", () => {
      try {
        assertMetaSchemaVersion(
          validEvent({ meta: { schema_version: 1.5 } }),
        );
        expect.fail("should have thrown");
      } catch (err) {
        const e = err as SchemaValidationError;
        expect(e.details.invalid_fields).toContain("meta.schema_version");
      }
    });

    it("fails when meta.schema_version is negative", () => {
      try {
        assertMetaSchemaVersion(
          validEvent({ meta: { schema_version: -1 } }),
        );
        expect.fail("should have thrown");
      } catch (err) {
        const e = err as SchemaValidationError;
        expect(e.details.invalid_fields).toContain("meta.schema_version");
      }
    });
  });

  describe("validateEvent (combined)", () => {
    it("passes for a fully valid event with meta", () => {
      expect(() =>
        validateEvent(validEvent({ meta: { schema_version: 2 } })),
      ).not.toThrow();
    });

    it("passes for a valid event without meta", () => {
      expect(() => validateEvent(validEvent())).not.toThrow();
    });

    it("fails on missing envelope fields even if meta is valid", () => {
      expect(() =>
        validateEvent({ meta: { schema_version: 1 } }),
      ).toThrow(SchemaValidationError);
    });

    it("fails on valid envelope but invalid meta", () => {
      expect(() =>
        validateEvent(validEvent({ meta: { schema_version: 0 } })),
      ).toThrow(SchemaValidationError);
    });
  });
});
