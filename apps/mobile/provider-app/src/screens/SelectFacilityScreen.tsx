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
import { Screen, Header, Card, CardBody, Button, TextField, LoadingSpinner, EmptyState, ErrorState, FacilityUnconfirmedTag, isUnconfirmedFacility, colors } from "@impilo/mobile-design-system";
import { appStore } from "../stores/appStore";

interface Facility {
  id: string;
  name: string;
  facilityType: string;
  district: string;
  province: string;
  /**
   * tuso regulatory_status. IMPORTED_PENDING_CONFIGURATION means HPA-registered
   * but not yet configured in Impilo. Measured by the HPA lane: ZERO workspaces
   * are attached to any of the 5,509 such facilities — so selecting one cannot
   * produce a valid work session under any policy. This is a fact, not a rule.
   */
  regulatoryStatus?: string | null;
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
            regulatoryStatus?: string | null;
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
          regulatoryStatus: f.attributes.regulatoryStatus ?? null,
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

  const [blockedFacility, setBlockedFacility] = useState<Facility | null>(null);

  const handleSelectFacility = useCallback(
    (facility: Facility) => {
      /**
       * A facility that is HPA-registered but not yet configured in Impilo has no
       * workspace to bind to, so a work session cannot legitimately start there.
       * We do NOT hard-block the tap: the person tapping may be exactly the
       * practice owner who should configure it, and blocking would strand all
       * 5,509 with no route forward. Selection is accepted as an INTENT and
       * explained, rather than silently producing a broken session.
       */
      if (isUnconfirmedFacility(facility.regulatoryStatus)) {
        setBlockedFacility(facility);
        return;
      }
      setBlockedFacility(null);
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
        {blockedFacility ? (
          <Card>
            <CardBody>
              <View testID="facility-not-configured-notice" style={styles.noticeBody}>
                <Text style={styles.noticeTitle}>{blockedFacility.name} is not yet set up in Impilo</Text>
                <Text style={styles.noticeText}>
                  This facility is registered with the Health Professions Authority but is not yet set
                  up in Impilo. You cannot start a work session here until it has been configured.
                </Text>
                <Text style={styles.noticeText}>
                  If you administer this facility you can complete its setup. Otherwise, ask your
                  facility administrator to complete setup, or contact the registry.
                </Text>
                <View style={styles.noticeActions}>
                  <Button
                    title="Dismiss"
                    variant="outline"
                    size="sm"
                    onPress={() => setBlockedFacility(null)}
                    testID="dismiss-not-configured"
                  />
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}
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
                      {/* Provider framing: the regulator HAS confirmed this facility is
                          real and registered — the only gap is OUR configuration. Copy
                          that implied doubt about legitimacy would teach providers to
                          distrust the register, the opposite of this programme's purpose. */}
                      <FacilityUnconfirmedTag
                        regulatoryStatus={f.regulatoryStatus}
                        label="Not yet set up in Impilo"
                      />
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
  noticeBody: { gap: 8 },
  noticeTitle: { fontSize: 15, fontWeight: "700", color: colors.ui.warning.text },
  noticeText: { fontSize: 13, color: "#78350F", lineHeight: 19 },
  noticeActions: { flexDirection: "row", gap: 8, marginTop: 4 },
  facilityName: {
    fontWeight: "bold",
  },
  facilityDetail: {
    fontSize: 14,
    color: colors.gray[500],
    marginTop: 4,
  },
});
