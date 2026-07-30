import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MEDICINE_SPECIALTIES, findSpecialty, matchesSpecialty } from "../specialty-config";
import { MEDICINE_CDS_TOPICS } from "@/hooks/queries/useMedicineCds";
import { CHRONIC_REGISTERS } from "@/hooks/queries/useChronicRegister";

const state = vi.hoisted(() => ({
  conditions: { data: undefined as unknown, isError: false, isLoading: false },
}));

vi.mock("@/hooks/queries/useConditions", () => ({ useConditions: () => state.conditions }));

// eslint-disable-next-line import/first
import { SpecialtyWorkspaceShell } from "../SpecialtyWorkspaceShell";

const condition = (id: string, name: string, icd: string) => ({
  id, type: "condition",
  attributes: { conditionName: name, icdCode: icd, clinicalStatus: "ACTIVE" },
});

describe("Specialty config — thirteen, and honest about each", () => {
  it("covers exactly the thirteen specialties §8 names", () => {
    // §8 lists thirteen. A pack that ships nine looks complete to everyone who did not count, and
    // the four that went missing are invisible rather than absent.
    expect(MEDICINE_SPECIALTIES).toHaveLength(13);
    expect(MEDICINE_SPECIALTIES.map((s) => s.key)).toEqual([
      "cardiology", "respiratory", "gastroenterology", "nephrology", "endocrinology",
      "neurology", "infectious-diseases", "rheumatology", "haematology", "oncology",
      "dermatology", "geriatrics", "palliative",
    ]);
  });

  it("every specialty cites the brief section it comes from", () => {
    // So a reader can check the claim rather than take the config's word for it.
    for (const s of MEDICINE_SPECIALTIES) {
      expect(s.section, s.key).toMatch(/^§8\.\d+$/);
    }
  });

  it("every specialty names conditions and at least one examination region", () => {
    for (const s of MEDICINE_SPECIALTIES) {
      expect(s.conditions.length, s.key).toBeGreaterThan(0);
      expect(s.examinationRegions.length, s.key).toBeGreaterThan(0);
    }
  });

  it("every specialty states what is NOT built", () => {
    // The load-bearing assertion. §8's tooling lists are largely unbuilt; a workspace that renders
    // them as tiles is a button that looks available and does nothing. If a specialty ever claims
    // nothing is missing, that is a claim to have built the lot, and this test makes it deliberate.
    for (const s of MEDICINE_SPECIALTIES) {
      expect(s.notBuilt.length, `${s.key} claims nothing is missing`).toBeGreaterThan(0);
    }
  });

  it("no specialty references a CDS topic the evaluator does not serve", () => {
    // A topic with no pack is a link that 404s on click.
    for (const s of MEDICINE_SPECIALTIES) {
      for (const topic of s.cdsTopics) {
        expect(MEDICINE_CDS_TOPICS, `${s.key} -> ${topic}`).toContain(topic);
      }
    }
  });

  it("no specialty references a register that does not exist", () => {
    const known = CHRONIC_REGISTERS.map((r) => r.programme as string);
    for (const s of MEDICINE_SPECIALTIES) {
      for (const r of s.registers) {
        expect(known, `${s.key} -> ${r}`).toContain(r);
      }
    }
  });

  it("matches a problem by code prefix and by display fragment", () => {
    const cardiology = findSpecialty("cardiology")!;
    expect(matchesSpecialty(cardiology, "I50.0", null)).toBe(true);
    expect(matchesSpecialty(cardiology, null, "Congestive heart failure")).toBe(true);
    expect(matchesSpecialty(cardiology, "E11", "Type 2 diabetes")).toBe(false);
  });

  it("a blank code or display matches nothing", () => {
    const cardiology = findSpecialty("cardiology")!;
    expect(matchesSpecialty(cardiology, "", "")).toBe(false);
    expect(matchesSpecialty(cardiology, null, null)).toBe(false);
  });

  it("a blank MATCHER never pulls in every problem", () => {
    // The direction that actually breaks: ANY string startsWith(""), so one blank entry in the
    // config would silently make a specialty claim the whole problem list. A mutation test caught
    // that the earlier version of this assertion guarded the value rather than the matcher, and so
    // could not fail.
    const withBlank = { ...findSpecialty("cardiology")!, problemMatchers: [""] };
    expect(matchesSpecialty(withBlank, "E11", "Type 2 diabetes")).toBe(false);
  });

  it("no shipped specialty carries a blank matcher", () => {
    for (const s of MEDICINE_SPECIALTIES) {
      for (const m of s.problemMatchers) {
        expect(m.trim(), `${s.key} has a blank matcher`).not.toBe("");
      }
    }
  });
});

describe("SpecialtyWorkspaceShell", () => {
  beforeEach(() => {
    state.conditions = { data: { data: [] }, isError: false, isLoading: false };
  });

  it("shows only the problems that fall in this specialty", () => {
    state.conditions = {
      ...state.conditions,
      data: {
        data: [
          condition("c1", "Congestive heart failure", "I50.0"),
          condition("c2", "Type 2 diabetes", "E11"),
        ],
      },
    };
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="cardiology" />);

    expect(screen.getByTestId("specialty-problems")).toHaveTextContent("Congestive heart failure");
    expect(screen.getByTestId("specialty-problems")).not.toHaveTextContent("Type 2 diabetes");
  });

  it("a failed problem read never says the patient has nothing in this specialty", () => {
    state.conditions = { data: undefined, isError: true, isLoading: false };
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="cardiology" />);

    expect(screen.getByTestId("specialty-problems-unavailable"))
      .toHaveTextContent(/not.*a statement that the patient has no conditions/i);
    expect(screen.queryByTestId("specialty-problems-empty")).not.toBeInTheDocument();
  });

  it("a clean but empty result says the list WAS read", () => {
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="cardiology" />);
    expect(screen.getByTestId("specialty-problems-empty")).toHaveTextContent(/list was read/i);
  });

  it("always renders the not-built section", () => {
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="cardiology" />);
    expect(screen.getByTestId("specialty-not-built")).toHaveTextContent(/Ambulatory monitoring/);
    expect(screen.getByTestId("specialty-not-built")).toHaveTextContent(/has not built it/i);
  });

  it("cardiology exposes shared-spine links for ECG/echo and volume status", () => {
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="cardiology" />);
    expect(screen.getByTestId("specialty-spine")).toHaveTextContent(/ECG \/ echo/);
    expect(screen.getByTestId("specialty-spine")).toHaveTextContent(/Volume status/);
  });

  it("every specialty has a spineLinks array (may be empty)", () => {
    for (const s of MEDICINE_SPECIALTIES) {
      expect(Array.isArray(s.spineLinks), s.key).toBe(true);
    }
  });

  it("offers the registers only for specialties that run one", () => {
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="dermatology" />);
    expect(screen.queryByTestId("specialty-registers")).not.toBeInTheDocument();
  });

  it("an unknown specialty says so rather than rendering an empty workspace", () => {
    render(<SpecialtyWorkspaceShell patientId="p1" specialtyKey="phrenology" />);
    expect(screen.getByTestId("specialty-unknown")).toBeInTheDocument();
  });
});
