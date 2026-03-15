/**
 * SelectFacilityScreen — Facility selection after authentication.
 *
 * Fetches facilities from indawo-service via BFF and lets the user
 * pick their working facility before entering the app.
 */

import React, { useState, useEffect, useCallback } from "react";
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
import { appStore } from "../stores/appStore";

interface Facility {
  id: string;
  name: string;
  facilityType: string;
  district: string;
  province: string;
}

export function SelectFacilityScreen() {
  const auth = useAuth();
  const [facilities, setFacilities] = useState<Facility[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchFacilities = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = search ? `?search=${encodeURIComponent(search)}` : "";
      const response = await apiClient.get<{
        data: {
          id: string;
          attributes: {
            name: string;
            facility_type: string;
            district: string;
            province: string;
          };
        }[];
      }>(`/internal/v1/facilities${params}`);
      setFacilities(
        response.data.data.map((f) => ({
          id: f.id,
          name: f.attributes.name,
          facilityType: f.attributes.facility_type,
          district: f.attributes.district,
          province: f.attributes.province,
        }))
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load facilities");
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    fetchFacilities();
  }, [fetchFacilities]);

  const handleSelectFacility = useCallback(
    (facility: Facility) => {
      appStore.getState().setFacilityContext(facility.id, facility.name);
      auth.setFacility(facility.id, facility.name);
    },
    [auth]
  );

  return (
    <Screen>
      <Header title="Select Facility" />
      <View testID="select-facility-screen" style={styles.container}>
        <TextField
          label="Search facilities"
          value={search}
          onChange={setSearch}
          placeholder="Type facility name..."
          testID="facility-search"
        />
        <View style={styles.listContainer}>
          {loading ? (
            <LoadingSpinner size="md" />
          ) : error ? (
            <ErrorState
              title="Error"
              message={error}
              onRetry={fetchFacilities}
            />
          ) : facilities.length === 0 ? (
            <EmptyState
              title="No facilities found"
              message="Try a different search term"
            />
          ) : (
            facilities.map((f) => (
              <Card key={f.id}>
                <CardBody>
                  <View style={styles.facilityRow}>
                    <View style={styles.facilityInfo}>
                      <Text style={styles.facilityName}>{f.name}</Text>
                      <Text style={styles.facilityDetail}>
                        {`${f.facilityType} \u00B7 ${f.district}, ${f.province}`}
                      </Text>
                    </View>
                    <Button
                      title="Select"
                      variant="primary"
                      size="sm"
                      onPress={() => handleSelectFacility(f)}
                      testID={`select-facility-${f.id}`}
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
  container: {
    padding: 16,
  },
  listContainer: {
    marginTop: 16,
  },
  facilityRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  facilityInfo: {
    flex: 1,
  },
  facilityName: {
    fontWeight: "bold",
  },
  facilityDetail: {
    fontSize: 14,
    color: "#6B7280",
    marginTop: 4,
  },
});
