/**
 * HouseholdListScreen — Household register with GPS-tagged entries.
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  TextField,
  Badge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import { getHouseholds, registerHousehold, recordCommunityVisit } from "../../services/householdService";
import { useAppStore } from "../../stores/appStore";
import { useOfflineStore } from "@impilo/mobile-offline";
import type { Household } from "../../types";

export function HouseholdListScreen() {
  const { facilityId, isOnline } = useAppStore();
  const [households, setHouseholds] = useState<Household[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showRegister, setShowRegister] = useState(false);
  const [registerForm, setRegisterForm] = useState({
    headOfHousehold: "",
    address: "",
  });
  const [saving, setSaving] = useState(false);

  // Offline store for households
  const { data: offlineHouseholds } = useOfflineStore<Household>("households");

  const loadHouseholds = useCallback(async () => {
    if (!facilityId) return;
    setLoading(true);
    setError(null);
    try {
      const result = await getHouseholds(facilityId, 0, 100);
      setHouseholds(result.households);
    } catch (err) {
      // Fall back to offline data
      if (offlineHouseholds.length > 0) {
        setHouseholds(offlineHouseholds);
      } else {
        setError(err instanceof Error ? err.message : "Failed to load households");
      }
    } finally {
      setLoading(false);
    }
  }, [facilityId, offlineHouseholds]);

  useEffect(() => {
    loadHouseholds();
  }, [loadHouseholds]);

  const handleRegister = useCallback(async () => {
    if (!facilityId || !registerForm.headOfHousehold || !registerForm.address) return;
    setSaving(true);
    setError(null);
    try {
      const household = await registerHousehold({
        headOfHousehold: registerForm.headOfHousehold,
        address: registerForm.address,
        gpsLatitude: 0,
        gpsLongitude: 0,
        facilityId,
        members: [],
      });
      setHouseholds((prev) => [household, ...prev]);
      setShowRegister(false);
      setRegisterForm({ headOfHousehold: "", address: "" });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setSaving(false);
    }
  }, [facilityId, registerForm]);

  const handleStartVisit = useCallback(async (household: Household) => {
    try {
      await recordCommunityVisit({
        householdId: household.id,
        visitType: "GENERAL",
        gpsLatitude: household.gpsLatitude,
        gpsLongitude: household.gpsLongitude,
      });
      loadHouseholds();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to start visit");
    }
  }, [loadHouseholds]);

  const filtered = households.filter(
    (h) =>
      !search ||
      h.headOfHousehold.toLowerCase().includes(search.toLowerCase()) ||
      h.address.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <Screen>
      <Header title="Households" />
      <ScrollView testID="household-list-screen" style={styles.container}>
        <View style={styles.searchRow}>
          <TextField
            label="Search"
            value={search}
            onChange={setSearch}
            placeholder="Search households..."
            testID="household-search"
          />
          <Button
            title="Register New"
            variant="primary"
            onPress={() => setShowRegister(true)}
            testID="register-household-btn"
          />
        </View>

        {showRegister && (
          <Card>
            <CardBody>
              <TextField
                label="Head of Household"
                value={registerForm.headOfHousehold}
                onChange={(v: string) => setRegisterForm((f) => ({ ...f, headOfHousehold: v }))}
                testID="hh-head"
              />
              <TextField
                label="Address"
                value={registerForm.address}
                onChange={(v: string) => setRegisterForm((f) => ({ ...f, address: v }))}
                testID="hh-address"
              />
              <View style={styles.formActions}>
                <Button title="Save" onPress={handleRegister} loading={saving} testID="save-household-btn" />
                <Button title="Cancel" variant="ghost" onPress={() => setShowRegister(false)} />
              </View>
            </CardBody>
          </Card>
        )}

        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadHouseholds} />
        ) : filtered.length === 0 ? (
          <EmptyState title="No households" message="Register a new household to begin" />
        ) : (
          filtered.map((hh) => (
            <Card key={hh.id}>
              <CardBody>
                <View testID={`household-${hh.id}`} style={styles.householdRow}>
                  <View>
                    <Text style={styles.boldText}>{hh.headOfHousehold}</Text>
                    <Text style={styles.addressText}>{hh.address}</Text>
                    <View style={styles.badgeRow}>
                      <Badge variant="outline">{`${hh.members.length} members`}</Badge>
                      <Badge
                        variant={hh.riskCategory === "HIGH" ? "destructive" : hh.riskCategory === "MEDIUM" ? "secondary" : "outline"}
                      >
                        {hh.riskCategory}
                      </Badge>
                      {hh.lastVisitDate && (
                        <Badge variant="outline">{`Last: ${new Date(hh.lastVisitDate).toLocaleDateString()}`}</Badge>
                      )}
                    </View>
                  </View>
                  <Button
                    title="Visit"
                    variant="primary"
                    size="sm"
                    onPress={() => handleStartVisit(hh)}
                    testID={`visit-hh-${hh.id}`}
                  />
                </View>
              </CardBody>
            </Card>
          ))
        )}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  searchRow: {
    flexDirection: "row",
    gap: 8,
    marginBottom: 16,
  },
  formActions: {
    flexDirection: "row",
    gap: 8,
    marginTop: 12,
  },
  householdRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  boldText: {
    fontWeight: "700",
  },
  addressText: {
    fontSize: 14,
    color: "#6B7280",
  },
  badgeRow: {
    flexDirection: "row",
    gap: 4,
    marginTop: 4,
  },
});
