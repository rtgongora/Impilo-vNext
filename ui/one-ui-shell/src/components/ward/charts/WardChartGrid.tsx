"use client";

/**
 * WardChartGrid — Reusable table-based charting component for any WardChartType.
 *
 * Renders:
 * - An "Add Entry" form row with inputs matching column definitions
 * - Timestamped data rows with alert highlighting
 * - A summary footer row (totals / latest score depending on chart type)
 */

import { useEffect, useState } from "react";
import { AlertTriangle, Plus } from "lucide-react";
import type { ChartColumn, WardChartType } from "@/data/wardChartTypes";
import { useRecordWardChartEntry, useWardChartEntries } from "@/hooks/queries/useWardCharts";

/* ---------- sample data per chart (realistic ward values) ---------- */

type EntryRow = Record<string, string | number | boolean>;

const SAMPLE_DATA: Record<string, EntryRow[]> = {
  "fluid-balance": [
    { TIME: "06:00", INTAKE_ORAL: 200, INTAKE_IV: 500, INTAKE_NG: 0, OUTPUT_URINE: 150, OUTPUT_DRAIN: 0, OUTPUT_VOMIT: 0, OUTPUT_OTHER: 0, NOTES: "IVF NaCl 0.9%" },
    { TIME: "08:00", INTAKE_ORAL: 150, INTAKE_IV: 0, INTAKE_NG: 0, OUTPUT_URINE: 25, OUTPUT_DRAIN: 50, OUTPUT_VOMIT: 0, OUTPUT_OTHER: 0, NOTES: "Low urine output" },
    { TIME: "12:00", INTAKE_ORAL: 300, INTAKE_IV: 500, INTAKE_NG: 250, OUTPUT_URINE: 200, OUTPUT_DRAIN: 30, OUTPUT_VOMIT: 100, OUTPUT_OTHER: 0, NOTES: "" },
  ],
  "obs-chart": [
    { TIME: "06:00", RR: 18, SPO2: 97, O2_DEVICE: "Room air", TEMP: 36.8, SBP: 125, DBP: 78, HR: 72, AVPU: "Alert", NEWS_SCORE: 1, NOTES: "" },
    { TIME: "10:00", RR: 22, SPO2: 94, O2_DEVICE: "Nasal prongs", TEMP: 38.1, SBP: 105, DBP: 65, HR: 98, AVPU: "Alert", NEWS_SCORE: 4, NOTES: "Escalated to doctor" },
    { TIME: "14:00", RR: 26, SPO2: 91, O2_DEVICE: "Face mask", TEMP: 38.7, SBP: 88, DBP: 55, HR: 132, AVPU: "Voice", NEWS_SCORE: 9, NOTES: "MET call triggered" },
  ],
  "drug-chart": [
    { DRUG_NAME: "Paracetamol 1 g", DOSE: "1 g", ROUTE: "PO", TIME_DUE: "06:00", TIME_GIVEN: "06:05", GIVEN: "Given", ADMIN_BY: "Sr. Moyo", NOTES: "" },
    { DRUG_NAME: "Ceftriaxone 1 g", DOSE: "1 g", ROUTE: "IV", TIME_DUE: "08:00", TIME_GIVEN: "08:10", GIVEN: "Given", ADMIN_BY: "Sr. Moyo", NOTES: "Reconstituted in 10 mL WFI" },
    { DRUG_NAME: "Metformin 500 mg", DOSE: "500 mg", ROUTE: "PO", TIME_DUE: "08:00", TIME_GIVEN: "", GIVEN: "Refused", ADMIN_BY: "Sr. Moyo", NOTES: "Patient nauseous" },
  ],
  "fit-chart": [
    { TIME: "06:00", GCS_E: "4-Spontaneous", GCS_V: "5-Oriented", GCS_M: "6-Obeys", PUPIL_L: "Reactive", PUPIL_R: "Reactive", SEIZURE: false, SEIZURE_TYPE: "None", LIMBS: "Normal", NOTES: "" },
    { TIME: "07:00", GCS_E: "3-Voice", GCS_V: "4-Confused", GCS_M: "5-Localises", PUPIL_L: "Sluggish", PUPIL_R: "Reactive", SEIZURE: true, SEIZURE_TYPE: "Tonic-clonic", LIMBS: "Weakness L", NOTES: "Midazolam 5 mg IV given" },
  ],
  "turn-chart": [
    { TIME: "06:00", POSITION: "Left lateral", SKIN_STATUS: "Intact", PRESSURE_AREAS: "Sacrum, heels", BRADEN_SCORE: 14, TURNED_BY: "Sr. Dlamini", NOTES: "" },
    { TIME: "08:00", POSITION: "Supine", SKIN_STATUS: "Reddened", PRESSURE_AREAS: "Sacrum", BRADEN_SCORE: 11, TURNED_BY: "Sr. Dlamini", NOTES: "Barrier cream applied" },
  ],
  "diet-chart": [
    { MEAL: "Breakfast", DIET_TYPE: "Diabetic", AMOUNT_EATEN: "3/4", SUPPLEMENT: "", FLUIDS_ML: 200, ASSISTED: false, NOTES: "" },
    { MEAL: "Lunch", DIET_TYPE: "Diabetic", AMOUNT_EATEN: "1/2", SUPPLEMENT: "Ensure 200 mL", FLUIDS_ML: 150, ASSISTED: true, NOTES: "Poor appetite" },
  ],
};

