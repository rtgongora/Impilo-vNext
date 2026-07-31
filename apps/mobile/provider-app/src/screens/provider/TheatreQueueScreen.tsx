/**
 * TheatreQueueScreen — Top-level theatre board for provider mobile.
 *
 * Lists real theatre cases via BFF GET /internal/v1/theatre/queue (listTheatreQueue).
 * A failed list must never render as an empty board — that would look like "no cases".
 * Selecting a row opens TheatreCaseScreen; the procedure-episode wizard remains on
 * TheatreProcedureScreen and is reached from the case when the episode exists.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl } from "react-native";
import { Screen, Header, Badge, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { listTheatreQueue } from "../../services/procedureService";
import { TheatreCaseScreen } from "./TheatreCaseScreen";
import { TheatreProcedureScreen } from "./TheatreProcedureScreen";

type TheatreCaseRow = {
  id?: string;
  patient_id?: string;
  procedure_name?: string;
  status?: string;
  triage_priority?: string;
  scheduled_at?: string;
  theatre_room_id?: string;
  surgeon_id?: string;
};

function asRows(data: unknown): TheatreCaseRow[] {
  return Array.isArray(data) ? (data as TheatreCaseRow[]) : [];
}

export function TheatreQueueScreen() {
  const [caseId, setCaseId] = useState<string | null>(null);
  const [showProcedure, setShowProcedure] = useState(false);

  const queueQuery = useQuery({
    queryKey: ["theatre-queue"],
    queryFn: listTheatreQueue,
  });

  if (showProcedure && caseId) {
    return (
      <TheatreProcedureScreen
        episodeId={caseId}
        onBack={() => setShowProcedure(false)}
      />
    );
  }

  if (caseId) {
    return (
      <TheatreCaseScreen
        caseId={caseId}
        onBack={() => setCaseId(null)}
        onOpenProcedure={() => setShowProcedure(true)}
      />
    );
  }

  if (queueQuery.isLoading) {
    return (
      <Screen>
        <Header title="Theatre" />
        <LoadingSpinner />
      </Screen>
    );
  }

  // Honesty: never paint an empty queue when the list call failed.
  if (queueQuery.isError) {
    return (
      <Screen>
        <Header title="Theatre" />
        <ErrorState
          testID="theatre-queue-error"
          title="Theatre queue unavailable"
          message="Could not load the theatre list. Do not treat this as an empty board — retry when the service is reachable."
          onRetry={() => void queueQuery.refetch()}
        />
      </Screen>
    );
  }

  const rows = asRows(queueQuery.data);

  return (
    <Screen>
      <Header title="Theatre" />
      <View testID="theatre-queue-screen" style={styles.container}>
        <Text style={styles.hint}>
          {rows.length === 0
            ? "No active theatre cases on the board."
            : `${rows.length} active case${rows.length === 1 ? "" : "s"}`}
        </Text>
        <ScrollView
          refreshControl={
            <RefreshControl
              refreshing={queueQuery.isRefetching}
              onRefresh={() => void queueQuery.refetch()}
            />
          }
        >
          {rows.length === 0 ? (
            <Text testID="theatre-queue-empty" style={styles.empty}>
              The theatre queue is empty. Cases appear here after an OROS PROCEDURE order is intaken.
            </Text>
          ) : (
            rows.map((item, index) => (
              <TouchableOpacity
                key={String(item.id ?? `row-${index}`)}
                testID={`theatre-queue-row-${item.id ?? "unknown"}`}
                style={styles.card}
                onPress={() => item.id && setCaseId(String(item.id))}
                disabled={!item.id}
              >
                <View style={styles.cardBody}>
                  <Text style={styles.cardTitle}>{String(item.procedure_name ?? "Procedure")}</Text>
                  <Text style={styles.cardMeta}>
                    {String(item.patient_id ?? "—")}
                    {item.scheduled_at ? ` · ${String(item.scheduled_at)}` : ""}
                  </Text>
                </View>
                <View style={styles.badges}>
                  {item.triage_priority ? (
                    <Badge label={String(item.triage_priority)} variant="warning" />
                  ) : null}
                  <Badge
                    label={String(item.status ?? "UNKNOWN")}
                    variant={item.status === "IN_PROGRESS" ? "info" : "default"}
                  />
                </View>
              </TouchableOpacity>
            ))
          )}
        </ScrollView>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  hint: { fontSize: 13, color: colors.gray[600], marginBottom: 12 },
  empty: { padding: 24, textAlign: "center", color: colors.gray[500], fontSize: 14 },
  card: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: "#FFF",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 14,
    marginBottom: 8,
    gap: 10,
  },
  cardBody: { flex: 1, gap: 4 },
  cardTitle: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
  badges: { gap: 6, alignItems: "flex-end" },
});
