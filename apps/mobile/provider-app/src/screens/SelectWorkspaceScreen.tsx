/**
 * SelectWorkspaceScreen — Workspace selection after facility selection.
 *
 * Tier-3 ops foundation: providers choose their working workspace/department
 * before entering the main app shell.
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { View, Text, StyleSheet } from "react-native";
import { apiClient } from "@impilo/mobile-api-client";
import { useAuth } from "@impilo/mobile-auth";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { useAppStore, appStore } from "../stores/appStore";

interface Workspace {
  id: string;
  name: string;
  category?: string;
}

export function SelectWorkspaceScreen() {
  const auth = useAuth();
  const { facilityId, facilityName } = useAppStore();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const title = useMemo(() => {
    return facilityName ? `Workspace @ ${facilityName}` : "Select Workspace";
  }, [facilityName]);

  const fetchWorkspaces = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const base = facilityId ? `/internal/v1/facilities/${encodeURIComponent(facilityId)}/workspaces` : "/internal/v1/workspaces";
      const params = search ? `?search=${encodeURIComponent(search)}` : "";
      const response = await apiClient.get<{
        data: { id: string; attributes?: { name?: string; category?: string } }[];
      }>(`${base}${params}`);

      setWorkspaces(
        response.data.data.map((w) => ({
          id: w.id,
          name: w.attributes?.name ?? w.id,
          category: w.attributes?.category,
        }))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load workspaces");
    } finally {
      setLoading(false);
    }
  }, [facilityId, search]);

  useEffect(() => {
    void fetchWorkspaces();
  }, [fetchWorkspaces]);

  const handleSelectWorkspace = useCallback(
    (w: Workspace) => {
      appStore.getState().setWorkspaceContext(w.id, w.name);
      auth.setWorkspace?.(w.id, w.name);
    },
    [auth]
  );

  return (
    <Screen>
      <Header title={title} />
      <View testID="select-workspace-screen" style={styles.container}>
        <TextField
          label="Search workspaces"
          value={search}
          onChange={setSearch}
          placeholder="Type workspace name..."
          testID="workspace-search"
        />

        <View style={styles.listContainer}>
          {loading ? (
            <LoadingSpinner size="md" />
          ) : error ? (
            <ErrorState title="Error" message={error} onRetry={fetchWorkspaces} />
          ) : workspaces.length === 0 ? (
            <EmptyState title="No workspaces found" message="Try a different search term" />
          ) : (
            workspaces.map((w) => (
              <Card key={w.id}>
                <CardBody>
                  <View style={styles.row}>
                    <View style={styles.info}>
                      <Text style={styles.name}>{w.name}</Text>
                      {w.category ? <Text style={styles.meta}>{w.category}</Text> : null}
                    </View>
                    <Button
                      title="Select"
                      variant="primary"
                      size="sm"
                      onPress={() => handleSelectWorkspace(w)}
                      testID={`select-workspace-${w.id}`}
                    />
                  </View>
                </CardBody>
              </Card>
            ))
          )}
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16 },
  listContainer: { marginTop: 16 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  info: { flex: 1 },
  name: { fontWeight: "700", color: "#111827" },
  meta: { fontSize: 13, color: "#6B7280", marginTop: 4 },
});

