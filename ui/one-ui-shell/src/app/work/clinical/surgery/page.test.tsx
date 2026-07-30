/**
 * The completion wave's clinician surface: shared-specialty care (V011), the audited reopen
 * (V010) and the MDT decision forum (V012).
 *
 * What these tests exist to protect is the honesty of three states that a careless render
 * collapses into two:
 *   - a failed specialty read must NOT show an empty roster — on a theatre surface that reads
 *     as "no second team is coming";
 *   - REOPENED must not be offered as a plain transition, because the server refuses it and
 *     because a reopen with no recorded reason is not a record of anything;
 *   - an unverified MDT reference must not render as either a confirmed board decision or an
 *     absent one.
 */

import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SurgicalEpisodesPage from "./page";

const { get, post, put, del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));
vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post, put, delete: del },
}));

const EPISODE = {
  id: "3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d",
  subjectCpid: "CPID-1",
  journeyId: "j-1",
  encounterId: null,
  procedureEpisodeRef: null,
  pctProblemRef: null,
  operativeIndication: "Obstructing sigmoid tumour",
  nonOperativeOptionsConsidered: null,
  status: "OPERATED" as const,
  specialty: "COLORECTAL",
  pctContributed: false,
};

/** A 404 is the "not recorded yet" gap the panels must not render as a failure. */
const notRecorded = { status: 404 };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <SurgicalEpisodesPage />
    </QueryClientProvider>,
  );
}

/** Load a patient's episodes and open the one above, which every test below works from. */
async function openEpisode() {
  renderPage();
  fireEvent.change(screen.getByTestId("surgery-cpid-input"), { target: { value: "CPID-1" } });
  fireEvent.click(screen.getByTestId("surgery-load-episodes"));
  await waitFor(() => expect(screen.getByTestId("surgery-episode-item")).toBeInTheDocument());
  fireEvent.click(screen.getByTestId("surgery-episode-item"));
  await waitFor(() => expect(screen.getByTestId("surgery-episode-detail")).toBeInTheDocument());
}

/** Route the four verbs by URL so each test states only the responses it cares about. */
function routeGets(specialties: unknown) {
  get.mockImplementation((url: string) => {
    if (url.includes("/specialties")) {
      return specialties instanceof Error ? Promise.reject(specialties) : Promise.resolve(specialties);
    }
    if (url.includes("/assessment") || url.includes("/decision")) return Promise.reject(notRecorded);
    return Promise.resolve([EPISODE]);
  });
}

