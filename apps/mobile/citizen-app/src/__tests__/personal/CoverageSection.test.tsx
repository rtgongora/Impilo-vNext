/**
 * CoverageSection Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("../../services/coverageService", () => ({
  fetchCoverage: vi.fn().mockResolvedValue([]),
}));

describe("CoverageSection", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/CoverageSection");
    expect(typeof mod.CoverageSection).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/CoverageSection");
    const element = React.createElement(mod.CoverageSection);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.CoverageSection);
  });
});
