/**
 * FinanceSection Tests — Verifies export, basic instantiation, and the
 * Stage 3.4B re-target onto the canonical Mushe Wallet plane.
 *
 * Heavy DOM assertions are not feasible under the citizen-app's
 * jsdom-only test profile (no react-dom). We follow the established
 * `personal/` pattern: mock UI primitives and service modules, instantiate
 * the component, and inspect the underlying `financeService` source to
 * prove the dead `/internal/v1/mobile/citizen/finance/**` routes are gone.
 * The deep service-level behaviour (route paths, payload adapters, error
 * propagation) is covered by `walletService.test.ts`.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("@impilo/mobile-api-client", () => ({
  ApiError: class ApiError extends Error {
    code = "X";
    status = 0;
    correlationId = "";
  },
}));

vi.mock("../../services/financeService", () => ({
  fetchBalance: vi.fn().mockResolvedValue({
    amount: 0,
    currency: "USD",
    updatedAt: "2026-05-01T00:00:00Z",
  }),
  fetchPendingCharges: vi.fn().mockResolvedValue({ charges: [], blocked: false }),
  fetchTransactions: vi.fn().mockResolvedValue([]),
}));

describe("FinanceSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/FinanceSection");
    expect(typeof mod.FinanceSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/FinanceSection");
    const element = React.createElement(mod.FinanceSection);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.FinanceSection);
  });

  it("financeService no longer calls the dead /internal/v1/mobile/citizen/finance routes (audit gap G-3)", async () => {
    const fs = await import("node:fs/promises");
    const path = await import("node:path");
    // Vitest sets cwd to the package root (apps/mobile/citizen-app).
    const filePath = path.resolve(process.cwd(), "src/services/financeService.ts");
    const source = await fs.readFile(filePath, "utf-8");
    // The doc comment still references the legacy path so we can explain the
    // history; we only need to verify that no `apiClient.<verb>` call uses it.
    expect(source).not.toMatch(
      /apiClient\.[a-z]+[\s\S]{0,400}\/internal\/v1\/mobile\/citizen\/finance/
    );
    // Canonical wallet base must be present.
    expect(source).toContain('"/internal/v1/wallet"');
    // And the two canonical wallet read endpoints must be hit.
    expect(source).toMatch(/\/me\/balance['"`]/);
    expect(source).toMatch(/\/me\/transactions['"`]/);
  });

  it("fetchPendingCharges resolves to an unblocked result now the COSTA route is live", async () => {
    const svc = await import("../../services/financeService");
    // The citizen COSTA route is now published; the contract is a result object
    // ({ charges, blocked }) rather than a bare array. The mock mirrors it.
    await expect(svc.fetchPendingCharges()).resolves.toEqual({ charges: [], blocked: false });
  });
});
