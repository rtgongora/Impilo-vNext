/**
 * Shared layout for Tier-3 professional hub screens (refresh, hints, last-updated).
 */

import React from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl, Pressable, Linking } from "react-native";
import { Card, CardBody, Badge, LoadingSpinner } from "@impilo/mobile-design-system";
import { formatHubTimestamp } from "../lib/hubUi";

export type HubSection = {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
};

type Props = {
  rootTestID: string;
  heading: string;
  description: string;
  sections: HubSection[];
  /** True until the first query result is available (initial fetch). */
  isPending: boolean;
  isError: boolean;
  refreshedAt?: string | null;
  isRefetching: boolean;
  onRefresh: () => void | Promise<unknown>;
  getSectionTestId: (id: string) => string;
  /** Base URL for web shell deep links (e.g. https://health.example.gov) */
  webShellBaseUrl?: string;
  children?: React.ReactNode;
};

function resolveWebUrl(base: string | undefined, webPath: string): string | null {
  if (!webPath.startsWith("/")) return null;
  const root = (base ?? process.env.EXPO_PUBLIC_WEB_SHELL_URL ?? "").replace(/\/$/, "");
  if (!root) return null;
  return `${root}${webPath}`;
}

export function ProfessionalHubBody({
  rootTestID,
  heading,
  description,
  sections,
  isPending,
  isError,
  refreshedAt,
  isRefetching,
  onRefresh,
  getSectionTestId,
  webShellBaseUrl,
  children,
}: Props) {
  const timeLabel = formatHubTimestamp(refreshedAt ?? undefined);

  return (
    <View testID={rootTestID} style={styles.root}>
      <Text style={styles.heading}>{heading}</Text>
      <Text style={styles.sub}>{description}</Text>
      {isPending ? <LoadingSpinner /> : null}
      {isError ? (
        <Badge variant="warning">Showing offline layout — hub API unavailable</Badge>
      ) : refreshedAt ? (
        <Badge variant="success">Synced</Badge>
      ) : (
        <Badge variant="outline">Local layout</Badge>
      )}
      {timeLabel && !isError ? <Text style={styles.syncedAt}>Updated {timeLabel}</Text> : null}
      <ScrollView
        style={styles.list}
        contentContainerStyle={styles.listPad}
        refreshControl={
          <RefreshControl refreshing={isRefetching} onRefresh={() => void onRefresh()} tintColor="#2563EB" />
        }
      >
        {children}
        {sections.map((s) => {
          const url = resolveWebUrl(webShellBaseUrl, s.web_path);
          return (
            <Card key={s.id}>
              <CardBody>
                <Pressable
                  testID={getSectionTestId(s.id)}
                  disabled={!url}
                  onPress={() => {
                    if (url) void Linking.openURL(url);
                  }}
                  accessibilityRole="link"
                  accessibilityHint={
                    url ? "Opens this workflow in the web Health OS shell" : "Web deep link not configured"
                  }
                >
                  <Text style={styles.cardTitle}>{s.title}</Text>
                  <Text style={styles.path}>{s.web_path}</Text>
                  {url ? (
                    <Text style={styles.openLink}>Open in web shell</Text>
                  ) : (
                    <Text style={styles.hint}>Set EXPO_PUBLIC_WEB_SHELL_URL to enable deep links</Text>
                  )}
                  {s.hint ? <Text style={styles.hint}>{s.hint}</Text> : null}
                </Pressable>
              </CardBody>
            </Card>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, padding: 16 },
  heading: { fontSize: 22, fontWeight: "700", color: "#0F172A" },
  sub: { fontSize: 14, color: "#64748B", marginTop: 4, marginBottom: 12 },
  syncedAt: { fontSize: 12, color: "#64748B", marginBottom: 8 },
  list: { flex: 1 },
  listPad: { paddingBottom: 24, gap: 12 },
  cardTitle: { fontSize: 16, fontWeight: "600", color: "#0F172A" },
  path: { fontSize: 12, color: "#64748B", marginTop: 4, fontFamily: "monospace" },
  openLink: { fontSize: 12, color: "#2563EB", marginTop: 6, fontWeight: "600" },
  hint: { fontSize: 12, color: "#94A3B8", marginTop: 4 },
});
