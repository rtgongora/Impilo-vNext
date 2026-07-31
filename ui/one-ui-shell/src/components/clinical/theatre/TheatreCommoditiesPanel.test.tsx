import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TheatreCommoditiesPanel } from "./TheatreCommoditiesPanel";

const get = vi.fn();
const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { get: (u: string) => get(u), post: (u: string, b: unknown) => post(u, b) } }));

describe("TheatreCommoditiesPanel (Lane 2 — implant / instrument-set / controlled-drug)", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockImplementation((u: string) => {
      if (u.includes("/implants") && !u.includes("/recall") && !u.includes("/inventory/")) {
        return Promise.resolve({
          data: [{
            id: "im1",
            udi: "UDI-9",
            device_type: "HIP",
            status: "IMPLANTED",
            inventory_patient_implant_id: "inv-link-1",
          }],
        });
      }
      return Promise.resolve({ data: [] });
    });
  });

  it("reads the three registers from their real endpoints", async () => {
    render(<TheatreCommoditiesPanel caseId="c-1" patientCpid="CPID-1" />);
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/implants");
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/instrument-sets");
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/controlled-drugs");
    expect(await screen.findByText("UDI-9")).toBeInTheDocument();
    expect(screen.getByTestId("inventory-link-im1")).toBeInTheDocument();
  });

  it("records an implant via the real implants endpoint (UDI required)", async () => {
    post.mockResolvedValue({ data: { udi: "UDI-123", status: "IMPLANTED" } });
    render(<TheatreCommoditiesPanel caseId="c-2" patientCpid="CPID-1" />);
    await screen.findByText(/No instrument sets issued/i);
    fireEvent.change(screen.getByTestId("implant-udi"), { target: { value: "UDI-123" } });
    fireEvent.click(screen.getByText("Record implant"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-2/implants", expect.objectContaining({ udi: "UDI-123" })));
  });

  it("requires a witness before an ADMINISTER controlled-drug entry can be recorded", async () => {
    render(<TheatreCommoditiesPanel caseId="c-3" patientCpid="CPID-1" />);
    await screen.findByText(/No controlled-drug entries/i);
    fireEvent.change(screen.getByTestId("cd-item"), { target: { value: "MORPH-10" } });
    expect((screen.getByText("Record") as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByTestId("cd-witness"), { target: { value: "nurse-2" } });
    expect((screen.getByText("Record") as HTMLButtonElement).disabled).toBe(false);
  });

  it("removes via inventory SoR with removalReason and disables when inventory link is null", async () => {
    get.mockImplementation((u: string) => {
      if (u.endsWith("/implants")) {
        return Promise.resolve({
          data: [
            { id: "im-linked", udi: "UDI-A", status: "IMPLANTED", inventory_patient_implant_id: "inv-99" },
            { id: "im-orphan", udi: "UDI-B", status: "IMPLANTED", inventory_patient_implant_id: null },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });
    post.mockResolvedValue({ data: {} });
    render(<TheatreCommoditiesPanel caseId="c-rm" patientCpid="CPID-9" />);
    await screen.findByText("UDI-A");

    expect((screen.getByTestId("remove-implant-im-orphan") as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByTestId("revise-implant-im-orphan") as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(screen.getByTestId("removal-reason-im-linked"), { target: { value: "Infection" } });
    fireEvent.click(screen.getByTestId("remove-implant-im-linked"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/inventory/implants/inv-99/remove",
        { removalReason: "Infection" },
      ),
    );
  });

  it("revises via inventory SoR with newImplant.patientCpid from the case", async () => {
    post.mockResolvedValue({ data: {} });
    render(<TheatreCommoditiesPanel caseId="c-rev" patientCpid="CPID-42" />);
    await screen.findByText("UDI-9");
    fireEvent.change(screen.getByTestId("revise-udi-im1"), { target: { value: "UDI-NEW" } });
    fireEvent.click(screen.getByTestId("revise-implant-im1"));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/inventory/implants/inv-link-1/revise",
        expect.objectContaining({
          newImplant: expect.objectContaining({ patientCpid: "CPID-42", udi: "UDI-NEW" }),
        }),
      ),
    );
  });

  it("shows unavailable banner on register GET failure and never clears prior implants", async () => {
    get.mockImplementation((u: string) => {
      if (u.endsWith("/implants")) {
        return Promise.resolve({
          data: [{ id: "im-keep", udi: "UDI-KEEP", status: "IMPLANTED", inventory_patient_implant_id: "inv-1" }],
        });
      }
      return Promise.resolve({ data: [] });
    });
    render(<TheatreCommoditiesPanel caseId="c-keep" patientCpid="CPID-1" />);
    expect(await screen.findByText("UDI-KEEP")).toBeInTheDocument();

    get.mockImplementation((u: string) => Promise.reject({ status: 502, error: { message: "upstream down" } }));
    fireEvent.click(screen.getByText("Refresh"));
    expect(await screen.findByTestId("commodities-list-unavailable")).toHaveTextContent(/do not treat this as empty registers/i);
    expect(screen.getByText("UDI-KEEP")).toBeInTheDocument();
    expect(screen.queryByText(/No implants recorded/i)).not.toBeInTheDocument();
  });

  it("runs recall against inventory SoR and never treats 502 as zero affected", async () => {
    get.mockImplementation((u: string) => {
      if (u.includes("/inventory/implants/recall")) {
        return Promise.reject({
          status: 502,
          error: "implant_recall_trace_unavailable",
          message: "The recall trace could not be run. Do not treat this as no affected patients.",
        });
      }
      if (u.endsWith("/implants")) {
        return Promise.resolve({ data: [{ id: "im1", udi: "UDI-9", status: "IMPLANTED", inventory_patient_implant_id: "inv-link-1" }] });
      }
      return Promise.resolve({ data: [] });
    });
    render(<TheatreCommoditiesPanel caseId="c-rec" patientCpid="CPID-1" />);
    await screen.findByText("UDI-9");
    fireEvent.change(screen.getByTestId("recall-udi"), { target: { value: "UDI-RECALL" } });
    fireEvent.click(screen.getByTestId("recall-run"));
    expect(await screen.findByTestId("recall-unavailable")).toHaveTextContent(/do not treat as zero affected/i);
    expect(get).toHaveBeenCalledWith("/internal/v1/inventory/implants/recall?udi=UDI-RECALL");
    expect(get.mock.calls.every((c: string[]) => !String(c[0]).includes("/theatre/implants/recall"))).toBe(true);
    expect(screen.queryByTestId("recall-empty")).not.toBeInTheDocument();
  });
});
