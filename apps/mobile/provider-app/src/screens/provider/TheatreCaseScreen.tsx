/**
 * TheatreCaseScreen — Perioperative case actions + bedside OR panels on mobile.
 *
 * Wires procedureService theatre helpers (readiness, book, start, note, PACU, cancel,
 * safety, death) and Wave M2 OR panels (site/side, specimens custody, counts, implants,
 * blood, emergency consent, return-to-theatre). Case id IS the procedure episode id.
 * List GET failures surface as unavailable — never collapse to empty/none.
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
  confirmTheatreSiteSide,
  listTheatreSpecimens,
  collectTheatreSpecimen,
  confirmTheatreSpecimenLabel,
  receiveTheatreSpecimen,
  assessTheatreSpecimenAdequacy,
  listTheatreCounts,
  recordTheatreCount,
  listTheatreImplants,
  recordTheatreImplant,
  removeTheatreImplant,
  reviseTheatreImplant,
  traceImplantRecall,
  listTheatreBlood,
  theatreBloodAction,
  activateEmergencyTheatreCase,
  recordTheatreEmergencyConsentException,
  recordReturnToTheatre,
  completeChecklistPhase,
  getProcedureEpisode,
  listTheatrePacuObservations,
  recordTheatrePacuObservation,
  getTheatrePacuReadiness,
  escalateTheatrePacu,
  dischargeTheatrePacu,
} from "../../services/procedureService";

type Props = {
  caseId: string;
  onBack: () => void;
  onOpenProcedure?: () => void;
  /** Wave M4 — anaesthesia chart / CSSD / controlled-drug register. */
  onOpenCaseOps?: () => void;
};

type ReadinessResult = {
  bookable?: boolean;
  blockers?: Array<{ code?: string; message?: string }>;
  checks?: Array<{ domain?: string; status?: string; owner_service?: string }>;
};

const SITE_LATERALITY = ["LEFT", "RIGHT", "BILATERAL", "MIDLINE", "NOT_APPLICABLE", "MULTIPLE"] as const;
const RETURN_CATEGORIES = [
  "HAEMORRHAGE",
  "SEPSIS",
  "ANASTOMOTIC_LEAK",
  "WOUND_DEHISCENCE",
  "ISCHAEMIA",
  "OBSTRUCTION",
  "RETAINED_ITEM",
  "DEVICE_OR_IMPLANT_FAILURE",
  "ORGAN_INJURY",
  "PLANNED_SECOND_LOOK",
  "OTHER",
] as const;
const REOPENABLE = new Set(["PACU", "RECOVERED", "IN_PROGRESS"]);

function field(row: Record<string, unknown> | undefined, ...keys: string[]): string {
  if (!row) return "";
  for (const k of keys) {
    const v = row[k];
    if (v != null && String(v).length > 0) return String(v);
  }
  return "";
}

function asRows(data: unknown): Array<Record<string, unknown>> {
  return Array.isArray(data) ? (data as Array<Record<string, unknown>>) : [];
}

