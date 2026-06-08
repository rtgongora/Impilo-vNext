import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EncounterLabOrdersPanel } from "./EncounterLabOrdersPanel";

const { mutateAsync } = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
}));

vi.mock("@/hooks/queries/useLabOrders", () => ({
  useLabOrders: () => ({
    data: {
      data: [
        {
          id: "order-1",
          attributes: { test_name: "FBC", status: "ORDERED", encounter_id: "enc-1" },
        },
      ],
    },
    isLoading: false,
  }),
  useCreateLabOrder: () => ({
    mutateAsync,
    isPending: false,
  }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "user-1", displayName: "Dr. Moyo" } }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string } }) => unknown) =>
    selector({ facility: { id: "facility-1" } }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

describe("EncounterLabOrdersPanel", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({ data: { id: "order-2" } });
  });

  it("lists encounter-scoped orders and links to full orders workspace", () => {
    render(
      <EncounterLabOrdersPanel patientId="patient-1" encounterId="enc-1" patientCpid="CPID-1" />,
    );
    expect(screen.getByText("FBC")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open full orders workspace" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/orders?encounterId=enc-1",
    );
  });

  it("submits typed lab order for the active encounter", async () => {
    const user = userEvent.setup();
    render(
      <EncounterLabOrdersPanel patientId="patient-1" encounterId="enc-1" patientCpid="CPID-1" />,
    );

    await user.type(screen.getByLabelText("Test name"), "Troponin");
    await user.type(screen.getByLabelText("Test code (optional)"), "TROP");
    fireEvent.click(screen.getByRole("button", { name: "Place lab order" }));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          patientId: "patient-1",
          encounterId: "enc-1",
          testName: "Troponin",
          testCode: "TROP",
          patientCpid: "CPID-1",
          pctEncounterRef: "enc-1",
          orderedBy: "user-1",
        }),
      ),
    );
  });
});