function isBreaching(col: ChartColumn, value: unknown): boolean {
  if (typeof value !== "number") return false;
  if (col.alertLow !== undefined && value < col.alertLow) return true;
  if (col.alertHigh !== undefined && value > col.alertHigh) return true;
  return false;
}

function buildSummary(chart: WardChartType, rows: EntryRow[]): string {
  if (chart.id === "fluid-balance") {
    let totalIn = 0, totalOut = 0;
    for (const r of rows) {
      totalIn += (Number(r.INTAKE_ORAL) || 0) + (Number(r.INTAKE_IV) || 0) + (Number(r.INTAKE_NG) || 0);
      totalOut += (Number(r.OUTPUT_URINE) || 0) + (Number(r.OUTPUT_DRAIN) || 0) + (Number(r.OUTPUT_VOMIT) || 0) + (Number(r.OUTPUT_OTHER) || 0);
    }
    return `Total intake: ${totalIn} mL | Total output: ${totalOut} mL | Balance: ${totalIn - totalOut >= 0 ? "+" : ""}${totalIn - totalOut} mL`;
  }
  if (chart.id === "obs-chart" && rows.length > 0) {
    const last = rows[rows.length - 1];
    return `Latest NEWS score: ${last.NEWS_SCORE ?? "N/A"}`;
  }
  if (chart.id === "drug-chart") {
    const given = rows.filter((r) => r.GIVEN === "Given").length;
    return `${given}/${rows.length} medications administered`;
  }
  if (chart.id === "fit-chart" && rows.length > 0) {
    const last = rows[rows.length - 1];
    const gcsE = Number(String(last.GCS_E).charAt(0)) || 0;
    const gcsV = Number(String(last.GCS_V).charAt(0)) || 0;
    const gcsM = Number(String(last.GCS_M).charAt(0)) || 0;
    return `Latest GCS: ${gcsE + gcsV + gcsM}/15 (E${gcsE} V${gcsV} M${gcsM})`;
  }
  if (chart.id === "turn-chart" && rows.length > 0) {
    const last = rows[rows.length - 1];
    return `Latest Braden: ${last.BRADEN_SCORE ?? "N/A"} | Position: ${last.POSITION}`;
  }
  if (chart.id === "diet-chart") {
    const totalFluids = rows.reduce((s, r) => s + (Number(r.FLUIDS_ML) || 0), 0);
    return `Meals recorded: ${rows.length} | Total fluids: ${totalFluids} mL`;
  }
  return `${rows.length} entries recorded`;
}

function emptyRow(chart: WardChartType): EntryRow {
  const row: EntryRow = {};
  for (const col of chart.columns) {
    if (col.inputType === "number") row[col.code] = 0;
    else if (col.inputType === "boolean") row[col.code] = false;
    else row[col.code] = "";
  }
  return row;
}

interface WardChartGridProps {
  chart: WardChartType;
  patientId?: string;
  persistToApi?: boolean;
}

