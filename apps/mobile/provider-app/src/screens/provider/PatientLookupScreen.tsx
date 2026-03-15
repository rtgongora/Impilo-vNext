/**
 * PatientLookupScreen — Patient search by name, NID, or QR code.
 *
 * Drives the patient lookup → encounter start flow.
 */

import React, { useState, useCallback } from "react";
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

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: "Patient Lookup" }),
    React.createElement(
      "div",
      { "data-testid": "patient-lookup-screen", style: { padding: "16px" } },

      // Search bar
      React.createElement(
        "div",
        { style: { display: "flex", gap: "8px", marginBottom: "16px" } },
        React.createElement(TextField, {
          label: "Search patients",
          value: query,
          onChange: setQuery,
          placeholder: "Name, NID, or CPID...",
          testID: "patient-search-input",
        }),
        React.createElement(Button, {
          title: "Search",
          onPress: handleSearch,
          loading,
          testID: "patient-search-btn",
        })
      ),

      // Results
      loading
        ? React.createElement(LoadingSpinner, { size: "md" })
        : error
          ? React.createElement(ErrorState, { title: "Search Error", message: error, onRetry: handleSearch })
          : patients.length === 0 && query
            ? React.createElement(EmptyState, { title: "No patients found", message: "Try a different search term" })
            : patients.map((patient) =>
                React.createElement(
                  Card,
                  { key: patient.id },
                  React.createElement(
                    CardBody,
                    null,
                    React.createElement(
                      "div",
                      {
                        "data-testid": `patient-${patient.id}`,
                        style: { display: "flex", alignItems: "center", gap: "12px" },
                      },
                      React.createElement(Avatar, {
                        name: `${patient.givenName} ${patient.familyName}`,
                        size: "md",
                      }),
                      React.createElement(
                        "div",
                        { style: { flex: 1 } },
                        React.createElement(
                          "strong",
                          null,
                          `${patient.givenName} ${patient.familyName}`
                        ),
                        React.createElement(
                          "div",
                          { style: { display: "flex", gap: "8px", marginTop: "4px" } },
                          React.createElement(Badge, { variant: "outline", children: `NID: ${patient.nationalId}` }),
                          React.createElement(Badge, { variant: "outline", children: patient.sex }),
                          React.createElement(Badge, { variant: "outline", children: patient.dateOfBirth })
                        ),
                        React.createElement(
                          "p",
                          { style: { fontSize: "12px", color: "#9CA3AF", marginTop: "4px" } },
                          `CPID: ${patient.cpid}`
                        )
                      ),
                      React.createElement(Button, {
                        title: "Start Visit",
                        variant: "primary",
                        size: "sm",
                        onPress: () => handleStartEncounter(patient),
                        loading: startingEncounter,
                        testID: `start-visit-${patient.id}`,
                      })
                    )
                  )
                )
              )
    )
  );
}
