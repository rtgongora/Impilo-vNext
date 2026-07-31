/**
 * Public regulatory explore — anonymous councils + registers (no auth).
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  CardHeader,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  colors,
} from "@impilo/mobile-design-system";
import {
  getPublicCouncilRegisters,
  getPublicCouncils,
  type PublicCouncil,
  type PublicRegister,
} from "@impilo/mobile-api-client";

export function PublicRegulatoryExploreScreen() {
  const [councils, setCouncils] = useState<PublicCouncil[]>([]);
  const [registers, setRegisters] = useState<Record<string, PublicRegister[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await getPublicCouncils();
      setCouncils(list);
      const map: Record<string, PublicRegister[]> = {};
      await Promise.all(
        list.map(async (c) => {
          if (!c.councilCode) return;
          try {
            map[c.councilCode] = await getPublicCouncilRegisters(c.councilCode);
          } catch {
            map[c.councilCode] = [];
          }
        }),
      );
      setRegisters(map);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Regulatory information unavailable");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <Screen>
        <Header title="Regulatory explore" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error) {
    return (
      <Screen>
        <Header title="Regulatory explore" />
        <ErrorState title="Unavailable" message={error} onRetry={load} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Regulatory explore" />
      <ScrollView testID="public-regulatory-explore" style={styles.container}>
        <Text style={styles.intro}>
          Public reference for professional councils and their registers. Starting an application
          requires sign-in.
        </Text>
        {councils.length === 0 ? (
          <EmptyState title="No councils" message="No councils are published yet." />
        ) : (
          councils.map((c) => (
            <View key={c.councilCode}>
              <CardHeader title={c.name ?? c.councilCode ?? "Council"} />
              <Card>
                <CardBody>
                  {c.councilType ? <Text style={styles.meta}>{c.councilType}</Text> : null}
                  {c.description ? <Text style={styles.body}>{c.description}</Text> : null}
                  {(registers[c.councilCode ?? ""] ?? []).map((r) => (
                    <View key={r.registerCode ?? r.name} style={styles.registerRow}>
                      <Text style={styles.name}>{r.name ?? r.registerCode}</Text>
                      <Text style={styles.meta}>
                        {r.registerCode}
                        {r.status ? ` · ${r.status}` : ""}
                      </Text>
                    </View>
                  ))}
                  {(registers[c.councilCode ?? ""] ?? []).length === 0 ? (
                    <Text style={styles.meta}>No public registers listed yet.</Text>
                  ) : null}
                </CardBody>
              </Card>
            </View>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  intro: { marginBottom: 12, fontSize: 14, color: colors.gray[600], lineHeight: 20 },
  name: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  body: { marginTop: 4, fontSize: 13, color: colors.gray[700], lineHeight: 18 },
  meta: { marginTop: 2, fontSize: 12, color: colors.gray[500] },
  registerRow: {
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.gray[200],
  },
});
