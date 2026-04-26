/**
 * Ward Chart Types — standardised inpatient charting templates.
 *
 * Each chart type defines:
 * - Columns/parameters to record
 * - Frequency of recording
 * - Who records (nurse, doctor, dietitian, etc.)
 * - Alert thresholds
 */

export interface ChartColumn {
  code: string;
  name: string;
  unit?: string;
  inputType: "number" | "text" | "select" | "boolean" | "time";
  options?: string[];
  alertLow?: number;
  alertHigh?: number;
}

export interface WardChartType {
  id: string;
  name: string;
  shortName: string;
  description: string;
  frequency: string;
  recordedBy: string;
  columns: ChartColumn[];
}

export const WARD_CHARTS: WardChartType[] = [
  {
    id: "fluid-balance",
    name: "Fluid Balance Chart",
    shortName: "Fluids",
    description: "Track all fluid intake and output for accurate fluid balance calculation",
    frequency: "Hourly / per event",
    recordedBy: "Nursing staff",
    columns: [
      { code: "TIME", name: "Time", inputType: "time" },
      { code: "INTAKE_ORAL", name: "Oral Intake", unit: "mL", inputType: "number" },
      { code: "INTAKE_IV", name: "IV Fluids", unit: "mL", inputType: "number" },
      { code: "INTAKE_NG", name: "NG/PEG Feed", unit: "mL", inputType: "number" },
      { code: "OUTPUT_URINE", name: "Urine Output", unit: "mL", inputType: "number", alertLow: 30 },
      { code: "OUTPUT_DRAIN", name: "Drain Output", unit: "mL", inputType: "number" },
      { code: "OUTPUT_VOMIT", name: "Vomiting", unit: "mL", inputType: "number" },
      { code: "OUTPUT_OTHER", name: "Other Loss", unit: "mL", inputType: "number" },
      { code: "NOTES", name: "Notes", inputType: "text" },
    ],
  },
  {
    id: "drug-chart",
    name: "Drug Administration Chart",
    shortName: "Drugs",
    description: "Record medication administration times, doses, and route",
    frequency: "Per medication schedule",
    recordedBy: "Nursing staff",
    columns: [
      { code: "DRUG_NAME", name: "Drug", inputType: "text" },
      { code: "DOSE", name: "Dose", inputType: "text" },
      { code: "ROUTE", name: "Route", inputType: "select", options: ["PO", "IV", "IM", "SC", "SL", "PR", "INH", "TOP", "NG"] },
      { code: "TIME_DUE", name: "Time Due", inputType: "time" },
      { code: "TIME_GIVEN", name: "Time Given", inputType: "time" },
      { code: "GIVEN", name: "Given?", inputType: "select", options: ["Given", "Refused", "Held", "Not Available", "NPO"] },
      { code: "ADMIN_BY", name: "Administered By", inputType: "text" },
      { code: "NOTES", name: "Notes", inputType: "text" },
    ],
  },
  {
    id: "fit-chart",
    name: "Neurological Observations (Fit Chart)",
    shortName: "Neuro/Fit",
    description: "Track GCS, pupil reactions, seizure activity, and limb movements",
    frequency: "15min–4hourly as prescribed",
    recordedBy: "Nursing staff",
    columns: [
      { code: "TIME", name: "Time", inputType: "time" },
      { code: "GCS_E", name: "Eye Opening", inputType: "select", options: ["4-Spontaneous", "3-Voice", "2-Pain", "1-None"] },
      { code: "GCS_V", name: "Verbal", inputType: "select", options: ["5-Oriented", "4-Confused", "3-Words", "2-Sounds", "1-None"] },
      { code: "GCS_M", name: "Motor", inputType: "select", options: ["6-Obeys", "5-Localises", "4-Flexion", "3-Abnormal", "2-Extension", "1-None"] },
      { code: "PUPIL_L", name: "Left Pupil", inputType: "select", options: ["Reactive", "Sluggish", "Fixed", "Dilated"] },
      { code: "PUPIL_R", name: "Right Pupil", inputType: "select", options: ["Reactive", "Sluggish", "Fixed", "Dilated"] },
      { code: "SEIZURE", name: "Seizure Activity", inputType: "boolean" },
      { code: "SEIZURE_TYPE", name: "Seizure Type", inputType: "select", options: ["Tonic-clonic", "Focal", "Absence", "Myoclonic", "None"] },
      { code: "LIMBS", name: "Limb Movement", inputType: "select", options: ["Normal", "Weakness L", "Weakness R", "Paralysis L", "Paralysis R"] },
      { code: "NOTES", name: "Notes", inputType: "text" },
    ],
  },
  {
    id: "turn-chart",
    name: "Turning / Pressure Area Chart",
    shortName: "Turns",
    description: "Record patient repositioning for pressure injury prevention",
    frequency: "Every 2 hours",
    recordedBy: "Nursing staff",
    columns: [
      { code: "TIME", name: "Time", inputType: "time" },
      { code: "POSITION", name: "Position", inputType: "select", options: ["Left lateral", "Right lateral", "Supine", "Prone", "Semi-Fowler", "Chair"] },
      { code: "SKIN_STATUS", name: "Skin Status", inputType: "select", options: ["Intact", "Reddened", "Blanching", "Non-blanching", "Broken", "Dressed"] },
      { code: "PRESSURE_AREAS", name: "Areas Checked", inputType: "text" },
      { code: "BRADEN_SCORE", name: "Braden Score", inputType: "number", alertLow: 12 },
      { code: "TURNED_BY", name: "Turned By", inputType: "text" },
      { code: "NOTES", name: "Notes", inputType: "text" },
    ],
  },
  {
    id: "diet-chart",
    name: "Diet & Nutrition Chart",
    shortName: "Diet",
    description: "Track dietary intake, special diets, and nutritional supplements",
    frequency: "Per meal",
    recordedBy: "Nursing/Dietitian",
    columns: [
      { code: "MEAL", name: "Meal", inputType: "select", options: ["Breakfast", "Mid-morning", "Lunch", "Afternoon", "Dinner", "Evening", "Supplement"] },
      { code: "DIET_TYPE", name: "Diet Type", inputType: "select", options: ["Normal", "Diabetic", "Low sodium", "Renal", "Soft", "Liquid", "NBM", "NG feed", "TPN", "Halal", "Kosher", "Vegetarian"] },
      { code: "AMOUNT_EATEN", name: "Amount Eaten", inputType: "select", options: ["All", "3/4", "1/2", "1/4", "Refused", "NPO"] },
      { code: "SUPPLEMENT", name: "Supplement", inputType: "text" },
      { code: "FLUIDS_ML", name: "Fluids Taken", unit: "mL", inputType: "number" },
      { code: "ASSISTED", name: "Assisted?", inputType: "boolean" },
      { code: "NOTES", name: "Notes", inputType: "text" },
    ],
  },
  {
    id: "obs-chart",
    name: "Observation Chart (NEWS/MEWS)",
    shortName: "Obs",
    description: "Vital signs with early warning score calculation",
    frequency: "4-hourly (escalate per NEWS score)",
    recordedBy: "Nursing staff",
    columns: [
      { code: "TIME", name: "Time", inputType: "time" },
      { code: "RR", name: "Respiratory Rate", unit: "/min", inputType: "number", alertLow: 8, alertHigh: 25 },
      { code: "SPO2", name: "SpO₂", unit: "%", inputType: "number", alertLow: 92 },
      { code: "O2_DEVICE", name: "O₂ Device", inputType: "select", options: ["Room air", "Nasal prongs", "Face mask", "Venturi", "CPAP", "BiPAP", "Ventilator"] },
      { code: "TEMP", name: "Temperature", unit: "°C", inputType: "number", alertLow: 35.0, alertHigh: 38.5 },
      { code: "SBP", name: "Systolic BP", unit: "mmHg", inputType: "number", alertLow: 90, alertHigh: 180 },
      { code: "DBP", name: "Diastolic BP", unit: "mmHg", inputType: "number" },
      { code: "HR", name: "Heart Rate", unit: "bpm", inputType: "number", alertLow: 50, alertHigh: 130 },
      { code: "AVPU", name: "Consciousness", inputType: "select", options: ["Alert", "Voice", "Pain", "Unresponsive"] },
      { code: "NEWS_SCORE", name: "NEWS Score", inputType: "number", alertHigh: 5 },
      { code: "NOTES", name: "Notes", inputType: "text" },
    ],
  },
];

export function getChartById(id: string): WardChartType | undefined {
  return WARD_CHARTS.find((c) => c.id === id);
}
