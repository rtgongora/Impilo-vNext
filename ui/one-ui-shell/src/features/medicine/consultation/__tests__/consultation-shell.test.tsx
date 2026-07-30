import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";

const state = vi.hoisted(() => ({
  consultations: { data: undefined as unknown, isError: false, isLoading: false },
  decisions: { data: undefined as unknown, isError: false, isLoading: false },
  transfers: { data: undefined as unknown, isError: false, isLoading: false },
  request: { mutate: vi.fn(), isPending: false, isError: false },
  answer: { mutate: vi.fn(), isPending: false, isError: false },
  requestTransfer: { mutate: vi.fn(), isPending: false, isError: false },
  acceptTransfer: { mutate: vi.fn(), isPending: false, isError: false },
}));

vi.mock("@/hooks/queries/useConsultations", () => ({
  useConsultations: () => state.consultations,
  useMdtDecisions: () => state.decisions,
  useCareTransfers: () => state.transfers,
  useRequestConsultation: () => state.request,
  useAnswerConsultation: () => state.answer,
  useRequestCareTransfer: () => state.requestTransfer,
  useAcceptCareTransfer: () => state.acceptTransfer,
}));

// eslint-disable-next-line import/first
import { ConsultationShell } from "../ConsultationShell";

const consultation = (over: Record<string, unknown> = {}) => ({
  consultation_id: "c1", subject_cpid: "p1",
  requesting_service: "MEDICINE", requesting_actor: "med-1", asked_of_service: "SURGERY",
  question: "Is this abdomen surgical?", clinical_summary: null,
  urgency: "ROUTINE", status: "REQUESTED",
  owning_service: "MEDICINE",
  owning_service_note:
    "MEDICINE holds this patient. A consultation asks a question and never transfers care; a takeover is a separate, explicit act.",
  advice: null, responded_by: null, responded_at: null,
  takeover_recommended: false, decline_reason: null, requested_at: "2026-07-28",
  ...over,
});

const decision = (over: Record<string, unknown> = {}) => ({
  decision_id: "d1", meeting_type: "ONCOLOGY", met_on: "2026-07-28",
  chaired_by: "Dr M", participants: "Oncology, radiology",
  decision: "Proceed to staging CT", treatment_intent: null,
  next_action: null, responsible_service: null, ...over,
});

