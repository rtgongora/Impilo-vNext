/**
 * TheatreCaseScreen — Perioperative case actions on mobile.
 *
 * Wires existing procedureService theatre helpers (readiness, book, start, note draft/sign,
 * PACU disposition, cancel, safety events, death). Case id IS the procedure episode id.
 * The episode wizard (pre-op / consent / WHO checklist) stays on TheatreProcedureScreen.
 */
import React, { useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TextInput,
  StyleSheet,
  Alert,
  TouchableOpacity,
} from "react-native";
import {
  Screen,
  Header,
  Button,
  Badge,
  LoadingSpinner,
  ErrorState,
  colors,
} from "@impilo/mobile-design-system";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getTheatreCase,
  evaluateTheatreReadiness,
  bookTheatreCase,
  startTheatreCase,
  draftTheatreNote,
  signTheatreNote,
  getTheatreNote,
  recordTheatrePacuDisposition,
  cancelTheatreCase,
  reportTheatreSafetyEvent,
  listTheatreSafetyEvents,
  routeTheatreDeath,
} from "../../services/procedureService";

type Props = {
  caseId: string;
  onBack: () => void;
  onOpenProcedure?: () => void;
};

type ReadinessResult = {
  bookable?: boolean;
  blockers?: Array<{ code?: string; message?: string }>;
  checks?: Array<{ domain?: string; status?: string; owner_service?: string }>;
};

function field(row: Record<string, unknown> | undefined, ...keys: string[]): string {
  if (!row) return "";
  for (const k of keys) {
    const v = row[k];
    if (v != null && String(v).length > 0) return String(v);
  }
  return "";
}

