/**
 * Prehospital ePCR capture — responder / ambulance crew screen.
 *
 * The crew binds to their dispatched EMS mission, records a primary survey, and streams timed
 * observations (vitals / GCS / interventions) en route. Observations persist server-side via the
 * daidzai EMS ePCR endpoints and reach the destination ED's pre-arrival board before arrival.
 * Capture works offline: a retryable send is queued locally and replayed on reconnect (pending count
 * shown), so a crew with no signal never loses an observation.
 */

import { useCallback, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Card, CardHeader, CardBody, Button, TextField, Badge } from "@impilo/mobile-design-system";
import { useSyncEngine } from "@impilo/mobile-offline";
import {
  advanceMission,
  getMission,
  getMissionForIncident,
  recordObs,
  upsertEpcr,
  type EmsMission,
} from "../../services/epcrService";

type Loaded = { mission: EmsMission | null; error: string | null; loading: boolean };

export function PrehospitalEpcrScreen() {
  const sync = useSyncEngine();
  const pending = sync?.pendingCount ?? 0;

  const [incidentId, setIncidentId] = useState("");
  const [missionId, setMissionId] = useState("");
  const [state, setState] = useState<Loaded>({ mission: null, error: null, loading: false });
  const [status, setStatus] = useState<string | null>(null);

  // Primary survey
  const [airway, setAirway] = useState("");
  const [breathing, setBreathing] = useState("");
  const [circulation, setCirculation] = useState("");
  const [narrative, setNarrative] = useState("");

  // Vitals obs
  const [hr, setHr] = useState("");
  const [sbp, setSbp] = useState("");
  const [spo2, setSpo2] = useState("");
  const [rr, setRr] = useState("");
  const [gcs, setGcs] = useState("");
  const [intervention, setIntervention] = useState("");

  const [obsLog, setObsLog] = useState<Array<{ label: string; queued: boolean; at: string }>>([]);

  const mission = state.mission;

  const loadMission = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }));
    try {
      const m = missionId.trim()
        ? await getMission(missionId.trim())
        : await getMissionForIncident(incidentId.trim());
      setMissionId(m.id);
      setState({ mission: m, error: null, loading: false });
      setStatus(`Mission ${m.missionReference ?? m.id} — ${m.state}`);
    } catch (e) {
      setState({ mission: null, error: "Could not load mission", loading: false });
    }
  }, [incidentId, missionId]);

  const savePrimarySurvey = useCallback(async () => {
    if (!mission) return;
    const res = await upsertEpcr(mission.id, {
      primarySurvey: { airway, breathing, circulation },
      narrative,
    });
    setStatus(res.queued ? "Primary survey saved offline (will sync)" : "Primary survey saved");
  }, [mission, airway, breathing, circulation, narrative]);

  const logObs = useCallback(
    async (label: string, eventType: Parameters<typeof recordObs>[0]["eventType"], payload: Record<string, unknown>) => {
      if (!mission) return;
      const res = await recordObs({ missionId: mission.id, eventType, payload });
      setObsLog((prev) => [{ label, queued: res.queued, at: new Date().toISOString() }, ...prev]);
      setStatus(res.queued ? `${label} captured offline (will sync)` : `${label} captured`);
    },
    [mission]
  );

  const captureVitals = useCallback(() => {
    const payload: Record<string, unknown> = {};
    if (hr) payload.hr = Number(hr);
    if (sbp) payload.sbp = Number(sbp);
    if (spo2) payload.spo2 = Number(spo2);
    if (rr) payload.rr = Number(rr);
    return logObs(`Vitals (HR ${hr || "-"}, SBP ${sbp || "-"})`, "VITALS", payload);
  }, [hr, sbp, spo2, rr, logObs]);

  const captureGcs = useCallback(() => logObs(`GCS ${gcs}`, "GCS", { gcs: Number(gcs) }), [gcs, logObs]);
  const captureIntervention = useCallback(
    () => logObs(`Intervention: ${intervention}`, "INTERVENTION", { intervention }),
    [intervention, logObs]
  );

  const arriveHandover = useCallback(async () => {
    if (!mission) return;
    await advanceMission(mission.id, "ARRIVED_FACILITY");
    const m = await advanceMission(mission.id, "HANDOVER");
    setState((s) => ({ ...s, mission: m }));
    setStatus("Handover to ED recorded");
  }, [mission]);

  return (
    <ScrollView style={styles.root} testID="epcr-screen">
      <Card>
        <CardHeader title="Prehospital ePCR" />
        <CardBody>
          <View style={styles.rowBetween}>
            <Text style={styles.muted}>Ambulance responder capture</Text>
            {pending > 0 ? (
              <Badge label={`${pending} pending sync`} variant="warning" testID="epcr-pending-badge" />
            ) : (
              <Badge label="Synced" variant="success" testID="epcr-synced-badge" />
            )}
          </View>
          <TextField
            label="Incident ID"
            value={incidentId}
            onChangeText={setIncidentId}
            placeholder="daidzai incident id"
            testID="epcr-incident-input"
          />
          <TextField
            label="Mission ID (optional)"
            value={missionId}
            onChangeText={setMissionId}
            placeholder="EMS mission id"
            testID="epcr-mission-input"
          />
          <Button
            title={state.loading ? "Loading…" : "Load mission"}
            onPress={loadMission}
            testID="epcr-load-mission"
          />
          {status ? <Text style={styles.status} testID="epcr-status">{status}</Text> : null}
          {state.error ? <Text style={styles.error} testID="epcr-error">{state.error}</Text> : null}
        </CardBody>
      </Card>

      {mission ? (
        <>
          <Card>
            <CardHeader title={`Mission ${mission.state}`} />
            <CardBody>
              <TextField label="Airway" value={airway} onChangeText={setAirway} testID="epcr-airway" />
              <TextField label="Breathing" value={breathing} onChangeText={setBreathing} testID="epcr-breathing" />
              <TextField label="Circulation" value={circulation} onChangeText={setCirculation} testID="epcr-circulation" />
              <TextField label="Narrative" value={narrative} onChangeText={setNarrative} testID="epcr-narrative" />
              <Button title="Save primary survey" onPress={savePrimarySurvey} testID="epcr-save-survey" />
            </CardBody>
          </Card>

          <Card>
            <CardHeader title="Vitals" />
            <CardBody>
              <TextField label="HR" value={hr} onChangeText={setHr} keyboardType="numeric" testID="epcr-hr" />
              <TextField label="SBP" value={sbp} onChangeText={setSbp} keyboardType="numeric" testID="epcr-sbp" />
              <TextField label="SpO2" value={spo2} onChangeText={setSpo2} keyboardType="numeric" testID="epcr-spo2" />
              <TextField label="RR" value={rr} onChangeText={setRr} keyboardType="numeric" testID="epcr-rr" />
              <Button title="Capture vitals" onPress={captureVitals} testID="epcr-capture-vitals" />
              <TextField label="GCS" value={gcs} onChangeText={setGcs} keyboardType="numeric" testID="epcr-gcs" />
              <Button title="Capture GCS" onPress={captureGcs} testID="epcr-capture-gcs" />
              <TextField label="Intervention" value={intervention} onChangeText={setIntervention} testID="epcr-intervention" />
              <Button title="Log intervention" onPress={captureIntervention} testID="epcr-capture-intervention" />
            </CardBody>
          </Card>

          <Card>
            <CardHeader title={`Captured observations (${obsLog.length})`} />
            <CardBody>
              {obsLog.length === 0 ? (
                <Text style={styles.muted} testID="epcr-obs-empty">No observations captured yet</Text>
              ) : (
                obsLog.map((o, i) => (
                  <View key={i} style={styles.obsRow} testID={`epcr-obs-${i}`}>
                    <Text style={styles.obsLabel}>{o.label}</Text>
                    {o.queued ? <Badge label="queued" variant="warning" /> : <Badge label="sent" variant="success" />}
                  </View>
                ))
              )}
              <Button title="Arrive + handover to ED" onPress={arriveHandover} testID="epcr-handover" />
            </CardBody>
          </Card>
        </>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  rowBetween: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 8 },
  muted: { color: "#64748b" },
  status: { color: "#0f766e", marginTop: 8 },
  error: { color: "#b91c1c", marginTop: 8 },
  obsRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingVertical: 6 },
  obsLabel: { flex: 1, marginRight: 8 },
});
