/**
 * EmergencyEpisodeBoardScreen — facility episode board + open new episode.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, TouchableOpacity, StyleSheet, Alert } from "react-native";
import { Button, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAppStore } from "../../stores/appStore";
import {
  listEmergencyEpisodes,
  openEmergencyEpisode,
  type EmergencyEpisodeRow,
} from "../../services/emergencyEpisodeService";
import { EmergencyOutboxBadge } from "../../components/emergency/EmergencyOutboxBadge";
import { EmergencyEpisodeDetailScreen } from "./EmergencyEpisodeDetailScreen";

const ENTRY_ROUTES = ["WALK_IN", "AMBULANCE", "REFERRAL", "TRANSFER", "POLICE", "SELF_PRESENTATION"];

export function EmergencyEpisodeBoardScreen({
  selectedEpisodeId,
  onSelectEpisode,
}: {
  selectedEpisodeId: string | null;
  onSelectEpisode: (id: string | null) => void;
}) {
  const { facilityId } = useAppStore();
  const qc = useQueryClient();
  const [subjectCpid, setSubjectCpid] = useState("");
  const [entryRoute, setEntryRoute] = useState("WALK_IN");

  const { data: episodes, isLoading, isError, refetch } = useQuery({
    queryKey: ["emergency-episode-board", facilityId],
    queryFn: () => listEmergencyEpisodes(facilityId!),
    enabled: !!facilityId,
    refetchInterval: 15_000,
  });

  const openMut = useMutation({
    mutationFn: () =>
      openEmergencyEpisode({
        facilityId: facilityId!,
        entryRoute,
        subjectCpid: subjectCpid.trim() || undefined,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["emergency-episode-board"] });
      setSubjectCpid("");
      Alert.alert("Opened", "Emergency episode opened on the facility board.");
    },
    onError: () => Alert.alert("Error", "Could not open episode"),
  });

  if (selectedEpisodeId) {
    return (
      <EmergencyEpisodeDetailScreen
        episodeId={selectedEpisodeId}
        onBack={() => onSelectEpisode(null)}
      />
    );
  }

  if (!facilityId) {
    return (
      <View style={s.pad}>
        <Text style={s.muted}>Select a facility to view the emergency episode board.</Text>
      </View>
    );
  }

  return (
    <ScrollView style={s.scroll} contentContainerStyle={s.pad}>
      <EmergencyOutboxBadge onFlushComplete={() => void refetch()} />

      <Text style={s.h2}>Open episodes</Text>
      {isLoading ? <LoadingSpinner /> : null}
      {isError ? <ErrorState message="Could not load episode board" onRetry={() => void refetch()} /> : null}
      {(episodes ?? []).map((ep: EmergencyEpisodeRow) => (
        <TouchableOpacity
          key={String(ep.episode_id)}
          style={s.card}
          onPress={() => onSelectEpisode(String(ep.episode_id))}
        >
          <View style={s.row}>
            <Text style={s.cardTitle}>{String(ep.episode_reference ?? ep.episode_id)}</Text>
            <Badge variant="outline">{String(ep.state ?? "OPEN")}</Badge>
          </View>
          <Text style={s.muted}>
            {String(ep.entry_route ?? "")} · CPID {String(ep.subject_cpid ?? "unknown")}
          </Text>
          {ep.current_location ? <Text style={s.muted}>Location: {String(ep.current_location)}</Text> : null}
        </TouchableOpacity>
      ))}
      {!isLoading && !isError && (episodes ?? []).length === 0 ? (
        <Text style={s.muted}>No open episodes on this board.</Text>
      ) : null}

      <Text style={[s.h2, { marginTop: 16 }]}>Open new episode</Text>
      <Text style={s.label}>Subject CPID (optional for unknown)</Text>
      <TextInput value={subjectCpid} onChangeText={setSubjectCpid} style={s.input} placeholder="CPID" />
      <Text style={s.label}>Entry route</Text>
      <View style={s.chipRow}>
        {ENTRY_ROUTES.map((r) => (
          <TouchableOpacity key={r} onPress={() => setEntryRoute(r)} style={[s.chip, entryRoute === r && s.chipOn]}>
            <Text style={[s.chipText, entryRoute === r && s.chipTextOn]}>{r.replace(/_/g, " ")}</Text>
          </TouchableOpacity>
        ))}
      </View>
      <Button title="Open episode" onPress={() => openMut.mutate()} loading={openMut.isPending} />
    </ScrollView>
  );
}

const s = StyleSheet.create({
  scroll: { flex: 1 },
  pad: { padding: 16, gap: 10, paddingBottom: 32 },
  h2: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  label: { fontSize: 13, color: colors.gray[700] },
  muted: { fontSize: 12, color: colors.gray[500] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  card: { borderWidth: 1, borderColor: colors.gray[200], borderRadius: 8, padding: 12, gap: 4 },
  cardTitle: { fontSize: 14, fontWeight: "600" },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  chipRow: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  chip: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 14, backgroundColor: colors.gray[100] },
  chipOn: { backgroundColor: colors.ui.error.light },
  chipText: { fontSize: 11, color: colors.gray[700] },
  chipTextOn: { fontWeight: "600", color: colors.ui.error.main },
});
