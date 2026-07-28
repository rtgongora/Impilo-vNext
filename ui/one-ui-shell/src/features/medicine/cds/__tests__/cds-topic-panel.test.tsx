import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CDS_TOPICS } from "../cds-topics";
import { MEDICINE_CDS_TOPICS } from "@/hooks/queries/useMedicineCds";

const cds = vi.hoisted(() => ({
  state: { data: undefined as unknown, isError: false, isPending: false, mutate: vi.fn() },
}));

vi.mock("@/hooks/queries/useMedicineCds", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/queries/useMedicineCds")>();
  // MEDICINE_CDS_TOPICS is a plain constant and is deliberately NOT mocked — mocking it would mean
  // the coverage test below proves the panels match a fake list rather than the real one.
  return { ...actual, useMedicineCds: () => cds.state };
});

// eslint-disable-next-line import/first
import { CdsTopicPanel } from "../CdsTopicPanel";

const guidance = (over: Record<string, unknown> = {}) => ({
  data: {
    data: {
      assessed: true, referral_required: false, incomplete: false,
      alerts: [], not_assessed: [], content_version: "1.0.0", ...over,
    },
  },
});

const renderPanel = () =>
  render(
    <CdsTopicPanel
      topic="cvd-risk"
      title="Cardiovascular risk"
      description="WHO HEARTS"
      fields={[{ key: "systolicBp", label: "Systolic BP", type: "number" }]}
    />,
  );

describe("CdsTopicPanel — the three meanings of an empty alert list", () => {
  beforeEach(() => {
    cds.state = { data: undefined, isError: false, isPending: false, mutate: vi.fn() };
  });

  it("a FAILED evaluation says so, and never shows the no-alerts line", () => {
    // The dangerous case: nothing ran, so there is nothing to be reassured by.
    cds.state = { ...cds.state, isError: true };
    renderPanel();
    expect(screen.getByTestId("cds-failed-cvd-risk")).toHaveTextContent(/not.*an all-clear/i);
    expect(screen.queryByTestId("cds-noalerts-cvd-risk")).not.toBeInTheDocument();
  });

  it("an INCOMPLETE assessment names what was not assessed", () => {
    cds.state = { ...cds.state, data: guidance({ incomplete: true, not_assessed: ["systolicBp"] }).data };
    renderPanel();
    expect(screen.getByTestId("cds-incomplete-cvd-risk")).toHaveTextContent(/systolicBp/);
    expect(screen.getByTestId("cds-incomplete-cvd-risk")).toHaveTextContent(/not an all-clear/i);
  });

  it("a CLEAN run shows no-alerts and neither of the other two states", () => {
    cds.state = { ...cds.state, data: guidance().data };
    renderPanel();
    expect(screen.getByTestId("cds-noalerts-cvd-risk")).toBeInTheDocument();
    expect(screen.queryByTestId("cds-failed-cvd-risk")).not.toBeInTheDocument();
    expect(screen.queryByTestId("cds-incomplete-cvd-risk")).not.toBeInTheDocument();
  });

  it("renders an alert with its required action, not just the finding", () => {
    // A rule that names a problem without naming the next step is a notification, not decision
    // support — the same rule the content loader enforces on the server.
    cds.state = {
      ...cds.state,
      data: guidance({
        alerts: [{
          code: "CVD_HIGH", name: "High risk", severity: "HIGH",
          message: "High total cardiovascular risk", required_action: "Start intensive management",
          referral_required: true,
        }],
      }).data,
    };
    renderPanel();
    expect(screen.getByTestId("alert-CVD_HIGH")).toHaveTextContent("Start intensive management");
    expect(screen.getByTestId("alert-CVD_HIGH")).toHaveTextContent(/Referral required/i);
  });

  it("a blank field is omitted entirely — 'not stated' must never be sent as an answer", () => {
    // The engine reports unstated facts as unassessed. Sending "" would turn an unanswered question
    // into an answer and silently change the assessment.
    const mutate = vi.fn();
    cds.state = { ...cds.state, mutate };
    renderPanel();
    fireEvent.click(screen.getByTestId("evaluate-cvd-risk"));
    expect(mutate).toHaveBeenCalledWith({ topic: "cvd-risk", facts: {} });
  });

  it("a numeric field is sent as a number, because the engine's operators need one", () => {
    const mutate = vi.fn();
    cds.state = { ...cds.state, mutate };
    renderPanel();
    fireEvent.change(screen.getByTestId("field-systolicBp"), { target: { value: "150" } });
    fireEvent.click(screen.getByTestId("evaluate-cvd-risk"));
    expect(mutate).toHaveBeenCalledWith({ topic: "cvd-risk", facts: { systolicBp: 150 } });
  });
});