describe("SurgicalEpisodesPage — shared specialties, reopen and MDT forum", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
    del.mockReset();
  });

  it("lists every specialty on the case, marking the lead", async () => {
    routeGets([
      {
        id: "s-1",
        specialty: "COLORECTAL",
        role: "LEAD",
        contribution: null,
        addedBy: "migration:V011",
        addedAt: "2026-07-01T08:00:00Z",
      },
      {
        id: "s-2",
        specialty: "VASCULAR",
        role: "SHARED",
        contribution: "iliac vessel control",
        addedBy: "dr-a",
        addedAt: "2026-07-01T09:00:00Z",
      },
    ]);
    await openEpisode();

    const list = await screen.findByTestId("surgery-specialties-list");
    expect(within(list).getByText("COLORECTAL")).toBeInTheDocument();
    expect(within(list).getByText("VASCULAR")).toBeInTheDocument();
    expect(within(list).getByText("iliac vessel control")).toBeInTheDocument();
    expect(within(list).getByText("LEAD")).toBeInTheDocument();
    // Only the shared team may be removed — the lead is handed over, never dropped.
    expect(within(list).getAllByTestId("surgery-specialty-remove")).toHaveLength(1);
  });

  it("renders a failed specialty read as unavailable, never as an empty roster", async () => {
    routeGets(new Error("surgery-service unreachable"));
    await openEpisode();

    await waitFor(() =>
      expect(screen.getByTestId("surgery-specialties-unavailable")).toBeInTheDocument(),
    );
    expect(screen.queryByTestId("surgery-specialties-empty")).not.toBeInTheDocument();
    expect(screen.queryByTestId("surgery-specialties-list")).not.toBeInTheDocument();
  });

  it("sends the specialty as a query parameter on removal, not as a path segment", async () => {
    routeGets([
      { id: "s-1", specialty: "COLORECTAL", role: "LEAD", contribution: null, addedBy: "x", addedAt: "" },
      { id: "s-2", specialty: "VASCULAR", role: "SHARED", contribution: null, addedBy: "x", addedAt: "" },
    ]);
    del.mockResolvedValue([]);
    await openEpisode();

    fireEvent.click(await screen.findByTestId("surgery-specialty-remove"));

    await waitFor(() =>
      expect(del).toHaveBeenCalledWith(
        `/internal/v1/surgery/episodes/${EPISODE.id}/specialties?specialty=VASCULAR`,
      ),
    );
  });

  it("does not offer REOPENED as a plain transition", async () => {
    routeGets([]);
    await openEpisode();

    const options = Array.from(
      screen.getByTestId("surgery-transition-select").querySelectorAll("option"),
    ).map((o) => o.getAttribute("value"));
    expect(options).not.toContain("REOPENED");
  });

  it("requires a reason before an episode can be reopened, and sends it", async () => {
    routeGets([]);
    post.mockResolvedValue({ ...EPISODE, status: "REOPENED" });
    await openEpisode();

    const submit = screen.getByTestId("surgery-reopen-submit");
    expect(submit).toBeDisabled();

    fireEvent.change(screen.getByTestId("surgery-reopen-reason"), {
      target: { value: "post-operative haemorrhage" },
    });
    expect(submit).not.toBeDisabled();
    fireEvent.click(submit);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(`/internal/v1/surgery/episodes/${EPISODE.id}/reopen`, {
        reason: "post-operative haemorrhage",
        reoperationOfEpisodeId: null,
      }),
    );
  });

  it("offers no reopen at all on an episode that never reached theatre", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/specialties")) return Promise.resolve([]);
      if (url.includes("/assessment") || url.includes("/decision")) return Promise.reject(notRecorded);
      return Promise.resolve([{ ...EPISODE, status: "ASSESSMENT" }]);
    });
    await openEpisode();

    expect(screen.queryByTestId("surgery-reopen-panel")).not.toBeInTheDocument();
  });

  it("shows the reopen trail on an episode that has been back to theatre", async () => {
    get.mockImplementation((url: string) => {
      if (url.includes("/specialties")) return Promise.resolve([]);
      if (url.includes("/assessment") || url.includes("/decision")) return Promise.reject(notRecorded);
      return Promise.resolve([
        {
          ...EPISODE,
          status: "REOPENED",
          reopenedAt: "2026-07-02T11:00:00Z",
          reopenedBy: "dr-b",
          reopenReason: "anastomotic leak",
        },
      ]);
    });
    await openEpisode();

    const trail = screen.getByTestId("surgery-episode-reopen-trail");
    expect(trail).toHaveTextContent("anastomotic leak");
    expect(trail).toHaveTextContent("dr-b");
  });

  it("distinguishes a confirmed MDT decision from one recorded but not yet confirmed", async () => {
    const decision = {
      id: "d-1",
      surgicalEpisodeId: EPISODE.id,
      finalDecision: "PROCEED",
      decidedBy: "dr-c",
      decidedAt: "2026-07-01T10:00:00Z",
      decisionForum: "MDT",
      mdtDecisionRef: "11111111-2222-3333-4444-555555555555",
      mdtDecisionSource: "PCT_MDT_DECISION",
      mdtDecisionVerifiedAt: null,
      mdtDecisionVerified: false,
    };
    get.mockImplementation((url: string) => {
      if (url.includes("/specialties")) return Promise.resolve([]);
      if (url.includes("/decision")) return Promise.resolve(decision);
      if (url.includes("/assessment")) return Promise.reject(notRecorded);
      return Promise.resolve([EPISODE]);
    });
    await openEpisode();

    await waitFor(() => expect(screen.getByTestId("surgery-decision-forum")).toBeInTheDocument());
    // Unverified must be visibly unverified — not a tick, and not a missing board reference.
    expect(screen.getByTestId("surgery-decision-mdt-unverified")).toBeInTheDocument();
    expect(screen.queryByTestId("surgery-decision-mdt-verified")).not.toBeInTheDocument();
    expect(screen.getByTestId("surgery-decision-forum")).toHaveTextContent(
      "11111111-2222-3333-4444-555555555555",
    );
  });

  it("will not save an MDT decision that names no board record", async () => {
    routeGets([]);
    await openEpisode();

    fireEvent.click(screen.getByTestId("surgery-decision-edit"));
    fireEvent.change(screen.getByTestId("surgery-decision-forum-select"), {
      target: { value: "MDT" },
    });

    expect(screen.getByTestId("surgery-decision-save")).toBeDisabled();
    expect(screen.getByTestId("surgery-decision-mdt-required")).toBeInTheDocument();

    fireEvent.change(screen.getByTestId("surgery-decision-mdt-ref"), {
      target: { value: "11111111-2222-3333-4444-555555555555" },
    });
    expect(screen.getByTestId("surgery-decision-save")).not.toBeDisabled();

    put.mockResolvedValue({});
    fireEvent.click(screen.getByTestId("surgery-decision-save"));

    await waitFor(() =>
      expect(put).toHaveBeenCalledWith(
        `/internal/v1/surgery/episodes/${EPISODE.id}/decision`,
        expect.objectContaining({
          decisionForum: "MDT",
          mdtDecisionRef: "11111111-2222-3333-4444-555555555555",
          mdtDecisionSource: "PCT_MDT_DECISION",
        }),
      ),
    );
  });
});
