/**
 * Shared layout for Tier-3 professional hub screens (refresh, hints, last-updated).
 */

import React from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
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
  children?: React.ReactNode;
};

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
        {sections.map((s) => (
          <Card key={s.id}>
            <CardBody>
              <View testID={getSectionTestId(s.id)}>
                <Text style={styles.cardTitle}>{s.title}</Text>
                <Text style={styles.path}>{s.web_path}</Text>
                {s.hint ? <Text style={styles.hint}>{s.hint}</Text> : null}
              </View>
            </CardBody>
          </Card>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, gap: 10, paddingHorizontal: 4 },
  heading: { fontSize: 18, fontWeight: "800", color: "#111827" },
  sub: { fontSize: 13, color: "#6B7280", lineHeight: 18 },
  syncedAt: { fontSize: 11, color: "#6B7280", marginTop: -4 },
  list: { flex: 1 },
  listPad: { gap: 10, paddingBottom: 24 },
  cardTitle: { fontSize: 15, fontWeight: "700", color: "#111827" },
  path: { fontSize: 12, color: "#6B7280", marginTop: 4 },
  hint: { fontSize: 12, color: "#4B5563", marginTop: 6, lineHeight: 17 },
});
