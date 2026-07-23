import { describe, expect, it } from "vitest";
import {
  assessDanger,
  buildSummary,
  classifyUrgency,
  nextStep,
  toSosPayload,
  type TriageAnswers,
} from "./triage-protocol";

describe("triage-protocol", () => {
  it("walks the full question order when no danger signs are reported", () => {
    const answers: TriageAnswers = {};
    expect(nextStep(answers)).toBe("situation");
    answers.situation = "MEDICAL";
    expect(nextStep(answers)).toBe("conscious");
    answers.conscious = "yes";
    expect(nextStep(answers)).toBe("breathing");
    answers.breathing = "normal";
    expect(nextStep(answers)).toBe("bleeding");
    answers.bleeding = "none";
    expect(nextStep(answers)).toBe("redFlags");
    answers.redFlags = ["none"];
    expect(nextStep(answers)).toBe("onset");
    answers.onset = "today";
    expect(nextStep(answers)).toBe("age");
    answers.age = "adult";
    expect(nextStep(answers)).toBe("pregnancy");
    answers.pregnancy = "no";
    expect(nextStep(answers)).toBe("conditions");
    answers.conditions = ["none"];
    expect(nextStep(answers)).toBe("alone");
    answers.alone = "no";
    expect(nextStep(answers)).toBe("callback");
    answers.callbackNumber = "0771234567";
    expect(nextStep(answers)).toBe("location");
    answers.locationDescription = "Mbare Musika";
    expect(nextStep(answers)).toBe("review");
  });

  it("escalates on unconsciousness: danger flagged and questioning shortened to callback", () => {
    const answers: TriageAnswers = {
      situation: "TRAUMA",
      conscious: "no",
      breathing: "difficulty",
      bleeding: "none",
      redFlags: ["none"],
    };
    const danger = assessDanger(answers);
    expect(danger.danger).toBe(true);
    expect(danger.reasons).toContain("unconscious");
    // No onset/age/conditions questioning once escalated — straight to contact.
    expect(nextStep(answers)).toBe("callback");
  });

  it("treats cardiac situation as chest-pain danger even before red flags", () => {
    const danger = assessDanger({ situation: "CARDIAC", conscious: "yes" });
    expect(danger.danger).toBe(true);
    expect(danger.reasons).toContain("chest_pain");
  });

  it("skips the pregnancy question for children and for the obstetric path", () => {
    const child: TriageAnswers = {
      situation: "MEDICAL",
      conscious: "yes",
      breathing: "normal",
      bleeding: "none",
      redFlags: ["none"],
      onset: "now",
      age: "child",
    };
    expect(nextStep(child)).toBe("conditions");
    const obstetric: TriageAnswers = { ...child, situation: "OBSTETRIC", age: "adult" };
    expect(nextStep(obstetric)).toBe("conditions");
  });

  it("accepts GPS-only location capture", () => {
    const answers: TriageAnswers = {
      situation: "MEDICAL",
      conscious: "no",
      breathing: "unsure",
      bleeding: "none",
      redFlags: ["none"],
      callbackNumber: "0771234567",
      lat: -17.83,
      lng: 31.05,
    };
    expect(nextStep(answers)).toBe("review");
  });

  it("builds a RED summary and a real SOS payload from an escalated triage", () => {
    const answers: TriageAnswers = {
      situation: "TRAUMA",
      conscious: "yes",
      breathing: "normal",
      bleeding: "severe",
      redFlags: ["none"],
      callbackNumber: "0771 234 567",
      locationDescription: "Near Jimila Clinic",
      province: "Matabeleland North",
      lat: -18.1,
      lng: 27.5,
    };
    const summary = buildSummary(answers);
    expect(summary).toContain("RED — danger signs reported");
    expect(summary).toContain("severe bleeding");
    expect(summary).toContain("Injury or accident");

    const payload = toSosPayload(answers);
    expect(payload).toMatchObject({
      callbackNumber: "0771 234 567",
      emergencyCategory: "TRAUMA",
      lat: -18.1,
      lng: 27.5,
    });
    expect(String(payload.locationDescription)).toContain("Near Jimila Clinic");
    expect(String(payload.description)).toContain("[Nompilo guided triage]");
  });

  it("never reports danger for a calm presentation", () => {
    const danger = assessDanger({
      situation: "MEDICAL",
      conscious: "yes",
      breathing: "normal",
      bleeding: "minor",
      redFlags: ["none"],
    });
    expect(danger.danger).toBe(false);
    expect(danger.reasons).toHaveLength(0);
  });

  describe("classifyUrgency (doctrine §13.5 taxonomy)", () => {
    it("classifies any danger sign as emergency", () => {
      expect(classifyUrgency({ situation: "TRAUMA", bleeding: "severe" }).tier).toBe("emergency");
      expect(classifyUrgency({ situation: "CARDIAC", conscious: "yes" }).tier).toBe("emergency");
    });

    it("classifies pregnancy, mental-health and recent injury as urgency", () => {
      expect(
        classifyUrgency({ situation: "MEDICAL", conscious: "yes", breathing: "normal", bleeding: "none", redFlags: ["none"], pregnancy: "yes" }).tier,
      ).toBe("urgency");
      expect(classifyUrgency({ situation: "MENTAL_HEALTH", conscious: "yes", breathing: "normal", bleeding: "none", redFlags: ["none"] }).tier).toBe(
        "urgency",
      );
      expect(
        classifyUrgency({ situation: "TRAUMA", conscious: "yes", breathing: "normal", bleeding: "minor", redFlags: ["none"] }).tier,
      ).toBe("urgency");
    });

    it("classifies a calm, non-recent medical presentation as routine", () => {
      const tier = classifyUrgency({
        situation: "MEDICAL",
        conscious: "yes",
        breathing: "normal",
        bleeding: "none",
        redFlags: ["none"],
        onset: "longer",
        age: "adult",
        pregnancy: "no",
      }).tier;
      expect(tier).toBe("routine");
    });
  });
});
