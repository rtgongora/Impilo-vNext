import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type RawScore = {
  z_score: number;
  percentile: number;
} | null;

type RawGrowthMeasurement = {
  id: string;
  type: string;
  attributes: {
    patient_id: string;
    encounter_id: string | null;
    measured_at: string;
    recorded_by: string | null;
    weight_kg: number | null;
    length_cm: number | null;
    height_cm: number | null;
    head_circumference_cm: number | null;
    muac_cm: number | null;
    bmi: number | null;
    measurement_mode: string | null;
    notes: string | null;
    created_at: string;
    derived: {
      age_days: number | null;
      standard: string | null;
      normalized_stature_cm: number | null;
      normalized_stature_mode: string | null;
      stature_adjustment_cm: number | null;
      body_mass_index: number | null;
      weight_for_age: RawScore;
      length_height_for_age: RawScore;
      body_mass_index_for_age: RawScore;
      head_circumference_for_age: RawScore;
    };
  };
};

type RawGrowthResponse = ApiResponse<RawGrowthMeasurement[]>;
type RawGrowthItemResponse = ApiResponse<RawGrowthMeasurement>;

export type GrowthScore = {
  zScore: number;
  percentile: number;
} | null;

export type GrowthMeasurement = {
  id: string;
  measuredAt: string;
  recordedBy: string | null;
  weightKg: number | null;
  lengthCm: number | null;
  heightCm: number | null;
  headCircumferenceCm: number | null;
  muacCm: number | null;
  bmi: number | null;
  measurementMode: string | null;
  notes: string | null;
  derived: {
    ageDays: number | null;
    standard: string | null;
    normalizedStatureCm: number | null;
    normalizedStatureMode: string | null;
    statureAdjustmentCm: number | null;
    bodyMassIndex: number | null;
    weightForAge: GrowthScore;
    lengthHeightForAge: GrowthScore;
    bodyMassIndexForAge: GrowthScore;
    headCircumferenceForAge: GrowthScore;
  };
};

export type RecordGrowthPayload = {
  patientId: string;
  encounterId?: string | null;
  recordedBy?: string | null;
  measuredAt?: string | null;
  weightKg?: number | null;
  lengthCm?: number | null;
  heightCm?: number | null;
  headCircumferenceCm?: number | null;
  muacCm?: number | null;
  measurementMode?: string | null;
  notes?: string | null;
};

function mapScore(score: RawScore): GrowthScore {
  if (!score) return null;
  return {
    zScore: score.z_score,
    percentile: score.percentile,
  };
}

function mapMeasurement(resource: RawGrowthMeasurement): GrowthMeasurement {
  const a = resource.attributes;
  return {
    id: resource.id,
    measuredAt: a.measured_at,
    recordedBy: a.recorded_by,
    weightKg: a.weight_kg,
    lengthCm: a.length_cm,
    heightCm: a.height_cm,
    headCircumferenceCm: a.head_circumference_cm,
    muacCm: a.muac_cm,
    bmi: a.bmi,
    measurementMode: a.measurement_mode,
    notes: a.notes,
    derived: {
      ageDays: a.derived.age_days,
      standard: a.derived.standard,
      normalizedStatureCm: a.derived.normalized_stature_cm,
      normalizedStatureMode: a.derived.normalized_stature_mode,
      statureAdjustmentCm: a.derived.stature_adjustment_cm,
      bodyMassIndex: a.derived.body_mass_index,
      weightForAge: mapScore(a.derived.weight_for_age),
      lengthHeightForAge: mapScore(a.derived.length_height_for_age),
      bodyMassIndexForAge: mapScore(a.derived.body_mass_index_for_age),
      headCircumferenceForAge: mapScore(a.derived.head_circumference_for_age),
    },
  };
}

export function useGrowth(patientId: string) {
  return useQuery<GrowthMeasurement[]>({
    queryKey: ["growth", { patientId }],
    queryFn: async () => {
      const response = await apiClient.get<RawGrowthResponse>(
        `/internal/v1/growth?patient_id=${encodeURIComponent(patientId)}`,
      );
      return response.data.map(mapMeasurement);
    },
    enabled: !!patientId,
  });
}

export function useRecordGrowth() {
  const queryClient = useQueryClient();

  return useMutation<GrowthMeasurement, unknown, RecordGrowthPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<RawGrowthItemResponse>("/internal/v1/growth", {
        patient_id: payload.patientId,
        encounter_id: payload.encounterId ?? null,
        recorded_by: payload.recordedBy ?? null,
        measured_at: payload.measuredAt ?? null,
        weight_kg: payload.weightKg ?? null,
        length_cm: payload.lengthCm ?? null,
        height_cm: payload.heightCm ?? null,
        head_circumference_cm: payload.headCircumferenceCm ?? null,
        muac_cm: payload.muacCm ?? null,
        measurement_mode: payload.measurementMode ?? null,
        notes: payload.notes ?? null,
      });
      return mapMeasurement(response.data);
    },
    onSuccess: (_, payload) => {
      queryClient.invalidateQueries({ queryKey: ["growth", { patientId: payload.patientId }] });
    },
  });
}
