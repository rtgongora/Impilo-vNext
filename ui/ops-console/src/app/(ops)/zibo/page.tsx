"use client";

import React, { useState } from "react";
import Link from "next/link";
import { apiClient } from "@/lib/apiClient";

interface CodeSystem {
  id: string;
  systemUri: string;
  name: string;
  version: string;
  status: "ACTIVE" | "RETIRED" | "DRAFT";
  conceptCount: number;
  publishedAt: string;
}

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700",
  DRAFT: "bg-amber-50 text-amber-700",
  RETIRED: "bg-neutral-100 text-neutral-500",
};

export default function ZiboDashboardPage() {
  const [codeSystems, setCodeSystems] = useState<CodeSystem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [validateCode, setValidateCode] = useState("");
  const [validateSystem, setValidateSystem] = useState("");
  const [validationResult, setValidationResult] = useState<{
    valid: boolean;
    display?: string;
  } | null>(null);

  const loadCodeSystems = async () => {
    setLoading(true);
    try {
      const data = await apiClient.get<CodeSystem[]>(
        "/internal/v1/code-systems/snapshot?limit=50"
      );
      setCodeSystems(Array.isArray(data) ? data : []);
    } catch {
      setCodeSystems([]);
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  };

  const handleValidate = async () => {
    if (!validateCode.trim() || !validateSystem.trim()) return;
    try {
      const result = await apiClient.get<{ valid: boolean; display?: string }>(
        `/internal/v1/code-systems/${encodeURIComponent(validateSystem)}/validate?code=${encodeURIComponent(validateCode)}`
      );
      setValidationResult(result);
    } catch {
      setValidationResult({ valid: false });
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold text-neutral-900">
          ZIBO — Terminology Service
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          WHO-compliant terminology: SNOMED CT, ICD-10, LOINC, and custom code systems with validation
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <Link href="/zibo/code-systems" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Terminology</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Code Systems</p>
            <p className="text-xs text-neutral-500 mt-1">Manage SNOMED CT, ICD-10, LOINC</p>
          </div>
        </Link>

        <Link href="/zibo/value-sets" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Groupings</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Value Sets</p>
            <p className="text-xs text-neutral-500 mt-1">Curated clinical value set definitions</p>
          </div>
        </Link>

        <Link href="/zibo/concept-maps" className="block">
          <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6 hover:border-brand-primary/30 transition-colors">
            <h3 className="text-sm font-medium text-neutral-500">Translation</h3>
            <p className="text-xl font-semibold text-neutral-900 mt-2">Concept Maps</p>
            <p className="text-xs text-neutral-500 mt-1">Cross-system code mappings</p>
          </div>
        </Link>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
        <div className="md:col-span-2 bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-neutral-900">Code Systems</h2>
            <button
              onClick={loadCodeSystems}
              disabled={loading}
              className="px-4 py-1.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
            >
              {loading ? "Loading…" : loaded ? "Refresh" : "Load"}
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-neutral-100">
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium">Name</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium">Version</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium">Concepts</th>
                  <th className="text-left py-2 px-2 text-neutral-500 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {!loaded ? (
                  <tr>
                    <td colSpan={4} className="py-6 text-center text-neutral-400 text-sm">
                      Click &ldquo;Load&rdquo; to fetch code systems
                    </td>
                  </tr>
                ) : codeSystems.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="py-6 text-center text-neutral-400 text-sm">
                      No code systems found
                    </td>
                  </tr>
                ) : (
                  codeSystems.map((cs) => (
                    <tr key={cs.id} className="border-b border-neutral-50 hover:bg-neutral-25">
                      <td className="py-2 px-2">
                        <div className="font-medium text-neutral-900">{cs.name}</div>
                        <div className="text-xs text-neutral-400 font-mono">{cs.systemUri}</div>
                      </td>
                      <td className="py-2 px-2 text-neutral-500 text-xs">{cs.version}</td>
                      <td className="py-2 px-2 text-neutral-700">
                        {cs.conceptCount.toLocaleString()}
                      </td>
                      <td className="py-2 px-2">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                            STATUS_BADGE[cs.status] ?? "bg-neutral-100 text-neutral-600"
                          }`}
                        >
                          {cs.status}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          <h2 className="text-base font-semibold text-neutral-900 mb-4">Code Validator</h2>
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-medium text-neutral-500 mb-1">
                System (ID or URI)
              </label>
              <input
                type="text"
                value={validateSystem}
                onChange={(e) => setValidateSystem(e.target.value)}
                placeholder="e.g. snomed-ct"
                className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-neutral-500 mb-1">
                Code
              </label>
              <input
                type="text"
                value={validateCode}
                onChange={(e) => setValidateCode(e.target.value)}
                placeholder="e.g. 73211009"
                className="w-full px-3 py-2 border border-neutral-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30"
              />
            </div>
            <button
              onClick={handleValidate}
              disabled={!validateCode.trim() || !validateSystem.trim()}
              className="w-full px-4 py-2 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50"
            >
              Validate
            </button>

            {validationResult !== null && (
              <div
                className={`mt-2 px-3 py-2 rounded-lg text-sm ${
                  validationResult.valid
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-red-50 text-red-700"
                }`}
              >
                {validationResult.valid ? (
                  <>
                    <div className="font-semibold">Valid ✓</div>
                    {validationResult.display && (
                      <div className="text-xs mt-1">{validationResult.display}</div>
                    )}
                  </>
                ) : (
                  <div className="font-semibold">Invalid — code not found in system</div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