export function TheatreCaseScreen({ caseId, onBack, onOpenProcedure, onOpenCaseOps }: Props) {
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

  // M2 panel drafts
  const [laterality, setLaterality] = useState<string>("LEFT");
  const [anatomicalSite, setAnatomicalSite] = useState("");
  const [countKind, setCountKind] = useState("SWAB");
  const [countBaseline, setCountBaseline] = useState("");
  const [countClosing, setCountClosing] = useState("");
  const [implantUdi, setImplantUdi] = useState("");
  const [implantSerial, setImplantSerial] = useState("");
  const [bloodUnits, setBloodUnits] = useState("2");
  const [bloodComponent, setBloodComponent] = useState("PRBC");
  const [emergencyBasis, setEmergencyBasis] = useState("DEFERRED");
  const [emergencyReason, setEmergencyReason] = useState("");
  const [returnReason, setReturnReason] = useState("");
  const [returnCategory, setReturnCategory] = useState("");
  const [returnPlanned, setReturnPlanned] = useState(false);

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

  const specimensQuery = useQuery({
    queryKey: ["theatre-specimens", caseId],
    queryFn: () => listTheatreSpecimens(caseId),
    enabled: !!caseId && caseQuery.isSuccess,
    retry: false,
  });

  const countsQuery = useQuery({
    queryKey: ["theatre-counts", caseId],
    queryFn: () => listTheatreCounts(caseId),
    enabled: !!caseId && caseQuery.isSuccess,
    retry: false,
  });

  const implantsQuery = useQuery({
    queryKey: ["theatre-implants", caseId],
    queryFn: () => listTheatreImplants(caseId),
    enabled: !!caseId && caseQuery.isSuccess,
    retry: false,
  });

  const bloodQuery = useQuery({
    queryKey: ["theatre-blood", caseId],
    queryFn: () => listTheatreBlood(caseId),
    enabled: !!caseId && caseQuery.isSuccess,
    retry: false,
  });

  const refresh = () => {
    void caseQuery.refetch();
    void safetyQuery.refetch();
    void noteQuery.refetch();
    void specimensQuery.refetch();
    void countsQuery.refetch();
    void implantsQuery.refetch();
    void bloodQuery.refetch();
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
  const safetyEvents = asRows(safetyQuery.data);
  const specimens = asRows(specimensQuery.data);
  const counts = asRows(countsQuery.data);
  const implants = asRows(implantsQuery.data);
  const bloodLinks = asRows(bloodQuery.data);
  const returns = asRows(detail.returns_to_theatre ?? detail.returnsToTheatre);
  const siteConfirmed = Boolean(
    field(detail, "laterality") &&
      field(detail, "site_side_confirmed_by", "siteSideConfirmedBy") &&
      field(detail, "site_side_confirmed_at", "siteSideConfirmedAt"),
  );
  const consentStatus = field(detail, "consent_status", "consentStatus");
  const countDiscrepant = counts.filter((c) => Number(c.discrepancy ?? 0) !== 0);
  const showReturn = REOPENABLE.has(status);

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
        {onOpenCaseOps ? (
          <Button
            testID="theatre-open-case-ops"
            title="Case ops (anaesthesia / CSSD / drugs)"
            variant="secondary"
            onPress={onOpenCaseOps}
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

        {/* ── Wave M2: bedside OR panels ───────────────────────────────────── */}
        <Text style={styles.sub} testID="theatre-or-panels">
          3. Site / side marking
        </Text>
        {siteConfirmed ? (
          <Text testID="theatre-site-confirmed" style={styles.hint}>
            Confirmed: {[field(detail, "anatomical_site", "anatomicalSite"), field(detail, "laterality")]
              .filter(Boolean)
              .join(" · ")}
          </Text>
        ) : (
          <Text style={styles.hint}>Not confirmed — lateralised start is gated server-side.</Text>
        )}
        <TextInput
          testID="theatre-site-laterality"
          style={styles.input}
          value={laterality}
          onChangeText={setLaterality}
          placeholder={`Laterality (${SITE_LATERALITY.join("|")})`}
          autoCapitalize="characters"
        />
        <TextInput
          testID="theatre-site-anatomical"
          style={styles.input}
          value={anatomicalSite}
          onChangeText={setAnatomicalSite}
          placeholder="Anatomical site (optional)"
        />
        <Button
          testID="theatre-site-confirm"
          title="Confirm site / side"
          variant="secondary"
          disabled={run.isPending || !laterality.trim()}
          onPress={() =>
            run.mutate(() =>
              confirmTheatreSiteSide(caseId, {
                laterality: laterality.trim().toUpperCase(),
                ...(anatomicalSite.trim() ? { anatomicalSite: anatomicalSite.trim() } : {}),
              }),
            )
          }
        />

        <Text style={styles.sub}>4. Specimens (custody)</Text>
        {specimensQuery.isError ? (
          <Text testID="theatre-specimens-unavailable" style={styles.errorText}>
            Specimen list unavailable — do not treat this as no specimens.
          </Text>
        ) : specimens.length === 0 ? (
          <Text style={styles.hint}>No specimens for this case.</Text>
        ) : (
          specimens.map((s) => {
            const id = String(s.id ?? "");
            return (
              <View key={id || String(s.seq)} style={styles.rowBox} testID={`theatre-specimen-${id}`}>
                <Text style={styles.rowTitle}>
                  {String(s.specimen_label ?? s.body_site ?? "Specimen")} · {String(s.status ?? "—")}
                </Text>
                {!s.collected_at ? (
                  <Button
                    title="Collect"
                    variant="outline"
                    disabled={run.isPending || !id}
                    onPress={() => run.mutate(() => collectTheatreSpecimen(caseId, id))}
                  />
                ) : null}
                {s.collected_at && !s.label_confirmed_at ? (
                  <Button
                    title="Confirm label"
                    variant="outline"
                    disabled={run.isPending || !id}
                    onPress={() => run.mutate(() => confirmTheatreSpecimenLabel(caseId, id))}
                  />
                ) : null}
                {s.label_confirmed_at && !s.received_at ? (
                  <Button
                    title="Receive"
                    variant="outline"
                    disabled={run.isPending || !id}
                    onPress={() => run.mutate(() => receiveTheatreSpecimen(caseId, id))}
                  />
                ) : null}
                {s.received_at && (!s.adequacy || s.adequacy === "NOT_ASSESSED") ? (
                  <Button
                    title="Adequacy: ADEQUATE"
                    variant="outline"
                    disabled={run.isPending || !id}
                    onPress={() =>
                      run.mutate(() =>
                        assessTheatreSpecimenAdequacy(caseId, id, { adequacy: "ADEQUATE" }),
                      )
                    }
                  />
                ) : null}
              </View>
            );
          })
        )}

        <Text style={styles.sub}>5. Surgical counts</Text>
        {countsQuery.isError ? (
          <Text testID="theatre-counts-unavailable" style={styles.errorText}>
            Counts unavailable — do not assume Sign-Out is clear.
          </Text>
        ) : countDiscrepant.length > 0 ? (
          <Text testID="theatre-counts-blocked" style={styles.errorText}>
            WHO Sign-Out BLOCKED — discrepancy on{" "}
            {countDiscrepant.map((c) => `${c.kind} (${c.discrepancy})`).join(", ")}.
          </Text>
        ) : counts.length > 0 ? (
          <Text testID="theatre-counts-clear" style={styles.hint}>
            Counts reconciled — Sign-Out not blocked by counts.
          </Text>
        ) : (
          <Text style={styles.hint}>No counts recorded yet.</Text>
        )}
        {counts.map((c, i) => (
          <Text key={String(c.id ?? i)} style={styles.safetyRow}>
            {String(c.kind)} · base {String(c.baseline_count ?? "—")} → close{" "}
            {String(c.closing_count ?? "—")} · Δ {String(c.discrepancy ?? "—")}
          </Text>
        ))}
        <TextInput
          testID="theatre-count-kind"
          style={styles.input}
          value={countKind}
          onChangeText={setCountKind}
          placeholder="Kind (SWAB|INSTRUMENT|NEEDLE)"
          autoCapitalize="characters"
        />
        <TextInput
          testID="theatre-count-baseline"
          style={styles.input}
          value={countBaseline}
          onChangeText={setCountBaseline}
          placeholder="Baseline count"
          keyboardType="number-pad"
        />
        <TextInput
          testID="theatre-count-closing"
          style={styles.input}
          value={countClosing}
          onChangeText={setCountClosing}
          placeholder="Closing count"
          keyboardType="number-pad"
        />
        <Button
          testID="theatre-count-record"
          title="Record count"
          variant="secondary"
          disabled={run.isPending || (countBaseline === "" && countClosing === "")}
          onPress={() =>
            run.mutate(() =>
              recordTheatreCount(caseId, {
                kind: countKind.trim() || "SWAB",
                ...(countBaseline !== "" ? { baselineCount: Number(countBaseline) } : {}),
                ...(countClosing !== "" ? { closingCount: Number(countClosing) } : {}),
              }),
            )
          }
        />

        <Text style={styles.sub}>6. Implants</Text>
        {implantsQuery.isError ? (
          <Text testID="theatre-implants-unavailable" style={styles.errorText}>
            Implant list unavailable — do not treat this as no implants.
          </Text>
        ) : implants.length === 0 ? (
          <Text style={styles.hint}>No implants recorded.</Text>
        ) : (
          implants.map((im, i) => (
            <Text key={String(im.id ?? i)} style={styles.safetyRow}>
              {String(im.udi ?? im.serial_number ?? im.serialNumber ?? "implant")} ·{" "}
              {String(im.status ?? "—")}
            </Text>
          ))
        )}
        <TextInput
          testID="theatre-implant-udi"
          style={styles.input}
          value={implantUdi}
          onChangeText={setImplantUdi}
          placeholder="UDI"
          autoCapitalize="none"
        />
        <TextInput
          testID="theatre-implant-serial"
          style={styles.input}
          value={implantSerial}
          onChangeText={setImplantSerial}
          placeholder="Serial (if no UDI)"
          autoCapitalize="none"
        />
        <Button
          testID="theatre-implant-record"
          title="Record implant"
          variant="secondary"
          disabled={run.isPending || (!implantUdi.trim() && !implantSerial.trim())}
          onPress={() =>
            run.mutate(() =>
              recordTheatreImplant(caseId, {
                ...(implantUdi.trim() ? { udi: implantUdi.trim() } : {}),
                ...(implantSerial.trim() ? { serialNumber: implantSerial.trim() } : {}),
              }),
            )
          }
        />

        <Text style={styles.sub}>7. Blood (MADI)</Text>
        {bloodQuery.isError ? (
          <Text testID="theatre-blood-unavailable" style={styles.errorText}>
            Blood list unavailable — do not assume none ordered.
          </Text>
        ) : bloodLinks.length === 0 ? (
          <Text style={styles.hint}>No blood ordered for this case.</Text>
        ) : (
          bloodLinks.map((l, i) => (
            <Text key={String(l.id ?? i)} style={styles.safetyRow}>
              {String(l.component_type ?? "—")} · {String(l.units_requested ?? "—")} u ·{" "}
              {String(l.status ?? "—")}
            </Text>
          ))
        )}
        <TextInput
          testID="theatre-blood-units"
          style={styles.input}
          value={bloodUnits}
          onChangeText={setBloodUnits}
          placeholder="Units"
          keyboardType="number-pad"
        />
        <TextInput
          testID="theatre-blood-component"
          style={styles.input}
          value={bloodComponent}
          onChangeText={setBloodComponent}
          placeholder="Component (PRBC|FFP|…)"
          autoCapitalize="characters"
        />
        <Button
          testID="theatre-blood-request"
          title="Request blood"
          disabled={run.isPending}
          onPress={() =>
            run.mutate(() =>
              theatreBloodAction(caseId, "request", {
                units: Number(bloodUnits) || 1,
                componentType: bloodComponent.trim() || "PRBC",
              }),
            )
          }
        />
        <Button
          testID="theatre-blood-issue"
          title="Issue & transport"
          variant="outline"
          disabled={run.isPending || bloodLinks.length === 0}
          onPress={() => run.mutate(() => theatreBloodAction(caseId, "issue"))}
        />
        <Button
          testID="theatre-blood-administer"
          title="Administer"
          variant="outline"
          disabled={run.isPending || bloodLinks.length === 0}
          onPress={() =>
            run.mutate(() =>
              theatreBloodAction(caseId, "administer", {
                patientMethod: "WRISTBAND",
                unitMethod: "BARCODE",
              }),
            )
          }
        />

        <Text style={styles.sub}>8. Emergency consent exception</Text>
        {consentStatus === "EMERGENCY_EXCEPTION" ? (
          <Text testID="theatre-emergency-consent-status" style={styles.hint}>
            consent_status = EMERGENCY_EXCEPTION — start is override-gated (no GRANTED fabricated).
          </Text>
        ) : null}
        <TextInput
          testID="theatre-emergency-basis"
          style={styles.input}
          value={emergencyBasis}
          onChangeText={setEmergencyBasis}
          placeholder="Basis (DEFERRED|PROXY|TWO_DOCTOR)"
          autoCapitalize="characters"
        />
        <TextInput
          testID="theatre-emergency-reason"
          style={[styles.input, styles.multiline]}
          value={emergencyReason}
          onChangeText={setEmergencyReason}
          placeholder="Clinical justification (required)"
          multiline
        />
        <Button
          testID="theatre-emergency-consent"
          title="Record emergency consent exception"
          variant="outline"
          disabled={run.isPending || !emergencyReason.trim()}
          onPress={() =>
            run.mutate(() =>
              recordTheatreEmergencyConsentException(caseId, {
                basis: emergencyBasis.trim() || "DEFERRED",
                reason: emergencyReason.trim(),
              }),
            )
          }
        />

        {showReturn ? (
          <>
            <Text style={styles.sub} testID="theatre-return-panel">
              9. Return to theatre
            </Text>
            {returns.map((r, i) => (
              <Text key={String(r.id ?? r.seq ?? i)} style={styles.safetyRow}>
                #{String(r.seq ?? i + 1)} {String(r.complication_category ?? "—")} ·{" "}
                {String(r.reason ?? "")}
              </Text>
            ))}
            <TextInput
              testID="theatre-return-reason"
              style={[styles.input, styles.multiline]}
              value={returnReason}
              onChangeText={setReturnReason}
              placeholder="Reason (required)"
              multiline
            />
            <TextInput
              testID="theatre-return-category"
              style={styles.input}
              value={returnCategory}
              onChangeText={(v) => {
                setReturnCategory(v);
                setReturnPlanned(v.trim().toUpperCase() === "PLANNED_SECOND_LOOK");
              }}
              placeholder={`Category (${RETURN_CATEGORIES.slice(0, 3).join("|")}|…)`}
              autoCapitalize="characters"
            />
            <Button
              testID="theatre-return-submit"
              title="Record return to theatre"
              variant="outline"
              disabled={run.isPending || !returnReason.trim() || !returnCategory.trim()}
              onPress={() =>
                run.mutate(() =>
                  recordReturnToTheatre(caseId, {
                    reason: returnReason.trim(),
                    complicationCategory: returnCategory.trim().toUpperCase(),
                    planned: returnPlanned,
                  }),
                )
              }
            />
          </>
        ) : null}

        <Text style={styles.sub}>10. Operative note</Text>
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

        <Text style={styles.sub}>11. PACU disposition</Text>
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

        <Text style={styles.sub}>12. Safety event</Text>
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

        <Text style={styles.sub}>13. Cancel</Text>
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

        <Text style={styles.sub}>14. Death in theatre</Text>
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
  rowBox: {
    borderWidth: 1,
    borderColor: colors.gray[200],
    borderRadius: 8,
    padding: 8,
    gap: 6,
    backgroundColor: "#FAFAFA",
  },
  rowTitle: { fontSize: 13, fontWeight: "600", color: colors.gray[800] },
});
