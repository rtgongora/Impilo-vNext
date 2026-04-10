import { describe, expect, it } from "vitest";
import { mapWellnessActivityRow, mapWellnessChallengeRow, mapWellnessVitalRow } from "./wellnessService";

describe("wellnessService row mappers", () => {
  it("maps activity snake_case from BFF", () => {
    const a = mapWellnessActivityRow({
      id: "u1",
      activity_date: "2026-04-01",
      steps: 5000,
      calories_burned: 200,
      active_minutes: 45,
      distance_km: 3.2,
      sleep_hours: 7.5,
      sleep_quality: "GOOD",
      water_ml: 1500,
    });
    expect(a.activityDate).toBe("2026-04-01");
    expect(a.steps).toBe(5000);
    expect(a.caloriesBurned).toBe(200);
    expect(a.waterMl).toBe(1500);
  });

  it("maps vitals snake_case", () => {
    const v = mapWellnessVitalRow({
      id: "v1",
      vital_type: "HEART_RATE",
      value: 72,
      unit: "bpm",
      measured_at: "2026-04-09T10:00:00Z",
      source: "MANUAL",
    });
    expect(v.vitalType).toBe("HEART_RATE");
    expect(v.measuredAt).toContain("2026");
  });

  it("maps challenge snake_case", () => {
    const c = mapWellnessChallengeRow({
      id: "c1",
      title: "Walk 10k",
      challenge_type: "STEPS",
      target_value: 10000,
      target_unit: "steps",
      start_date: "2026-04-01",
      end_date: "2026-04-30",
      participant_count: 12,
      status: "ACTIVE",
    });
    expect(c.title).toBe("Walk 10k");
    expect(c.challengeType).toBe("STEPS");
    expect(c.participantCount).toBe(12);
  });
});
