/**
 * ReferralPanel — Referral creation within an encounter.
 */

import React, { useState, useCallback, useEffect } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Card, CardBody, Button, TextField, Select, Badge, ErrorState, colors } from "@impilo/mobile-design-system";
import { createReferral, getReferralsForEncounter, searchReferralFacilities } from "../../services/referralService";
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
  const [toFacilityName, setToFacilityName] = useState("");
  const [facilityQuery, setFacilityQuery] = useState("");
  const [facilityCandidates, setFacilityCandidates] = useState<Array<{ id: string; name: string }>>([]);
  const [facilitySearching, setFacilitySearching] = useState(false);
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

  useEffect(() => {
    let active = true;
    if (facilityQuery.trim().length < 2) {
      setFacilityCandidates([]);
      return;
    }
    setFacilitySearching(true);
    searchReferralFacilities(facilityQuery)
      .then((rows) => {
        if (active) setFacilityCandidates(rows);
      })
      .catch(() => {
        if (active) setFacilityCandidates([]);
      })
      .finally(() => {
        if (active) setFacilitySearching(false);
      });
    return () => {
      active = false;
    };
  }, [facilityQuery]);

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
        toFacilityName: toFacilityName || undefined,
        specialty,
        reason,
        urgency,
        notes: notes || undefined,
      });
      encounterStore.getState().addReferral(ref);
      setToFacilityId("");
      setToFacilityName("");
      setFacilityQuery("");
      setFacilityCandidates([]);
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
            <TextField label="Find facility" value={facilityQuery} onChange={setFacilityQuery} testID="ref-facility-query" />
            <TextField label="To Facility Name" value={toFacilityName} onChange={setToFacilityName} testID="ref-facility-name" />
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
          {facilitySearching ? <Text style={styles.helper}>Searching facilities…</Text> : null}
          {facilityCandidates.length > 0 ? (
            <View style={styles.candidateList}>
              {facilityCandidates.map((candidate) => (
                <Button
                  key={candidate.id}
                  title={`${candidate.name} (${candidate.id.slice(0, 8)})`}
                  size="sm"
                  variant="outline"
                  onPress={() => {
                    setToFacilityId(candidate.id);
                    setToFacilityName(candidate.name);
                  }}
                />
              ))}
            </View>
          ) : null}
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
  helper: {
    fontSize: 12,
    color: colors.gray[500],
    marginTop: 8,
  },
  candidateList: {
    marginTop: 8,
    gap: 6,
  },
  referralsList: {
    marginTop: 12,
  },
  bold: {
    fontWeight: "bold",
  },
  reason: {
    fontSize: 14,
    color: colors.gray[500],
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 4,
  },
});
