/**
 * ProgramsScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("../../services/programService", () => ({
  fetchPrograms: vi.fn().mockResolvedValue([]),
  enrollInProgram: vi.fn().mockResolvedValue({}),
  fetchMyEnrollments: vi.fn().mockResolvedValue([]),
}));

describe("ProgramsScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/personal/ProgramsScreen");
    expect(typeof mod.ProgramsScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/personal/ProgramsScreen");
    const element = React.createElement(mod.ProgramsScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.ProgramsScreen);
  });
});
