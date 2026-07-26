import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const adminRegistryMocks = vi.hoisted(() => ({
  fetchAdminRegistryHub: vi.fn(),
}));

const identityMocks = vi.hoisted(() => ({
  searchIdentities: vi.fn(),
  resolvePatientIdentity: vi.fn(),
  registerPatientIdentity: vi.fn(),
  startPatientRecovery: vi.fn(),
  verifyPatientRecovery: vi.fn(),
  createProviderIdentity: vi.fn(),
  getProviderIdentity: vi.fn(),
}));

const registryMocks = vi.hoisted(() => ({
  fetchBootstrapSnapshot: vi.fn(),
  fetchFacilityDashboardSummary: vi.fn(),
  searchFacilityRegistry: vi.fn(),
  createFacilityApplication: vi.fn(),
  submitFacilityApplication: vi.fn(),
  markFacilityApplicationReady: vi.fn(),
  fetchPendingLocalityProposals: vi.fn(),
  proposeLocality: vi.fn(),
  approveLocalityProposal: vi.fn(),
  rejectLocalityProposal: vi.fn(),
  createRegistryIntakeSession: vi.fn(),
  createRegistryRecoveryRequest: vi.fn(),
  createRegistryImportJob: vi.fn(),
  executeRegistryImportJob: vi.fn(),
  searchProductRegistry: vi.fn(),
  resolveTerminologyArtifact: vi.fn(),
  listTrustPolicies: vi.fn(),
  listTrustConsents: vi.fn(),
  probeRegistryServiceHealth: vi.fn(),
}));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal();
  const React = await import("react");
  return {
    ...actual,
    Card: ({ children }: { children: React.ReactNode }) => React.createElement("section", null, children),
    CardBody: ({ children }: { children: React.ReactNode }) => React.createElement("div", null, children),
    Badge: ({ children }: { children: React.ReactNode }) => React.createElement("span", null, children),
    LoadingSpinner: () => React.createElement("span", null, "loading"),
    Button: ({ title, onPress, disabled }: { title: string; onPress: () => void; disabled?: boolean }) =>
      React.createElement("button", { disabled, onClick: onPress }, title),
  };
});

vi.mock("../../services/adminRegistryService", () => ({
  fetchAdminRegistryHub: adminRegistryMocks.fetchAdminRegistryHub,
}));

vi.mock("../../services/identityRegistryService", () => identityMocks);
vi.mock("../../services/registryOperationsService", () => registryMocks);

function renderWithQuery(element: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(<QueryClientProvider client={queryClient}>{element}</QueryClientProvider>);
  });
  return { container, root, queryClient };
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

describe("AdminRegistryHubScreen interactions", () => {
  let mounted: { root: Root; container: HTMLElement; queryClient: QueryClient } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    adminRegistryMocks.fetchAdminRegistryHub.mockResolvedValue({ refreshed_at: "2026-05-26T00:00:00Z", sections: [] });
    identityMocks.searchIdentities.mockResolvedValue([{ healthId: "PHID-1", displayName: "Jane Client" }]);
    identityMocks.resolvePatientIdentity.mockResolvedValue({ cpid: "CPID-1" });
    identityMocks.registerPatientIdentity.mockResolvedValue({ accepted: true });
    identityMocks.startPatientRecovery.mockResolvedValue({ recoveryId: "rec-1" });
    identityMocks.verifyPatientRecovery.mockResolvedValue({ verified: true });
    identityMocks.createProviderIdentity.mockResolvedValue({ providerId: "prov-1" });
    identityMocks.getProviderIdentity.mockResolvedValue({ providerId: "prov-1" });
    Object.values(registryMocks).forEach((mock) => mock.mockResolvedValue([]));
    registryMocks.createFacilityApplication.mockResolvedValue({ applicationId: "app-1" });
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
      mounted = undefined;
    }
  });

  it("renders identity and non-identity registry operation panels", async () => {
    const mod = await import("../../screens/provider/AdminRegistryHubScreen");
    mounted = renderWithQuery(<mod.AdminRegistryHubScreen />);
    await flush();

    expect(mounted.container.querySelector('[data-testid="registry-identity-operations"]')).toBeTruthy();
    expect(mounted.container.querySelector('[data-testid="registry-nonidentity-operations"]')).toBeTruthy();
  });

  it("runs live facility and locality commands from the mobile registry panel", async () => {
    const mod = await import("../../screens/provider/AdminRegistryHubScreen");
    mounted = renderWithQuery(<mod.AdminRegistryHubScreen />);
    await flush();

    const search = inputByPlaceholder(mounted.container, "Search facilities");
    act(() => {
      search.value = "Clinic";
      search.dispatchEvent(new Event("input", { bubbles: true }));
    });
    clickButton(mounted.container, "Search facilities");
    clickButton(mounted.container, "Create facility application");
    clickButton(mounted.container, "Load pending proposals");
    await flush();

    expect(registryMocks.searchFacilityRegistry).toHaveBeenCalledWith({ search: "Clinic", size: 5 });
    expect(registryMocks.createFacilityApplication).toHaveBeenCalledWith(
      expect.objectContaining({ applicationType: "NEW_REGISTRATION", facilityName: "Sample Clinic" }),
    );
    expect(registryMocks.fetchPendingLocalityProposals).toHaveBeenCalled();
  });
});
