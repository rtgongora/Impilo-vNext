/**
 * Work Home (Phase G4) — mobile counterpart of web's /work page.
 * Renders governed BFF composition with actionable items, restrictions, and
 * honest friendlyState / DEGRADED handling.
 */
import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable, Linking } from "react-native";
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
import {
  getWorkHome,
  getWorkHomeSection,
  type WorkHome,
  type WorkHomeItem,
  type WorkHomeSection,
} from "../../services/workHomeService";
import { describeWorkContextRestrictions } from "../../lib/restrictionCopy";

const PREVIEW_WEB = process.env.EXPO_PUBLIC_WEB_BASE_URL ?? "https://impilo.mohcc.gov.zw";

const GROUP_LABELS: Array<{ key: string; label: string }> = [
  { key: "today", label: "Today" },
  { key: "regular", label: "My regular workplaces" },
  { key: "virtual", label: "Virtual work" },
  { key: "oversight", label: "Oversight roles" },
  { key: "other", label: "Other authorised workplaces" },
  { key: "personal", label: "Personal" },
];

function openWorkHomeHref(href: string) {
  const url = href.startsWith("http") ? href : `${PREVIEW_WEB}${href.startsWith("/") ? "" : "/"}${href}`;
  void Linking.openURL(url);
}

function ItemRow({ item }: { item: WorkHomeItem }) {
  const body = (
    <View style={styles.item}>
      <Text style={[styles.itemTitle, item.href ? styles.itemLink : null]}>{item.title ?? "Untitled"}</Text>
      {item.description ? <Text style={styles.itemBody}>{item.description}</Text> : null}
      <View style={styles.itemMeta}>
        {item.priority ? <Text style={styles.metaText}>{item.priority}</Text> : null}
        {item.due_at ? <Text style={styles.metaText}>Due {item.due_at}</Text> : null}
      </View>
    </View>
  );
  if (item.href) {
    return (
      <Pressable
        onPress={() => openWorkHomeHref(item.href as string)}
        testID={`work-home-item-${item.id ?? "x"}`}
        accessibilityRole="link"
      >
        {body}
      </Pressable>
    );
  }
  return <View testID={`work-home-item-${item.id ?? "x"}`}>{body}</View>;
}

function SectionCard({
  section,
  onRetry,
  retrying,
}: {
  section: WorkHomeSection;
  onRetry: (sectionId: string) => void;
  retrying: boolean;
}) {
  const buckets = Object.entries(section.buckets ?? {}).filter(([, items]) => items.length > 0);
  const items = buckets.length > 0
    ? buckets.flatMap(([, list]) => list).slice(0, 12)
    : (section.items ?? []).slice(0, 12);

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
          ) : items.length === 0 ? (
            <Text style={styles.emptyBody}>{section.note ?? "Nothing here right now."}</Text>
          ) : (
            items.map((item, index) => <ItemRow key={item.id ?? String(index)} item={item} />)
          )}
        </View>
      </CardBody>
    </Card>
  );
}

function friendlyMessage(state: string | undefined): { title: string; message: string } | null {
  if (!state) return null;
  if (state === "work_mode_unavailable") {
    return {
      title: "Mode unavailable",
      message: "This workplace isn't available in the requested mode right now.",
    };
  }
  if (state === "work_context_unavailable") {
    return {
      title: "Workplace unavailable",
      message: "The assignment could not be re-proven from its source. Try another workplace.",
    };
  }
  return {
    title: "Work unavailable",
    message: "Your work context isn't available right now.",
  };
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
        // Leave DEGRADED
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
            {group.items.map((context) => {
              const restrictions = describeWorkContextRestrictions(context.restrictions);
              return (
                <Card key={context.contextId}>
                  <CardBody>
                    <View style={styles.contextRow} testID={`work-context-${context.contextId}`}>
                      <View style={styles.contextInfo}>
                        <Text style={styles.contextLabel}>{context.label}</Text>
                        {context.defaultMode ? (
                          <Text style={styles.contextMeta}>
                            {context.defaultMode.replace(/_/g, " ").toLowerCase()}
                          </Text>
                        ) : null}
                        {restrictions.map((line) => (
                          <Text key={line} style={styles.restriction}>
                            {line}
                          </Text>
                        ))}
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
              );
            })}
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

  const friendly = friendlyMessage(workHome?.friendlyState);

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
        ) : friendly ? (
          <EmptyState title={friendly.title} message={friendly.message} />
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
  itemLink: { color: colors.primary[600], textDecorationLine: "underline" },
  itemBody: { fontSize: 13, color: colors.gray[700], marginTop: 2 },
  itemMeta: { flexDirection: "row", gap: 8, marginTop: 2 },
  metaText: { fontSize: 11, color: colors.gray[500] },
  contextRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  contextInfo: { flex: 1, paddingRight: 8 },
  contextLabel: { fontSize: 15, fontWeight: "600" },
  contextMeta: { fontSize: 12, color: colors.gray[500], marginTop: 2 },
  restriction: { fontSize: 11, color: "#B45309", marginTop: 4 },
  switchError: { fontSize: 14, color: colors.ui.error.main },
});
