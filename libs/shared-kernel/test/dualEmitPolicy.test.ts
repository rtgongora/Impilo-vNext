import { describe, it, expect } from "vitest";
import {
  resolveEmitMode,
  shouldEmitV1,
  shouldEmitV1_1,
} from "../src/events/dualEmitPolicy.js";

describe("DualEmitPolicy", () => {
  describe("resolveEmitMode", () => {
    it("returns V1_ONLY when env is V1_ONLY", () => {
      expect(resolveEmitMode({ EMIT_MODE: "V1_ONLY" })).toBe("V1_ONLY");
    });

    it("returns V1_1_ONLY when env is V1_1_ONLY", () => {
      expect(resolveEmitMode({ EMIT_MODE: "V1_1_ONLY" })).toBe("V1_1_ONLY");
    });

    it("returns DUAL when env is DUAL", () => {
      expect(resolveEmitMode({ EMIT_MODE: "DUAL" })).toBe("DUAL");
    });

    it("defaults to DUAL when EMIT_MODE is missing", () => {
      expect(resolveEmitMode({})).toBe("DUAL");
    });

    it("defaults to DUAL when EMIT_MODE is an invalid value", () => {
      expect(resolveEmitMode({ EMIT_MODE: "INVALID" })).toBe("DUAL");
    });

    it("defaults to DUAL when EMIT_MODE is empty string", () => {
      expect(resolveEmitMode({ EMIT_MODE: "" })).toBe("DUAL");
    });

    it("is case-sensitive (lowercase rejected)", () => {
      expect(resolveEmitMode({ EMIT_MODE: "dual" })).toBe("DUAL");
    });
  });

  describe("shouldEmitV1", () => {
    it("true for V1_ONLY", () => {
      expect(shouldEmitV1("V1_ONLY")).toBe(true);
    });

    it("true for DUAL", () => {
      expect(shouldEmitV1("DUAL")).toBe(true);
    });

    it("false for V1_1_ONLY", () => {
      expect(shouldEmitV1("V1_1_ONLY")).toBe(false);
    });
  });

  describe("shouldEmitV1_1", () => {
    it("true for V1_1_ONLY", () => {
      expect(shouldEmitV1_1("V1_1_ONLY")).toBe(true);
    });

    it("true for DUAL", () => {
      expect(shouldEmitV1_1("DUAL")).toBe(true);
    });

    it("false for V1_ONLY", () => {
      expect(shouldEmitV1_1("V1_ONLY")).toBe(false);
    });
  });
});
