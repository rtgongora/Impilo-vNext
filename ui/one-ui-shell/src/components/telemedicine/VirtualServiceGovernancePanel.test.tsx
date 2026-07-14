import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { VirtualServiceGovernancePanel } from "./VirtualServiceGovernancePanel";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

const { hasRoleMock } = vi.hoisted(() => ({ hasRoleMock: vi.fn() }));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: (s: { hasRole: (r: string) => boolean }) => unknown) =>
    selector({ hasRole: hasRoleMock }),
}));

function renderWithQuery(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

describe("VirtualServiceGovernancePanel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    hasRoleMock.mockReset();
    get.mockResolvedValue({
      vsUid: "vh-national-telemedicine",
      lifecycleStatus: "CONFIGURED",
      primaryPoolId: null,
      duty: { onDutyCount: 0, nextShiftStart: null },
    });
    post.mockResolvedValue({});
  });

  it("renders nothing for non-registry-admins", () => {
    hasRoleMock.mockReturnValue(false); // no SYSTEM_ADMIN / HIE_ADMIN
    const { container } = renderWithQuery(
      <VirtualServiceGovernancePanel vsUid="vh-national-telemedicine" />,
    );
    expect(container).toBeEmptyDOMElement();
    expect(get).not.toHaveBeenCalled();
  });

  it("shows the governed lifecycle + read-backs for a registry admin", async () => {
    hasRoleMock.mockImplementation((r: string) => r === "SYSTEM_ADMIN");
    renderWithQuery(<VirtualServiceGovernancePanel vsUid="vh-national-telemedicine" />);

    expect(await screen.findByTestId("vh-governance-panel")).toBeInTheDocument();
    expect(await screen.findByText("CONFIGURED")).toBeInTheDocument();
    expect(screen.getByTestId("vh-activate")).toBeInTheDocument();
    expect(screen.getByTestId("vh-suspend")).toBeInTheDocument();
  });

  it("calls the governed activate endpoint (server enforces preconditions)", async () => {
    hasRoleMock.mockImplementation((r: string) => r === "SYSTEM_ADMIN");
    const user = userEvent.setup();
    renderWithQuery(<VirtualServiceGovernancePanel vsUid="vh-national-telemedicine" />);

    await user.click(await screen.findByTestId("vh-activate"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/telemedicine/virtual-services/vh-national-telemedicine/activate",
        {},
      ),
    );
  });

  it("surfaces a fail-closed activation rejection honestly", async () => {
    hasRoleMock.mockImplementation((r: string) => r === "SYSTEM_ADMIN");
    post.mockRejectedValue(new Error("needs at least one active routing seam"));
    const user = userEvent.setup();
    renderWithQuery(<VirtualServiceGovernancePanel vsUid="vh-national-telemedicine" />);

    await user.click(await screen.findByTestId("vh-activate"));
    expect(await screen.findByTestId("vh-governance-error")).toHaveTextContent(
      /at least one active routing seam/i,
    );
  });
});
