/**
 * FacilityDirectoryScreen — Citizen facility directory + selection (Tier-3).
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { View, Text, StyleSheet, Pressable, ScrollView, RefreshControl } from "react-native";
import { apiClient } from "@impilo/mobile-api-client";
import { Screen, Header, Card, CardBody, Button, TextField, LoadingSpinner, EmptyState, ErrorState, Badge } from "@impilo/mobile-design-system";
import { appStore, useAppStore } from "../stores/appStore";

interface FacilityResource {
  id: string;
  attributes: {
    name: string;
    facility_type?: string;
    district?: string;
    province?: string;
  };
}

interface Facility {
  id: string;
  name: string;
  facilityType?: string;
  district?: string;
  province?: string;
}

async function fetchFacilities(search?: string): Promise<Facility[]> {
  const params = search ? `?search=${encodeURIComponent(search)}` : "";
  const r = await apiClient.get<{ data: FacilityResource[] }>(`/internal/v1/facilities${params}`);
  return r.data.data.map((f) => ({
    id: f.id,
    name: f.attributes.name,
    facilityType: f.attributes.facility_type,
    district: f.attributes.district,
    province: f.attributes.province,
  }));
}

export function FacilityDirectoryScreen({ onOpenFacility }: { onOpenFacility?: (id: string) => void }) {
  const { selectedFacilityId, selectedFacilityName } = useAppStore((s) => ({
    selectedFacilityId: s.selectedFacilityId,
    selectedFacilityName: s.selectedFacilityName,
  }));
  const [items, setItems] = useState<Facility[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchBusy, setSearchBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const initialized = useRef(false);

  const load = useCallback(
    async (mode: "initial-or-search" | "refresh") => {
      const isRefresh = mode === "refresh";
      if (isRefresh) setRefreshing(true);
      else if (!initialized.current) setLoading(true);
      else setSearchBusy(true);
      setError(null);
      try {
        setItems(await fetchFacilities(search));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load facilities");
      } finally {
        setLoading(false);
        setRefreshing(false);
        setSearchBusy(false);
        initialized.current = true;
      }
    },
    [search]
  );

  useEffect(() => {
    void load("initial-or-search");
  }, [load]);

  const selectedChip = useMemo(() => {
    if (!selectedFacilityId || !selectedFacilityName) return null;
    return (
      <View style={styles.selectedRow}>
        <Badge variant="success">Selected</Badge>
        <Text testID="selected-facility-name" style={styles.selectedText}>{selectedFacilityName}</Text>
        <Button title="Change" size="sm" variant="outline" onPress={() => appStore.getState().clearSelectedFacility()} testID="change-facility" />
      </View>
    );
  }, [selectedFacilityId, selectedFacilityName]);

  const initialBlocking = loading && items.length === 0 && !error;
  const listEmptyAfterLoad = !loading && !refreshing && !searchBusy && !error && items.length === 0;

  return (
    <Screen>
      <Header title="Facilities" />
      <View testID="facility-directory-screen" style={styles.container}>
        {selectedChip}
        <TextField
          label="Search facilities"
          value={search}
          onChange={setSearch}
          placeholder="Type facility name..."
          testID="facility-search"
        />
        {searchBusy ? <Text style={styles.updating}>Updating results…</Text> : null}

        {initialBlocking ? (
          <View style={styles.centered}>
            <LoadingSpinner size="md" />
          </View>
        ) : error && items.length === 0 ? (
          <ErrorState title="Error" message={error} onRetry={() => void load("initial-or-search")} />
        ) : listEmptyAfterLoad ? (
          <EmptyState title="No facilities found" message="Try a different search term" />
        ) : (
          <ScrollView
            style={styles.list}
            contentContainerStyle={styles.listPad}
            refreshControl={
              <RefreshControl
                refreshing={refreshing}
                onRefresh={() => void load("refresh")}
                tintColor="#2563EB"
              />
            }
          >
            {error && items.length > 0 ? (
              <Text style={styles.inlineError}>{error} — showing previous results.</Text>
            ) : null}
            {items.map((f) => (
              <Card key={f.id}>
                <CardBody>
                  <Pressable
                    testID={`facility-row-${f.id}`}
                    onPress={() => onOpenFacility?.(f.id)}
                    style={({ pressed }) => [styles.row, { opacity: pressed ? 0.8 : 1 }]}
                  >
                    <View style={styles.info}>
                      <Text style={styles.name}>{f.name}</Text>
                      <Text style={styles.meta}>
                        {`${f.facilityType ?? "Facility"} · ${f.district ?? "—"}, ${f.province ?? "—"}`}
                      </Text>
                    </View>
                    <Button
                      title="View"
                      size="sm"
                      variant="outline"
                      onPress={() => onOpenFacility?.(f.id)}
                      testID={`view-facility-${f.id}`}
                    />
                  </Pressable>
                </CardBody>
              </Card>
            ))}
          </ScrollView>
        )}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  selectedRow: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 12 },
  selectedText: { flex: 1, fontSize: 13, color: "#111827", fontWeight: "600" },
  updating: { fontSize: 12, color: "#6B7280", marginTop: 8 },
  centered: { marginTop: 24, alignItems: "center" },
  list: { marginTop: 16, flex: 1 },
  listPad: { gap: 10, paddingBottom: 24 },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  info: { flex: 1, paddingRight: 10 },
  name: { fontSize: 15, fontWeight: "700", color: "#111827" },
  meta: { fontSize: 13, color: "#6B7280", marginTop: 4 },
  inlineError: { fontSize: 12, color: "#B91C1C", marginBottom: 8 },
});
