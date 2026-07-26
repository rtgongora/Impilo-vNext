/**
 * AdaptiveEncounterCockpit (mobile) — renders the encounter cockpit tab/action spine STRICTLY from the
 * PCT Cadre Engine decision (mobile mirror of the web component). Doctrine:
 *  - The tab set is whatever the Cadre Engine returns for the context — never hardcoded.
 *  - A disabled action is shown greyed with its escalation reason (step-up / supervisor) — never a live
 *    or dead button. Authz-gated actions still traverse Tshepo ext_authz at execution.
 * Backed by the live cadre-decision route via cadreDecisionService. Form-content depth is GAP-10.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { Card, CardHeader, CardBody, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  resolveCadreDecision,
  type CadreDecision,
  type CadreDecisionRequest,
} from "../../services/cadreDecisionService";

const TAB_LABELS: Record<string, string> = {
  OVERVIEW: "Overview",
  ASSESSMENT: "Assessment",
  PROBLEMS: "Problems",
  ORDERS_RESULTS: "Orders & Results",
  CARE: "Care",
  CONSULTS_REFERRALS: "Consults & Referrals",
  NOTES: "Notes",
  VISIT_OUTCOME: "Visit Outcome",
};

function humanise(token: string): string {
  return token
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function AdaptiveEncounterCockpit({ request }: { request: CadreDecisionRequest }) {
  const [decision, setDecision] = useState<CadreDecision | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const key = [
    request.role,
    request.cadre,
    request.scope,
    request.visitType,
    request.acuity,
    request.context,
    request.accessState,
    request.journeyId,
    request.encounterId,
  ].join("|");

  const resolve = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDecision(await resolveCadreDecision(request));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resolve cadre decision");
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useEffect(() => {
    resolve();
  }, [resolve]);

  if (loading) return <LoadingSpinner size="md" />;
  if (error) return <ErrorState title="Cockpit unavailable" message={error} onRetry={resolve} />;
  if (!decision || decision.cockpitSpine.length === 0) {
    return <EmptyState title="No permitted actions" message="The cadre engine returned no cockpit for this context" />;
  }

  return (
    <View testID="adaptive-encounter-cockpit" style={styles.container}>
      {decision.escalation.breakGlassAvailable && (
        <Badge variant="warning">Break-glass available</Badge>
      )}
      {decision.cockpitSpine.map((tab) => (
        <Card key={tab.tab}>
          <CardHeader title={TAB_LABELS[tab.tab] ?? humanise(tab.tab)} />
          <CardBody>
            {!tab.enabled && (
              <Text style={styles.disabledNote}>Not available for your cadre in this context</Text>
            )}
            {tab.actions.length === 0 ? (
              <Text style={styles.disabledNote}>No actions</Text>
            ) : (
              <View style={styles.actionList}>
                {tab.actions.map((a) => (
                  <View key={a.action} testID={`action-${a.action}`} style={styles.actionRow}>
                    <Text style={[styles.actionLabel, !a.enabled && styles.actionDisabled]}>
                      {humanise(a.action)}
                    </Text>
                    {a.enabled ? (
                      a.requiresStepUp ? (
                        <Badge variant="warning">Step-up</Badge>
                      ) : (
                        <Badge variant="success">Permitted</Badge>
                      )
                    ) : (
                      <Badge variant="secondary">
                        {decision.escalation.supervisorRequiredFor.includes(a.action)
                          ? "Supervisor"
                          : "Not permitted"}
                      </Badge>
                    )}
                  </View>
                ))}
              </View>
            )}
          </CardBody>
        </Card>
      ))}
      <Text style={styles.auditRef}>Audit ref: {decision.auditRef}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  actionList: { gap: 8 },
  actionRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  actionLabel: { fontSize: 14, color: colors.gray[900], fontWeight: "600" },
  actionDisabled: { color: colors.gray[400], fontWeight: "400" },
  disabledNote: { fontSize: 13, color: colors.gray[400] },
  auditRef: { fontSize: 11, color: colors.gray[400], marginTop: 4 },
});
