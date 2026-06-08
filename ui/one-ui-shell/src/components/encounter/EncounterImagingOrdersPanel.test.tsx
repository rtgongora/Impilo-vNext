import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EncounterImagingOrdersPanel } from "./EncounterImagingOrdersPanel";

const { mutateAsync } = vi.hoisted(() => ({
  mutateAsync: vi.fn(),
}));

vi.mock("@/hooks/queries/useLabOrders", () => ({
  useLabOrders: () => ({
    data: {
      data: [
        {
          id: "img-1",
          attributes: {
            test_name: "Chest X-ray",
            status: "ORDERED",
            encounter_id: "enc-1",
            category: "IMAGING",
          },
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

describe("EncounterImagingOrdersPanel", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({ data: { id: "img-2" } });
  });

  it("lists encounter-scoped imaging orders and links to imaging lane", () => {
    render(
      <EncounterImagingOrdersPanel patientId="patient-1" encounterId="enc-1" patientCpid="CPID-1" />,
    );
    expect(screen.getByText("Chest X-ray")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Open imaging lane" })).toHaveAttribute(
      "href",
      "/ehr/patient-1/orders?encounterId=enc-1&lane=IMAGING",
    );
  });

  it("submits IMAGING category order for the active encounter", async () => {
    const user = userEvent.setup();
    render(
      <EncounterImagingOrdersPanel patientId="patient-1" encounterId="enc-1" patientCpid="CPID-1" />,
    );

    await user.type(screen.getByLabelText(/study \/ procedure/i), "Chest X-ray PA");
    fireEvent.click(screen.getByRole("button", { name: "Place imaging order" }));

    await waitFor(() =>
      expect(mutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          patientId: "patient-1",
          encounterId: "enc-1",
          testName: "Chest X-ray PA",
          category: "IMAGING",
          patientCpid: "CPID-1",
        }),
      ),
    );
  });
});
