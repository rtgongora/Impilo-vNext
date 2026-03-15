/**
 * EncounterScreen — Active encounter workflow.
 *
 * Displays the current encounter with tabs for Vitals, Diagnosis, Rx, Labs, Referrals, Notes.
 */

import React, { useState, useCallback } from "react";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Button,
  TabBar,
  Badge,
  LoadingSpinner,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useEncounterStore, encounterStore } from "../../stores/encounterStore";
import { closeEncounter, addEncounterNotes } from "../../services/encounterService";
import { VitalsPanel } from "./VitalsPanel";
import { DiagnosisPanel } from "./DiagnosisPanel";
import { PrescriptionPanel } from "./PrescriptionPanel";
import { LabOrderPanel } from "./LabOrderPanel";
import { ReferralPanel } from "./ReferralPanel";
import { NotesPanel } from "./NotesPanel";

const TABS = [
  { key: "vitals", label: "Vitals" },
  { key: "diagnosis", label: "Diagnosis" },
  { key: "rx", label: "Rx" },
  { key: "labs", label: "Labs" },
  { key: "referrals", label: "Referrals" },
  { key: "notes", label: "Notes" },
];

export function EncounterScreen() {
  const { activeEncounter, vitals, diagnoses, prescriptions, labOrders, referrals, isDirty } =
    useEncounterStore();
  const [activeTab, setActiveTab] = useState("vitals");
  const [closing, setClosing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCloseEncounter = useCallback(async () => {
    if (!activeEncounter) return;
    setClosing(true);
    setError(null);
    try {
      const primaryDx = diagnoses.find((d) => d.isPrimary);
      await closeEncounter(activeEncounter.id, primaryDx?.icdDescription);
      encounterStore.getState().clearEncounter();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to close encounter");
    } finally {
      setClosing(false);
    }
  }, [activeEncounter, diagnoses]);

  if (!activeEncounter) {
    return React.createElement(
      Screen,
      null,
      React.createElement(Header, { title: "Encounter" }),
      React.createElement(
        "div",
        { style: { padding: "16px", textAlign: "center" } },
        React.createElement("p", null, "No active encounter. Search for a patient to start a visit.")
      )
    );
  }

  const renderPanel = () => {
    switch (activeTab) {
      case "vitals":
        return React.createElement(VitalsPanel, { encounterId: activeEncounter.id });
      case "diagnosis":
        return React.createElement(DiagnosisPanel, { encounterId: activeEncounter.id });
      case "rx":
        return React.createElement(PrescriptionPanel, {
          encounterId: activeEncounter.id,
          patientId: activeEncounter.patientId,
        });
      case "labs":
        return React.createElement(LabOrderPanel, {
          encounterId: activeEncounter.id,
          patientId: activeEncounter.patientId,
        });
      case "referrals":
        return React.createElement(ReferralPanel, {
          encounterId: activeEncounter.id,
          patientId: activeEncounter.patientId,
        });
      case "notes":
        return React.createElement(NotesPanel, { encounterId: activeEncounter.id });
      default:
        return null;
    }
  };

  return React.createElement(
    Screen,
    null,
    React.createElement(Header, { title: `Encounter — ${activeEncounter.encounterType}` }),
    React.createElement(
      "div",
      { "data-testid": "encounter-screen", style: { padding: "16px" } },

      // Encounter summary
      React.createElement(
        Card,
        null,
        React.createElement(
          CardBody,
          null,
          React.createElement(
            "div",
            { style: { display: "flex", justifyContent: "space-between", alignItems: "center" } },
            React.createElement(
              "div",
              null,
              React.createElement(Badge, { variant: "primary", children: activeEncounter.status }),
              React.createElement(
                "span",
                { style: { marginLeft: "8px", fontSize: "14px", color: "#6B7280" } },
                `Started: ${new Date(activeEncounter.startedAt).toLocaleTimeString()}`
              )
            ),
            React.createElement(
              "div",
              { style: { display: "flex", gap: "8px" } },
              React.createElement(Badge, { variant: "outline", children: `${vitals.length} vitals` }),
              React.createElement(Badge, { variant: "outline", children: `${diagnoses.length} Dx` }),
              React.createElement(Badge, { variant: "outline", children: `${prescriptions.length} Rx` }),
              React.createElement(Badge, { variant: "outline", children: `${labOrders.length} labs` }),
              React.createElement(Badge, { variant: "outline", children: `${referrals.length} refs` })
            )
          ),
          activeEncounter.chiefComplaint
            ? React.createElement(
                "p",
                { style: { marginTop: "8px", fontSize: "14px" } },
                `Chief Complaint: ${activeEncounter.chiefComplaint}`
              )
            : null
        )
      ),

      // Encounter tabs
      React.createElement(
        "div",
        { style: { margin: "16px 0" } },
        React.createElement(TabBar, {
          items: TABS,
          activeKey: activeTab,
          onSelect: setActiveTab,
        })
      ),

      // Active panel
      renderPanel(),

      // Close encounter
      error
        ? React.createElement(ErrorState, { title: "Error", message: error })
        : null,
      React.createElement(
        "div",
        { style: { marginTop: "24px", display: "flex", gap: "12px" } },
        React.createElement(Button, {
          title: "Close Encounter",
          variant: "primary",
          onPress: handleCloseEncounter,
          loading: closing,
          disabled: diagnoses.length === 0,
          testID: "close-encounter-btn",
          accessibilityLabel: "Close this encounter",
        })
      )
    )
  );
}
