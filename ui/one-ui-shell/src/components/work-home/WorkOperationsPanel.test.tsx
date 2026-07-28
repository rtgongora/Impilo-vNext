// @vitest-environment jsdom
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WorkOperationsPanel } from "./WorkOperationsPanel";

const { searchParamsState, replaceMock, coreTransactionMock, workflowFeedMock, dispatchFeedMock, contractState } = vi.hoisted(() => ({
  searchParamsState: { value: "" },
  replaceMock: vi.fn(),
  coreTransactionMock: vi.fn(),
  workflowFeedMock: vi.fn(),
  dispatchFeedMock: vi.fn(),
  contractState: { contract: undefined as unknown },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/work",
  useRouter: () => ({ replace: replaceMock }),
  useSearchParams: () => new URLSearchParams(searchParamsState.value),
}));

vi.mock("@/hooks/queries/useCoreTransactionExperience", () => ({
  useCoreTransactionFeed: () => coreTransactionMock(),
  useWorkflowOperatorFeed: () => workflowFeedMock(),
  useDispatchOperatorFeed: () => dispatchFeedMock(),
}));

vi.mock("@/hooks/useSessionExperienceContract", () => ({
  useSessionExperienceContract: () => contractState,
}));

afterEach(() => cleanup());

beforeEach(() => {
  searchParamsState.value = "";
  replaceMock.mockReset();
  contractState.contract = undefined;
  coreTransactionMock.mockReturnValue({ items: [], isLoading: false, isError: false });
  workflowFeedMock.mockReturnValue({ items: [], isLoading: false, isError: false });
  dispatchFeedMock.mockReturnValue({ items: [], isLoading: false, isError: false });
});

describe("WorkOperationsPanel (Phase F6 — migrated from /provider-workspace)", () => {
  it("hides the administration & governance banner when the contract does not grant it", () => {
    contractState.contract = { tabs: { work: { visible: true } }, visibleManagementWorkspaces: [], visibleWorkspaces: [] };

    render(<WorkOperationsPanel />);

    expect(screen.queryByText("Administration & Governance")).toBeNull();
  });

  it("shows the administration & governance banner when the contract grants management workspaces", () => {
    contractState.contract = {
      tabs: { work: { visible: true } },
      visibleManagementWorkspaces: ["facility-admin"],
      visibleWorkspaces: [],
    };

    render(<WorkOperationsPanel />);

    expect(screen.getByText("Administration & Governance")).toBeTruthy();
    expect(screen.getByText("Open Administration & Governance →").closest("a")).toHaveAttribute(
      "href",
      "/work/administration-governance",
    );
  });

  it("renders real workflow and dispatch telemetry items from the live hooks", () => {
    workflowFeedMock.mockReturnValue({
      items: [{ id: "wf-1", label: "Referral triage", status: "IN_PROGRESS" }],
      isLoading: false,
      isError: false,
    });
    dispatchFeedMock.mockReturnValue({
      items: [{ id: "dp-1", label: "Sample pickup", status: "PENDING" }],
      isLoading: false,
      isError: false,
    });

    render(<WorkOperationsPanel />);

    expect(screen.getByText("Provider workflow telemetry")).toBeTruthy();
    expect(screen.getByText("Provider dispatch telemetry")).toBeTruthy();
  });

  it("updates the pWfStatus query param via router.replace when the workflow status filter changes", () => {
    const { container } = render(<WorkOperationsPanel />);

    const workflowStatusLabel = Array.from(container.querySelectorAll("label")).find((el) =>
      el.textContent?.trim().startsWith("Workflow status"),
    );
    const workflowStatusSelect = workflowStatusLabel?.querySelector("select");
    expect(workflowStatusSelect).toBeTruthy();

    fireEvent.change(workflowStatusSelect as HTMLSelectElement, { target: { value: "PENDING" } });

    expect(replaceMock).toHaveBeenCalledWith("/work?pWfStatus=PENDING", { scroll: false });
  });

  it("shows an honest empty/loading/error core-transaction state instead of fabricating a transaction", () => {
    coreTransactionMock.mockReturnValue({ items: [], isLoading: false, isError: true });

    render(<WorkOperationsPanel />);

    expect(screen.getByText(/Live core-transaction fetch failed/i)).toBeTruthy();
  });
});
