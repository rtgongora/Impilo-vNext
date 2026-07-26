import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const identityMocks = vi.hoisted(() => ({
  searchIdentities: vi.fn(),
  resolvePatientIdentity: vi.fn(),
  startPatientRecovery: vi.fn(),
  verifyPatientRecovery: vi.fn(),
}));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  const React = await import("react");
  return {
    ...actual,
    Screen: ({ children }: { children: React.ReactNode }) => React.createElement("main", null, children),
    Header: ({ title }: { title: string }) => React.createElement("h1", null, title),
    Card: ({ children }: { children: React.ReactNode }) => React.createElement("section", null, children),
    CardBody: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Button: ({ title, onPress, disabled }: { title: string; onPress: () => void; disabled?: boolean }) =>
      React.createElement("button", { disabled, onClick: onPress }, title),
  };
});

vi.mock("../../services/identityRegistryService", () => identityMocks);

function renderWithQuery(element: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<QueryClientProvider client={queryClient}>{element}</QueryClientProvider>);
  });
  return { container, root };
}

function inputByPlaceholder(container: HTMLElement, placeholder: string) {
  const input = Array.from(container.querySelectorAll("input")).find(
    (node) => node.getAttribute("placeholder") === placeholder,
  );
  if (!input) throw new Error(`Missing input: ${placeholder}`);
  return input as HTMLInputElement;
}

function clickButton(container: HTMLElement, label: string) {
  const button = Array.from(container.querySelectorAll("button")).find((node) => node.textContent === label);
  if (!button) throw new Error(`Missing button: ${label}`);
  act(() => {
    button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

describe("IdRecoverySection interactions", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    identityMocks.searchIdentities.mockResolvedValue([{ healthId: "PHID-1" }]);
    identityMocks.resolvePatientIdentity.mockResolvedValue({ cpid: "CPID-1" });
    identityMocks.startPatientRecovery.mockResolvedValue({ recoveryId: "rec-1" });
    identityMocks.verifyPatientRecovery.mockResolvedValue({ verified: true });
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
    }
    mounted = undefined;
  });

  it("runs search, resolve, and recovery commands through live identity service", async () => {
    const mod = await import("../../screens/personal/IdRecoverySection");
    mounted = renderWithQuery(<mod.IdRecoverySection />);
    await flush();

    const search = inputByPlaceholder(mounted.container, "Search name, PHID, phone");
    act(() => {
      search.value = "Jane";
      search.dispatchEvent(new Event("input", { bubbles: true }));
    });
    clickButton(mounted.container, "Search identity");

    const healthId = inputByPlaceholder(mounted.container, "Health ID / PHID to resolve");
    act(() => {
      healthId.value = "PHID-1";
      healthId.dispatchEvent(new Event("input", { bubbles: true }));
    });
    clickButton(mounted.container, "Resolve Health ID");
    clickButton(mounted.container, "Start recovery");
    clickButton(mounted.container, "Verify recovery");
    await flush();

    expect(identityMocks.searchIdentities).toHaveBeenCalledWith("Jane");
    expect(identityMocks.resolvePatientIdentity.mock.calls[0][0]).toBe("PHID-1");
    expect(identityMocks.startPatientRecovery.mock.calls[0][0]).toEqual(
      expect.objectContaining({ method: "phone_otp", contact: "+263000000000" }),
    );
    expect(identityMocks.verifyPatientRecovery.mock.calls[0][0]).toEqual(
      expect.objectContaining({ recoveryId: "RECOVERY-ID", otp: "000000" }),
    );
  });
});
