import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { ProviderCertificatesAdminPanel } from "./ProviderCertificatesAdminPanel";
import type { RegistryCertificate } from "@/hooks/queries/useProviderCertificatesAdmin";

const { list, actionMutate, createMutate } = vi.hoisted(() => ({
  list: { data: [] as RegistryCertificate[] },
  actionMutate: vi.fn(),
  createMutate: vi.fn(),
}));

vi.mock("@/hooks/queries/useProviderCertificatesAdmin", () => ({
  useProviderCertificatesAdmin: () => ({ data: list.data, isPending: false }),
  useCreateCertificate: () => ({ mutate: createMutate, isPending: false }),
  useCertificateAction: () => ({ mutate: actionMutate, isPending: false }),
}));

describe("ProviderCertificatesAdminPanel", () => {
  beforeEach(() => {
    actionMutate.mockReset();
    createMutate.mockReset();
    list.data = [{ id: 5, certificateType: "PRACTISING_CERTIFICATE", certificateNumber: "NC-9", status: "ACTIVE" }];
  });

  it("requires a reason before suspending a certificate", () => {
    render(<ProviderCertificatesAdminPanel providerPublicId="PRV-1" providerId={42} />);
    fireEvent.click(screen.getByRole("button", { name: "Suspend" }));
    // The reason box appears; confirm is disabled until a reason is typed.
    const confirm = screen.getByRole("button", { name: /Confirm suspend/i });
    expect(confirm).toBeDisabled();
    expect(actionMutate).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText(/Reason to suspend/i), { target: { value: "Interim order" } });
    fireEvent.click(confirm);
    expect(actionMutate).toHaveBeenCalledWith(
      expect.objectContaining({ certificateId: 5, action: "suspend", reason: "Interim order" }),
      expect.anything(),
    );
  });

  it("reinstates a suspended certificate without a reason prompt", () => {
    list.data = [{ id: 6, certificateType: "PRACTISING_CERTIFICATE", status: "SUSPENDED" }];
    render(<ProviderCertificatesAdminPanel providerPublicId="PRV-1" providerId={42} />);
    fireEvent.click(screen.getByRole("button", { name: "Reinstate" }));
    expect(actionMutate).toHaveBeenCalledWith(
      expect.objectContaining({ certificateId: 6, action: "reinstate" }),
    );
  });

  it("issues a certificate with the numeric providerId", () => {
    render(<ProviderCertificatesAdminPanel providerPublicId="PRV-1" providerId={42} />);
    fireEvent.click(screen.getByRole("button", { name: /Issue$/ }));
    fireEvent.click(screen.getByRole("button", { name: /Issue certificate/i }));
    expect(createMutate).toHaveBeenCalledWith(
      expect.objectContaining({ providerId: 42, certificateType: "PRACTISING_CERTIFICATE" }),
      expect.anything(),
    );
  });
});
