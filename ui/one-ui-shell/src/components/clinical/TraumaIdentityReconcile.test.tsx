import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

import { TraumaIdentityReconcile } from "./TraumaIdentityReconcile";

function renderCmp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TraumaIdentityReconcile subjectIdentityMode="ANONYMOUS" />
    </QueryClientProvider>,
  );
}

describe("TraumaIdentityReconcile", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockResolvedValue({ data: { items: [{ crid: "CRID-SURV", displayName: "Jane Doe", healthId: "H-1" }] } });
  });

  it("mints a provisional identity via the trauma identity proxy", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    post.mockResolvedValue({ data: { health_id: "TEMP-1", crid: "CRID-1", status: "PROVISIONAL" } });
    renderCmp();
    await user.click(screen.getByRole("button", { name: /^Mint$/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/trauma/identity/provisional",
        expect.objectContaining({ estimated_sex: "UNKNOWN" }),
      ),
    );
    await waitFor(() => expect(screen.getByText(/Provisional minted/i)).toBeInTheDocument());
  });

  it("reconciles a provisional CRID to a searched survivor via the merge proxy", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    post.mockResolvedValue({ data: { merged: true } });
    renderCmp();
    await user.type(screen.getByPlaceholderText(/Provisional CRID/i), "CRID-1");
    await user.type(screen.getByPlaceholderText(/Search confirmed patient/i), "Jane");
    await waitFor(() => expect(screen.getByText("Jane Doe")).toBeInTheDocument());
    await user.click(screen.getByText("Jane Doe"));
    await user.click(screen.getByRole("button", { name: /Reconcile identity/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/trauma/identity/merge",
        expect.objectContaining({ survivor_crid: "CRID-SURV", merged_crids: ["CRID-1"], reason: "IDENTIFIED" }),
      ),
    );
  });
});
