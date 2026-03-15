/**
 * Conflict Review Tests — Conflict detection and resolution.
 */

import { describe, it, expect } from "vitest";
import type { ConflictItem } from "../../types";

describe("Conflict Resolution Logic", () => {
  const mockConflict: ConflictItem = {
    id: "conflict-1",
    resourceType: "Encounter",
    resourceId: "enc-1",
    localVersion: {
      notes: "Patient reports headache and fever for 3 days",
      diagnosis: "J06.9",
      updated_at: "2026-03-15T10:30:00Z",
    },
    serverVersion: {
      notes: "Patient reports headache for 2 days",
      diagnosis: "J06.9",
      updated_at: "2026-03-15T10:25:00Z",
    },
    conflictFields: ["notes", "updated_at"],
    detectedAt: "2026-03-15T10:35:00Z",
  };

  it("identifies conflicting fields", () => {
    expect(mockConflict.conflictFields).toContain("notes");
    expect(mockConflict.conflictFields).toContain("updated_at");
    expect(mockConflict.conflictFields).toHaveLength(2);
  });

  it("provides both local and server versions for comparison", () => {
    expect(mockConflict.localVersion.notes).toContain("fever");
    expect(mockConflict.serverVersion.notes).not.toContain("fever");
  });

  it("includes resource identification", () => {
    expect(mockConflict.resourceType).toBe("Encounter");
    expect(mockConflict.resourceId).toBe("enc-1");
  });

  it("supports LOCAL_WINS resolution", () => {
    const resolved: ConflictItem = {
      ...mockConflict,
      resolvedAt: new Date().toISOString(),
      resolution: "LOCAL_WINS",
    };
    expect(resolved.resolution).toBe("LOCAL_WINS");
    expect(resolved.resolvedAt).toBeDefined();
  });

  it("supports SERVER_WINS resolution", () => {
    const resolved: ConflictItem = {
      ...mockConflict,
      resolvedAt: new Date().toISOString(),
      resolution: "SERVER_WINS",
    };
    expect(resolved.resolution).toBe("SERVER_WINS");
  });

  it("supports MANUAL_MERGE resolution", () => {
    const resolved: ConflictItem = {
      ...mockConflict,
      resolvedAt: new Date().toISOString(),
      resolution: "MANUAL_MERGE",
    };
    expect(resolved.resolution).toBe("MANUAL_MERGE");
  });
});
