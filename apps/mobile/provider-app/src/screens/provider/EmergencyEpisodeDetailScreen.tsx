/**
 * EmergencyEpisodeDetailScreen — FSM, handover handshake, disposition, observation, alerts, order sets, meds.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Button, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getEmergencyEpisode,
  listEmergencyHandovers,
  arriveEmergencyEpisode,
  transitionEmergencyEpisode,
  confirmEmergencyLocation,
  requestEmergencyHandover,
  acceptEmergencyHandover,
  declineEmergencyHandover,
  expireEmergencyHandover,
  getEmergencyDisposition,
  recordEmergencyDisposition,
  listObservationStays,
  startObservationStay,
  endObservationStay,
  listIdentityLinks,
  linkEmergencyIdentity,
  listOrderSets,
  invokeOrderSet,
  orderSetItem,
  declineOrderSetItem,
  listEpisodeMedications,
  recordEpisodeMedication,
  listEpisodeAlerts,
  acknowledgeAlert,
  respondAlert,
  closeAlert,
} from "../../services/emergencyEpisodeService";
import { EmergencyOutboxBadge } from "../../components/emergency/EmergencyOutboxBadge";

type Section = "overview" | "handover" | "disposition" | "observation" | "identity" | "orders" | "meds" | "alerts";

const SECTIONS: { id: Section; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "handover", label: "Handover" },
  { id: "disposition", label: "Disposition" },
  { id: "observation", label: "Obs stay" },
  { id: "identity", label: "Identity" },
  { id: "orders", label: "Orders" },
  { id: "meds", label: "Meds" },
  { id: "alerts", label: "Alerts" },
];

export function EmergencyEpisodeDetailScreen({
  episodeId,
  onBack,
}: {
  episodeId: string;
  onBack: () => void;
}) {
  const qc = useQueryClient();
  const [section, setSection] = useState<Section>("overview");
  const [location, setLocation] = useState("");
  const [targetState, setTargetState] = useState("IN_TREATMENT");
  const [handoverTarget, setHandoverTarget] = useState("INPATIENT");
  const [resolvedCpid, setResolvedCpid] = useState("");
  const [orderSetCode, setOrderSetCode] = useState("TRAUMA_MAJOR");
  const [medCode, setMedCode] = useState("");
  const [medDose, setMedDose] = useState("");
  const [declineReason, setDeclineReason] = useState("");
  const actorId = "provider-mobile";

  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: ["emergency-episode", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-episode-handovers", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-disposition", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-observation-stays", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-order-sets", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-medications", episodeId] });
    void qc.invalidateQueries({ queryKey: ["emergency-episode-alerts", episodeId] });
  };

  const { data: episode, isLoading, isError, refetch } = useQuery({
    queryKey: ["emergency-episode", episodeId],
    queryFn: () => getEmergencyEpisode(episodeId),
  });

  const { data: handovers } = useQuery({
    queryKey: ["emergency-episode-handovers", episodeId],
    queryFn: () => listEmergencyHandovers(episodeId),
  });

  const { data: disposition } = useQuery({
    queryKey: ["emergency-disposition", episodeId],
    queryFn: () => getEmergencyDisposition(episodeId),
  });

  const { data: obsStays } = useQuery({
    queryKey: ["emergency-observation-stays", episodeId],
    queryFn: () => listObservationStays(episodeId),
  });

  const { data: identityLinks } = useQuery({
    queryKey: ["emergency-identity-links", episodeId],
    queryFn: () => listIdentityLinks(episodeId),
  });

  const { data: orderSets } = useQuery({
    queryKey: ["emergency-order-sets", episodeId],
    queryFn: () => listOrderSets(episodeId),
  });

  const { data: medications } = useQuery({
    queryKey: ["emergency-medications", episodeId],
    queryFn: () => listEpisodeMedications(episodeId),
  });

  const { data: alerts } = useQuery({
    queryKey: ["emergency-episode-alerts", episodeId],
    queryFn: () => listEpisodeAlerts(episodeId),
  });

  const arriveMut = useMutation({
    mutationFn: () => arriveEmergencyEpisode(episodeId, { journeyId: String(episode?.journey_id ?? episodeId) }),
    onSuccess: () => { invalidate(); Alert.alert("Arrived", "Patient arrival recorded."); },
    onError: () => Alert.alert("Error", "Arrive failed"),
  });

  const transitionMut = useMutation({
    mutationFn: () => transitionEmergencyEpisode(episodeId, { state: targetState }),
    onSuccess: () => { invalidate(); Alert.alert("Transitioned", `State → ${targetState}`); },
    onError: () => Alert.alert("Error", "Transition failed"),
  });

  const locationMut = useMutation({
    mutationFn: () => confirmEmergencyLocation(episodeId, { location }),
    onSuccess: () => { invalidate(); Alert.alert("Location confirmed"); },
    onError: () => Alert.alert("Error", "Location update failed"),
  });

  const handoverMut = useMutation({
    mutationFn: () =>
      requestEmergencyHandover(episodeId, { targetType: handoverTarget, requestedBy: actorId }),
    onSuccess: () => { invalidate(); Alert.alert("Handover requested", "Awaiting acceptance."); },
    onError: () => Alert.alert("Error", "Handover request failed"),
  });

  const dispositionMut = useMutation({
    mutationFn: () =>
      recordEmergencyDisposition(episodeId, {
        dispositionType: "DISCHARGE",
        destination: "Home",
        recordedBy: actorId,
      }),
    onSuccess: () => { invalidate(); Alert.alert("Disposition recorded"); },
    onError: () => Alert.alert("Error", "Disposition failed"),
  });

  const obsStartMut = useMutation({
    mutationFn: () => startObservationStay(episodeId, { reason: "Clinical observation", startedBy: actorId }),
    onSuccess: () => { invalidate(); Alert.alert("Observation stay started"); },
    onError: () => Alert.alert("Error", "Could not start observation stay"),
  });

  const identityMut = useMutation({
    mutationFn: () =>
      linkEmergencyIdentity(episodeId, {
        resolvedSubjectCpid: resolvedCpid,
        resolutionMethod: "MANUAL_MATCH",
        resolvedBy: actorId,
      }),
    onSuccess: () => { invalidate(); setResolvedCpid(""); Alert.alert("Identity linked"); },
    onError: () => Alert.alert("Error", "Identity link failed"),
  });

  const orderSetMut = useMutation({
    mutationFn: () =>
      invokeOrderSet(episodeId, { orderSetCode, invokedBy: actorId, orderSetName: orderSetCode }),
    onSuccess: () => { invalidate(); Alert.alert("Order set invoked"); },
    onError: () => Alert.alert("Error", "Order set failed"),
  });

  const medMut = useMutation({
    mutationFn: () =>
      recordEpisodeMedication(episodeId, {
        medicationCode: medCode,
        doseValue: Number(medDose) || 1,
        doseUnit: "mg",
        route: "IV",
        administeredBy: actorId,
      }),
    onSuccess: () => { invalidate(); setMedCode(""); setMedDose(""); },
    onError: () => Alert.alert("Error", "Medication record failed"),
  });

  const pendingHandover = (handovers ?? []).find((h) => String(h.status) === "PENDING");

  return (
    <View style={s.flex}>
      <View style={s.topBar}>
        <TouchableOpacity onPress={onBack}>
          <Text style={s.back}>← Board</Text>
        </TouchableOpacity>
        <Text style={s.title}>{String(episode?.episode_reference ?? episodeId)}</Text>
        <Badge variant="outline">{String(episode?.state ?? "—")}</Badge>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.tabBar}>
        {SECTIONS.map((sec) => (
          <TouchableOpacity key={sec.id} onPress={() => setSection(sec.id)} style={[s.tab, section === sec.id && s.activeTab]}>
            <Text style={[s.tabText, section === sec.id && s.activeTabText]}>{sec.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <ScrollView style={s.scroll} contentContainerStyle={s.pad}>
        <EmergencyOutboxBadge onFlushComplete={() => void refetch()} />
        {isLoading ? <LoadingSpinner /> : null}
        {isError ? <ErrorState message="Could not read episode" onRetry={() => void refetch()} /> : null}

        {section === "overview" && episode ? (
          <View style={s.section}>
            <Text style={s.muted}>Entry: {String(episode.entry_route)} · CPID {String(episode.subject_cpid ?? "unknown")}</Text>
            {episode.current_location ? <Text style={s.muted}>Location: {String(episode.current_location)}</Text> : null}
            <Button title="Record arrival" onPress={() => arriveMut.mutate()} loading={arriveMut.isPending} />
            <TextInput value={location} onChangeText={setLocation} style={s.input} placeholder="Confirm location" />
            <Button title="Confirm location" variant="outline" onPress={() => locationMut.mutate()} disabled={!location.trim()} />
            <TextInput value={targetState} onChangeText={setTargetState} style={s.input} placeholder="Target FSM state" />
            <Button title="Transition state" variant="outline" onPress={() => transitionMut.mutate()} />
          </View>
        ) : null}

        {section === "handover" ? (
          <View style={s.section}>
            <Text style={s.h2}>Handover handshake</Text>
            {(handovers ?? []).map((h) => (
              <View key={String(h.handover_id)} style={s.card}>
                <Text style={s.cardTitle}>{String(h.target_type)} → {String(h.status)}</Text>
                {String(h.status) === "PENDING" && h.handover_id ? (
                  <View style={s.row}>
                    <Button title="Accept" size="sm" onPress={() => acceptEmergencyHandover(String(h.handover_id), { acceptedBy: actorId, acceptingRef: `accept-${h.handover_id}` }).then(invalidate)} />
                    <Button title="Decline" size="sm" variant="outline" onPress={() => { if (!declineReason.trim()) { Alert.alert("Reason required"); return; } declineEmergencyHandover(String(h.handover_id), { declinedBy: actorId, reason: declineReason }).then(invalidate); }} />
                    <Button title="Expire" size="sm" variant="outline" onPress={() => expireEmergencyHandover(String(h.handover_id), { expiredBy: actorId }).then(invalidate)} />
                  </View>
                ) : null}
              </View>
            ))}
            {!pendingHandover ? (
              <>
                <TextInput value={handoverTarget} onChangeText={setHandoverTarget} style={s.input} placeholder="Target type" />
                <TextInput value={declineReason} onChangeText={setDeclineReason} style={s.input} placeholder="Decline reason" />
                <Button title="Request handover" onPress={() => handoverMut.mutate()} loading={handoverMut.isPending} />
              </>
            ) : null}
          </View>
        ) : null}

        {section === "disposition" ? (
          <View style={s.section}>
            {disposition ? <Text style={s.muted}>Recorded: {String(disposition.disposition_type)} → {String(disposition.destination ?? "")}</Text> : <Text style={s.muted}>No disposition recorded yet.</Text>}
            <Button title="Record discharge disposition" onPress={() => dispositionMut.mutate()} loading={dispositionMut.isPending} />
          </View>
        ) : null}

        {section === "observation" ? (
          <View style={s.section}>
            {(obsStays ?? []).map((stay) => (
              <View key={String(stay.stay_id)} style={s.card}>
                <Text style={s.cardTitle}>{String(stay.status)} — {String(stay.reason)}</Text>
                {String(stay.status) === "ACTIVE" && stay.stay_id ? (
                  <Button title="End stay" size="sm" variant="outline" onPress={() => endObservationStay(String(stay.stay_id), { outcome: "DISCHARGED", endedBy: actorId }).then(invalidate)} />
                ) : null}
              </View>
            ))}
            <Button title="Start observation stay" onPress={() => obsStartMut.mutate()} loading={obsStartMut.isPending} />
          </View>
        ) : null}

        {section === "identity" ? (
          <View style={s.section}>
            {(identityLinks ?? []).map((link) => (
              <Text key={String(link.link_id)} style={s.muted}>{String(link.resolved_subject_cpid)} via {String(link.resolution_method)}</Text>
            ))}
            <TextInput value={resolvedCpid} onChangeText={setResolvedCpid} style={s.input} placeholder="Resolved CPID" />
            <Button title="Link identity" onPress={() => identityMut.mutate()} disabled={!resolvedCpid.trim()} />
          </View>
        ) : null}

        {section === "orders" ? (
          <View style={s.section}>
            <TextInput value={orderSetCode} onChangeText={setOrderSetCode} style={s.input} placeholder="Order set code" />
            <Button title="Invoke order set" onPress={() => orderSetMut.mutate()} loading={orderSetMut.isPending} />
            {(orderSets ?? []).map((os) => (
              <View key={String(os.instance_id)} style={s.card}>
                <Text style={s.cardTitle}>{String(os.order_set_name ?? os.order_set_code)}</Text>
                {((os.items as Record<string, unknown>[]) ?? []).map((item) => (
                  <View key={String(item.item_id)} style={s.row}>
                    <Text style={s.muted}>{String(item.order_label ?? item.order_code)} — {String(item.status)}</Text>
                    {String(item.status) === "PENDING" || !item.status ? (
                      <>
                        <Button title="Order" size="sm" onPress={() => orderSetItem(String(item.item_id), { orderedBy: actorId }).then(invalidate)} />
                        <Button title="Decline" size="sm" variant="outline" onPress={() => { if (!declineReason.trim()) { Alert.alert("Reason required"); return; } declineOrderSetItem(String(item.item_id), { declinedBy: actorId, declineReason }).then(invalidate); }} />
                      </>
                    ) : null}
                  </View>
                ))}
              </View>
            ))}
          </View>
        ) : null}

        {section === "meds" ? (
          <View style={s.section}>
            <TextInput value={medCode} onChangeText={setMedCode} style={s.input} placeholder="Medication code" />
            <TextInput value={medDose} onChangeText={setMedDose} style={s.input} placeholder="Dose value" keyboardType="numeric" />
            <Button title="Record administration" onPress={() => medMut.mutate()} disabled={!medCode.trim()} />
            {(medications ?? []).map((m) => (
              <Text key={String(m.administration_id)} style={s.muted}>{String(m.medication_name ?? m.medication_code)} {String(m.dose_value)} {String(m.dose_unit)}</Text>
            ))}
          </View>
        ) : null}

        {section === "alerts" ? (
          <View style={s.section}>
            {(alerts ?? []).map((a) => (
              <View key={String(a.alert_id)} style={s.card}>
                <Text style={s.cardTitle}>{String(a.alert_type)} — {String(a.severity)}</Text>
                <Text style={s.muted}>{String(a.detail ?? "")}</Text>
                <View style={s.row}>
                  <Button title="Ack" size="sm" onPress={() => acknowledgeAlert(String(a.alert_id), { acknowledgedBy: actorId }).then(invalidate)} />
                  <Button title="Respond" size="sm" variant="outline" onPress={() => respondAlert(String(a.alert_id), { respondedBy: actorId, responseNote: "Seen on mobile" }).then(invalidate)} />
                  <Button title="Close" size="sm" variant="outline" onPress={() => closeAlert(String(a.alert_id), { closedBy: actorId }).then(invalidate)} />
                </View>
              </View>
            ))}
            {(alerts ?? []).length === 0 ? <Text style={s.muted}>No alerts — or they could not be read.</Text> : null}
          </View>
        ) : null}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  topBar: { flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 16, paddingVertical: 8, borderBottomWidth: 1, borderColor: colors.gray[200] },
  back: { color: colors.ui.error.main, fontWeight: "600", fontSize: 13 },
  title: { flex: 1, fontSize: 15, fontWeight: "700" },
  tabBar: { maxHeight: 40, borderBottomWidth: 1, borderColor: colors.gray[200] },
  tab: { paddingHorizontal: 10, paddingVertical: 8 },
  activeTab: { borderBottomWidth: 2, borderColor: colors.ui.error.main },
  tabText: { fontSize: 11, color: colors.gray[500] },
  activeTabText: { color: colors.ui.error.main, fontWeight: "600" },
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 10, paddingBottom: 32 },
  section: { gap: 10 },
  h2: { fontSize: 15, fontWeight: "700" },
  muted: { fontSize: 12, color: colors.gray[500] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  card: { borderWidth: 1, borderColor: colors.gray[200], borderRadius: 8, padding: 10, gap: 6 },
  cardTitle: { fontSize: 13, fontWeight: "600" },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 6, alignItems: "center" },
});
