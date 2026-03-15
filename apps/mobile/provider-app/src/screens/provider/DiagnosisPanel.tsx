/**
 * DiagnosisPanel — ICD-11 search and diagnosis recording within an encounter.
 */

import React, { useState, useCallback } from "react";
import { Card, CardBody, Button, TextField, Badge, DiagnosisBadge, Checkbox, ErrorState } from "@impilo/mobile-design-system";
import { searchICD11, recordDiagnosis, type ICD11SearchResult } from "../../services/diagnosisService";
import { encounterStore } from "../../stores/encounterStore";
import { useEncounterStore } from "../../stores/encounterStore";

interface DiagnosisPanelProps {
  encounterId: string;
}

export function DiagnosisPanel({ encounterId }: DiagnosisPanelProps) {
  const { diagnoses } = useEncounterStore();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ICD11SearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isPrimary, setIsPrimary] = useState(false);

  const handleSearch = useCallback(async () => {
    if (!query.trim()) return;
    setSearching(true);
    try {
      const icdResults = await searchICD11(query);
      setResults(icdResults);
    } catch (err) {
      setError(err instanceof Error ? err.message : "ICD-11 search failed");
    } finally {
      setSearching(false);
    }
  }, [query]);

  const handleSelect = useCallback(async (result: ICD11SearchResult) => {
    setSaving(true);
    setError(null);
    try {
      const dx = await recordDiagnosis({
        encounterId,
        icdCode: result.code,
        icdDescription: result.description,
        isPrimary: isPrimary || diagnoses.length === 0,
      });
      encounterStore.getState().addDiagnosis(dx);
      setResults([]);
      setQuery("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record diagnosis");
    } finally {
      setSaving(false);
    }
  }, [encounterId, isPrimary, diagnoses.length]);

  return React.createElement(
    "div",
    { "data-testid": "diagnosis-panel" },

    // Search
    React.createElement(
      Card,
      null,
      React.createElement(
        CardBody,
        null,
        React.createElement(
          "div",
          { style: { display: "flex", gap: "8px", alignItems: "flex-end" } },
          React.createElement(TextField, {
            label: "Search ICD-11",
            value: query,
            onChange: setQuery,
            placeholder: "Type condition name...",
            testID: "icd-search-input",
          }),
          React.createElement(Button, {
            title: "Search",
            onPress: handleSearch,
            loading: searching,
            testID: "icd-search-btn",
          })
        ),
        React.createElement(Checkbox, {
          label: "Primary diagnosis",
          checked: isPrimary,
          onChange: setIsPrimary,
          testID: "primary-dx-checkbox",
        }),
        error ? React.createElement(ErrorState, { title: "Error", message: error }) : null
      )
    ),

    // Search results
    results.length > 0
      ? React.createElement(
          "div",
          { style: { marginTop: "8px" } },
          results.map((r) =>
            React.createElement(
              Card,
              { key: r.code },
              React.createElement(
                CardBody,
                null,
                React.createElement(
                  "div",
                  {
                    style: { display: "flex", justifyContent: "space-between", alignItems: "center", cursor: "pointer" },
                    onClick: () => handleSelect(r),
                    "data-testid": `icd-result-${r.code}`,
                  },
                  React.createElement(
                    "div",
                    null,
                    React.createElement(Badge, { variant: "outline", children: r.code }),
                    React.createElement("span", { style: { marginLeft: "8px" } }, r.description),
                    React.createElement(
                      "span",
                      { style: { fontSize: "12px", color: "#9CA3AF", marginLeft: "8px" } },
                      r.chapter
                    )
                  ),
                  React.createElement(Button, {
                    title: saving ? "Adding..." : "Add",
                    variant: "primary",
                    size: "sm",
                    onPress: () => handleSelect(r),
                    loading: saving,
                  })
                )
              )
            )
          )
        )
      : null,

    // Current diagnoses
    React.createElement(
      "div",
      { style: { marginTop: "12px" } },
      diagnoses.map((dx) =>
        React.createElement(DiagnosisBadge, {
          key: dx.id,
          code: dx.icdCode,
          description: dx.icdDescription,
          isPrimary: dx.isPrimary,
        })
      )
    )
  );
}
