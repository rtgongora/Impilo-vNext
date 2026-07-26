import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Screen, Header, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  discoverEvents,
  listMyRegistrations,
  listReplays,
  registerForEvent,
  type LiveEvent,
} from "../../services/impiloLiveService";
import { LiveEventScreen } from "./LiveEventScreen";

type DiscoverTab = "upcoming" | "registrations" | "replays";

function statusVariant(status: string): "success" | "warning" | "error" | "outline" {
  if (status === "LIVE") return "success";
  if (status === "ENDED" || status === "CANCELLED") return "error";
  if (status === "SCHEDULED") return "warning";
  return "outline";
}

function EventCard({
  event,
  registered,
  onOpen,
  onRegister,
  registering,
}: {
  event: LiveEvent;
  registered: boolean;
  onOpen: () => void;
  onRegister: () => void;
  registering: boolean;
}) {
  const isLive = event.status === "LIVE";
  const isEnded = event.status === "ENDED";

  return (
    <TouchableOpacity style={styles.card} onPress={onOpen} activeOpacity={0.85} testID={`live-event-${event.id}`}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>{event.title}</Text>
        <Badge variant={statusVariant(event.status)}>{event.status}</Badge>
      </View>
      <Text style={styles.cardMeta}>
        {event.eventType.replace(/_/g, " ")}
        {event.startTime ? ` · ${new Date(event.startTime).toLocaleString()}` : ""}
      </Text>
      {event.description ? <Text style={styles.cardDesc} numberOfLines={2}>{event.description}</Text> : null}
      <View style={styles.cardActions}>
        <Button title="Details" size="sm" variant="ghost" onPress={onOpen} />
        {!isEnded && !registered ? (
          <Button
            title={registering ? "Registering…" : isLive ? "Join" : "Register"}
            size="sm"
            variant="primary"
            onPress={onRegister}
            disabled={registering}
          />
        ) : registered ? (
          <Badge variant="success">Registered</Badge>
        ) : null}
      </View>
    </TouchableOpacity>
  );
}

export function LiveDiscoverScreen() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<DiscoverTab>("upcoming");
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
  const [registeringId, setRegisteringId] = useState<string | null>(null);

  const discoverQuery = useQuery({
    queryKey: ["citizen-live-discover"],
    queryFn: () => discoverEvents("CITIZEN"),
    staleTime: 20_000,
  });

  const registrationsQuery = useQuery({
    queryKey: ["citizen-live-registrations"],
    queryFn: () => listMyRegistrations(),
    staleTime: 15_000,
  });

  const replaysQuery = useQuery({
    queryKey: ["citizen-live-replays"],
    queryFn: () => listReplays(),
    staleTime: 20_000,
  });

  const registerMutation = useMutation({
    mutationFn: (eventId: string) => registerForEvent(eventId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["citizen-live-registrations"] });
      queryClient.invalidateQueries({ queryKey: ["citizen-live-discover"] });
    },
  });

  const registrationIds = useMemo(
    () => new Set((registrationsQuery.data ?? []).map((r) => r.eventId)),
    [registrationsQuery.data],
  );

  const upcomingEvents = useMemo(() => {
    const events = discoverQuery.data ?? [];
    return events.filter((e) => e.status === "SCHEDULED" || e.status === "LIVE");
  }, [discoverQuery.data]);

  const registeredEvents = useMemo(() => {
    const byId = new Map((discoverQuery.data ?? []).map((e) => [e.id, e]));
    const replayById = new Map((replaysQuery.data ?? []).map((e) => [e.id, e]));
    return (registrationsQuery.data ?? [])
      .map((r) => byId.get(r.eventId) ?? replayById.get(r.eventId))
      .filter((e): e is LiveEvent => Boolean(e));
  }, [discoverQuery.data, replaysQuery.data, registrationsQuery.data]);

  const replayEvents = replaysQuery.data ?? [];

  const activeQuery =
    tab === "upcoming" ? discoverQuery : tab === "registrations" ? registrationsQuery : replaysQuery;

  const eventsToShow =
    tab === "upcoming" ? upcomingEvents : tab === "registrations" ? registeredEvents : replayEvents;

  if (selectedEventId) {
    return <LiveEventScreen eventId={selectedEventId} onBack={() => setSelectedEventId(null)} />;
  }

  async function handleRegister(eventId: string) {
    setRegisteringId(eventId);
    try {
      await registerMutation.mutateAsync(eventId);
    } finally {
      setRegisteringId(null);
    }
  }

  return (
    <Screen>
      <Header title="Impilo Live" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.intro}>
          Discover governed live health talks, webinars, and community broadcasts — register, join, and ask questions in real time.
        </Text>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabRow}>
          {(
            [
              { id: "upcoming" as const, label: "Upcoming" },
              { id: "registrations" as const, label: "My registrations" },
              { id: "replays" as const, label: "Replays" },
            ] as const
          ).map((t) => (
            <TouchableOpacity
              key={t.id}
              style={[styles.tabPill, tab === t.id && styles.tabPillActive]}
              onPress={() => setTab(t.id)}
              testID={`live-tab-${t.id}`}
            >
              <Text style={[styles.tabText, tab === t.id && styles.tabTextActive]}>{t.label}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {activeQuery.isLoading ? <LoadingSpinner label="Loading events…" /> : null}
        {activeQuery.isError ? (
          <ErrorState message="Could not load live events." onRetry={() => activeQuery.refetch()} />
        ) : null}

        {!activeQuery.isLoading && eventsToShow.length === 0 ? (
          <EmptyState
            icon="radio-outline"
            title={tab === "replays" ? "No replays yet" : "No events"}
            description={
              tab === "registrations"
                ? "Register for an upcoming session to see it here."
                : "Check back soon for new live health sessions."
            }
          />
        ) : null}

        {eventsToShow.map((event) => (
          <EventCard
            key={event.id}
            event={event}
            registered={registrationIds.has(event.id)}
            onOpen={() => setSelectedEventId(event.id)}
            onRegister={() => handleRegister(event.id)}
            registering={registeringId === event.id}
          />
        ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.gray[50] },
  content: { padding: 16, gap: 12 },
  intro: { fontSize: 14, color: colors.gray[600], lineHeight: 20 },
  tabRow: { flexDirection: "row", gap: 8, paddingVertical: 4 },
  tabPill: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: colors.gray[100],
  },
  tabPillActive: { backgroundColor: "#EDE9FE" },
  tabText: { fontSize: 13, color: colors.gray[500], fontWeight: "500" },
  tabTextActive: { color: "#6D28D9", fontWeight: "600" },
  card: {
    backgroundColor: "#FFFFFF",
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: colors.gray[200],
    gap: 6,
  },
  cardHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", gap: 8 },
  cardTitle: { flex: 1, fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
  cardDesc: { fontSize: 13, color: colors.gray[700] },
  cardActions: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 4 },
});