describe("CdsTopicPanel — a stated fact must not look like a detected one", () => {
  beforeEach(() => {
    cds.state = { data: undefined, isError: false, isPending: false, mutate: vi.fn() };
  });

  it("names the facts the engine was told rather than detected", () => {
    // The harm: an assessment that rests on a clinician's recollection reads, on screen, exactly
    // like one the system confirmed against the medicine list.
    cds.state = {
      ...cds.state,
      data: guidance({
        fact_provenance: { onNsaid: "CLINICIAN_ASSERTED", egfr: "DERIVED_FROM_MEDICATION_LIST" },
      }).data,
    };
    renderPanel();
    expect(screen.getByTestId("cds-asserted-cvd-risk")).toHaveTextContent(/onNsaid/);
    expect(screen.getByTestId("cds-asserted-cvd-risk")).not.toHaveTextContent(/egfr/);
  });

  it("says nothing when every fact was derived", () => {
    cds.state = {
      ...cds.state,
      data: guidance({ fact_provenance: { onNsaid: "DERIVED_FROM_MEDICATION_LIST" } }).data,
    };
    renderPanel();
    expect(screen.queryByTestId("cds-asserted-cvd-risk")).not.toBeInTheDocument();
  });

  it("shows the derivation note when part of the medicine list could not be read", () => {
    cds.state = {
      ...cds.state,
      data: guidance({ derivation_note: "1 of 3 medicine(s) could not be recognised" }).data,
    };
    renderPanel();
    expect(screen.getByTestId("cds-derivation-cvd-risk")).toHaveTextContent(/could not be recognised/);
  });

  it("sends the subject so the server can compose the medicine list", () => {
    const mutate = vi.fn();
    cds.state = { ...cds.state, mutate };
    render(
      <CdsTopicPanel
        topic="deprescribing"
        title="Medication review"
        description="WHO PEN"
        fields={[{ key: "egfr", label: "eGFR", type: "number" }]}
        subjectCpid="CPID-1"
      />,
    );
    fireEvent.click(screen.getByTestId("evaluate-deprescribing"));
    expect(mutate).toHaveBeenCalledWith({ topic: "deprescribing", facts: {}, subjectCpid: "CPID-1" });
  });
});

describe("CDS topic coverage", () => {
  it("the deprescribing panel never asks a clinician to assert a medication fact", () => {
    // These five were YES/NO dropdowns, and were the only producers of the facts the renal-safety
    // rules gate on — so DEPRX_DUPLICATE_THERAPY_REVIEW fired when a clinician had already spotted
    // the duplicate and said so. They are now derived server-side from the medicine list. Asking
    // again would let a typed answer overwrite evidence, so this guards the fabrication's return.
    const derived = ["onMetformin", "onNsaid", "onNephrotoxin", "onRenallyClearedDrug", "duplicateTherapyDetected"];
    for (const t of CDS_TOPICS) {
      for (const f of t.fields) {
        expect(derived, `${t.topic} asks for ${f.key}, which the server derives`).not.toContain(f.key);
      }
    }
  });


  it("every topic the BFF serves has a panel definition, and none is invented", () => {
    // Guards both directions: a topic without a panel is CDS a clinician cannot reach, and a panel
    // for a topic the evaluator does not serve would 404 on click.
    const defined = CDS_TOPICS.map((t) => t.topic).sort();
    expect(defined).toEqual([...MEDICINE_CDS_TOPICS].sort());
  });

  it("every field a panel offers is named, typed and non-empty", () => {
    for (const t of CDS_TOPICS) {
      expect(t.fields.length, `${t.topic} has no fields`).toBeGreaterThan(0);
      for (const f of t.fields) {
        expect(f.key).toBeTruthy();
        expect(f.label).toBeTruthy();
        if (f.type === "select") expect(f.options?.length ?? 0).toBeGreaterThan(0);
      }
    }
  });
});
