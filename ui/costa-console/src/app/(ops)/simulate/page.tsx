"use client";

import { useState } from "react";
import {
  costaApi,
  type EstimateRequest,
  type EstimateLineInput,
  type BillLineKind,
  type CostMethodType,
  type ExemptionCategory,
} from "@/lib/costaApi";

const KINDS: BillLineKind[] = [
  "SERVICE", "PRODUCT", "FEE", "BEDDAY", "PROCEDURE", "THEATRE",
  "IMPLANT", "NURSING", "OVERHEAD", "TRANSPORT", "PER_DIEM",
  "COLD_CHAIN", "DELIVERY", "BUNDLE",
];

const COST_METHODS: CostMethodType[] = ["TARIFF", "MICRO", "ABC", "STANDARD", "STOCK_AVERAGE"];

const EXEMPTION_CATEGORIES: ExemptionCategory[] = [
  "CHILD", "ELDERLY", "MATERNITY", "CHRONIC", "DISABLED", "VETERAN",
  "HEALTHCARE_WORKER", "INDIGENT", "EMERGENCY", "GOVERNMENT", "OTHER",
];

const EMPTY_LINE: EstimateLineInput = {
  msikaCode: "",
  description: "",
  kind: "SERVICE",
  qty: 1,
};

export default function SimulatePage() {
  const [items, setItems] = useState<EstimateLineInput[]>([{ ...EMPTY_LINE }]);
  const [exemptionCategory, setExemptionCategory] = useState<ExemptionCategory | "">("");
  const [insurancePlanId, setInsurancePlanId] = useState<string>("");
  const [result, setResult] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const addLine = () => setItems([...items, { ...EMPTY_LINE }]);

  const removeLine = (index: number) => {
    setItems(items.filter((_, i) => i !== index));
  };

  const updateLine = (index: number, field: keyof EstimateLineInput, value: unknown) => {
    const updated = [...items];
    updated[index] = { ...updated[index], [field]: value };
    setItems(updated);
  };

  const handleSimulate = async () => {
    if (items.length === 0 || !items[0].msikaCode.trim()) {
      setError("At least one line item with a MSIKA code is required.");
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const request: EstimateRequest = {
        items: items.filter((i) => i.msikaCode.trim()),
        exemptionCategory:
          exemptionCategory === "" ? undefined : exemptionCategory,
        insurancePlanId: insurancePlanId ? parseInt(insurancePlanId, 10) : undefined,
      };

      const data = await costaApi.createEstimate(request);
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Simulation failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">Cost Simulation</h1>
        <p className="text-sm text-neutral-500 mt-1">
          Estimate costs for a set of line items with optional exemptions and insurance.
        </p>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-red-800">
          {error}
        </div>
      )}

      <div className="card p-6 mb-6">
        <h2 className="text-lg font-semibold text-neutral-900 mb-4">Line Items</h2>

        {items.map((item, index) => (
          <div key={index} className="grid grid-cols-6 gap-3 mb-3">
            <input
              type="text"
              value={item.msikaCode}
              onChange={(e) => updateLine(index, "msikaCode", e.target.value)}
              placeholder="MSIKA Code"
              className="input-field"
            />
            <input
              type="text"
              value={item.description || ""}
              onChange={(e) => updateLine(index, "description", e.target.value)}
              placeholder="Description"
              className="input-field"
            />
            <select
              value={item.kind}
              onChange={(e) => updateLine(index, "kind", e.target.value)}
              className="select-field"
            >
              {KINDS.map((k) => (
                <option key={k} value={k}>{k}</option>
              ))}
            </select>
            <input
              type="number"
              value={item.qty}
              onChange={(e) => updateLine(index, "qty", parseFloat(e.target.value) || 1)}
              min={0.01}
              step={0.01}
              className="input-field"
            />
            <select
              value={item.costMethod || ""}
              onChange={(e) => updateLine(index, "costMethod", e.target.value || undefined)}
              className="select-field"
            >
              <option value="">Auto</option>
              {COST_METHODS.map((m) => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>
            <div className="flex items-center">
              {items.length > 1 && (
                <button
                  onClick={() => removeLine(index)}
                  className="text-xs text-red-600 hover:text-red-800 font-medium"
                >
                  Remove
                </button>
              )}
            </div>
          </div>
        ))}

        <button onClick={addLine} className="text-xs text-amber-600 hover:text-warning-foreground font-medium mt-2">
          + Add Line
        </button>

        <div className="grid grid-cols-2 gap-4 mt-6">
          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">
              Exemption Category (optional)
            </label>
            <select
              value={exemptionCategory}
              onChange={(e) =>
                setExemptionCategory(
                  e.target.value === "" ? "" : (e.target.value as ExemptionCategory)
                )
              }
              className="select-field"
            >
              <option value="">None</option>
              {EXEMPTION_CATEGORIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-neutral-700 mb-1">
              Insurance Plan ID (optional)
            </label>
            <input
              type="text"
              value={insurancePlanId}
              onChange={(e) => setInsurancePlanId(e.target.value)}
              placeholder="e.g. 1"
              className="input-field"
            />
          </div>
        </div>

        <div className="mt-6">
          <button onClick={handleSimulate} disabled={loading} className="btn-primary">
            {loading ? "Computing..." : "Run Simulation"}
          </button>
        </div>
      </div>

      {/* Results */}
      {result !== null && (
        <div className="card p-6">
          <h2 className="text-lg font-semibold text-neutral-900 mb-4">Simulation Result</h2>
          <pre className="text-xs text-neutral-800 font-mono whitespace-pre-wrap bg-neutral-50 rounded-lg p-4 border border-neutral-200 max-h-96 overflow-y-auto">
            {JSON.stringify(result, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
}
