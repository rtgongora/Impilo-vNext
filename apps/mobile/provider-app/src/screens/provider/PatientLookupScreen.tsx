import React, { useState, useCallback } from "react";
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from "react-native";
import { Ionicons } from "@expo/vector-icons";
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
  ErrorState,
} from "@impilo/mobile-design-system";
import { searchPatients } from "../../services/patientService";
import { createEncounter } from "../../services/encounterService";
import { encounterStore } from "../../stores/encounterStore";
import { useAppStore } from "../../stores/appStore";
import type { Patient } from "../../types";

export function PatientLookupScreen() {
  const { facilityId, setProviderTab } = useAppStore();
  const [query, setQuery] = useState("");
  const [patients, setPatients] = useState<Patient[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedPatient, setSelectedPatient] = useState<Patient | null>(null);
  const [startingEncounter, setStartingEncounter] = useState(false);
  const [isFocused, setIsFocused] = useState(false);

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
      setProviderTab("encounter");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start encounter");
    } finally {
      setStartingEncounter(false);
    }
  }, [facilityId, setProviderTab]);

  return (
    <Screen>
      <Header title="Patient Lookup" />
      <ScrollView testID="patient-lookup-screen" style={styles.container} contentContainerStyle={styles.contentContainer}>
        <View style={[styles.searchCard, isFocused && styles.searchCardFocused]}>
          <View style={styles.searchInputRow}>
            <Ionicons name="search-outline" size={20} color={isFocused ? "#1E40AF" : "#9CA3AF"} style={styles.searchIcon} />
            <TextField
              label=""
              value={query}
              onChange={setQuery}
              placeholder="Name, NID, or CPID..."
              testID="patient-search-input"
              onFocus={() => setIsFocused(true)}
              onBlur={() => setIsFocused(false)}
            />
          </View>
          <TouchableOpacity
            style={[styles.searchButton, loading && styles.searchButtonDisabled]}
            onPress={handleSearch}
            disabled={loading}
            testID="patient-search-btn"
          >
            {loading ? (
              <LoadingSpinner size="sm" />
            ) : (
              <>
                <Ionicons name="search" size={16} color="#FFFFFF" />
                <Text style={styles.searchButtonText}>Search</Text>
              </>
            )}
          </TouchableOpacity>
        </View>

        {loading ? (
          <View style={styles.centerContainer}>
            <LoadingSpinner size="md" />
          </View>
        ) : error ? (
          <ErrorState title="Search Error" message={error} onRetry={handleSearch} />
        ) : patients.length === 0 && query ? (
          <View style={styles.emptyContainer}>
            <View style={styles.emptyIconCircle}>
              <Ionicons name="search-outline" size={40} color="#9CA3AF" />
            </View>
            <Text style={styles.emptyTitle}>No patients found</Text>
            <Text style={styles.emptySubtitle}>Try a different name, NID, or CPID</Text>
          </View>
        ) : (
          patients.map((patient) => (
            <View key={patient.id} style={styles.patientCard} testID={`patient-${patient.id}`}>
              <View style={styles.patientCardInner}>
                <Avatar
                  name={`${patient.givenName} ${patient.familyName}`}
                  size="md"
                />
                <View style={styles.patientInfo}>
                  <Text style={styles.patientName}>
                    {`${patient.givenName} ${patient.familyName}`}
                  </Text>
                  <View style={styles.metaRow}>
                    <Ionicons name="card-outline" size={12} color="#6B7280" />
                    <Text style={styles.metaText}>{patient.nationalId}</Text>
                  </View>
                  <View style={styles.metaRow}>
                    <Ionicons name="person-outline" size={12} color="#6B7280" />
                    <Text style={styles.metaText}>{patient.sex}</Text>
                    <Ionicons name="calendar-outline" size={12} color="#6B7280" style={styles.metaIconSpaced} />
                    <Text style={styles.metaText}>{patient.dateOfBirth}</Text>
                  </View>
                  <Text style={styles.cpidText}>{`CPID: ${patient.cpid}`}</Text>
                </View>
                <TouchableOpacity
                  style={[styles.startVisitButton, startingEncounter && styles.startVisitButtonDisabled]}
                  onPress={() => handleStartEncounter(patient)}
                  disabled={startingEncounter}
                  testID={`start-visit-${patient.id}`}
                >
                  {startingEncounter ? (
                    <LoadingSpinner size="sm" />
                  ) : (
                    <>
                      <Ionicons name="rocket-outline" size={14} color="#FFFFFF" />
                      <Text style={styles.startVisitButtonText}>Start Visit</Text>
                    </>
                  )}
                </TouchableOpacity>
              </View>
            </View>
          ))
        )}

        {patients.length === 0 && (
          <TouchableOpacity style={styles.newPatientButton}>
            <Ionicons name="person-add-outline" size={16} color="#1E40AF" />
            <Text style={styles.newPatientButtonText}>New Patient Registration</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F8FAFC",
  },
  contentContainer: {
    padding: 16,
    paddingBottom: 32,
  },
  searchCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: "#E5E7EB",
    padding: 12,
    marginBottom: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 6,
    elevation: 2,
  },
  searchCardFocused: {
    borderColor: "#1E40AF",
    shadowColor: "#1E40AF",
    shadowOpacity: 0.12,
  },
  searchInputRow: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  searchIcon: {
    marginLeft: 4,
  },
  searchButton: {
    backgroundColor: "#1E40AF",
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 10,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  searchButtonDisabled: {
    opacity: 0.6,
  },
  searchButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
    fontSize: 14,
  },
  centerContainer: {
    alignItems: "center",
    paddingVertical: 40,
  },
  emptyContainer: {
    alignItems: "center",
    paddingVertical: 48,
    gap: 8,
  },
  emptyIconCircle: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: "#F3F4F6",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 8,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#374151",
  },
  emptySubtitle: {
    fontSize: 14,
    color: "#9CA3AF",
  },
  patientCard: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    marginBottom: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
    overflow: "hidden",
  },
  patientCardInner: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    gap: 12,
  },
  patientInfo: {
    flex: 1,
    gap: 3,
  },
  patientName: {
    fontSize: 15,
    fontWeight: "700",
    color: "#111827",
    marginBottom: 2,
  },
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  metaText: {
    fontSize: 12,
    color: "#6B7280",
  },
  metaIconSpaced: {
    marginLeft: 8,
  },
  cpidText: {
    fontSize: 11,
    color: "#9CA3AF",
    fontFamily: "monospace",
    marginTop: 2,
  },
  startVisitButton: {
    backgroundColor: "#1E40AF",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  startVisitButtonDisabled: {
    opacity: 0.6,
  },
  startVisitButtonText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "600",
  },
  newPatientButton: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderWidth: 1.5,
    borderColor: "#1E40AF",
    borderRadius: 12,
    paddingVertical: 14,
    marginTop: 8,
    backgroundColor: "#EFF6FF",
  },
  newPatientButtonText: {
    color: "#1E40AF",
    fontWeight: "600",
    fontSize: 14,
  },
});
