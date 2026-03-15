/**
 * ReferralPanel — Referral creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, StyleSheet } from "react-native";
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

  return (
    <View testID="referral-panel">
      <Card>
        <CardBody>
          <View style={styles.formGrid}>
            <TextField label="To Facility ID" value={toFacilityId} onChange={setToFacilityId} testID="ref-facility" />
            <TextField label="Specialty" value={specialty} onChange={setSpecialty} testID="ref-specialty" />
            <TextField label="Reason" value={reason} onChange={setReason} testID="ref-reason" />
            <Select
              label="Urgency"
              value={urgency}
              options={[
                { value: "ROUTINE", label: "Routine" },
                { value: "URGENT", label: "Urgent" },
                { value: "EMERGENCY", label: "Emergency" },
              ]}
              onChange={(v: string) => setUrgency(v as "ROUTINE" | "URGENT" | "EMERGENCY")}
              testID="ref-urgency"
            />
          </View>
          <TextField label="Notes" value={notes} onChange={setNotes} testID="ref-notes" />
          <View style={styles.buttonContainer}>
            <Button title="Create Referral" onPress={handleCreate} loading={saving} testID="create-ref-btn" />
          </View>
          {error ? <ErrorState title="Error" message={error} /> : null}
        </CardBody>
      </Card>

      <View style={styles.referralsList}>
        {referrals.map((ref) => (
          <Card key={ref.id}>
            <CardBody>
              <View testID={`referral-${ref.id}`}>
                <Text style={styles.bold}>{`${ref.specialty} → ${ref.toFacilityName}`}</Text>
                <Text style={styles.reason}>{ref.reason}</Text>
                <View style={styles.badgeRow}>
                  <Badge variant={ref.urgency === "EMERGENCY" ? "destructive" : "outline"}>
                    {ref.urgency}
                  </Badge>
                  <Badge variant="secondary">{ref.status}</Badge>
                </View>
              </View>
            </CardBody>
          </Card>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  formGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  buttonContainer: {
    marginTop: 12,
  },
  referralsList: {
    marginTop: 12,
  },
  bold: {
    fontWeight: "bold",
  },
  reason: {
    fontSize: 14,
    color: "#6B7280",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
});