describe("ConsultationShell — asking a question never hands over the patient", () => {
  beforeEach(() => {
    state.consultations = { data: { data: [] }, isError: false, isLoading: false };
    state.decisions = { data: { data: [] }, isError: false, isLoading: false };
    state.transfers = { data: { data: [] }, isError: false, isLoading: false };
    state.request = { mutate: vi.fn(), isPending: false, isError: false };
    state.answer = { mutate: vi.fn(), isPending: false, isError: false };
    state.requestTransfer = { mutate: vi.fn(), isPending: false, isError: false };
    state.acceptTransfer = { mutate: vi.fn(), isPending: false, isError: false };
  });

  it("every consultation says who still holds the patient", () => {
    // §23.10's failure is not a decision anybody makes — it is what happens when nobody says out
    // loud who still holds the patient. So this line is unconditional.
    state.consultations = { ...state.consultations, data: { data: [consultation()] } };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("owner-c1")).toHaveTextContent(/MEDICINE holds this patient/);
    expect(screen.getByTestId("owner-c1")).toHaveTextContent(/never transfers care/);
  });

  it("says who holds the patient even after the consultation was answered", () => {
    // The moment a reader is most likely to assume the answering team took over.
    state.consultations = {
      ...state.consultations,
      data: { data: [consultation({ status: "ANSWERED", advice: "Not surgical", responded_by: "surg-1" })] },
    };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("owner-c1")).toHaveTextContent(/MEDICINE holds this patient/);
  });

  it("a recommended takeover is shown as a recommendation that has NOT happened", () => {
    state.consultations = {
      ...state.consultations,
      data: {
        data: [consultation({
          status: "ANSWERED", advice: "Should be surgical", responded_by: "surg-1",
          takeover_recommended: true,
          takeover_note: "A takeover was RECOMMENDED and has not happened. MEDICINE still holds this patient.",
        })],
      },
    };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("takeover-c1")).toHaveTextContent(/has not happened/i);
    expect(screen.getByTestId("owner-c1")).toHaveTextContent(/MEDICINE/);
    expect(screen.getByTestId("request-transfer-c1")).toBeInTheDocument();
  });

  it("requesting a transfer does not pretend ownership has moved", () => {
    state.consultations = {
      ...state.consultations,
      data: {
        data: [consultation({
          status: "ANSWERED", advice: "Surgical", responded_by: "surg-1",
          takeover_recommended: true,
        })],
      },
    };
    render(<ConsultationShell patientId="p1" />);

    fireEvent.click(screen.getByTestId("request-transfer-c1"));
    expect(state.requestTransfer.mutate).toHaveBeenCalledWith(
      expect.objectContaining({
        consultation_id: "c1",
        from_service: "MEDICINE",
        to_service: "SURGERY",
      }),
    );
    expect(screen.getByTestId("owner-c1")).toHaveTextContent(/MEDICINE holds this patient/);
  });

  it("accepting a pending transfer requires the receiving service's own record id", () => {
    state.consultations = {
      ...state.consultations,
      data: {
        data: [consultation({
          status: "ANSWERED", advice: "Surgical", responded_by: "surg-1",
          takeover_recommended: true,
        })],
      },
    };
    state.transfers = {
      data: {
        data: [{
          transfer_id: "t1",
          subject_cpid: "p1",
          consultation_id: "c1",
          from_service: "MEDICINE",
          to_service: "SURGERY",
          reason: "Take over",
          status: "PENDING",
          accepting_ref: null,
          ownership_note: "MEDICINE still holds this patient. A transfer has been requested of SURGERY and has not been accepted.",
          requested_at: "2026-07-30",
        }],
      },
      isError: false,
      isLoading: false,
    };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("accept-transfer-t1")).toBeDisabled();
    fireEvent.change(screen.getByTestId("accepting-ref-t1"), { target: { value: "surg-episode-1" } });
    fireEvent.click(screen.getByTestId("accept-transfer-t1"));
    expect(state.acceptTransfer.mutate).toHaveBeenCalledWith({
      transferId: "t1",
      accepting_ref: "surg-episode-1",
    });
  });

  it("answering requires advice before the button is usable", () => {
    // An answered consultation with no advice closes the loop while leaving the question open.
    state.consultations = { ...state.consultations, data: { data: [consultation()] } };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("answer-c1")).toBeDisabled();
    fireEvent.change(screen.getByTestId("advice-input-c1"), { target: { value: "Not surgical" } });
    expect(screen.getByTestId("answer-c1")).not.toBeDisabled();
  });

  it("an answered consultation offers no answer box", () => {
    state.consultations = {
      ...state.consultations,
      data: { data: [consultation({ status: "ANSWERED", advice: "Not surgical" })] },
    };
    render(<ConsultationShell patientId="p1" />);
    expect(screen.queryByTestId("advice-input-c1")).not.toBeInTheDocument();
  });

  it("a failed consultation read never says none was requested", () => {
    state.consultations = { data: undefined, isError: true, isLoading: false };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("consultations-failed"))
      .toHaveTextContent(/not.*a statement that none was requested/i);
    expect(screen.queryByTestId("consultations-empty")).not.toBeInTheDocument();
  });

  it("a clean but empty list says the list WAS read", () => {
    render(<ConsultationShell patientId="p1" />);
    expect(screen.getByTestId("consultations-empty")).toHaveTextContent(/list was read/i);
  });

  it("a failed MDT read warns that treatment intent may already be set", () => {
    // The dangerous reading: a blank MDT section taken as "no decision has been made", when the
    // meeting may have set a curative or palliative intent nobody can now see.
    state.decisions = { data: undefined, isError: true, isLoading: false };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("mdt-failed")).toHaveTextContent(/treatment intent may already be set/i);
  });

  it("an MDT decision with no recorded intent says so rather than leaving a blank", () => {
    // A blank reads as "no decision on intent was needed"; the record cannot support that.
    state.decisions = { ...state.decisions, data: { data: [decision()] } };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("mdt-no-intent-d1"))
      .toHaveTextContent(/No treatment intent was recorded/i);
  });

  it("an MDT decision always shows its chair and participants", () => {
    state.decisions = {
      ...state.decisions,
      data: { data: [decision({ treatment_intent: "LIFE_PROLONGING" })] },
    };
    render(<ConsultationShell patientId="p1" />);

    expect(screen.getByTestId("mdt-d1")).toHaveTextContent(/chaired by Dr M/i);
    expect(screen.getByTestId("mdt-d1")).toHaveTextContent(/Oncology, radiology/);
    expect(screen.getByTestId("mdt-d1")).toHaveTextContent(/life prolonging/i);
  });

  it("a failed send says nobody has been asked", () => {
    state.request = { ...state.request, isError: true };
    render(<ConsultationShell patientId="p1" />);
    expect(screen.getByTestId("consultation-send-failed")).toHaveTextContent(/Nobody has been asked/i);
  });

  it("a consultation cannot be sent without both a team and a question", () => {
    render(<ConsultationShell patientId="p1" />);
    expect(screen.getByTestId("consultation-send")).toBeDisabled();

    fireEvent.change(screen.getByTestId("consultation-asked-of"), { target: { value: "SURGERY" } });
    expect(screen.getByTestId("consultation-send")).toBeDisabled();

    fireEvent.change(screen.getByTestId("consultation-question"), { target: { value: "Surgical?" } });
    expect(screen.getByTestId("consultation-send")).not.toBeDisabled();
  });
});
