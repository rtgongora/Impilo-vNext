"use client";

import { useState, useMemo } from "react";
import { Activity, Save, Thermometer, Heart, Wind, Droplets, Loader2, AlertTriangle } from "lucide-react";
import { useEncounterVitals, useRecordVitals, type VitalsResource } from "@/hooks/queries/useVitals";

interface VitalsFormData {
  temperature: string;
  pulse_rate: string;
  respiratory_rate: string;
  blood_pressure_systolic: string;
  blood_pressure_diastolic: string;
  oxygen_saturation: string;
  pain_score: string;
  gcs: string;
  weight: string;
  height: string;
  blood_glucose: string;
  notes: string;
}

interface VitalSign {
  id: string;
  recorded_at: string;
  temperature: number | null;
  pulse_rate: number | null;
  respiratory_rate: number | null;
  blood_pressure_systolic: number | null;
  blood_pressure_diastolic: number | null;
  oxygen_saturation: number | null;
  pain_score: number | null;
  gcs: number | null;
  weight: number | null;
  height: number | null;
  blood_glucose: number | null;
}

/* ---------- mapper ---------- */
function mapVitalsResource(resource: VitalsResource): VitalSign {
  const a = resource.attributes;
  return {
    id: resource.id,
    recorded_at: a.recordedAt,
    temperature: a.temperature,
    pulse_rate: a.heartRate,
    respiratory_rate: a.respiratoryRate,
    blood_pressure_systolic: a.systolic,
    blood_pressure_diastolic: a.diastolic,
    oxygen_saturation: a.oxygenSaturation,
    pain_score: a.painScore,
    gcs: null,
    weight: a.weight,
    height: a.height,
    blood_glucose: null,
  };
}

const INITIAL_FORM: VitalsFormData = {
  temperature: "",
  pulse_rate: "",
  respiratory_rate: "",
  blood_pressure_systolic: "",
  blood_pressure_diastolic: "",
  oxygen_saturation: "",
  pain_score: "",
  gcs: "",
  weight: "",
  height: "",
  blood_glucose: "",
  notes: "",
};

interface VitalsRecorderProps {
  encounterId: string;
  patientId?: string;
  existingVitals?: VitalSign[];
  onVitalsSaved?: () => void;
}

