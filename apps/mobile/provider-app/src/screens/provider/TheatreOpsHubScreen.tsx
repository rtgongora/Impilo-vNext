/**
 * TheatreOpsHubScreen — Wave M4 ops surfaces for provider mobile.
 *
 * Readiness board, surgical referrals inbox + decide, waitlist, theatre lists
 * (+ session detail), utilisation report (read-only). Case-scoped anaesthesia /
 * CSSD / controlled-drugs live on TheatreCaseOpsScreen.
 * List GET failures surface unavailable — never invent empty boards.
 */
import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
  TextInput,
} from "react-native";
import { Screen, Header, Badge, LoadingSpinner, ErrorState, Button, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchTheatreReadinessBoard,
  listSurgicalReferralWorklist,
  decideSurgicalReferral,
  listSurgicalWaitlist,
  listTheatreSessions,
  getTheatreSession,
  runTheatreUtilisationReport,
} from "../../services/theatreOpsService";
import { TheatreCaseOpsScreen } from "./TheatreCaseOpsScreen";

type OpsView =
  | "hub"
  | "readiness"
  | "referrals"
  | "waitlist"
  | "lists"
  | "session"
  | "utilisation"
  | "caseOps";

type Props = {
  onBack: () => void;
  initialCaseId?: string | null;
};

function str(v: unknown, fallback = "—"): string {
  if (v == null || v === "") return fallback;
  return String(v);
}

export function TheatreOpsHubScreen({ onBack, initialCaseId = null }: Props) {
  const [view, setView] = useState<OpsView>(initialCaseId ? "caseOps" : "hub");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [caseId, setCaseId] = useState<string | null>(initialCaseId);
  const [caseIdDraft, setCaseIdDraft] = useState(initialCaseId ?? "");

  if (view === "caseOps" && caseId) {
    return (
      <TheatreCaseOpsScreen
        caseId={caseId}
        onBack={() => {
          setView("hub");
          setCaseId(null);
        }}
      />
    );
  }

  if (view === "readiness") {
    return <ReadinessBoardView onBack={() => setView("hub")} />;
  }
  if (view === "referrals") {
    return <ReferralsInboxView onBack={() => setView("hub")} />;
  }
  if (view === "waitlist") {
    return <WaitlistView onBack={() => setView("hub")} />;
  }
  if (view === "lists") {
    return (
      <ListsView
        onBack={() => setView("hub")}
        onOpenSession={(id) => {
          setSessionId(id);
          setView("session");
        }}
      />
    );
  }
  if (view === "session" && sessionId) {
    return (
      <SessionDetailView
        sessionId={sessionId}
        onBack={() => {
          setSessionId(null);
          setView("lists");
        }}
      />
    );
  }
  if (view === "utilisation") {
    return <UtilisationView onBack={() => setView("hub")} />;
  }

  const links: { key: OpsView; title: string; subtitle: string; testID: string }[] = [
    {
      key: "readiness",
      title: "Readiness board",
      subtitle: "Per-case theatre-day readiness across domains",
      testID: "theatre-ops-readiness",
    },
    {
      key: "referrals",
      title: "Surgical referrals",
      subtitle: "Receiving-team inbox — accept funnels onto the waitlist",
      testID: "theatre-ops-referrals",
    },
    {
      key: "waitlist",
      title: "Surgical waitlist",
      subtitle: "WAITING cases sorted by wait days",
      testID: "theatre-ops-waitlist",
    },
    {
      key: "lists",
      title: "Theatre lists",
      subtitle: "OR sessions — open a session for detail",
      testID: "theatre-ops-lists",
    },
    {
      key: "utilisation",
      title: "Theatre utilisation",
      subtitle: "Read-only KPIs from theatre.* event projection",
      testID: "theatre-ops-utilisation",
    },
  ];

  return (
    <Screen>
      <Header title="Theatre ops" onBack={onBack} />
      <ScrollView testID="theatre-ops-hub" style={styles.container}>
        <Text style={styles.hint}>
          Ops board, referrals, lists and reports. Bedside OR actions stay on the case screen.
        </Text>
        {links.map((link) => (
          <TouchableOpacity
            key={link.key}
            testID={link.testID}
            style={styles.card}
            onPress={() => setView(link.key)}
          >
            <Text style={styles.cardTitle}>{link.title}</Text>
            <Text style={styles.cardMeta}>{link.subtitle}</Text>
          </TouchableOpacity>
        ))}

        <View style={styles.caseOpsBox} testID="theatre-ops-case-entry">
          <Text style={styles.cardTitle}>Case ops (anaesthesia / CSSD / drugs)</Text>
          <Text style={styles.cardMeta}>
            Enter a theatre case id to open anaesthesia chart and commodity registers.
          </Text>
          <TextInput
            testID="theatre-ops-case-id"
            style={styles.input}
            value={caseIdDraft}
            onChangeText={setCaseIdDraft}
            placeholder="Case / episode id"
            autoCapitalize="none"
          />
          <Button
            testID="theatre-ops-open-case"
            title="Open case ops"
            onPress={() => {
              const id = caseIdDraft.trim();
              if (!id) return;
              setCaseId(id);
              setView("caseOps");
            }}
          />
        </View>
      </ScrollView>
    </Screen>
  );
}

