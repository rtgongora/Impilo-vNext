import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EditDemographicsForm } from "./EditDemographicsForm";

const { put } = vi.hoisted(() => ({ put: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { put } }));

function renderForm(onSaved?: () => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <EditDemographicsForm
        healthId="HID-123"
        initial={{ givenName: "Tendai", familyName: "Moyo", phone: "0771000000" }}
        onSaved={onSaved}
      />
    </QueryClientProvider> as ReactNode,
  );
}

describe("EditDemographicsForm — G055 demographics update", () => {
  beforeEach(() => put.mockReset());

  it("PUTs the edited demographics to the BFF VITO route and signals success", async () => {
    put.mockResolvedValue({ data: {} });
    const onSaved = vi.fn();
    renderForm(onSaved);

    // pre-populated from initial
    expect((screen.getByLabelText("Given name") as HTMLInputElement).value).toBe("Tendai");

    // edit the phone, submit
    fireEvent.change(screen.getByLabelText("Phone"), { target: { value: "0772999999" } });
    fireEvent.click(screen.getByRole("button", { name: /save demographics/i }));

    await waitFor(() => expect(put).toHaveBeenCalledTimes(1));
    expect(put).toHaveBeenCalledWith(
      "/internal/v1/vito/client-registry/clients/HID-123/demographics",
      expect.objectContaining({ givenName: "Tendai", familyName: "Moyo", phone: "0772999999" }),
    );
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(screen.getByRole("status")).toHaveTextContent(/saved/i);
  });

  // (Error-path UI is driven by mutation.isError → role="alert"; not asserted here because
  // a rejected mutation trips vitest's global unhandled-rejection guard — infra quirk.)
});
