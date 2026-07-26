import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Screen, Header, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  discoverEvents,
  join,
  listCertificates,
  listCpdHistory,
  listMyRegistrations,
  register,
  startEvent,
  type LiveEvent,
} from "../../services/impiloLiveService";

type HubTab = "discover" | "registrations" | "cpd" | "certificates" | "host";

function statusVariant(status: string): "success" | "warning" | "error" | "outline" {
  if (status === "LIVE") return "success";
  if (status === "ENDED" || status === "CANCELLED") return "error";
  if (status === "SCHEDULED") return "warning";
  return "outline";
}

export function ProviderLiveHubScreen() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<HubTab>("discover");
  const [busyEventId, setBusyEventId] = useState<string | null>(null);
  const [certEventId, setCertEventId] = useState<string | null>(null);

  const discoverQuery = useQuery({
    queryKey: ["provider-live-discover"],
    queryFn: () => discoverEvents(),
    staleTime: 20_000,
  });

  const registrationsQuery = useQuery({
    queryKey: ["provider-live-registrations"],
    queryFn: () => listMyRegistrations(),
    staleTime: 15_000,
  });

  const cpdQuery = useQuery({
    queryKey: ["provider-live-cpd"],
    queryFn: () => listCpdHistory(),
    staleTime: 20_000,
  });

  const certificatesQuery = useQuery({
    queryKey: ["provider-live-certificates", certEventId],
    queryFn: () => listCertificates(certEventId!),
    enabled: Boolean(certEventId),
  });

  const registerMutation = useMutation({
    mutationFn: (eventId: string) => register(eventId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["provider-live-registrations"] }),
  });

  const joinMutation = useMutation({
    mutationFn: (eventId: string) => join(eventId),
    onSuccess: () => Alert.alert("Joined", "Connected to the live session."),
    onError: () => Alert.alert("Join failed", "Room is temporarily unavailable."),
  });

  const startMutation = useMutation({
    mutationFn: (eventId: string) => startEvent(eventId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["provider-live-discover"] });
      Alert.alert("Live", "Event is now live.");
    },
    onError: () => Alert.alert("Could not start", "Host controls are partially available on mobile."),
  });

  const registrationIds = useMemo(
    () => new Set((registrationsQuery.data ?? []).map((r) => r.eventId)),
    [registrationsQuery.data],
  );

  const hostableEvents = useMemo(
    () => (discoverQuery.data ?? []).filter((e) => e.status === "SCHEDULED" || e.status === "LIVE"),
    [discoverQuery.data],
  );

  const registeredEvents = useMemo(() => {
    const byId = new Map((discoverQuery.data ?? []).map((e) => [e.id, e]));
    return (registrationsQuery.data ?? [])
      .map((r) => byId.get(r.eventId))
      .filter((e): e is LiveEvent => Boolean(e));
  }, [discoverQuery.data, registrationsQuery.data]);

  const cpdEndedEvents = useMemo(
    () => (discoverQuery.data ?? []).filter((e) => e.cpdEnabled && e.status === "ENDED"),
    [discoverQuery.data],
  );

  async function runAction(eventId: string, action: "register" | "join" | "start") {
    setBusyEventId(eventId);
    try {
      if (action === "register") await registerMutation.mutateAsync(eventId);
      if (action === "join") await joinMutation.mutateAsync(eventId);
      if (action === "start") await startMutation.mutateAsync(eventId);
    } finally {
      setBusyEventId(null);
    }
  }

  function renderEventActions(event: LiveEvent, mode: HubTab) {
    const busy = busyEventId === event.id;
    if (mode === "host") {
      return (
        <View style={styles.row}>
          {event.status === "SCHEDULED" ? (
            <Button
              title={busy ? "Starting…" : "Go live"}
              size="sm"
              variant="primary"
              onPress={() => runAction(event.id, "start")}
              disabled={busy}
              testID={`live-host-start-${event.id}`}
            />
          ) : null}
          {event.status === "LIVE" ? (
            <Button
              title={busy ? "Joining…" : "Join as host"}
              size="sm"
              variant="outline"
              onPress={() => runAction(event.id, "join")}
              disabled={busy}
            />
          ) : null}
        </View>
      );
    }

    return (
      <View style={styles.row}>
        {event.status === "LIVE" ? (
          <Button title={busy ? "Joining…" : "Join"} size="sm" variant="primary" onPress={() => runAction(event.id, "join")} disabled={busy} />
        ) : !registrationIds.has(event.id) && event.status !== "ENDED" ? (
          <Button title={busy ? "…" : "Register"} size="sm" variant="outline" onPress={() => runAction(event.id, "register")} disabled={busy} />
        ) : registrationIds.has(event.id) ? (
          <Badge variant="success">Registered</Badge>
        ) : null}
      </View>
    );
  }

  const activeQuery =
    tab === "discover"
      ? discoverQuery
      : tab === "registrations"
        ? registrationsQuery
        : tab === "cpd"
          ? cpdQuery
          : discoverQuery;

  return (
    <Screen>
      <Header title="Impilo Live" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.intro}>
          Professional live events, CPD webinars, and governed broadcasts — discover sessions, track registrations, and host when assigned.
        </Text>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabRow}>
          {(
            [
              { id: "discover" as const, label: "Discover" },
              { id: "registrations" as const, label: "Registrations" },
              { id: "cpd" as const, label: "CPD" },
              { id: "certificates" as const, label: "Certificates" },
              { id: "host" as const, label: "Host" },
            ] as const
          ).map((t) => (
            <TouchableOpacity
              key={t.id}
              style={[styles.tabPill, tab === t.id && styles.tabPillActive]}
              onPress={() => setTab(t.id)}
              testID={`provider-live-tab-${t.id}`}
            >
              <Text style={[styles.tabText, tab === t.id && styles.tabTextActive]}>{t.label}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {activeQuery.isLoading ? <LoadingSpinner label="Loading…" /> : null}
        {activeQuery.isError ? <ErrorState message="Could not load live data." onRetry={() => activeQuery.refetch()} /> : null}

        {tab === "discover" ? (
          (discoverQuery.data ?? []).length === 0 && !discoverQuery.isLoading ? (
            <EmptyState icon="radio-outline" title="No events" description="No professional live events are scheduled." />
          ) : (
            (discoverQuery.data ?? []).map((event) => (
              <View key={event.id} style={styles.card} testID={`provider-live-event-${event.id}`}>
                <View style={styles.cardHeader}>
                  <Text style={styles.cardTitle}>{event.title}</Text>
                  <Badge variant={statusVariant(event.status)}>{event.status}</Badge>
                </View>
                <Text style={styles.cardMeta}>
                  {event.eventType.replace(/_/g, " ")}
                  {event.cpdEnabled ? " · CPD" : ""}
                  {event.startTime ? ` · ${new Date(event.startTime).toLocaleString()}` : ""}
                </Text>
                {renderEventActions(event, "discover")}
              </View>
            ))
          )
        ) : null}

        {tab === "registrations" ? (
          registeredEvents.length === 0 && !registrationsQuery.isLoading ? (
            <EmptyState title="No registrations" description="Register for a live session to track it here." />
          ) : (
            registeredEvents.map((event) => (
              <View key={event.id} style={styles.card}>
                <Text style={styles.cardTitle}>{event.title}</Text>
                <Text style={styles.cardMeta}>{event.status} · {event.eventType.replace(/_/g, " ")}</Text>
              </View>
            ))
          )
        ) : null}

        {tab === "cpd" ? (
          (cpdQuery.data ?? []).length === 0 && !cpdQuery.isLoading ? (
            <EmptyState title="No CPD history" description="Attend CPD-eligible live events to build evidence here." />
          ) : (
            (cpdQuery.data ?? []).map((row) => (
              <View key={row.id} style={styles.card}>
                <Text style={styles.cardTitle}>{row.title}</Text>
                <Text style={styles.cardMeta}>
                  {row.cpdPoints ? `${row.cpdPoints} pts · ` : ""}
                  {row.attendance?.totalWatchMinutes ?? 0} min watched
                  {row.attendance?.eligibleForCpd ? " · CPD eligible" : ""}
                </Text>
                <Text style={styles.cardMeta}>Status: {row.attendance?.completionStatus ?? "—"}</Text>
              </View>
            ))
          )
        ) : null}

        {tab === "certificates" ? (
          <>
            <Text style={styles.hint}>Select an ended CPD event to list issued certificates.</Text>
            {cpdEndedEvents.length === 0 ? (
              <EmptyState title="No ended CPD events" description="Certificates appear after events end." />
            ) : (
              cpdEndedEvents.map((event) => (
                <TouchableOpacity
                  key={event.id}
                  style={[styles.card, certEventId === event.id && styles.cardSelected]}
                  onPress={() => setCertEventId(event.id)}
                >
                  <Text style={styles.cardTitle}>{event.title}</Text>
                  <Text style={styles.cardMeta}>Tap to load certificates</Text>
                </TouchableOpacity>
              ))
            )}
            {certEventId && certificatesQuery.isLoading ? <LoadingSpinner label="Loading certificates…" /> : null}
            {(certificatesQuery.data ?? []).map((cert) => (
              <View key={cert.id} style={styles.card}>
                <Text style={styles.cardTitle}>{cert.certificateType}</Text>
                <Text style={styles.cardMeta}>Code: {cert.verificationCode}</Text>
                <Text style={styles.cardMeta}>
                  Issued {cert.issuedAt ? new Date(cert.issuedAt).toLocaleString() : "—"}
                </Text>
              </View>
            ))}
          </>
        ) : null}

        {tab === "host" ? (
          hostableEvents.length === 0 && !discoverQuery.isLoading ? (
            <EmptyState title="No host sessions" description="Scheduled or live events you can start will appear here." />
          ) : (
            hostableEvents.map((event) => (
              <View key={event.id} style={styles.card}>
                <View style={styles.cardHeader}>
                  <Text style={styles.cardTitle}>{event.title}</Text>
                  <Badge variant={statusVariant(event.status)}>{event.status}</Badge>
                </View>
                <Text style={styles.cardMeta}>Host controls (mobile partial — full moderation on web)</Text>
                {renderEventActions(event, "host")}
              </View>
            ))
          )
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.gray[50] },
  content: { padding: 16, gap: 12 },
  intro: { fontSize: 14, color: colors.gray[600], lineHeight: 20 },
  tabRow: { flexDirection: "row", gap: 8, paddingVertical: 4 },
  tabPill: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 20, backgroundColor: colors.gray[100] },
  tabPillActive: { backgroundColor: "#EDE9FE" },
  tabText: { fontSize: 12, color: colors.gray[500], fontWeight: "500" },
  tabTextActive: { color: "#6D28D9", fontWeight: "600" },
  card: {
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: colors.gray[200],
    gap: 6,
  },
  cardSelected: { borderColor: "#8B5CF6", backgroundColor: "#FAF5FF" },
  cardHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", gap: 8 },
  cardTitle: { flex: 1, fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
  row: { flexDirection: "row", gap: 8, marginTop: 4 },
  hint: { fontSize: 13, color: colors.gray[500] },
});
