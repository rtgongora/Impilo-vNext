/**
 * FacilitySetupScreen — facility go-live configuration for facility staff.
 *
 * Pick a facility (from the control-tower aggregate), see its go-live readiness checklist, and
 * configure departments (units) and service points with inline create forms. Backed by the live
 * facility-mode setup routes (facilitySetupService, same as the web facility setup wizard).
 * Closes part of GAP-19. Real data only — no mock rows.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet, TextInput, TouchableOpacity } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Badge,
  Button,
  LoadingSpinner,
  EmptyState,
  ErrorState,
} from "@impilo/mobile-design-system";
import {
  fetchControlTowerAggregate,
  type ControlTowerFacility,
} from "../../services/controlTowerService";
import {
  createFacilityUnit,
  createServicePoint,
  fetchFacilityUnits,
  fetchServicePoints,
  fetchSetupState,
  type FacilitySetupState,
  type FacilityUnit,
  type ServicePoint,
} from "../../services/facilitySetupService";

const READINESS: Array<{ key: keyof FacilitySetupState; label: string }> = [
  { key: "departmentsConfigured", label: "Departments" },
  { key: "servicePointsConfigured", label: "Service points" },
  { key: "queuesConfigured", label: "Queues" },
  { key: "workflowsConfigured", label: "Workflows" },
  { key: "workforceLinked", label: "Workforce" },
  { key: "orosRoutingConfigured", label: "OROS routing" },
  { key: "khulumaChannelsConfigured", label: "Comms channels" },
  { key: "fundoReady", label: "Learning" },
];

type Section = "checklist" | "units" | "service_points";

export function FacilitySetupScreen() {
  const [facilities, setFacilities] = useState<ControlTowerFacility[]>([]);
  const [selected, setSelected] = useState<ControlTowerFacility | null>(null);
  const [section, setSection] = useState<Section>("checklist");
  const [setup, setSetup] = useState<FacilitySetupState | null>(null);
  const [units, setUnits] = useState<FacilityUnit[]>([]);
  const [servicePoints, setServicePoints] = useState<ServicePoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [sectionLoading, setSectionLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [unitName, setUnitName] = useState("");
  const [unitType, setUnitType] = useState("");
  const [spName, setSpName] = useState("");
  const [spType, setSpType] = useState("");

  const loadFacilities = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setFacilities((await fetchControlTowerAggregate()).facilities);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load facilities");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadSection = useCallback(
    async (facility: ControlTowerFacility, sec: Section) => {
      setSectionLoading(true);
      try {
        if (sec === "checklist") setSetup(await fetchSetupState(facility.facilityId));
        else if (sec === "units") setUnits(await fetchFacilityUnits(facility.facilityId));
        else setServicePoints(await fetchServicePoints(facility.facilityId));
      } finally {
        setSectionLoading(false);
      }
    },
    [],
  );

  const selectFacility = useCallback(
    (facility: ControlTowerFacility) => {
      setSelected(facility);
      setSection("checklist");
      void loadSection(facility, "checklist");
    },
    [loadSection],
  );

  const switchSection = useCallback(
    (sec: Section) => {
      setSection(sec);
      if (selected) void loadSection(selected, sec);
    },
    [selected, loadSection],
  );

  const onAddUnit = useCallback(async () => {
    if (!selected || !unitName.trim()) return;
    setBusy(true);
    try {
      await createFacilityUnit(selected.facilityId, {
        name: unitName.trim(),
        unitType: unitType.trim() || undefined,
      });
      setUnitName("");
      setUnitType("");
      await loadSection(selected, "units");
    } finally {
      setBusy(false);
    }
  }, [selected, unitName, unitType, loadSection]);

  const onAddServicePoint = useCallback(async () => {
    if (!selected || !spName.trim()) return;
    setBusy(true);
    try {
      await createServicePoint(selected.facilityId, {
        name: spName.trim(),
        servicePointType: spType.trim() || undefined,
      });
      setSpName("");
      setSpType("");
      await loadSection(selected, "service_points");
    } finally {
      setBusy(false);
    }
  }, [selected, spName, spType, loadSection]);

  useEffect(() => {
    loadFacilities();
  }, [loadFacilities]);

  return (
    <Screen>
      <Header title="Facility Setup" />
      <View testID="facility-setup-screen" style={styles.container}>
        {loading ? (
          <LoadingSpinner size="md" />
        ) : error ? (
          <ErrorState title="Error" message={error} onRetry={loadFacilities} />
        ) : (
          <ScrollView style={styles.scrollArea} contentContainerStyle={styles.scrollContent}>
            {!selected ? (
              facilities.length === 0 ? (
                <EmptyState title="No facilities" message="No facilities in your control scope" />
              ) : (
                <>
                  <Text style={styles.sectionLabel}>Select a facility to configure</Text>
                  {facilities.map((f) => (
                    <TouchableOpacity
                      key={f.facilityId}
                      testID={`facility-${f.facilityId}`}
                      onPress={() => selectFacility(f)}
                      activeOpacity={0.7}
                    >
                      <Card>
                        <CardBody>
                          <Text style={styles.boldText}>{f.name}</Text>
                        </CardBody>
                      </Card>
                    </TouchableOpacity>
                  ))}
                </>
              )
            ) : (
              <>
                <View style={styles.toggleRow}>
                  <Button title="Checklist" variant={section === "checklist" ? "primary" : "outline"} onPress={() => switchSection("checklist")} testID="section-checklist" />
                  <Button title="Departments" variant={section === "units" ? "primary" : "outline"} onPress={() => switchSection("units")} testID="section-units" />
                  <Button title="Service Points" variant={section === "service_points" ? "primary" : "outline"} onPress={() => switchSection("service_points")} testID="section-service-points" />
                </View>
                <Button title="← Change facility" variant="ghost" onPress={() => setSelected(null)} testID="change-facility" />

                {sectionLoading ? (
                  <LoadingSpinner size="md" />
                ) : section === "checklist" && setup ? (
                  <Card>
                    <CardHeader title={`${selected.name} — go-live readiness`} />
                    <CardBody>
                      <View style={styles.checklist}>
                        {READINESS.map((step) => (
                          <View key={step.key} style={styles.checkRow}>
                            <Text style={styles.detailText}>{step.label}</Text>
                            <Badge variant={setup[step.key] ? "success" : "secondary"}>
                              {setup[step.key] ? "Done" : "Pending"}
                            </Badge>
                          </View>
                        ))}
                      </View>
                      <View style={styles.goLiveRow}>
                        <Text style={styles.boldText}>Ready for go-live</Text>
                        <Badge variant={setup.readyForGoLive ? "success" : "warning"}>
                          {setup.readyForGoLive ? "Yes" : "Not yet"}
                        </Badge>
                      </View>
                      {setup.nextStep ? (
                        <Text style={styles.detailText}>Next step: {setup.nextStep}</Text>
                      ) : null}
                    </CardBody>
                  </Card>
                ) : section === "units" ? (
                  <>
                    <Card>
                      <CardHeader title="Add a department" />
                      <CardBody>
                        <TextInput testID="unit-name" style={styles.input} placeholder="Department name" value={unitName} onChangeText={setUnitName} />
                        <TextInput testID="unit-type" style={styles.input} placeholder="Type (e.g. OUTPATIENT, WARD)" value={unitType} onChangeText={setUnitType} />
                        <Button title={busy ? "Adding…" : "Add department"} variant="primary" onPress={onAddUnit} disabled={busy || !unitName.trim()} testID="add-unit" />
                      </CardBody>
                    </Card>
                    {units.length === 0 ? (
                      <EmptyState title="No departments" message="No units configured yet" />
                    ) : (
                      units.map((u) => (
                        <Card key={u.id}>
                          <CardBody>
                            <View style={styles.listRow}>
                              <View style={styles.listRowInfo}>
                                <Text style={styles.boldText}>{u.name}</Text>
                                <Text style={styles.detailText}>{u.unitType}</Text>
                              </View>
                              {u.registrationRequired ? <Badge variant="warning">Reg required</Badge> : null}
                            </View>
                          </CardBody>
                        </Card>
                      ))
                    )}
                  </>
                ) : section === "service_points" ? (
                  <>
                    <Card>
                      <CardHeader title="Add a service point" />
                      <CardBody>
                        <TextInput testID="sp-name" style={styles.input} placeholder="Service point name" value={spName} onChangeText={setSpName} />
                        <TextInput testID="sp-type" style={styles.input} placeholder="Type (e.g. TRIAGE, DISPENSING)" value={spType} onChangeText={setSpType} />
                        <Button title={busy ? "Adding…" : "Add service point"} variant="primary" onPress={onAddServicePoint} disabled={busy || !spName.trim()} testID="add-service-point" />
                      </CardBody>
                    </Card>
                    {servicePoints.length === 0 ? (
                      <EmptyState title="No service points" message="No service points configured yet" />
                    ) : (
                      servicePoints.map((sp) => (
                        <Card key={sp.id}>
                          <CardBody>
                            <View style={styles.listRow}>
                              <View style={styles.listRowInfo}>
                                <Text style={styles.boldText}>{sp.name}</Text>
                                <Text style={styles.detailText}>{sp.servicePointType}</Text>
                              </View>
                              <Badge variant={sp.active ? "success" : "secondary"}>{sp.status}</Badge>
                            </View>
                          </CardBody>
                        </Card>
                      ))
                    )}
                  </>
                ) : null}
              </>
            )}
          </ScrollView>
        )}

        <View style={styles.refreshContainer}>
          <Button title="Refresh" variant="outline" onPress={loadFacilities} testID="refresh-setup" />
        </View>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  scrollArea: { flex: 1 },
  scrollContent: { gap: 12, paddingBottom: 16 },
  toggleRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  sectionLabel: { fontSize: 13, fontWeight: "700", color: "#6B7280" },
  checklist: { gap: 8 },
  checkRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  goLiveRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginTop: 12 },
  listRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  listRowInfo: { flex: 1, gap: 2 },
  boldText: { fontWeight: "700", color: "#111827" },
  detailText: { fontSize: 13, color: "#6B7280" },
  input: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
    color: "#111827",
  },
  refreshContainer: { marginTop: 8 },
});
