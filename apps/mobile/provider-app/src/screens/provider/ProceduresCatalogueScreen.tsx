/**
 * ProceduresCatalogueScreen — Clinical Procedures Pipeline catalogue on provider mobile.
 *
 * Mirrors web `/work/clinical/procedures` via BFF `/internal/v1/procedures/**`.
 * Honesty: catalogue load failure ≠ empty catalogue.
 */
import React, { useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  RefreshControl,
} from "react-native";
import {
  Screen,
  Header,
  Badge,
  Button,
  LoadingSpinner,
  ErrorState,
  colors,
} from "@impilo/mobile-design-system";
import { useMutation, useQuery } from "@tanstack/react-query";
import { authStore } from "@impilo/mobile-auth";
import {
  searchCatalogue,
  getCatalogueDetail,
  evaluateAppropriateness,
  resolveCompetence,
  getSafetyPauseTemplate,
  listSedationLevels,
  getSedationLevelDetail,
  listRecoverySettings,
  getRecoverySettingDetail,
  getAftercareTemplate,
  listAnalyticsIndicators,
  listClavienDindoGrades,
  getComplicationProfile,
  type CatalogueSummary,
} from "../../services/proceduresCatalogueService";

type Panel = "search" | "detail" | "safety" | "analytics";

function str(v: unknown, fallback = "—"): string {
  if (v == null || v === "") return fallback;
  return String(v);
}

function currentProviderId(): string {
  const session = authStore.getState().session as
    | { providerId?: string; actorId?: string }
    | null
    | undefined;
  return session?.providerId ?? session?.actorId ?? "";
}

