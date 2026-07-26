/**
 * SupportScreen Tests — Verifies export and basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
  Card: ({ children }: any) => children,
  CardHeader: ({ title }: any) => title,
  CardBody: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  Badge: ({ children }: any) => children,
  TextField: () => null,
  Select: () => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("../../services/supportService", () => ({
  fetchTickets: vi.fn().mockResolvedValue({ items: [] }),
  createTicket: vi.fn().mockResolvedValue({ id: "ticket-1" }),
  fetchArticles: vi.fn().mockResolvedValue({ items: [] }),
}));

describe("SupportScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/support/SupportScreen");
    expect(typeof mod.SupportScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/support/SupportScreen");
    const element = React.createElement(mod.SupportScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.SupportScreen);
  });
});
