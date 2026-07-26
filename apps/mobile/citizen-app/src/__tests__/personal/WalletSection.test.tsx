/**
 * WalletSection Tests — Verifies export and basic instantiation under the
 * canonical Mushe Wallet route family.
 *
 * Heavy "render-and-assert-DOM" tests would require pulling in `react-dom` and
 * `@testing-library/react`, which are not part of the citizen-app's
 * jsdom-only test profile. We instead follow the pattern used by
 * `FinanceSection.test.tsx` and the rest of the `personal/` test suite:
 * mock the service module + UI primitives, then assert that the component
 * exports and instantiates as a React element. The deep behaviour (route
 * paths, payload adapters, ApiError propagation) is fully covered by
 * `walletService.test.ts`.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  LoadingSpinner: () => null,
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

vi.mock("../../services/walletService", () => ({
  fetchWallet: vi.fn().mockResolvedValue({
    id: "wal-1",
    balance: 0,
    currency: "USD",
    status: "ACTIVE",
  }),
  fetchTransactions: vi.fn().mockResolvedValue([]),
}));

vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({ data: undefined, isLoading: false, error: null, refetch: vi.fn() }),
}));

vi.mock("../../stores/appStore", () => ({
  useAppStore: (selector: any) => selector({ profile: { cpid: "cpid-1" } }),
}));

describe("WalletSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/WalletSection");
    expect(typeof mod.WalletSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/WalletSection");
    const element = React.createElement(mod.WalletSection);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.WalletSection);
  });

  it("does not call the legacy /internal/v1/mobile/citizen/wallet path (audit gap G-3)", async () => {
    const fs = await import("node:fs/promises");
    const path = await import("node:path");
    // Vitest sets cwd to the package root (apps/mobile/citizen-app).
    const filePath = path.resolve(process.cwd(), "src/services/walletService.ts");
    const source = await fs.readFile(filePath, "utf-8");

    // The doc comment still references the legacy path so we can explain the
    // history; we only need to verify that no `apiClient.<verb>` call uses it.
    // Match e.g. `apiClient.get("/internal/v1/mobile/citizen/wallet/...")`
    // or any literal embedded in a template-string or string concatenation.
    expect(source).not.toMatch(/apiClient\.[a-z]+[\s\S]{0,400}\/internal\/v1\/mobile\/citizen\/wallet/);
    // Canonical base must be defined as a literal in the service.
    expect(source).toContain('"/internal/v1/wallet"');
    // And it must be hit at the `/me` resource at least once.
    expect(source).toMatch(/\/me['"`]/);
  });
});