export function TheatreCaseScreen({ caseId, onBack, onOpenProcedure }: Props) {
  const qc = useQueryClient();
  const [readiness, setReadiness] = useState<ReadinessResult | null>(null);
  const [performedProcedure, setPerformedProcedure] = useState("");
  const [findings, setFindings] = useState("");
  const [postopPlan, setPostopPlan] = useState("");
  const [signedProviderId, setSignedProviderId] = useState("");
  const [cancelReason, setCancelReason] = useState("");
  const [safetyCategory, setSafetyCategory] = useState("NEAR_MISS");
  const [safetyDescription, setSafetyDescription] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const caseQuery = useQuery({
    queryKey: ["theatre-case", caseId],
    queryFn: () => getTheatreCase(caseId),
    enabled: !!caseId,
  });

  const safetyQuery = useQuery({
    queryKey: ["theatre-safety", caseId],
    queryFn: () => listTheatreSafetyEvents(caseId),
    enabled: !!caseId && caseQuery.isSuccess,
  });

  const noteQuery = useQuery({
    queryKey: ["theatre-note", caseId],
    queryFn: () => getTheatreNote(caseId),
    enabled: !!caseId && caseQuery.isSuccess,
    retry: false,
  });

  const refresh = () => {
    void caseQuery.refetch();
    void safetyQuery.refetch();
    void noteQuery.refetch();
    void qc.invalidateQueries({ queryKey: ["theatre-queue"] });
  };

  const run = useMutation({
    mutationFn: async (fn: () => Promise<unknown>) => fn(),
    onSuccess: () => {
      setActionError(null);
      refresh();
    },
    onError: (e: unknown) => {
      const msg =
        e && typeof e === "object" && "message" in e
          ? String((e as { message?: string }).message)
          : "Action failed. Please try again.";
      setActionError(msg);
    },
  });

  if (caseQuery.isLoading) {
    return (
      <Screen>
        <Header title="Theatre Case" />
        <LoadingSpinner />
      </Screen>
    );
  }

  if (caseQuery.isError || !caseQuery.data) {
    return (
      <Screen>
        <Header title="Theatre Case" />
        <TouchableOpacity onPress={onBack} style={styles.backBtn}>
          <Text style={styles.back}>← Theatre queue</Text>
        </TouchableOpacity>
        <ErrorState
          testID="theatre-case-error"
          title="Theatre case unavailable"
          message="Could not load this theatre case. Do not assume readiness, consent, or checklist state — retry when the service is reachable."
          onRetry={() => void caseQuery.refetch()}
        />
      </Screen>
    );
  }

  const detail = caseQuery.data as Record<string, unknown>;
  const status = field(detail, "status");
  const procedureName = field(detail, "procedure_name", "procedureName") || "Theatre case";
  const episodeExists = Boolean(caseId && (detail.id || detail.episode_id || detail.episodeId));
  const note = noteQuery.data as Record<string, unknown> | undefined;
  const noteStatus = field(note, "status");
  const safetyEvents = Array.isArray(safetyQuery.data)
    ? (safetyQuery.data as Array<Record<string, unknown>>)
    : [];

  return (
    <Screen>
      <Header title="Theatre Case" />
      <ScrollView
        testID="theatre-case-screen"
        style={styles.wrap}
        contentContainerStyle={styles.pad}
      >
        <TouchableOpacity onPress={onBack} style={styles.backBtn}>
          <Text style={styles.back}>← Theatre queue</Text>
        </TouchableOpacity>

        <Text style={styles.title}>{procedureName}</Text>
        <View style={styles.metaRow}>
          <Badge label={status || "UNKNOWN"} variant={status === "COMPLETED" ? "success" : "warning"} />
          {field(detail, "triage_priority", "triagePriority") ? (
            <Badge label={field(detail, "triage_priority", "triagePriority")} variant="info" />
          ) : null}
        </View>
        <Text style={styles.meta}>
          Patient {field(detail, "patient_id", "patientId", "subject_cpid") || "—"} · Case {caseId}
        </Text>

        {episodeExists && onOpenProcedure ? (
          <Button
            testID="theatre-open-procedure"
            title="Open procedure episode wizard"
            variant="secondary"
            onPress={onOpenProcedure}
          />
        ) : null}

        {actionError ? (
          <Text testID="theatre-case-action-error" style={styles.errorText}>
            {actionError}
          </Text>
        ) : null}

        <Text style={styles.sub}>1. Readiness</Text>
        <Button
          testID="theatre-evaluate-readiness"
          title="Evaluate readiness"
          variant="secondary"
          disabled={run.isPending}
          onPress={() =>
            run.mutate(async () => {
              const r = (await evaluateTheatreReadiness(caseId)) as ReadinessResult;
              setReadiness(r);
              if (r.bookable === false) {
                Alert.alert(
                  "Not bookable",
                  (r.blockers ?? []).map((b) => b.message ?? b.code).join("; ") ||
                    "Readiness blockers remain.",
                );
              }
            })
          }
        />
        {readiness ? (
          <View testID="theatre-readiness-result" style={styles.readinessBox}>
            <Text style={styles.readinessLabel}>
              {readiness.bookable ? "Bookable" : "Blocked"}
            </Text>
            {(readiness.blockers ?? []).map((b, i) => (
              <Text key={`${b.code}-${i}`} style={styles.blocker}>
                · {b.message ?? b.code}
              </Text>
            ))}
          </View>
        ) : null}

        <Text style={styles.sub}>2. Book / start</Text>
        <Button
          testID="theatre-book"
          title="Book case"
          disabled={run.isPending}
          onPress={() => run.mutate(() => bookTheatreCase(caseId))}
        />
        <Button
          testID="theatre-start"
          title="Start case"
          disabled={run.isPending}
          onPress={() =>
            run.mutate(async () => {
              try {
                await startTheatreCase(caseId);
              } catch {
                Alert.alert(
                  "Cannot start",
                  "WHO checklist / readiness gates are enforced server-side. Resolve blockers first.",
                );
                throw new Error("Start blocked by server gates");
              }
            })
          }
        />

        <Text style={styles.sub}>3. Operative note</Text>
        {noteQuery.isError ? (
          <Text testID="theatre-note-unavailable" style={styles.errorText}>
            Operative note could not be loaded — do not assume there is no note yet.
          </Text>
        ) : noteStatus === "SIGNED" ? (
          <Text style={styles.hint}>Note signed ({noteStatus})</Text>
        ) : (
          <>
            <TextInput
              testID="theatre-note-procedure"
              style={styles.input}
              value={performedProcedure}
              onChangeText={setPerformedProcedure}
              placeholder="Performed procedure"
            />
            <TextInput
              testID="theatre-note-findings"
              style={[styles.input, styles.multiline]}
              value={findings}
              onChangeText={setFindings}
              placeholder="Findings"
              multiline
            />
            <TextInput
              testID="theatre-note-postop"
              style={[styles.input, styles.multiline]}
              value={postopPlan}
              onChangeText={setPostopPlan}
              placeholder="Post-op plan"
              multiline
            />
            <TextInput
              testID="theatre-note-signer"
              style={styles.input}
              value={signedProviderId}
              onChangeText={setSignedProviderId}
              placeholder="Signing provider id"
              autoCapitalize="none"
            />
            <Button
              testID="theatre-note-draft"
              title="Save note draft"
              variant="secondary"
              disabled={run.isPending || !performedProcedure.trim()}
              onPress={() =>
                run.mutate(() =>
                  draftTheatreNote(caseId, {
                    performedProcedure: performedProcedure.trim(),
                    findings: findings.trim() || undefined,
                    postopPlan: postopPlan.trim() || undefined,
                  }),
                )
              }
            />
            <Button
              testID="theatre-note-sign"
              title="Sign note"
              disabled={run.isPending || !signedProviderId.trim()}
              onPress={() =>
                run.mutate(() => signTheatreNote(caseId, signedProviderId.trim()))
              }
            />
          </>
        )}

        <Text style={styles.sub}>4. PACU disposition</Text>
        <Button
          testID="theatre-pacu-ward"
          title="PACU → Ward"
          variant="secondary"
          disabled={run.isPending}
          onPress={() =>
            run.mutate(() =>
              recordTheatrePacuDisposition(caseId, {
                disposition: "WARD",
                aldreteScore: 9,
              }),
            )
          }
        />

        <Text style={styles.sub}>5. Safety event</Text>
        <TextInput
          testID="theatre-safety-category"
          style={styles.input}
          value={safetyCategory}
          onChangeText={setSafetyCategory}
          placeholder="Category (e.g. NEAR_MISS)"
          autoCapitalize="characters"
        />
        <TextInput
          testID="theatre-safety-description"
          style={[styles.input, styles.multiline]}
          value={safetyDescription}
          onChangeText={setSafetyDescription}
          placeholder="Description"
          multiline
        />
        <Button
          testID="theatre-safety-report"
          title="Report safety event"
          variant="outline"
          disabled={run.isPending || !safetyDescription.trim()}
          onPress={() =>
            run.mutate(() =>
              reportTheatreSafetyEvent(caseId, {
                category: safetyCategory.trim() || "NEAR_MISS",
                description: safetyDescription.trim(),
              }),
            )
          }
        />
        {safetyQuery.isError ? (
          <Text testID="theatre-safety-unavailable" style={styles.errorText}>
            Safety events could not be loaded — do not assume there are none.
          </Text>
        ) : (
          safetyEvents.map((ev, i) => (
            <Text key={String(ev.id ?? i)} style={styles.safetyRow}>
              {String(ev.category ?? "EVENT")} · {String(ev.routed_owner ?? ev.routedOwner ?? "—")}
            </Text>
          ))
        )}

        <Text style={styles.sub}>6. Cancel</Text>
        <TextInput
          testID="theatre-cancel-reason"
          style={styles.input}
          value={cancelReason}
          onChangeText={setCancelReason}
          placeholder="Cancellation reason"
        />
        <Button
          testID="theatre-cancel"
          title="Cancel case"
          variant="outline"
          disabled={run.isPending || !cancelReason.trim()}
          onPress={() =>
            run.mutate(() => cancelTheatreCase(caseId, cancelReason.trim()))
          }
        />

        <Text style={styles.sub}>7. Death in theatre</Text>
        <Text style={styles.hint}>
          Routes to the PCT death pathway — theatre never owns the death case.
        </Text>
        <Button
          testID="theatre-death"
          title="Route death to PCT"
          variant="outline"
          disabled={run.isPending}
          onPress={() =>
            Alert.alert(
              "Route death?",
              "This opens the PCT death pathway for this case. Continue?",
              [
                { text: "Back", style: "cancel" },
                {
                  text: "Route",
                  style: "destructive",
                  onPress: () =>
                    run.mutate(() =>
                      routeTheatreDeath(caseId, { resuscitationAttempted: true }),
                    ),
                },
              ],
            )
          }
        />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1 },
  pad: { padding: 16, gap: 10, paddingBottom: 48 },
  backBtn: { marginBottom: 4 },
  back: { color: "#2563EB", fontSize: 14, fontWeight: "600" },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  metaRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  meta: { fontSize: 12, color: colors.gray[500] },
  sub: { fontSize: 14, fontWeight: "600", marginTop: 10, color: colors.gray[700] },
  hint: { fontSize: 13, color: colors.gray[500] },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    padding: 10,
    fontSize: 14,
    backgroundColor: "#FFF",
  },
  multiline: { minHeight: 72, textAlignVertical: "top" },
  readinessBox: {
    backgroundColor: "#FEF3C7",
    borderRadius: 8,
    padding: 10,
    gap: 4,
  },
  readinessLabel: { fontWeight: "700", color: colors.gray[800] },
  blocker: { fontSize: 12, color: colors.gray[700] },
  errorText: { color: colors.ui.error.main, fontSize: 13 },
  safetyRow: { fontSize: 12, color: colors.gray[600] },
});
