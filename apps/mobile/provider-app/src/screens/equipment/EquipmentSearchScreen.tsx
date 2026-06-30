/**
 * EquipmentSearchScreen — field/biomedical/facility-admin asset lookup.
 *
 * Manual tag/name/serial search over the facility equipment registry. Camera/QR scan
 * substrate is not yet wired in this app (documented as a gap); this provides the
 * functional manual-search path used to locate equipment in the field.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, StyleSheet, ScrollView, TextInput } from "react-native";
import { Card, CardHeader, CardBody, Badge, LoadingSpinner, EmptyState } from "@impilo/mobile-design-system";
import { searchEquipment, type EquipmentRow } from "../../services/equipmentService";

export function EquipmentSearchScreen({ facilityId }: { facilityId?: string }) {
  const [rows, setRows] = useState<EquipmentRow[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await searchEquipment(facilityId));
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, [facilityId]);

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = rows.filter((r) => {
    const s = query.trim().toLowerCase();
    if (!s) return true;
    return (
      r.name?.toLowerCase().includes(s) ||
      r.equipment_type?.toLowerCase().includes(s) ||
      (r.serial_number ?? "").toLowerCase().includes(s) ||
      (r.tag ?? "").toLowerCase().includes(s)
    );
  });

  return (
    <ScrollView style={styles.container}>
      <TextInput
        style={styles.search}
        placeholder="Search name, type, tag or serial"
        value={query}
        onChangeText={setQuery}
      />
      {loading ? (
        <LoadingSpinner />
      ) : error ? (
        <Text style={styles.error}>Could not load equipment.</Text>
      ) : filtered.length === 0 ? (
        <EmptyState title="No equipment" message="No equipment matches this search at your facility." />
      ) : (
        filtered.map((r) => (
          <Card key={r.equipment_id}>
            <CardHeader title={r.name} />
            <CardBody>
              <Text style={styles.meta}>{r.equipment_type} · {r.tag || r.serial_number || "no tag"}</Text>
              <View style={styles.badges}>
                <Badge label={r.status} />
                <Badge label={r.criticality} />
                {r.device_id ? <Badge label="IoT linked" /> : null}
              </View>
            </CardBody>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 12 },
  search: { borderWidth: 1, borderColor: "#d1d5db", borderRadius: 8, padding: 10, marginBottom: 12 },
  meta: { color: "#6b7280", marginBottom: 6 },
  badges: { flexDirection: "row", gap: 6 },
  error: { color: "#b91c1c", padding: 12 },
});
