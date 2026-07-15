import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ObstetricSection } from "./ObstetricSection";

const post = vi.fn();
vi.mock("@/lib/api-client", () => ({ apiClient: { post: (u: string, b: unknown) => post(u, b) } }));

describe("ObstetricSection (Lane 1 — emergency C-section)", () => {
  beforeEach(() => post.mockReset());

  it("records the delivery and DISPLAYS the mother↔baby linkage the service returns (never mints identity)", async () => {
    post.mockResolvedValue({ data: { mother_cpid: "CPID-MOTHER-1", baby_cpid: "CPID-BABY-1", delivery: { outcome: "LIVE_BIRTH", apgar_5: 9 } } });
    render(<ObstetricSection caseId="c-1" />);
    fireEvent.change(screen.getByTestId("baby-cpid"), { target: { value: "CPID-BABY-1" } });
    fireEvent.click(screen.getByText("Record delivery"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-1/obstetric/delivery", expect.objectContaining({ babyCpid: "CPID-BABY-1" })));
    const linkage = await screen.findByTestId("delivery-linkage");
    expect(linkage).toHaveTextContent("CPID-MOTHER-1".slice(0, 12));
    expect(linkage).toHaveTextContent("CPID-BABY-1".slice(0, 12));
  });

  it("pages the neonatal team against the real endpoint", async () => {
    post.mockResolvedValue({ data: { neonatal_team: "PAGED" } });
    render(<ObstetricSection caseId="c-2" />);
    fireEvent.click(screen.getByText("Page neonatal team"));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/internal/v1/theatre/cases/c-2/obstetric/neonatal-alert", expect.objectContaining({})));
    expect(await screen.findByTestId("neonatal-team-status")).toHaveTextContent("PAGED");
  });

  it("blocks delivery until a provisional baby CPID is entered (UI does not mint identity)", () => {
    render(<ObstetricSection caseId="c-3" />);
    expect((screen.getByText("Record delivery") as HTMLButtonElement).disabled).toBe(true);
  });
});
