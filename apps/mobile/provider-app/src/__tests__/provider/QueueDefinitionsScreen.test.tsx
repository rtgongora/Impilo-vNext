/**
 * QueueDefinitionsScreen Tests — export + basic instantiation.
 */

import { describe, it, expect, vi } from "vitest";
import React from "react";

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
  Screen: ({ children }: any) => children,
  Header: ({ title }: any) => title,
  Card: ({ children }: any) => children,
  CardBody: ({ children }: any) => children,
  Badge: ({ children }: any) => children,
  Button: ({ title, onPress }: any) => null,
  LoadingSpinner: () => null,
  EmptyState: ({ title }: any) => title,
  ErrorState: ({ title }: any) => title,
  };
});

vi.mock("../../stores/appStore", () => ({
  useAppStore: () => ({ facilityId: null }),
}));

vi.mock("../../services/queueService", () => ({
  fetchQueueDefinitions: vi.fn().mockResolvedValue([]),
}));

describe("QueueDefinitionsScreen", () => {
  it("exports a function component", async () => {
    const mod = await import("../../screens/provider/QueueDefinitionsScreen");
    expect(typeof mod.QueueDefinitionsScreen).toBe("function");
  });

  it("can be instantiated", async () => {
    const mod = await import("../../screens/provider/QueueDefinitionsScreen");
    const element = React.createElement(mod.QueueDefinitionsScreen);
    expect(element).toBeDefined();
    expect(element.type).toBe(mod.QueueDefinitionsScreen);
  });
});
