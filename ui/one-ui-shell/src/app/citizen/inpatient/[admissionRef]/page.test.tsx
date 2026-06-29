import { type ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useParams: () => ({ admissionRef: "ADM-1" }),
}));

const get = vi.fn();
vi.mock("@/lib/api-client", () => ({
  apiClient: { get: (...args: unknown[]) => get(...args) },
}));

import CitizenInpatientStatusPage from "./page";

function wrap(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("CitizenInpatientStatusPage", () => {
  beforeEach(() => get.mockReset());

  it("renders the inpatient status with ward and bed locators", async () => {
    get.mockResolvedValue({
      data: {
        admissionRef: "ADM-1",
        available: true,
        statusLabel: "On the ward",
        message: "You're admitted and being cared for on the ward.",
        ward: "Ward 4",
        bed: "B-12",
      },
    });

    wrap(<CitizenInpatientStatusPage />);

    await waitFor(() => expect(screen.getByText("On the ward")).toBeInTheDocument());
    expect(screen.getByText("Ward 4")).toBeInTheDocument();
    expect(screen.getByText("B-12")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/mobile/citizen/inpatient/ADM-1/status");
  });
});
