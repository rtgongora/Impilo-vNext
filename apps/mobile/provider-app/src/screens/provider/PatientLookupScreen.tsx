/**
 * PatientLookupScreen — Patient search by name, NID, or QR code.
 *
 * Drives the patient lookup → encounter start flow.
 */

import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Badge,
  Avatar,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { searchPatients } from "../../services/patientService";
import { createEncounter } from "../../services/encounterService";
import { encounterStore } from "../../stores/encounterStore";
import { useAppStore } from "../../stores/appStore";
import type { Patient } from "../../types";

export function PatientLookupScreen() {
  const { facilityId } = useAppStore();
  const [query, setQuery] = useState("");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null);
  const [startingEncounter, setStartingEncounter] = useState(false);

  const handleSearch = useCallback(async () => {
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await searchPatients(query);
      setPatients(result.patients);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Search failed");
    } finally {
      setLoading(false);
    }
  }, [query]);

  const handleStartEncounter = useCallback(async (patient: Patient) => {
    if (!facilityId) return;
    setStartingEncounter(true);
    try {
      const encounter = await createEncounter({
        patientId: patient.id,
        facilityId,
        encounterType: "OUTPATIENT",
      });
      encounterStore.getState().setActiveEncounter(encounter);
      setSelectedPatient(patient);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start encounter");
    } finally {
      setStartingEncounter(false);
    }
  }, [facilityId]);

  return (
    <Screen>
      <Header title="Patient Lookup" />
      <ScrollView testID="patient-lookup-screen" style={styles.container}>
        {/* Search bar */}
        <View style={styles.searchRow}>
          <TextField
            label="Search patients"
            value={query}
            onChange={setQuery}
            placeholder="Name, NID, or CPID..."
            testID="patient-search-input"
          />
          <Button
            title="Search"
            onPress={handleSearch}
            loading={loading}
            testID="patient-search-btn"
          />
        </View>

        {/* Results */}
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Search Error" message={error} onRetry={handleSearch} />
        ) : patients.length === 0 && query ? (
          <EmptyState title="No patients found" message="Try a different search term" />
        ) : (
          patients.map((patient) => (
            <Card key={patient.id}>
              <CardBody>
                <View testID={`patient-${patient.id}`} style={styles.patientRow}>
                  <Avatar
                    name={`${patient.givenName} ${patient.familyName}`}
                    size="md"
                  />
                  <View style={styles.patientInfo}>
                    <Text style={styles.bold}>
                      {`${patient.givenName} ${patient.familyName}`}
                    </Text>
                    <View style={styles.badgeRow}>
                      <Badge variant="outline">{`NID: ${patient.nationalId}`}</Badge>
                      <Badge variant="outline">{patient.sex}</Badge>
                      <Badge variant="outline">{patient.dateOfBirth}</Badge>
                    </View>
                    <Text style={styles.cpid}>
                      {`CPID: ${patient.cpid}`}
                    </Text>
                  </View>
                  <Button
                    title="Start Visit"
                    variant="primary"
                    size="sm"
                    onPress={() => handleStartEncounter(patient)}
                    loading={startingEncounter}
                    testID={`start-visit-${patient.id}`}
                  />
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  searchRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  patientRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  patientInfo: {
    flex: 1,
  },
  bold: {
    fontWeight: "bold",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
  cpid: {
    fontSize: 12,
    color: "#9CA3AF",
    marginTop: 4,
  },
});
