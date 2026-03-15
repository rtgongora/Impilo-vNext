/**
 * ReferralPanel — Referral creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { Card, CardBody, Button, TextField, Select, Badge, ErrorState } from "@impilo/mobile-design-system";
import { createReferral, getReferralsForEncounter } from "../../services/referralService";
import { encounterStore, useEncounterStore } from "../../stores/encounterStore";
import { useAppStore } from "../../stores/appStore";

interface ReferralPanelProps {
  encounterId: string;
  patientId: string;
}

export function ReferralPanel({ encounterId, patientId }: ReferralPanelProps) {
  const { referrals } = useEncounterStore();
  const { facilityId } = useAppStore();
  const [toFacilityId, setToFacilityId] = useState("");
  const [specialty, setSpecialty] = useState("");
  const [reason, setReason] = useState("");
  const [urgency, setUrgency] = useState<"ROUTINE" | "URGENT" | "EMERGENCY">("ROUTINE");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getReferralsForEncounter(encounterId)
      .then((refs) => refs.forEach((r) => encounterStore.getState().addReferral(r)))
      .catch(() => {});
  }, [encounterId]);

  const handleCreate = useCallback(async () => {
    if (!toFacilityId.trim() || !specialty.trim() || !reason.trim() || !facilityId) return;
    setSaving(true);
    setError(null);
    try {
      const ref = await createReferral({
        encounterId,
        patientId,
        fromFacilityId: facilityId,
        toFacilityId,
        specialty,
        reason,
        urgency,
        notes: notes || undefined,
      });
      encounterStore.getState().addReferral(ref);
      setToFacilityId("");
      setSpecialty("");
      setReason("");
      setNotes("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create referral");
    } finally {
      setSaving(false);
    }
  }, [encounterId, patientId, facilityId, toFacilityId, specialty, reason, urgency, notes]);

  return React.createElement(
    "div",
    { "data-testid": "referral-panel" },

    React.createElement(
      Card,
      null,
      React.createElement(
        CardBody,
        null,
        React.createElement(
          "div",
          { style: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" } },
          React.createElement(TextField, { label: "To Facility ID", value: toFacilityId, onChange: setToFacilityId, testID: "ref-facility" }),
          React.createElement(TextField, { label: "Specialty", value: specialty, onChange: setSpecialty, testID: "ref-specialty" }),
          React.createElement(TextField, { label: "Reason", value: reason, onChange: setReason, testID: "ref-reason" }),
          React.createElement(Select, {
            label: "Urgency",
            value: urgency,
            options: [
              { value: "ROUTINE", label: "Routine" },
              { value: "URGENT", label: "Urgent" },
              { value: "EMERGENCY", label: "Emergency" },
            ],
            onChange: (v: string) => setUrgency(v as "ROUTINE" | "URGENT" | "EMERGENCY"),
            testID: "ref-urgency",
          })
        ),
        React.createElement(TextField, { label: "Notes", value: notes, onChange: setNotes, testID: "ref-notes" }),
        React.createElement(
          "div",
          { style: { marginTop: "12px" } },
          React.createElement(Button, { title: "Create Referral", onPress: handleCreate, loading: saving, testID: "create-ref-btn" })
        ),
        error ? React.createElement(ErrorState, { title: "Error", message: error }) : null
      )
    ),

    React.createElement(
      "div",
      { style: { marginTop: "12px" } },
      referrals.map((ref) =>
        React.createElement(
          Card,
          { key: ref.id },
          React.createElement(
            CardBody,
            null,
            React.createElement(
              "div",
              { "data-testid": `referral-${ref.id}` },
              React.createElement("strong", null, `${ref.specialty} → ${ref.toFacilityName}`),
              React.createElement("p", { style: { fontSize: "14px", color: "#6B7280" } }, ref.reason),
              React.createElement(
                "div",
                { style: { display: "flex", gap: "8px", marginTop: "4px" } },
                React.createElement(Badge, { variant: ref.urgency === "EMERGENCY" ? "destructive" : "outline", children: ref.urgency }),
                React.createElement(Badge, { variant: "secondary", children: ref.status })
              )
            )
          )
        )
      )
    )
  );
}
