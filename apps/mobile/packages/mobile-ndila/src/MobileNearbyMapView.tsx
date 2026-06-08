/**
 * Citizen/provider nearby discovery map — device location + Ndila nearby API.
 */
import React, { useMemo } from "react";
import { View, Text, StyleSheet, Pressable } from "react-native";
import { MobileNdilaMapView } from "./MobileNdilaMapView";
import { useDeviceLocation, useNearbyServices } from "./hooks";
import type { NdilaCoordinate } from "./types";

const DEFAULT_CENTER: NdilaCoordinate = { latitude: -17.8252, longitude: 31.0335 };

export interface MobileNearbyMapViewProps {
  radiusMeters?: number;
  entityTypes?: string[];
  height?: number;
  autoCapture?: boolean;
}

export function MobileNearbyMapView({
  radiusMeters = 15_000,
  entityTypes = ["FACILITY", "PHARMACY", "CLINIC", "HOSPITAL", "PUBLIC_HEALTH_SITE"],
  height = 240,
  autoCapture = true,
}: MobileNearbyMapViewProps) {
  const { location, capture, capturing, error } = useDeviceLocation(autoCapture);
  const origin = location?.coordinate ?? null;
  const { results, loading } = useNearbyServices(origin, radiusMeters, entityTypes);

  const markers = useMemo(
    () =>
      results
        .filter((item) => item.latitude != null && item.longitude != null)
        .map((item) => ({
          id: item.id,
          label: item.name,
          latitude: item.latitude as number,
          longitude: item.longitude as number,
        })),
    [results],
  );

  const center = origin ?? (markers[0] ? { latitude: markers[0].latitude, longitude: markers[0].longitude } : DEFAULT_CENTER);

  return (
    <View style={styles.wrap} testID="mobile-nearby-map-view">
      <View style={styles.toolbar}>
        <Text style={styles.toolbarText}>
          {loading ? "Loading nearby…" : `${markers.length} nearby on Ndila`}
        </Text>
        <Pressable onPress={() => void capture()} disabled={capturing} style={styles.refreshBtn}>
          <Text style={styles.refreshText}>{capturing ? "Locating…" : "Use my location"}</Text>
        </Pressable>
      </View>
      {error ? <Text style={styles.error}>Location unavailable — showing default region.</Text> : null}
      <MobileNdilaMapView center={center} markers={markers} height={height} />
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: 8 },
  toolbar: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  toolbarText: { fontSize: 12, color: "#475569" },
  refreshBtn: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 8, backgroundColor: "#0f766e" },
  refreshText: { fontSize: 11, fontWeight: "600", color: "#fff" },
  error: { fontSize: 11, color: "#b45309" },
});
