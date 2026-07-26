/**
 * DiagnosisPanel — ICD-11 search and diagnosis recording within an encounter.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { Card, CardBody, Button, TextField, Badge, DiagnosisBadge, Checkbox, ErrorState, colors } from "@impilo/mobile-design-system";
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

  return (
    <View testID="diagnosis-panel">
      {/* Search */}
      <Card>
        <CardBody>
          <View style={styles.searchRow}>
            <TextField
              label="Search ICD-11"
              value={query}
              onChange={setQuery}
              placeholder="Type condition name..."
              testID="icd-search-input"
            />
            <Button
              title="Search"
              onPress={handleSearch}
              loading={searching}
              testID="icd-search-btn"
            />
          </View>
          <Checkbox
            label="Primary diagnosis"
            checked={isPrimary}
            onChange={setIsPrimary}
            testID="primary-dx-checkbox"
          />
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>

      {/* Search results */}
      {results.length > 0 ? (
        <View style={styles.resultsContainer}>
          {results.map((r) => (
            <Card key={r.code}>
              <CardBody>
                <TouchableOpacity
                  style={styles.resultRow}
                  onPress={() => handleSelect(r)}
                  testID={`icd-result-${r.code}`}
                >
                  <View style={styles.resultInfo}>
                    <Badge variant="outline">{r.code}</Badge>
                    <Text style={styles.resultDescription}>{r.description}</Text>
                    <Text style={styles.resultChapter}>{r.chapter}</Text>
                  </View>
                  <Button
                    title={saving ? "Adding..." : "Add"}
                    variant="primary"
                    size="sm"
                    onPress={() => handleSelect(r)}
                    loading={saving}
                  />
                </TouchableOpacity>
              </CardBody>
            </Card>
          ))}
        </View>
      ) : null}

      {/* Current diagnoses */}
      <View style={styles.diagnosesContainer}>
        {diagnoses.map((dx) => (
          <DiagnosisBadge
            key={dx.id}
            code={dx.icdCode}
            description={dx.icdDescription}
            isPrimary={dx.isPrimary}
          />
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  searchRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "flex-end",
  },
  resultsContainer: {
    marginTop: 8,
  },
  resultRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  resultInfo: {
    flexDirection: "row",
    alignItems: "center",
    flex: 1,
  },
  resultDescription: {
    marginLeft: 8,
  },
  resultChapter: {
    fontSize: 12,
    color: colors.gray[400],
    marginLeft: 8,
  },
  diagnosesContainer: {
    marginTop: 12,
  },
});
