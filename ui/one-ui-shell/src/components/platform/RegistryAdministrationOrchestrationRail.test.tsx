import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RegistryAdministrationOrchestrationRail } from "./RegistryAdministrationOrchestrationRail";

vi.mock("@/hooks/queries/useRegistry", () => ({
  useProviders: () => ({
    data: { data: [{ id: "p1", type: "provider", attributes: { displayName: "Dr. A", status: "PENDING_VERIFICATION" } }] },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useVitoRegistryAdmin", () => ({
  usePendingProvisionalIds: () => ({
    data: { data: { totalElements: 4 } },
    isLoading: false,
    isError: false,
  }),
  usePendingRegistryDedupCases: () => ({
    data: { data: { totalElements: 2 } },
    isLoading: false,
    isError: false,
  }),
}));

vi.mock("@/hooks/queries/useVitoIssuance", () => ({
  useIssuanceQueue: () => ({
    data: { data: { totalElements: 7 } },
    isLoading: false,
    isError: false,
  }),
}));

describe("RegistryAdministrationOrchestrationRail", () => {
  it("links to verification and VITO reconciliation queues", () => {
    render(<RegistryAdministrationOrchestrationRail />);
    expect(screen.getByTestId("registry-administration-orchestration-rail")).toBeInTheDocument();
    expect(screen.getByTestId("registry-queue-summary")).toHaveTextContent(/4 provisional ID/i);
    expect(screen.getByRole("link", { name: /Verification queue/i })).toHaveAttribute(
      "href",
      "/registry/providers/verification",
    );
    expect(screen.getByRole("link", { name: /Provisional reconciliation/i })).toHaveAttribute(
      "href",
      "/operations/vito/registry-admin",
    );
    expect(screen.getByRole("link", { name: /Dedup queue/i })).toHaveAttribute("href", "/operations/vito/dedup");
  });
});
