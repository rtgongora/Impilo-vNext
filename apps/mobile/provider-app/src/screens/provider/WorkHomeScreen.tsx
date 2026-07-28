/**
 * Work Home (Phase G4) — the mobile counterpart of web's /work page, rendering
 * the same governed BFF composition, plus the one-tap workplace switcher web
 * carries in ActiveWorkContextBar.
 *
 * Section status is rendered honestly: a DEGRADED section says so and offers a
 * retry, and is never collapsed into an innocuous "nothing to do" — the BFF
 * always answers 200 and expresses downstream failure in-band, so treating a
 * successful response as healthy would silently hide an outage.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { useAuth } from "@impilo/mobile-auth";
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  Badge,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  colors,
} from "@impilo/mobile-design-system";
import { useAppStore } from "../../stores/appStore";
import { useSwitchWorkContext } from "../../hooks/useSwitchWorkContext";
import { getWorkHome, getWorkHomeSection, type WorkHome, type WorkHomeSection } from "../../services/workHomeService";

const GROUP_LABELS: Array<{ key: string; label: string }> = [
  { key: "today", label: "Today" },
  { key: "regular", label: "My regular workplaces" },
  { key: "virtual", label: "Virtual work" },
  { key: "oversight", label: "Oversight roles" },
  { key: "other", label: "Other authorised workplaces" },
  { key: "personal", label: "Personal" },
];

function SectionCard({
  section,
  onRetry,
  retrying,
}: {
  section: WorkHomeSection;
  onRetry: (sectionId: string) => void;
  retrying: boolean;
}) {
  return (
    <Card>
      <CardBody>
        <View testID={`work-home-section-${section.sectionId}`}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>{section.title}</Text>
            {section.status === "DEGRADED" ? <Badge variant="destructive">Unavailable</Badge> : null}
          </View>

          {section.status === "DEGRADED" ? (
            <View testID={`work-home-degraded-${section.sectionId}`}>
              <Text style={styles.degradedBody}>
                {section.note ?? "This information could not be loaded."} This is not an empty list —
                retry before relying on it.
              </Text>
              <View style={styles.retryRow}>
                <Button
                  title={retrying ? "Retrying…" : "Retry"}
                  variant="outline"
                  size="sm"
                  onPress={() => onRetry(section.sectionId)}
                  testID={`work-home-retry-${section.sectionId}`}
                />
              </View>
            </View>
          ) : section.items.length === 0 ? (
            <Text style={styles.emptyBody}>{section.note ?? "Nothing here right now."}</Text>
          ) : (
            section.items.map((item, index) => (
              <View key={item.id ?? String(index)} style={styles.item} testID={`work-home-item-${item.id ?? index}`}>
                <Text style={styles.itemTitle}>{item.title ?? "Untitled"}</Text>
                {item.description ? <Text style={styles.itemBody}>{item.description}</Text> : null}
              </View>
            ))
          )}
        </View>
      </CardBody>
    </Card>
  );
}

export function WorkHomeScreen() {
  const auth = useAuth();
  const { resolvedWorkContexts } = useAppStore();
  const { switching, error: switchError, switchWorkContext } = useSwitchWorkContext();

  const [workHome, setWorkHome] = useState<WorkHome | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryingSection, setRetryingSection] = useState<string | null>(null);
  const [showSwitcher, setShowSwitcher] = useState(false);

  const contextId = auth.session?.workContextId;
  const workMode = auth.session?.workMode;

  const load = useCallback(async () => {
    if (!contextId) {
      setWorkHome(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setWorkHome(await getWorkHome(contextId, workMode));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load your work");
    } finally {
      setLoading(false);
    }
  }, [contextId, workMode]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSectionRetry = useCallback(
    async (sectionId: string) => {
      if (!contextId) return;
      setRetryingSection(sectionId);
      try {
        const fresh = await getWorkHomeSection(sectionId, contextId, workMode);
        setWorkHome((current) =>
          current
            ? { ...current, sections: current.sections.map((s) => (s.sectionId === sectionId ? fresh : s)) }
            : current
        );
      } catch {
        // Leave the section DEGRADED — a failed retry must not look like success.
      } finally {
        setRetryingSection(null);
      }
    },
    [contextId, workMode]
  );

  const handleSelectContext = useCallback(
    async (context: ResolvedWorkContextView) => {
      const ok = await switchWorkContext(context);
      if (ok) {
        setShowSwitcher(false);
        void load();
      }
    },
    [switchWorkContext, load]
  );

  const contexts = resolvedWorkContexts ?? [];
  const groups = GROUP_LABELS.map((g) => ({
    ...g,
    items: contexts.filter((c) => c.groupHint === g.key),
  })).filter((g) => g.items.length > 0);

  const switcher = (
    <View testID="work-context-switcher">
      <CardHeader>Choose a workplace</CardHeader>
      {switchError ? (
        <Card>
          <CardBody>
            <Text testID="work-switch-error" style={styles.switchError}>
              {switchError}
            </Text>
          </CardBody>
        </Card>
      ) : null}
      {groups.length === 0 ? (
        <EmptyState
          title="No workplaces resolved"
          message="No authorised workplace could be resolved for you yet."
        />
      ) : (
        groups.map((group) => (
          <View key={group.key}>
            <CardHeader>{group.label}</CardHeader>
            {group.items.map((context) => (
              <Card key={context.contextId}>
                <CardBody>
                  <View style={styles.contextRow} testID={`work-context-${context.contextId}`}>
                    <View style={styles.contextInfo}>
                      <Text style={styles.contextLabel}>{context.label}</Text>
                      {context.defaultMode ? (
                        <Text style={styles.contextMeta}>{context.defaultMode.replace(/_/g, " ").toLowerCase()}</Text>
                      ) : null}
                    </View>
                    <Button
                      title={context.contextId === contextId ? "Current" : switching ? "Switching…" : "Select"}
                      variant={context.contextId === contextId ? "outline" : "primary"}
                      size="sm"
                      onPress={() => handleSelectContext(context)}
                      testID={`select-context-${context.contextId}`}
                    />
                  </View>
                </CardBody>
              </Card>
            ))}
          </View>
        ))
      )}
    </View>
  );

  if (!contextId) {
    return (
      <Screen>
        <Header title="Work" />
        <ScrollView style={styles.container}>{switcher}</ScrollView>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Work" />
      <ScrollView testID="work-home" style={styles.container}>
        <View style={styles.switchRow}>
          <Button
            title={showSwitcher ? "Hide workplaces" : "Change workplace"}
            variant="outline"
            size="sm"
            onPress={() => setShowSwitcher((v) => !v)}
            testID="toggle-work-context-switcher"
          />
        </View>

        {showSwitcher ? switcher : null}

        {loading ? (
          <LoadingSpinner size="lg" />
        ) : error ? (
          <ErrorState title="Could not load your work" message={error} onRetry={load} />
        ) : !workHome || workHome.sections.length === 0 ? (
          <EmptyState title="Nothing to show" message="No work sections are available for this workplace." />
        ) : (
          workHome.sections.map((section) => (
            <SectionCard
              key={section.sectionId}
              section={section}
              onRetry={handleSectionRetry}
              retrying={retryingSection === section.sectionId}
            />
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  switchRow: { marginBottom: 12, alignItems: "flex-start" },
  sectionHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  sectionTitle: { fontSize: 15, fontWeight: "700", flex: 1 },
  degradedBody: { fontSize: 14, color: colors.ui.error.main, marginTop: 6 },
  retryRow: { marginTop: 8, alignItems: "flex-start" },
  emptyBody: { fontSize: 14, color: colors.gray[500], marginTop: 6 },
  item: { marginTop: 10 },
  itemTitle: { fontSize: 14, fontWeight: "600" },
  itemBody: { fontSize: 13, color: colors.gray[700], marginTop: 2 },
  contextRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  contextInfo: { flex: 1 },
  contextLabel: { fontSize: 15, fontWeight: "600" },
  contextMeta: { fontSize: 12, color: colors.gray[500], marginTop: 2 },
  switchError: { fontSize: 14, color: colors.ui.error.main },
});