export function WardChartGrid({ chart, patientId, persistToApi = false }: WardChartGridProps) {
  const { data: apiEntries = [] } = useWardChartEntries(patientId ?? "", chart.id);
  const recordMutation = useRecordWardChartEntry(patientId ?? "", chart.id);
  const [rows, setRows] = useState<EntryRow[]>(persistToApi ? [] : (SAMPLE_DATA[chart.id] ?? []));
  const [newEntry, setNewEntry] = useState<EntryRow>(() => emptyRow(chart));
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    if (!persistToApi) return;
    const mapped = (apiEntries as Array<Record<string, unknown>>).map((e) => {
      const params = (e.parameters ?? {}) as EntryRow;
      return params;
    });
    setRows(mapped);
  }, [apiEntries, persistToApi]);

  function handleFieldChange(code: string, value: string | number | boolean) {
    setNewEntry((prev) => ({ ...prev, [code]: value }));
  }

  function addEntry() {
    const entry = { ...newEntry };
    if (persistToApi && patientId) {
      recordMutation.mutate(entry, {
        onSuccess: () => {
          setNewEntry(emptyRow(chart));
          setShowForm(false);
        },
      });
      return;
    }
    setRows((prev) => [...prev, entry]);
    setNewEntry(emptyRow(chart));
    setShowForm(false);
  }

  const summary = buildSummary(chart, rows);

  return (
    <div className="space-y-4">
      {/* Add Entry toggle */}
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-slate-900">{chart.name}</h3>
        <button onClick={() => setShowForm((v) => !v)}
          className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800 transition-colors">
          <Plus className="h-4 w-4" /> {showForm ? "Cancel" : "Add Entry"}
        </button>
      </div>

      {/* Add Entry form */}
      {showForm && (
        <div className="rounded-xl border border-sky-200 bg-sky-50 p-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {chart.columns.map((col) => (
              <label key={col.code} className="block text-sm">
                <span className="font-medium text-slate-700">{col.name}{col.unit ? ` (${col.unit})` : ""}</span>
                {col.inputType === "number" && (
                  <input type="number" value={newEntry[col.code] as number} onChange={(e) => handleFieldChange(col.code, Number(e.target.value))}
                    className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                )}
                {col.inputType === "text" && (
                  <input type="text" value={newEntry[col.code] as string} onChange={(e) => handleFieldChange(col.code, e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                )}
                {col.inputType === "time" && (
                  <input type="time" value={newEntry[col.code] as string} onChange={(e) => handleFieldChange(col.code, e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400" />
                )}
                {col.inputType === "select" && (
                  <select value={newEntry[col.code] as string} onChange={(e) => handleFieldChange(col.code, e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm focus:border-sky-400 focus:outline-none focus:ring-1 focus:ring-sky-400">
                    <option value="">Select...</option>
                    {col.options?.map((opt) => <option key={opt} value={opt}>{opt}</option>)}
                  </select>
                )}
                {col.inputType === "boolean" && (
                  <div className="mt-2 flex items-center gap-2">
                    <input type="checkbox" checked={!!newEntry[col.code]} onChange={(e) => handleFieldChange(col.code, e.target.checked)}
                      className="h-4 w-4 rounded border-slate-300 text-sky-600 focus:ring-sky-400" />
                    <span className="text-xs text-slate-600">{newEntry[col.code] ? "Yes" : "No"}</span>
                  </div>
                )}
              </label>
            ))}
          </div>
          <div className="mt-4 flex justify-end">
            <button onClick={addEntry}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 transition-colors">
              Save Entry
            </button>
          </div>
        </div>
      )}

      {/* Data table */}
      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
        {rows.length === 0 ? (
          <div className="p-8 text-center text-sm text-slate-500">No entries recorded yet. Click &quot;Add Entry&quot; to begin charting.</div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                {chart.columns.map((col) => (
                  <th key={col.code} className="whitespace-nowrap px-3 py-2.5 font-medium">
                    {col.name}{col.unit ? <span className="ml-1 text-xs text-slate-400">({col.unit})</span> : null}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr key={idx} className="border-t border-slate-100">
                  {chart.columns.map((col) => {
                    const val = row[col.code];
                    const breaching = isBreaching(col, val);
                    return (
                      <td key={col.code} className={`whitespace-nowrap px-3 py-2 ${breaching ? "bg-red-50 font-semibold text-red-700" : "text-slate-700"}`}>
                        {breaching && <AlertTriangle className="mr-1 inline h-3.5 w-3.5 text-red-500" />}
                        {typeof val === "boolean" ? (val ? "Yes" : "No") : String(val ?? "")}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Summary row */}
      {rows.length > 0 && (
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700">
          {summary}
        </div>
      )}
    </div>
  );
}
