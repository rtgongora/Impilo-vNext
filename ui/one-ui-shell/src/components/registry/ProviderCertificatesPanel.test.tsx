import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProviderCertificatesPanel } from "./ProviderCertificatesPanel";
import type { MyCertificate } from "@/hooks/queries/useMyCertificates";

const { certs } = vi.hoisted(() => ({ certs: { list: [] as MyCertificate[] } }));

vi.mock("@/hooks/queries/useMyCertificates", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/queries/useMyCertificates")>(
    "@/hooks/queries/useMyCertificates",
  );
  return {
    ...actual,
    useMyCertificates: () => ({ data: certs.list, isPending: false, isError: false }),
  };
});

vi.mock("@/lib/api-client", () => ({ apiClient: { getBlob: vi.fn() } }));

describe("ProviderCertificatesPanel", () => {
  it("lists a certificate with a download button when downloadable", () => {
    certs.list = [
      { id: 1, licenseType: "PRACTISING_CERTIFICATE", licenseNumber: "NC-123", status: "ACTIVE", validTo: "2030-01-01" },
    ];
    render(<ProviderCertificatesPanel />);
    expect(screen.getByText(/PRACTISING_CERTIFICATE · NC-123/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /PDF/i })).toBeInTheDocument();
  });

  it("hides download and shows Unavailable for a suspended certificate", () => {
    certs.list = [{ id: 2, licenseType: "PRACTISING_CERTIFICATE", status: "SUSPENDED", validTo: "2030-01-01" }];
    render(<ProviderCertificatesPanel />);
    expect(screen.getByText(/Unavailable/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /PDF/i })).not.toBeInTheDocument();
  });

  it("shows an empty state with no certificates", () => {
    certs.list = [];
    render(<ProviderCertificatesPanel />);
    expect(screen.getByText(/No certificates on record/i)).toBeInTheDocument();
  });
});