export function ProceduresCatalogueScreen() {
  const providerId = currentProviderId();
  const [q, setQ] = useState("");
  const [submittedQ, setSubmittedQ] = useState("");
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [panel, setPanel] = useState<Panel>("search");

  const catalogueQuery = useQuery({
    queryKey: ["procedures-catalogue", "search", submittedQ],
    queryFn: () => searchCatalogue({ q: submittedQ || undefined }),
    retry: false,
  });

  if (selectedCode && panel === "detail") {
    return (
      <DetailPanel
        code={selectedCode}
        providerId={providerId}
        onBack={() => {
          setSelectedCode(null);
          setPanel("search");
        }}
      />
    );
  }

  if (panel === "safety") {
    return <SafetyReferencePanel onBack={() => setPanel("search")} />;
  }

  if (panel === "analytics") {
    return <AnalyticsPanel onBack={() => setPanel("search")} />;
  }

  if (catalogueQuery.isLoading) {
    return (
      <Screen>
        <Header title="Procedures" />
        <LoadingSpinner />
      </Screen>
    );
  }

  if (catalogueQuery.isError) {
    return (
      <Screen>
        <Header title="Procedures" />
        <ErrorState
          testID="procedures-catalogue-error"
          title="Procedures catalogue unavailable"
          message="Could not load the catalogue. Do not treat this as an empty procedure list — retry when the service is reachable."
          onRetry={() => void catalogueQuery.refetch()}
        />
      </Screen>
    );
  }

  const data = catalogueQuery.data;
  const items = data?.items ?? [];

  return (
    <Screen>
      <Header title="Procedures" />
      <ScrollView
        testID="procedures-catalogue-screen"
        style={styles.container}
        refreshControl={
          <RefreshControl
            refreshing={catalogueQuery.isRefetching}
            onRefresh={() => void catalogueQuery.refetch()}
          />
        }
      >
        <Text style={styles.hint}>
          National clinical procedures catalogue — search, appropriateness, competence, and
          safety references.
        </Text>
        <View style={styles.row}>
          <TouchableOpacity
            testID="procedures-open-safety"
            style={styles.linkChip}
            onPress={() => setPanel("safety")}
          >
            <Text style={styles.linkChipText}>Safety / sedation / recovery</Text>
          </TouchableOpacity>
          <TouchableOpacity
            testID="procedures-open-analytics"
            style={styles.linkChip}
            onPress={() => setPanel("analytics")}
          >
            <Text style={styles.linkChipText}>Analytics / Clavien</Text>
          </TouchableOpacity>
        </View>
        <TextInput
          testID="procedures-catalogue-q"
          style={styles.input}
          value={q}
          onChangeText={setQ}
          placeholder="Search clinical name or code"
          autoCapitalize="none"
        />
        <Button
          testID="procedures-catalogue-search"
          title="Search"
          onPress={() => setSubmittedQ(q.trim())}
        />
        <Text style={styles.meta} testID="procedures-catalogue-counts">
          Matched {data?.matched ?? 0} of {data?.catalogueSize ?? 0} published definitions
        </Text>
        {items.length === 0 ? (
          <Text testID="procedures-catalogue-empty" style={styles.empty}>
            {data && data.catalogueSize === 0
              ? "The catalogue is genuinely empty."
              : "No procedures match this filter."}
          </Text>
        ) : (
          items.map((item: CatalogueSummary) => (
            <TouchableOpacity
              key={item.definitionCode}
              testID={`procedures-catalogue-row-${item.definitionCode}`}
              style={styles.card}
              onPress={() => {
                setSelectedCode(item.definitionCode);
                setPanel("detail");
              }}
            >
              <Text style={styles.cardTitle}>{item.clinicalName}</Text>
              <Text style={styles.cardMeta}>
                {item.definitionCode}
                {item.owningSpecialty ? ` · ${item.owningSpecialty}` : ""}
                {item.category ? ` · ${item.category}` : ""}
              </Text>
            </TouchableOpacity>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

function DetailPanel({
  code,
  providerId,
  onBack,
}: {
  code: string;
  providerId: string;
  onBack: () => void;
}) {
  const detailQuery = useQuery({
    queryKey: ["procedures-catalogue", "detail", code],
    queryFn: () => getCatalogueDetail(code),
    retry: false,
  });

  const appropriateness = useMutation({
    mutationFn: () => evaluateAppropriateness({ definitionCode: code }),
  });
  const competence = useMutation({
    mutationFn: () => resolveCompetence(providerId, code),
  });

  const safetyCode = detailQuery.data?.safetyPauseTemplate ?? null;
  const sedationCode = detailQuery.data?.defaultSedationLevelCode ?? null;
  const recoveryCode = detailQuery.data?.defaultRecoverySettingCode ?? null;
  const aftercareCode =
    detailQuery.data?.aftercareTemplate ?? detailQuery.data?.defaultAftercareTemplateCode ?? null;
  const complicationCode = detailQuery.data?.complicationProfile ?? null;

  const safetyQuery = useQuery({
    queryKey: ["procedures-catalogue", "safety", safetyCode],
    queryFn: () => getSafetyPauseTemplate(safetyCode!),
    enabled: !!safetyCode,
    retry: false,
  });
  const sedationQuery = useQuery({
    queryKey: ["procedures-catalogue", "sedation", sedationCode],
    queryFn: () => getSedationLevelDetail(sedationCode!),
    enabled: !!sedationCode,
    retry: false,
  });
  const recoveryQuery = useQuery({
    queryKey: ["procedures-catalogue", "recovery", recoveryCode],
    queryFn: () => getRecoverySettingDetail(recoveryCode!),
    enabled: !!recoveryCode,
    retry: false,
  });
  const aftercareQuery = useQuery({
    queryKey: ["procedures-catalogue", "aftercare", aftercareCode],
    queryFn: () => getAftercareTemplate(aftercareCode!),
    enabled: !!aftercareCode,
    retry: false,
  });
  const complicationQuery = useQuery({
    queryKey: ["procedures-catalogue", "complication", complicationCode],
    queryFn: () => getComplicationProfile(complicationCode!),
    enabled: !!complicationCode,
    retry: false,
  });

  if (detailQuery.isLoading) {
    return (
      <Screen>
        <Header title="Procedure" onBack={onBack} />
        <LoadingSpinner />
      </Screen>
    );
  }
  if (detailQuery.isError) {
    return (
      <Screen>
        <Header title="Procedure" onBack={onBack} />
        <ErrorState
          testID="procedures-detail-error"
          title="Procedure detail unavailable"
          message="Could not load this definition. Retry when the service is reachable."
          onRetry={() => void detailQuery.refetch()}
        />
      </Screen>
    );
  }

  const d = detailQuery.data!;
  return (
    <Screen>
      <Header title={d.clinicalName} onBack={onBack} subtitle={code} />
      <ScrollView testID="procedures-detail" style={styles.container}>
        <Badge label={str(d.category, "PROCEDURE")} variant="info" />
        <Text style={styles.body}>{str(d.purpose)}</Text>
        <Text style={styles.meta}>
          Specialty {str(d.owningSpecialty)} · ZIBO {str(d.ziboCode)} · {str(d.status)}
        </Text>

        <Text style={styles.section}>Appropriateness</Text>
        <Button
          testID="procedures-appropriateness-run"
          title={appropriateness.isPending ? "Evaluating…" : "Evaluate appropriateness"}
          onPress={() => appropriateness.mutate()}
          disabled={appropriateness.isPending}
        />
        {appropriateness.isError ? (
          <Text style={styles.danger} testID="procedures-appropriateness-error">
            Appropriateness check failed — no verdict was produced.
          </Text>
        ) : null}
        {appropriateness.data ? (
          <View testID="procedures-appropriateness-verdict" style={styles.card}>
            <Text style={styles.cardTitle}>{str(appropriateness.data.outcome)}</Text>
            <Text style={styles.cardMeta}>
              {str(appropriateness.data.detectionCount, "0")} detections
            </Text>
          </View>
        ) : null}

        <Text style={styles.section}>Competence</Text>
        {!providerId ? (
          <Text style={styles.muted}>Provider id required for competence resolution.</Text>
        ) : (
          <Button
            testID="procedures-competence-run"
            title={competence.isPending ? "Resolving…" : "Resolve competence"}
            onPress={() => competence.mutate()}
            disabled={competence.isPending}
          />
        )}
        {competence.isError ? (
          <Text style={styles.danger} testID="procedures-competence-error">
            Competence check failed — do not assume the provider is cleared.
          </Text>
        ) : null}
        {competence.data ? (
          <View testID="procedures-competence-verdict" style={styles.card}>
            <Text style={styles.cardTitle}>
              {str(competence.data.outcome ?? (competence.data.competent ? "COMPETENT" : "UNKNOWN"))}
            </Text>
            <Text style={styles.cardMeta}>{str(competence.data.message, "")}</Text>
          </View>
        ) : null}

        <Text style={styles.section}>Safety pause</Text>
        {safetyCode == null ? (
          <Text style={styles.muted}>No safety-pause template on this definition.</Text>
        ) : safetyQuery.isError ? (
          <Text style={styles.danger} testID="procedures-safety-unavailable">
            Safety-pause template unavailable.
          </Text>
        ) : (
          <Text testID="procedures-safety-view" style={styles.body}>
            {str(safetyQuery.data?.code ?? safetyCode)} —{" "}
            {str(safetyQuery.data?.label ?? safetyQuery.data?.name, "loaded")}
          </Text>
        )}

        <Text style={styles.section}>Sedation / recovery / aftercare</Text>
        {sedationCode ? (
          sedationQuery.isError ? (
            <Text style={styles.danger}>Sedation level unavailable.</Text>
          ) : (
            <Text testID="procedures-sedation-view" style={styles.body}>
              Sedation: {str(sedationQuery.data?.label ?? sedationCode)}
            </Text>
          )
        ) : (
          <Text style={styles.muted}>No default sedation level.</Text>
        )}
        {recoveryCode ? (
          recoveryQuery.isError ? (
            <Text style={styles.danger}>Recovery setting unavailable.</Text>
          ) : (
            <Text testID="procedures-recovery-view" style={styles.body}>
              Recovery: {str(recoveryQuery.data?.label ?? recoveryCode)}
            </Text>
          )
        ) : (
          <Text style={styles.muted}>No default recovery setting.</Text>
        )}
        {aftercareCode ? (
          aftercareQuery.isError ? (
            <Text style={styles.danger}>Aftercare template unavailable.</Text>
          ) : (
            <Text testID="procedures-aftercare-view" style={styles.body}>
              Aftercare: {str(aftercareQuery.data?.label ?? aftercareCode)}
            </Text>
          )
        ) : (
          <Text style={styles.muted}>No aftercare template.</Text>
        )}

        <Text style={styles.section}>Complication profile</Text>
        {complicationCode == null ? (
          <Text style={styles.muted}>No complication profile linked.</Text>
        ) : complicationQuery.isError ? (
          <Text style={styles.danger} testID="procedures-complication-unavailable">
            Complication profile unavailable.
          </Text>
        ) : (
          <Text testID="procedures-complication-view" style={styles.body}>
            {str(complicationQuery.data?.label ?? complicationCode)}
          </Text>
        )}
      </ScrollView>
    </Screen>
  );
}

function SafetyReferencePanel({ onBack }: { onBack: () => void }) {
  const sedation = useQuery({
    queryKey: ["procedures-catalogue", "sedation-levels"],
    queryFn: listSedationLevels,
    retry: false,
  });
  const recovery = useQuery({
    queryKey: ["procedures-catalogue", "recovery-settings"],
    queryFn: listRecoverySettings,
    retry: false,
  });

  return (
    <Screen>
      <Header title="Safety references" onBack={onBack} />
      <ScrollView testID="procedures-safety-ref" style={styles.container}>
        <Text style={styles.section}>Sedation continuum</Text>
        {sedation.isError ? (
          <ErrorState
            testID="procedures-sedation-levels-error"
            title="Sedation levels unavailable"
            message="Could not load sedation levels. Do not treat this as an empty continuum."
            onRetry={() => void sedation.refetch()}
          />
        ) : (
          (sedation.data ?? []).map((row, i) => (
            <View key={str(row.code, `sed-${i}`)} style={styles.card}>
              <Text style={styles.cardTitle}>{str(row.label ?? row.code)}</Text>
              <Text style={styles.cardMeta}>{str(row.code)}</Text>
            </View>
          ))
        )}
        <Text style={styles.section}>Recovery settings</Text>
        {recovery.isError ? (
          <ErrorState
            testID="procedures-recovery-settings-error"
            title="Recovery settings unavailable"
            message="Could not load recovery settings."
            onRetry={() => void recovery.refetch()}
          />
        ) : (
          (recovery.data ?? []).map((row, i) => (
            <View key={str(row.code, `rec-${i}`)} style={styles.card}>
              <Text style={styles.cardTitle}>{str(row.label ?? row.code)}</Text>
              <Text style={styles.cardMeta}>{str(row.code)}</Text>
            </View>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

function AnalyticsPanel({ onBack }: { onBack: () => void }) {
  const indicators = useQuery({
    queryKey: ["procedures-catalogue", "analytics"],
    queryFn: listAnalyticsIndicators,
    retry: false,
  });
  const clavien = useQuery({
    queryKey: ["procedures-catalogue", "clavien"],
    queryFn: listClavienDindoGrades,
    retry: false,
  });

  return (
    <Screen>
      <Header title="Analytics / Clavien" onBack={onBack} />
      <ScrollView testID="procedures-analytics" style={styles.container}>
        <Text style={styles.section}>Indicators</Text>
        {indicators.isError ? (
          <ErrorState
            testID="procedures-analytics-error"
            title="Analytics unavailable"
            message="Could not load procedure analytics indicators."
            onRetry={() => void indicators.refetch()}
          />
        ) : (
          <View style={styles.card} testID="procedures-analytics-summary">
            <Text style={styles.cardTitle}>Indicator catalogue</Text>
            <Text style={styles.cardMeta}>{JSON.stringify(indicators.data ?? {}).slice(0, 240)}</Text>
          </View>
        )}
        <Text style={styles.section}>Clavien-Dindo grades</Text>
        {clavien.isError ? (
          <ErrorState
            testID="procedures-clavien-error"
            title="Clavien-Dindo grades unavailable"
            message="Could not load severity grades. Catalogue risk content only — not pathway grading."
            onRetry={() => void clavien.refetch()}
          />
        ) : (clavien.data ?? []).length === 0 ? (
          <Text style={styles.empty}>No Clavien-Dindo grades published.</Text>
        ) : (
          (clavien.data ?? []).map((g, i) => (
            <View key={str(g.code, `cd-${i}`)} style={styles.card} testID={`procedures-clavien-row-${i}`}>
              <Text style={styles.cardTitle}>{str(g.label ?? g.code)}</Text>
              <Text style={styles.cardMeta}>{str(g.description ?? g.code)}</Text>
            </View>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  hint: { fontSize: 13, color: colors.gray[600], marginBottom: 12 },
  meta: { fontSize: 12, color: colors.gray[500], marginVertical: 8 },
  muted: { fontSize: 13, color: colors.gray[500], marginBottom: 8 },
  body: { fontSize: 14, color: colors.gray[800], marginVertical: 6 },
  empty: { padding: 24, textAlign: "center", color: colors.gray[500], fontSize: 14 },
  danger: {
    marginVertical: 8,
    padding: 10,
    borderRadius: 8,
    backgroundColor: "#FEF2F2",
    color: "#991B1B",
    fontSize: 13,
  },
  section: {
    marginTop: 16,
    marginBottom: 8,
    fontSize: 16,
    fontWeight: "700",
    color: colors.gray[900],
  },
  row: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 12 },
  linkChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.gray[300],
    backgroundColor: "#FFF",
  },
  linkChipText: { fontSize: 12, fontWeight: "600", color: colors.gray[800] },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: colors.gray[900],
    backgroundColor: "#FFF",
    marginBottom: 8,
  },
  card: {
    backgroundColor: "#FFF",
    borderRadius: 10,
    borderWidth: 1,
    borderColor: colors.gray[200],
    padding: 14,
    marginBottom: 8,
    gap: 4,
  },
  cardTitle: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  cardMeta: { fontSize: 12, color: colors.gray[500] },
});