export function VitalsRecorder({
  encounterId,
  patientId,
  existingVitals,
  onVitalsSaved,
}: VitalsRecorderProps) {
  const { data, isLoading: vitalsLoading, error: vitalsError } = useEncounterVitals(encounterId);
  const recordVitalsMutation = useRecordVitals();
  const [formData, setFormData] = useState<VitalsFormData>({ ...INITIAL_FORM });

  const vitalsHistory: VitalSign[] = useMemo(() => {
    if (existingVitals) return existingVitals;
    return (data?.data ?? []).map(mapVitalsResource);
  }, [existingVitals, data]);

  const handleChange = (field: keyof VitalsFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  // --- Auto-calculated fields ---
  const bmi = useMemo(() => {
    const w = parseFloat(formData.weight);
    const h = parseFloat(formData.height);
    if (!w || !h || h === 0) return null;
    const heightM = h / 100;
    return (w / (heightM * heightM)).toFixed(1);
  }, [formData.weight, formData.height]);

  const map = useMemo(() => {
    const sys = parseFloat(formData.blood_pressure_systolic);
    const dia = parseFloat(formData.blood_pressure_diastolic);
    if (!sys || !dia) return null;
    return ((dia + (sys - dia) / 3)).toFixed(0);
  }, [formData.blood_pressure_systolic, formData.blood_pressure_diastolic]);

  // --- Severity ranges ---
  const getVitalStatus = (
    type: string,
    value: number | null
  ): "normal" | "abnormal" | "critical" | null => {
    if (value === null || value === undefined) return null;

    const ranges: Record<
      string,
      { low: number; high: number; critical_low?: number; critical_high?: number }
    > = {
      temperature: { low: 36, high: 37.5, critical_low: 35, critical_high: 39 },
      pulse_rate: { low: 60, high: 100, critical_low: 40, critical_high: 150 },
      respiratory_rate: { low: 12, high: 20, critical_low: 8, critical_high: 30 },
      oxygen_saturation: { low: 95, high: 100, critical_low: 90 },
      blood_pressure_systolic: { low: 90, high: 140, critical_low: 80, critical_high: 180 },
      blood_glucose: { low: 4.0, high: 7.8, critical_low: 3.0, critical_high: 11.1 },
      pain_score: { low: 0, high: 3, critical_high: 7 },
      gcs: { low: 15, high: 15, critical_low: 8 },
    };

    const range = ranges[type];
    if (!range) return "normal";

    if (range.critical_low !== undefined && value < range.critical_low) return "critical";
    if (range.critical_high !== undefined && value > range.critical_high) return "critical";
    if (type === "gcs" && value < range.low) return "abnormal";
    if (value < range.low || value > range.high) return "abnormal";
    return "normal";
  };

  const statusColors: Record<string, string> = {
    normal: "text-green-600",
    abnormal: "text-yellow-600",
    critical: "text-red-600 font-bold",
  };

  const saving = recordVitalsMutation.isPending;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    recordVitalsMutation.mutate(
      {
        patientId: patientId ?? "",
        encounterId,
        systolic: formData.blood_pressure_systolic ? parseInt(formData.blood_pressure_systolic) : null,
        diastolic: formData.blood_pressure_diastolic ? parseInt(formData.blood_pressure_diastolic) : null,
        heartRate: formData.pulse_rate ? parseInt(formData.pulse_rate) : null,
        temperature: formData.temperature ? parseFloat(formData.temperature) : null,
        respiratoryRate: formData.respiratory_rate ? parseInt(formData.respiratory_rate) : null,
        oxygenSaturation: formData.oxygen_saturation ? parseInt(formData.oxygen_saturation) : null,
        weight: formData.weight ? parseFloat(formData.weight) : null,
        height: formData.height ? parseFloat(formData.height) : null,
        painScore: formData.pain_score ? parseInt(formData.pain_score) : null,
        notes: formData.notes || null,
      },
      {
        onSuccess: () => {
          setFormData({ ...INITIAL_FORM });
          onVitalsSaved?.();
        },
      }
    );
  };

  const formatDateTime = (iso: string): string => {
    const d = new Date(iso);
    const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
    const month = months[d.getMonth()];
    const day = d.getDate();
    const year = d.getFullYear();
    const hours = d.getHours().toString().padStart(2, "0");
    const mins = d.getMinutes().toString().padStart(2, "0");
    return `${month} ${day}, ${year} ${hours}:${mins}`;
  };

  const computeMAP = (sys: number | null, dia: number | null): string | null => {
    if (!sys || !dia) return null;
    return (dia + (sys - dia) / 3).toFixed(0);
  };

  const computeBMI = (w: number | null, h: number | null): string | null => {
    if (!w || !h || h === 0) return null;
    const hm = h / 100;
    return (w / (hm * hm)).toFixed(1);
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {/* Record New Vitals */}
      <div className="rounded-lg border border-border bg-card shadow-sm">
        <div className="px-4 py-3 border-b border-border">
          <h3 className="text-lg font-semibold flex items-center gap-2">
            <Activity className="h-5 w-5" />
            Record Vital Signs
          </h3>
        </div>
        <div className="p-4">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              {/* Temperature */}
              <div>
                <label
                  htmlFor="temperature"
                  className="block text-sm font-medium text-foreground mb-1 flex items-center gap-1"
                >
                  <Thermometer className="h-3 w-3" />
                  Temperature (&deg;C)
                </label>
                <input
                  id="temperature"
                  type="number"
                  step="0.1"
                  value={formData.temperature}
                  onChange={(e) => handleChange("temperature", e.target.value)}
                  placeholder="36.5"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Pulse Rate */}
              <div>
                <label
                  htmlFor="pulse_rate"
                  className="block text-sm font-medium text-foreground mb-1 flex items-center gap-1"
                >
                  <Heart className="h-3 w-3" />
                  Pulse Rate (bpm)
                </label>
                <input
                  id="pulse_rate"
                  type="number"
                  value={formData.pulse_rate}
                  onChange={(e) => handleChange("pulse_rate", e.target.value)}
                  placeholder="72"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Respiratory Rate */}
              <div>
                <label
                  htmlFor="respiratory_rate"
                  className="block text-sm font-medium text-foreground mb-1 flex items-center gap-1"
                >
                  <Wind className="h-3 w-3" />
                  Respiratory Rate
                </label>
                <input
                  id="respiratory_rate"
                  type="number"
                  value={formData.respiratory_rate}
                  onChange={(e) => handleChange("respiratory_rate", e.target.value)}
                  placeholder="16"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* SpO2 */}
              <div>
                <label
                  htmlFor="oxygen_saturation"
                  className="block text-sm font-medium text-foreground mb-1 flex items-center gap-1"
                >
                  <Droplets className="h-3 w-3" />
                  SpO2 (%)
                </label>
                <input
                  id="oxygen_saturation"
                  type="number"
                  value={formData.oxygen_saturation}
                  onChange={(e) => handleChange("oxygen_saturation", e.target.value)}
                  placeholder="98"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Blood Pressure */}
              <div className="col-span-2">
                <label className="block text-sm font-medium text-foreground mb-1">
                  Blood Pressure (mmHg)
                </label>
                <div className="flex gap-2 items-center">
                  <input
                    type="number"
                    value={formData.blood_pressure_systolic}
                    onChange={(e) => handleChange("blood_pressure_systolic", e.target.value)}
                    placeholder="120"
                    className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                  />
                  <span className="text-muted-foreground font-medium">/</span>
                  <input
                    type="number"
                    value={formData.blood_pressure_diastolic}
                    onChange={(e) => handleChange("blood_pressure_diastolic", e.target.value)}
                    placeholder="80"
                    className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                  />
                </div>
                {map && (
                  <p className="text-xs text-muted-foreground mt-1">
                    MAP: <span className="font-medium">{map} mmHg</span>
                  </p>
                )}
              </div>

              {/* Pain Score */}
              <div>
                <label htmlFor="pain_score" className="block text-sm font-medium text-foreground mb-1">
                  Pain Score (0-10)
                </label>
                <input
                  id="pain_score"
                  type="number"
                  min="0"
                  max="10"
                  value={formData.pain_score}
                  onChange={(e) => handleChange("pain_score", e.target.value)}
                  placeholder="0"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* GCS */}
              <div>
                <label htmlFor="gcs" className="block text-sm font-medium text-foreground mb-1">
                  GCS (3-15)
                </label>
                <input
                  id="gcs"
                  type="number"
                  min="3"
                  max="15"
                  value={formData.gcs}
                  onChange={(e) => handleChange("gcs", e.target.value)}
                  placeholder="15"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Blood Glucose */}
              <div>
                <label htmlFor="blood_glucose" className="block text-sm font-medium text-foreground mb-1">
                  Blood Glucose (mmol/L)
                </label>
                <input
                  id="blood_glucose"
                  type="number"
                  step="0.1"
                  value={formData.blood_glucose}
                  onChange={(e) => handleChange("blood_glucose", e.target.value)}
                  placeholder="5.5"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Weight */}
              <div>
                <label htmlFor="weight" className="block text-sm font-medium text-foreground mb-1">
                  Weight (kg)
                </label>
                <input
                  id="weight"
                  type="number"
                  step="0.1"
                  value={formData.weight}
                  onChange={(e) => handleChange("weight", e.target.value)}
                  placeholder="70"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Height */}
              <div>
                <label htmlFor="height" className="block text-sm font-medium text-foreground mb-1">
                  Height (cm)
                </label>
                <input
                  id="height"
                  type="number"
                  step="0.1"
                  value={formData.height}
                  onChange={(e) => handleChange("height", e.target.value)}
                  placeholder="170"
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                />
              </div>

              {/* Auto-calculated BMI */}
              {bmi && (
                <div className="col-span-2">
                  <p className="text-xs text-muted-foreground">
                    BMI: <span className="font-medium">{bmi} kg/m&sup2;</span>
                    {parseFloat(bmi) < 18.5 && (
                      <span className="ml-2 text-yellow-600">(Underweight)</span>
                    )}
                    {parseFloat(bmi) >= 18.5 && parseFloat(bmi) < 25 && (
                      <span className="ml-2 text-green-600">(Normal)</span>
                    )}
                    {parseFloat(bmi) >= 25 && parseFloat(bmi) < 30 && (
                      <span className="ml-2 text-yellow-600">(Overweight)</span>
                    )}
                    {parseFloat(bmi) >= 30 && (
                      <span className="ml-2 text-red-600">(Obese)</span>
                    )}
                  </p>
                </div>
              )}

              {/* Notes */}
              <div className="col-span-2">
                <label htmlFor="notes" className="block text-sm font-medium text-foreground mb-1">
                  Notes
                </label>
                <textarea
                  id="notes"
                  value={formData.notes}
                  onChange={(e) => handleChange("notes", e.target.value)}
                  placeholder="Additional observations..."
                  rows={2}
                  className="w-full rounded-md border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400 resize-none"
                />
              </div>
            </div>

            <div className="flex justify-end pt-2">
              <button
                type="submit"
                disabled={saving}
                className={`inline-flex items-center px-4 py-2 rounded-md text-sm font-medium text-white ${
                  saving
                    ? "bg-impilo-400 cursor-not-allowed"
                    : "bg-primary hover:bg-primary-hover"
                } focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 transition-colors`}
              >
                {saving ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Recording...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4 mr-2" />
                    Record Vitals
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* Vitals History */}
      <div className="rounded-lg border border-border bg-card shadow-sm">
        <div className="px-4 py-3 border-b border-border">
          <h3 className="text-lg font-semibold">Vitals History</h3>
        </div>
        <div className="overflow-y-auto max-h-[500px]">
          <div className="p-4 space-y-3">
            {vitalsLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-primary" />
                <span className="ml-2 text-sm text-muted-foreground">Loading vitals...</span>
              </div>
            ) : vitalsError ? (
              <div className="flex items-center justify-center py-8 text-red-600">
                <AlertTriangle className="h-4 w-4 mr-2" />
                <span className="text-sm">Failed to load vitals history.</span>
              </div>
            ) : vitalsHistory.length === 0 ? (
              <p className="text-sm text-muted-foreground text-center py-4">
                No vital signs recorded for this encounter
              </p>
            ) : (
              vitalsHistory.map((vital) => {
                const bpStatus = getVitalStatus(
                  "blood_pressure_systolic",
                  vital.blood_pressure_systolic
                );
                const hrStatus = getVitalStatus("pulse_rate", vital.pulse_rate);
                const rrStatus = getVitalStatus("respiratory_rate", vital.respiratory_rate);
                const tempStatus = getVitalStatus("temperature", vital.temperature);
                const spo2Status = getVitalStatus("oxygen_saturation", vital.oxygen_saturation);
                const glucoseStatus = getVitalStatus("blood_glucose", vital.blood_glucose);
                const gcsStatus = getVitalStatus("gcs", vital.gcs);
                const painStatus = getVitalStatus("pain_score", vital.pain_score);

                // Determine if any reading is critical for the card border
                const hasCritical = [bpStatus, hrStatus, rrStatus, tempStatus, spo2Status, glucoseStatus, gcsStatus, painStatus].includes("critical");
                const hasAbnormal = [bpStatus, hrStatus, rrStatus, tempStatus, spo2Status, glucoseStatus, gcsStatus, painStatus].includes("abnormal");

                const historyMAP = computeMAP(vital.blood_pressure_systolic, vital.blood_pressure_diastolic);
                const historyBMI = computeBMI(vital.weight, vital.height);

                return (
                  <div
                    key={vital.id}
                    className={`p-3 rounded-lg border ${
                      hasCritical
                        ? "border-red-300 bg-danger-soft"
                        : hasAbnormal
                        ? "border-yellow-300 bg-yellow-50"
                        : "border-border bg-card"
                    }`}
                  >
                    <div className="text-xs text-muted-foreground mb-2">
                      {formatDateTime(vital.recorded_at)}
                    </div>
                    <div className="grid grid-cols-3 gap-2 text-sm">
                      {vital.temperature !== null && (
                        <div className={statusColors[tempStatus || "normal"]}>
                          <span className="text-muted-foreground">Temp:</span> {vital.temperature}&deg;C
                        </div>
                      )}
                      {vital.pulse_rate !== null && (
                        <div className={statusColors[hrStatus || "normal"]}>
                          <span className="text-muted-foreground">HR:</span> {vital.pulse_rate}
                        </div>
                      )}
                      {vital.respiratory_rate !== null && (
                        <div className={statusColors[rrStatus || "normal"]}>
                          <span className="text-muted-foreground">RR:</span> {vital.respiratory_rate}
                        </div>
                      )}
                      {vital.blood_pressure_systolic !== null &&
                        vital.blood_pressure_diastolic !== null && (
                          <div className={statusColors[bpStatus || "normal"]}>
                            <span className="text-muted-foreground">BP:</span>{" "}
                            {vital.blood_pressure_systolic}/{vital.blood_pressure_diastolic}
                            {historyMAP && (
                              <span className="text-muted-foreground text-xs ml-1">(MAP {historyMAP})</span>
                            )}
                          </div>
                        )}
                      {vital.oxygen_saturation !== null && (
                        <div className={statusColors[spo2Status || "normal"]}>
                          <span className="text-muted-foreground">SpO2:</span> {vital.oxygen_saturation}%
                        </div>
                      )}
                      {vital.blood_glucose !== null && (
                        <div className={statusColors[glucoseStatus || "normal"]}>
                          <span className="text-muted-foreground">Gluc:</span> {vital.blood_glucose}
                        </div>
                      )}
                      {vital.pain_score !== null && (
                        <div className={statusColors[painStatus || "normal"]}>
                          <span className="text-muted-foreground">Pain:</span> {vital.pain_score}/10
                        </div>
                      )}
                      {vital.gcs !== null && (
                        <div className={statusColors[gcsStatus || "normal"]}>
                          <span className="text-muted-foreground">GCS:</span> {vital.gcs}/15
                        </div>
                      )}
                      {historyBMI && (
                        <div className="text-muted-foreground">
                          <span className="text-muted-foreground">BMI:</span> {historyBMI}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
