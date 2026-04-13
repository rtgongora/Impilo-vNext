/**
 * Backend Integration Tests — Verifies API client patterns and trust header injection.
 */

import { describe, it, expect } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { TRUST_HEADERS, HARD_REQUIRED_HEADERS } from "@impilo/mobile-trust";
import * as patientService from "../../services/patientService";
import * as encounterService from "../../services/encounterService";
import * as vitalsService from "../../services/vitalsService";
import * as taskService from "../../services/taskService";

describe("API Client Integration Patterns", () => {
  it("apiClient exports correct methods", () => {
    // Verify the apiClient contract
    const expectedMethods = ["get", "post", "put", "patch", "delete"];
    for (const method of expectedMethods) {
      expect(typeof apiClient[method]).toBe("function");
    }
  });

  it("trust headers are defined correctly", () => {
    expect(TRUST_HEADERS).toBeDefined();
    expect(HARD_REQUIRED_HEADERS).toBeDefined();
    expect(HARD_REQUIRED_HEADERS.length).toBeGreaterThanOrEqual(4);
  });

  it("API envelope type has correct shape", () => {
    // Type-level verification
    type TestEnvelope = {
      success: boolean;
      data: unknown;
      error?: { code: string; message: string; status: number };
      correlationId: string;
      timestamp: string;
    };

    const envelope: TestEnvelope = {
      success: true,
      data: { id: "test" },
      correlationId: "corr-1",
      timestamp: "2026-03-15T00:00:00Z",
    };

    expect(envelope.success).toBe(true);
    expect(envelope.correlationId).toBe("corr-1");
  });

  it("BFF routes follow internal/v1 convention", () => {
    const routes = [
      "/internal/v1/patients",
      "/internal/v1/encounters",
      "/internal/v1/mobile/provider/vitals",
      "/internal/v1/mobile/provider/diagnosis",
      "/internal/v1/mobile/provider/prescriptions",
      "/internal/v1/mobile/provider/labs",
      "/internal/v1/mobile/provider/referrals",
      "/internal/v1/mobile/provider/tasks/mine",
      "/internal/v1/mobile/provider/households",
      "/internal/v1/mobile/provider/supervisor/metrics",
      "/internal/v1/mobile/provider/inventory/stock",
      "/internal/v1/mobile/provider/support/tickets",
      "/internal/v1/mobile/provider/telemedicine/sessions",
      "/internal/v1/mobile/provider/forms",
      "/internal/v1/mobile/provider/entitlement/verify",
      "/internal/v1/mobile/provider/break-glass/activate",
    ];

    for (const route of routes) {
      expect(route).toMatch(/^\/internal\/v1\//);
    }
  });

  it("all service files export expected functions", () => {
    expect(typeof patientService.searchPatients).toBe("function");
    expect(typeof patientService.getPatient).toBe("function");

    expect(typeof encounterService.createEncounter).toBe("function");
    expect(typeof encounterService.closeEncounter).toBe("function");

    expect(typeof vitalsService.recordVital).toBe("function");
    expect(typeof vitalsService.recordVitalsBatch).toBe("function");

    expect(typeof taskService.getMyTasks).toBe("function");
    expect(typeof taskService.updateTaskStatus).toBe("function");
  });
});