function ReadinessBoardView({ onBack }: { onBack: () => void }) {
  const q = useQuery({
    queryKey: ["theatre-ops", "readiness-board"],
    queryFn: () => fetchTheatreReadinessBoard(),
    retry: false,
  });

  if (q.isLoading) {
    return (
      <Screen>
        <Header title="Readiness board" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (q.isError) {
    return (
      <Screen>
        <Header title="Readiness board" onBack={onBack} />
        <ErrorState
          testID="theatre-readiness-board-error"
          title="Readiness board unavailable"
          message="Could not load the board. Do not treat this as an empty theatre day — retry when the service is reachable."
          onRetry={() => void q.refetch()}
        />
      </Screen>
    );
  }

  const rows = q.data ?? [];
  return (
    <Screen>
      <Header title="Readiness board" onBack={onBack} />
      <ScrollView
        testID="theatre-readiness-board"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={q.isRefetching} onRefresh={() => void q.refetch()} />}
      >
        {rows.length === 0 ? (
          <Text testID="theatre-readiness-board-empty" style={styles.empty}>
            No theatre cases on the board for this facility today.
          </Text>
        ) : (
          rows.map((row, i) => {
            const c = (row.case ?? {}) as Record<string, unknown>;
            const board = (row.board ?? {}) as Record<string, unknown>;
            const caseId = str(c.episode_id ?? c.id, `row-${i}`);
            return (
              <View key={caseId} style={styles.card} testID={`theatre-readiness-row-${caseId}`}>
                <Text style={styles.cardTitle}>{str(c.procedure_name, "Procedure")}</Text>
                <Text style={styles.cardMeta}>{str(c.patient_id)}</Text>
                <Badge label={str(board.board_state, "Unknown")} variant="info" />
              </View>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

function ReferralsInboxView({ onBack }: { onBack: () => void }) {
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ["theatre-ops", "referrals", "PENDING"],
    queryFn: () => listSurgicalReferralWorklist({ decision: "PENDING" }),
    retry: false,
  });
  const decide = useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: string }) =>
      decideSurgicalReferral(id, decision),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["theatre-ops", "referrals"] }),
  });

  if (q.isLoading) {
    return (
      <Screen>
        <Header title="Surgical referrals" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (q.isError) {
    return (
      <Screen>
        <Header title="Surgical referrals" onBack={onBack} />
        <ErrorState
          testID="theatre-referrals-error"
          title="Referral worklist unavailable"
          message="Could not load surgical referrals. Do not treat this as an empty inbox."
          onRetry={() => void q.refetch()}
        />
      </Screen>
    );
  }

  const rows = q.data ?? [];
  return (
    <Screen>
      <Header title="Surgical referrals" onBack={onBack} />
      <ScrollView
        testID="theatre-referrals-inbox"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={q.isRefetching} onRefresh={() => void q.refetch()} />}
      >
        {decide.isError ? (
          <Text style={styles.danger} testID="theatre-referrals-decide-error">
            Decision failed — the referral was not updated.
          </Text>
        ) : null}
        {rows.length === 0 ? (
          <Text testID="theatre-referrals-empty" style={styles.empty}>
            No pending surgical referrals.
          </Text>
        ) : (
          rows.map((r, i) => {
            const id = str(r.id, `ref-${i}`);
            return (
              <View key={id} style={styles.card} testID={`theatre-referral-row-${id}`}>
                <Text style={styles.cardTitle}>{str(r.patientId ?? r.patient_id)}</Text>
                <Text style={styles.cardMeta}>
                  {str(r.intendedProcedureZiboCode ?? r.intended_procedure_zibo_code)} ·{" "}
                  {str(r.targetSpecialty ?? r.target_specialty)} ·{" "}
                  {str(r.clinicalPriority ?? r.clinical_priority, "P4")}
                </Text>
                <View style={styles.rowActions}>
                  <Button
                    testID={`theatre-referral-accept-${id}`}
                    title="Accept"
                    onPress={() => decide.mutate({ id, decision: "ACCEPTED" })}
                  />
                  <Button
                    testID={`theatre-referral-decline-${id}`}
                    title="Decline"
                    variant="secondary"
                    onPress={() => decide.mutate({ id, decision: "DECLINED" })}
                  />
                </View>
              </View>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

function WaitlistView({ onBack }: { onBack: () => void }) {
  const q = useQuery({
    queryKey: ["theatre-ops", "waitlist"],
    queryFn: () => listSurgicalWaitlist({ status: "WAITING", sort: "wait_days" }),
    retry: false,
  });

  if (q.isLoading) {
    return (
      <Screen>
        <Header title="Surgical waitlist" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (q.isError) {
    return (
      <Screen>
        <Header title="Surgical waitlist" onBack={onBack} />
        <ErrorState
          testID="theatre-waitlist-error"
          title="Waitlist unavailable"
          message="Could not load the surgical waitlist. Do not treat this as an empty list."
          onRetry={() => void q.refetch()}
        />
      </Screen>
    );
  }

  const rows = q.data ?? [];
  return (
    <Screen>
      <Header title="Surgical waitlist" onBack={onBack} />
      <ScrollView
        testID="theatre-waitlist"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={q.isRefetching} onRefresh={() => void q.refetch()} />}
      >
        {rows.length === 0 ? (
          <Text testID="theatre-waitlist-empty" style={styles.empty}>
            No WAITING cases on the surgical waitlist.
          </Text>
        ) : (
          rows.map((r, i) => {
            const id = str(r.id, `wl-${i}`);
            return (
              <View key={id} style={styles.card} testID={`theatre-waitlist-row-${id}`}>
                <Text style={styles.cardTitle}>{str(r.patientId ?? r.patient_id)}</Text>
                <Text style={styles.cardMeta}>
                  {str(r.procedureName ?? r.procedure_name ?? r.zibo)} · wait{" "}
                  {str(r.waitDays ?? r.wait_days, "?")}d · {str(r.priority ?? r.clinical_priority)}
                </Text>
              </View>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

function ListsView({
  onBack,
  onOpenSession,
}: {
  onBack: () => void;
  onOpenSession: (id: string) => void;
}) {
  const q = useQuery({
    queryKey: ["theatre-ops", "sessions"],
    queryFn: () => listTheatreSessions(),
    retry: false,
  });

  if (q.isLoading) {
    return (
      <Screen>
        <Header title="Theatre lists" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (q.isError) {
    return (
      <Screen>
        <Header title="Theatre lists" onBack={onBack} />
        <ErrorState
          testID="theatre-lists-error"
          title="Theatre lists unavailable"
          message="Could not load OR sessions. Do not treat this as no sessions scheduled."
          onRetry={() => void q.refetch()}
        />
      </Screen>
    );
  }

  const rows = q.data ?? [];
  return (
    <Screen>
      <Header title="Theatre lists" onBack={onBack} />
      <ScrollView
        testID="theatre-lists"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={q.isRefetching} onRefresh={() => void q.refetch()} />}
      >
        {rows.length === 0 ? (
          <Text testID="theatre-lists-empty" style={styles.empty}>
            No OR sessions yet.
          </Text>
        ) : (
          rows.map((s, i) => {
            const id = str(s.id, `sess-${i}`);
            return (
              <TouchableOpacity
                key={id}
                testID={`theatre-session-row-${id}`}
                style={styles.card}
                onPress={() => s.id && onOpenSession(String(s.id))}
                disabled={!s.id}
              >
                <Text style={styles.cardTitle}>{str(s.sessionDate ?? s.session_date)}</Text>
                <Text style={styles.cardMeta}>
                  {str(s.facilityId ?? s.facility_id)} · {str(s.slot)} · {str(s.status)}
                </Text>
              </TouchableOpacity>
            );
          })
        )}
      </ScrollView>
    </Screen>
  );
}

function SessionDetailView({ sessionId, onBack }: { sessionId: string; onBack: () => void }) {
  const q = useQuery({
    queryKey: ["theatre-ops", "session", sessionId],
    queryFn: () => getTheatreSession(sessionId),
    retry: false,
  });

  if (q.isLoading) {
    return (
      <Screen>
        <Header title="Theatre list session" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (q.isError) {
    return (
      <Screen>
        <Header title="Theatre list session" onBack={onBack} />
        <ErrorState
          testID="theatre-session-error"
          title="Session unavailable"
          message="Could not load this OR session. Do not treat this as an empty list."
          onRetry={() => void q.refetch()}
        />
      </Screen>
    );
  }

  const session = q.data ?? {};
  const items = Array.isArray(session.items) ? (session.items as Record<string, unknown>[]) : [];
  return (
    <Screen>
      <Header title="Theatre list session" onBack={onBack} />
      <ScrollView testID="theatre-session-detail" style={styles.container}>
        <Text style={styles.cardTitle}>{str(session.sessionDate ?? session.session_date)}</Text>
        <Text style={styles.cardMeta}>
          {str(session.facilityId ?? session.facility_id)} · {str(session.status)}
        </Text>
        {items.length === 0 ? (
          <Text testID="theatre-session-items-empty" style={styles.empty}>
            No items on this session list yet.
          </Text>
        ) : (
          items.map((item, i) => (
            <View key={str(item.id, `item-${i}`)} style={styles.card}>
              <Text style={styles.cardTitle}>{str(item.patientId ?? item.patient_id)}</Text>
              <Text style={styles.cardMeta}>
                {str(item.zibo)} · {str(item.status)}
              </Text>
            </View>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

function UtilisationView({ onBack }: { onBack: () => void }) {
  const q = useQuery({
    queryKey: ["theatre-ops", "utilisation"],
    queryFn: () => runTheatreUtilisationReport(),
    retry: false,
  });

  const tiles = useMemo(
    () => [
      { key: "total_cases", label: "Total cases" },
      { key: "completed_cases", label: "Completed" },
      { key: "cancelled_cases", label: "Cancelled" },
      { key: "cancellation_rate_pct", label: "Cancellation rate %" },
      { key: "returns_to_theatre", label: "Returns to theatre" },
      { key: "complication_cases", label: "Complications" },
      { key: "avg_theatre_minutes", label: "Avg theatre minutes" },
      { key: "blood_units_used", label: "Blood units" },
    ],
    [],
  );

  if (q.isLoading) {
    return (
      <Screen>
        <Header title="Theatre utilisation" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (q.isError) {
    return (
      <Screen>
        <Header title="Theatre utilisation" onBack={onBack} />
        <ErrorState
          testID="theatre-utilisation-error"
          title="Utilisation report unavailable"
          message="Could not run theatre-utilisation. Do not treat missing tiles as zero activity."
          onRetry={() => void q.refetch()}
        />
      </Screen>
    );
  }

  const metrics = q.data ?? {};
  return (
    <Screen>
      <Header title="Theatre utilisation" onBack={onBack} />
      <ScrollView
        testID="theatre-utilisation"
        style={styles.container}
        refreshControl={<RefreshControl refreshing={q.isRefetching} onRefresh={() => void q.refetch()} />}
      >
        <Text style={styles.hint}>Read-only KPIs from the seeded theatre-utilisation report.</Text>
        {tiles.map((t) => (
          <View key={t.key} style={styles.card} testID={`theatre-utilisation-${t.key}`}>
            <Text style={styles.cardMeta}>{t.label}</Text>
            <Text style={styles.cardTitle}>{str(metrics[t.key], "—")}</Text>
          </View>
        ))}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  hint: { fontSize: 13, color: colors.gray[600], marginBottom: 12 },
  empty: { padding: 24, textAlign: "center", color: colors.gray[500], fontSize: 14 },
  danger: {
    marginBottom: 10,
    padding: 10,
    borderRadius: 8,
    backgroundColor: "#FEF2F2",
    color: "#991B1B",
    fontSize: 13,
  },
  card: {
    backgroundColor: "#FFF",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 14,
    marginBottom: 8,
    gap: 6,
  },
  cardTitle: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
  rowActions: { flexDirection: "row", gap: 8, marginTop: 8 },
  caseOpsBox: {
    marginTop: 8,
    marginBottom: 24,
    padding: 14,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    backgroundColor: "#FFF",
    gap: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: colors.gray[900],
    backgroundColor: "#FFF",
  },
});
