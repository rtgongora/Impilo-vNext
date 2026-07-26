/**
 * FacilityDetailScreen — Citizen facility details + select (Tier-3).
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { View, Text, StyleSheet, ScrollView, RefreshControl } from "react-native";
import { apiClient } from "@impilo/mobile-api-client";
import { Screen, Header, Card, CardBody, Button, LoadingSpinner, ErrorState, Badge } from "@impilo/mobile-design-system";
import { appStore, useAppStore } from "../stores/appStore";
import {
  FacilityDisclosure,
  UnconfirmedField,
  isUnconfirmedFacility,
  toLocationPrecision,
  type LocationPrecision,
} from "@impilo/mobile-design-system";

interface FacilityResource {
  id: string;
  attributes: {
    name: string;
    facility_type?: string;
    district?: string;
    province?: string;
    address?: string;
    phone?: string;
    regulatoryStatus?: string | null;
    regulatory_status?: string | null;
    latitude?: number | null;
    longitude?: number | null;
    locationPrecision?: string | null;
  };
}

interface Facility {
  id: string;
  name: string;
  facilityType?: string;
  district?: string;
  province?: string;
  address?: string;
  phone?: string;
  regulatoryStatus?: string | null;
  locationPrecision: LocationPrecision;
}

/**
 * Location precision is derived defensively: use the server tier once the
 * coordinate programme sends one, else infer from whether a coordinate exists.
 * Defaults to "absent" — never claim a location we cannot substantiate.
 */
function resolvePrecision(a: FacilityResource["attributes"]): LocationPrecision {
  if (a.locationPrecision) return toLocationPrecision(a.locationPrecision);
  return a.latitude != null && a.longitude != null ? "address-precise" : "absent";
}

async function fetchFacility(id: string): Promise<Facility> {
  const r = await apiClient.get<{ data: FacilityResource }>(`/internal/v1/facilities/${encodeURIComponent(id)}`);
  const f = r.data.data;
  return {
    id: f.id,
    name: f.attributes.name,
    facilityType: f.attributes.facility_type,
    district: f.attributes.district,
    province: f.attributes.province,
    address: f.attributes.address,
    phone: f.attributes.phone,
    regulatoryStatus: f.attributes.regulatoryStatus ?? f.attributes.regulatory_status ?? null,
    locationPrecision: resolvePrecision(f.attributes),
  };
}

export function FacilityDetailScreen({ facilityId, onBack }: { facilityId: string; onBack?: () => void }) {
  const { selectedFacilityId } = useAppStore((s) => ({ selectedFacilityId: s.selectedFacilityId }));
  const [facility, setFacility] = useState<Facility | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (mode: "replace" | "refresh") => {
      const isRefresh = mode === "refresh";
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        setFacility(await fetchFacility(facilityId));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load facility");
        if (!isRefresh) setFacility(null);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [facilityId]
  );

  useEffect(() => {
    setFacility(null);
    void load("replace");
  }, [load]);

  const onRefresh = useCallback(() => void load("refresh"), [load]);

  const isSelected = useMemo(() => selectedFacilityId === facilityId, [selectedFacilityId, facilityId]);
  const unconfirmed = isUnconfirmedFacility(facility?.regulatoryStatus);

  if (loading && !facility) {
    return (
      <Screen>
        <Header title="Facility" />
        <LoadingSpinner size="lg" />
      </Screen>
    );
  }

  if (error && !facility) {
    return (
      <Screen>
        <Header title="Facility" />
        <ErrorState title="Error" message={error} onRetry={() => void load("replace")} />
      </Screen>
    );
  }

  if (!facility) {
    return (
      <Screen>
        <Header title="Facility" />
        <ErrorState title="Unavailable" message="Facility could not be loaded." onRetry={() => void load("replace")} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Facility Details" />
      <ScrollView
        testID="facility-detail-screen"
        style={styles.container}
        contentContainerStyle={styles.pad}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#2563EB" />}
      >
        {error ? <Text style={styles.banner}>{error}</Text> : null}
        <Card>
          <CardBody>
            <View style={styles.titleRow}>
              <Text style={styles.name}>{facility.name}</Text>
              {isSelected ? <Badge variant="success">Selected</Badge> : null}
            </View>
            <Text style={styles.meta}>
              {`${facility.facilityType ?? "Facility"} · ${facility.district ?? "—"}, ${facility.province ?? "—"}`}
            </Text>
            {/* HPA-listed facilities carry no confirmation of services, hours or
                contact details — never render these as plain verified facts. */}
            <UnconfirmedField
              label="Address"
              value={facility.address}
              unconfirmed={unconfirmed}
              testID="facility-address"
            />
            <UnconfirmedField
              label="Phone"
              value={facility.phone}
              unconfirmed={unconfirmed}
              testID="facility-phone"
            />
          </CardBody>
        </Card>

        <FacilityDisclosure
          regulatoryStatus={facility.regulatoryStatus}
          locationPrecision={facility.locationPrecision}
          hasPhone={!!facility.phone}
        />

        <View style={styles.actions}>
          <Button
            title={isSelected ? "Selected" : "Select this facility"}
            variant={isSelected ? "secondary" : "primary"}
            onPress={() => appStore.getState().setSelectedFacility(facility.id, facility.name)}
            disabled={isSelected}
            testID="select-facility-btn"
          />
          <Button title="Back to list" variant="outline" onPress={onBack ?? (() => {})} testID="back-to-facilities" />
        </View>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  pad: { padding: 16, gap: 12, paddingBottom: 32 },
  banner: { fontSize: 12, color: "#B91C1C", marginBottom: 4 },
  titleRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 10 },
  name: { flex: 1, fontSize: 16, fontWeight: "800", color: "#111827" },
  meta: { fontSize: 13, color: "#6B7280", marginTop: 6 },
  field: { fontSize: 13, color: "#374151", marginTop: 10 },
  actions: { gap: 10, marginTop: 4 },
});
