import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

const state = vi.hoisted(() => ({
  vocabulary: { data: undefined as unknown, isError: false, isLoading: false },
  history: { data: undefined as unknown, isError: false, isLoading: false },
  detail: { data: undefined as unknown, isError: false, isLoading: false },
  record: { mutate: vi.fn(), isPending: false, isError: false },
}));

vi.mock("@/hooks/queries/useExaminations", () => ({
  useExaminationVocabulary: () => state.vocabulary,
  useExaminations: () => state.history,
  useExamination: () => state.detail,
  useRecordExamination: () => state.record,
}));

// eslint-disable-next-line import/first
import { ExaminationShell } from "../ExaminationShell";

const VOCAB = {
  data: {
    regions: ["ABDOMEN", "RESPIRATORY", "NEUROLOGY"],
    states: ["NORMAL", "ABNORMAL", "NOT_EXAMINED", "UNABLE_TO_EXAMINE", "DEFERRED", "UNCERTAIN"],
    examined_states: ["NORMAL", "ABNORMAL", "UNCERTAIN"],
    states_needing_detail: ["ABNORMAL", "UNCERTAIN", "UNABLE_TO_EXAMINE", "DEFERRED"],
    graphics: ["ABDOMINAL_REGIONS"],
    laterality: ["LEFT", "RIGHT"],
    not_examined_is_not_normal:
      "NOT_EXAMINED, UNABLE_TO_EXAMINE and DEFERRED are not findings of normality.",
  },
};

describe("ExaminationShell — nothing defaults to normal", () => {
  beforeEach(() => {
    state.vocabulary = { data: VOCAB, isError: false, isLoading: false };
    state.history = { data: { data: [] }, isError: false, isLoading: false };
    state.detail = { data: undefined, isError: false, isLoading: false };
    state.record = { mutate: vi.fn(), isPending: false, isError: false };
  });

  it("every region starts unrecorded, not normal", () => {
    // A form pre-set to "normal" produces a full normal examination from a clinician who touched
    // three fields, and does it silently.
    render(<ExaminationShell patientId="p1" />);
    for (const region of VOCAB.data.regions) {
      expect(screen.getByTestId(`state-${region}`)).toHaveValue("");
    }
  });

  it("a region left unrecorded is not submitted at all", () => {
    // Unset is neither NORMAL nor NOT_EXAMINED: the examiner never reached it, and absence says so.
    const mutate = vi.fn();
    state.record = { ...state.record, mutate };
    render(<ExaminationShell patientId="p1" />);
    fireEvent.change(screen.getByTestId("state-ABDOMEN"), { target: { value: "NORMAL" } });
    fireEvent.click(screen.getByTestId("save-examination"));

    expect(mutate).toHaveBeenCalledWith(
      expect.objectContaining({ findings: [{ region: "ABDOMEN", state: "NORMAL", detail: undefined }] }),
    );
  });

  it("choosing ABNORMAL demands a detail field before saving", () => {
    render(<ExaminationShell patientId="p1" />);
    fireEvent.change(screen.getByTestId("state-ABDOMEN"), { target: { value: "ABNORMAL" } });

    expect(screen.getByTestId("detail-ABDOMEN")).toBeInTheDocument();
    expect(screen.getByTestId("examination-detail-missing")).toBeInTheDocument();
    expect(screen.getByTestId("save-examination")).toBeDisabled();
  });

  it("an unable-to-examine region is asked why, not what", () => {
    render(<ExaminationShell patientId="p1" />);
    fireEvent.change(screen.getByTestId("state-NEUROLOGY"), { target: { value: "UNABLE_TO_EXAMINE" } });

    expect(screen.getByTestId("detail-NEUROLOGY")).toHaveAttribute("placeholder", "Why not?");
  });

  it("an abnormal region is asked what, not why", () => {
    render(<ExaminationShell patientId="p1" />);
    fireEvent.change(screen.getByTestId("state-ABDOMEN"), { target: { value: "ABNORMAL" } });

    expect(screen.getByTestId("detail-ABDOMEN")).toHaveAttribute("placeholder", "What did you find?");
  });

  it("NORMAL and NOT_EXAMINED need no detail and do not block saving", () => {
    render(<ExaminationShell patientId="p1" />);
    fireEvent.change(screen.getByTestId("state-ABDOMEN"), { target: { value: "NORMAL" } });
    fireEvent.change(screen.getByTestId("state-NEUROLOGY"), { target: { value: "NOT_EXAMINED" } });

    expect(screen.queryByTestId("detail-ABDOMEN")).not.toBeInTheDocument();
    expect(screen.queryByTestId("examination-detail-missing")).not.toBeInTheDocument();
    expect(screen.getByTestId("save-examination")).not.toBeDisabled();
  });

  it("a failed vocabulary read hides the form rather than guessing the states", () => {
    // A form rendered with a guessed vocabulary is how a six-state framework becomes a two-state one.
    state.vocabulary = { data: undefined, isError: true, isLoading: false };
    render(<ExaminationShell patientId="p1" />);

    expect(screen.getByTestId("examination-vocab-failed")).toHaveTextContent(/not.*guessed set of states/i);
    expect(screen.queryByTestId("examination")).not.toBeInTheDocument();
  });

  it("a failed history read never says the patient has not been examined", () => {
    state.history = { data: undefined, isError: true, isLoading: false };
    render(<ExaminationShell patientId="p1" />);

    expect(screen.getByTestId("examination-history-failed"))
      .toHaveTextContent(/not.*never been examined/i);
    expect(screen.queryByTestId("examination-history-empty")).not.toBeInTheDocument();
  });

  it("an empty history says the list WAS read", () => {
    render(<ExaminationShell patientId="p1" />);
    expect(screen.getByTestId("examination-history-empty")).toHaveTextContent(/list was read/i);
  });

  it("the coverage note and the never-considered regions are shown on a read", () => {
    // What was not looked at has no row of its own, so it is invisible unless said out loud.
    state.detail = {
      data: {
        data: {
          examination_id: "e1", subject_cpid: "p1", examined_at: "2026-07-28", examined_by: "clin-1",
          context_note: null,
          findings: [{ region: "ABDOMEN", state: "NORMAL", was_examined: true, detail: null,
                       finding_code: null, graphic: null, site: null, laterality: null }],
          coverage: {
            examined: ["ABDOMEN"], recorded_not_examined: [], never_considered: ["NEUROLOGY", "SKIN"],
            note: "1 of 22 regions were examined. 0 were explicitly recorded as not examined and 21 were never considered.",
          },
        },
      },
      isError: false, isLoading: false,
    };
    render(<ExaminationShell patientId="p1" />);

    expect(screen.getByTestId("examination-coverage")).toHaveTextContent(/never considered/i);
    expect(screen.getByTestId("examination-never-considered")).toHaveTextContent(/Neurology/);
  });

  it("a save failure says nothing was recorded", () => {
    state.record = { ...state.record, isError: true };
    render(<ExaminationShell patientId="p1" />);

    expect(screen.getByTestId("examination-save-failed")).toHaveTextContent(/not.*saved/i);
  });
});
