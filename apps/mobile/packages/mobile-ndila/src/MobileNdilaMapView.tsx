/**
 * Governed mobile map surface — reuses Ndila tile config and marker contracts.
 * Uses react-native-maps when the host app provides it; otherwise renders a
 * coordinate fallback for tests and web previews.
 */
import React, { useMemo } from "react";
import { View, Text, StyleSheet } from "react-native";
import type { NdilaCoordinate } from "./types";
import { useNdilaTileConfig } from "./hooks";

export interface MobileNdilaMapMarker {
  id: string;
  label?: string;
  latitude: number;
  longitude: number;
}

export interface MobileNdilaMapViewProps {
  center: NdilaCoordinate;
  markers?: MobileNdilaMapMarker[];
  height?: number;
  testID?: string;
}

type MapModule = {
  default: React.ComponentType<{
    style?: object;
    initialRegion?: {
      latitude: number;
      longitude: number;
      latitudeDelta: number;
      longitudeDelta: number;
    };
    children?: React.ReactNode;
  }>;
  Marker: React.ComponentType<{
    coordinate: NdilaCoordinate;
    title?: string;
    description?: string;
  }>;
};

let mapModule: MapModule | null = null;
try {
  // Optional native dependency — host apps add react-native-maps.
  mapModule = require("react-native-maps") as MapModule;
} catch {
  mapModule = null;
}

const DEFAULT_DELTA = 0.08;

export function MobileNdilaMapView({
  center,
  markers = [],
  height = 220,
  testID = "mobile-ndila-map",
}: MobileNdilaMapViewProps) {
  const { config, loading } = useNdilaTileConfig();

  const region = useMemo(
    () => ({
      latitude: center.latitude,
      longitude: center.longitude,
      latitudeDelta: DEFAULT_DELTA,
      longitudeDelta: DEFAULT_DELTA,
    }),
    [center.latitude, center.longitude],
  );

  if (!mapModule) {
    return (
      <View style={[styles.fallback, { height }]} testID={`${testID}-fallback`}>
        <Text style={styles.fallbackTitle}>Ndila map (list fallback)</Text>
        <Text style={styles.fallbackMeta}>
          Provider: {loading ? "loading…" : config?.provider ?? "Ndila"}
        </Text>
        {markers.length === 0 ? (
          <Text style={styles.fallbackEmpty}>No geo-tagged markers</Text>
        ) : (
          markers.slice(0, 6).map((marker) => (
            <Text key={marker.id} style={styles.fallbackRow}>
              {marker.label ?? marker.id}: {marker.latitude.toFixed(4)}, {marker.longitude.toFixed(4)}
            </Text>
          ))
        )}
      </View>
    );
  }

  const MapView = mapModule.default;
  const Marker = mapModule.Marker;

  return (
    <View style={{ height }} testID={testID}>
      <MapView style={styles.map} initialRegion={region}>
        {markers.map((marker) => (
          <Marker
            key={marker.id}
            coordinate={{ latitude: marker.latitude, longitude: marker.longitude }}
            title={marker.label}
          />
        ))}
      </MapView>
      <Text style={styles.attribution}>
        {config?.attribution ?? "Ndila governed tiles"}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  map: { flex: 1, borderRadius: 12 },
  attribution: { marginTop: 4, fontSize: 10, color: "#64748b" },
  fallback: {
    borderRadius: 12,
    borderWidth: 1,
    borderColor: "#cbd5e1",
    backgroundColor: "#f8fafc",
    padding: 12,
  },
  fallbackTitle: { fontSize: 13, fontWeight: "600", color: "#0f172a", marginBottom: 4 },
  fallbackMeta: { fontSize: 11, color: "#64748b", marginBottom: 8 },
  fallbackEmpty: { fontSize: 12, color: "#94a3b8" },
  fallbackRow: { fontSize: 11, color: "#334155", marginTop: 2 },
});
