/**
 * GatewayVerifyScreen — guest "trust" pillar (no sign-in): verify a health professional's
 * registration and search the national facility register. Mirrors the web /verify/practitioner
 * and facility search over the anonymous public gateway lanes. Register facts only, no PII.
 */
import React, { useCallback, useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header, Card, CardBody, TextField, Button, Badge, LoadingSpinner, EmptyState, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  verifyPractitioner,
  searchPublicFacilities,
  fetchPublicFacilityProfile,
  type PractitionerVerification,
  type PublicFacility,
} from "../../services/gatewayVerifyService";

const GREEN = "#059669";

type Mode = "practitioner" | "facility";

export function GatewayVerifyScreen({ onBack }: { onBack?: () => void }) {
  const [mode, setMode] = useState<Mode>("practitioner");

  return (
    <Screen>
      <Header
        title="Verify"
        subtitle="Check a practitioner or find a facility"
        leftElement={
          onBack ? (
            <Pressable onPress={onBack} hitSlop={8} accessibilityLabel="Back">
              <Ionicons name="arrow-back" size={22} color={GREEN} />
            </Pressable>
          ) : undefined
        }
      />
      <View style={styles.tabRow}>
        <Button
          title="Practitioner"
          size="sm"
          variant={mode === "practitioner" ? "primary" : "outline"}
          onPress={() => setMode("practitioner")}
          testID="verify-tab-practitioner"
        />
        <Button
          title="Facility"
          size="sm"
          variant={mode === "facility" ? "primary" : "outline"}
          onPress={() => setMode("facility")}
          testID="verify-tab-facility"
        />
      </View>
      {mode === "practitioner" ? <PractitionerTab /> : <FacilityTab />}
    </Screen>
  );
}

function PractitionerTab() {
  const [regNo, setRegNo] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [result, setResult] = useState<PractitionerVerification | null>(null);

  const run = useCallback(async () => {
    if (!regNo.trim()) return;
    setLoading(true);
    setError(null);
    setSearched(true);
    try {
      setResult(await verifyPractitioner(regNo));
    } catch {
      setError("Verification is temporarily unavailable. Please try again shortly.");
      setResult(null);
    } finally {
      setLoading(false);
    }
  }, [regNo]);

  const current = result?.registerStatus?.toUpperCase() === "ACTIVE" || result?.status?.toUpperCase() === "ACTIVE";

  return (
    <ScrollView testID="verify-practitioner" contentContainerStyle={styles.body}>
      <Text style={styles.help}>
        Enter a registration number to confirm it belongs to a registered health professional and
        whether their practising certificate is current.
      </Text>
      <View style={styles.searchRow}>
        <View style={styles.searchField}>
          <TextField
            testID="verify-practitioner-input"
            value={regNo}
            onChangeText={setRegNo}
            placeholder="e.g. MDCN-12345"
            autoCapitalize="characters"
          />
        </View>
        <Button title="Verify" size="md" onPress={run} disabled={loading || !regNo.trim()} testID="verify-practitioner-go" />
      </View>

      {loading ? (
        <LoadingSpinner />
      ) : error ? (
        <ErrorState message={error} onRetry={run} />
      ) : searched && !result ? (
        <EmptyState
          title="No match found"
          description="No registered professional matches that number. Check the number and try again."
        />
      ) : result ? (
        <Card>
          <CardBody>
            <View style={styles.resultHeader}>
              <Text style={styles.resultName}>{result.displayName ?? "Registered professional"}</Text>
              <Badge label={current ? "Certificate current" : "Not current"} variant={current ? "success" : "warning"} />
            </View>
            {result.profession ? <Field label="Profession" value={result.profession} /> : null}
            {result.registrationNumber ? <Field label="Registration no." value={result.registrationNumber} /> : null}
            {result.registerStatus ? <Field label="Register status" value={result.registerStatus} /> : null}
            {result.certificateValidTo ? <Field label="Certificate valid to" value={result.certificateValidTo} /> : null}
            {result.registeringAuthority ? <Field label="Authority" value={result.registeringAuthority} /> : null}
          </CardBody>
        </Card>
      ) : null}
    </ScrollView>
  );
}

function FacilityTab() {
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [facilities, setFacilities] = useState<PublicFacility[]>([]);
  const [profile, setProfile] = useState<PublicFacility | null>(null);
  const [profileLoading, setProfileLoading] = useState(false);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    setSearched(true);
    setProfile(null);
    try {
      const res = await searchPublicFacilities({ query: q || undefined, size: 20 });
      setFacilities(res.data);
    } catch {
      setError("Facility search is temporarily unavailable. Please try again shortly.");
      setFacilities([]);
    } finally {
      setLoading(false);
    }
  }, [q]);

  const openProfile = useCallback(async (id: string | number | undefined) => {
    if (id === undefined) return;
    setProfileLoading(true);
    try {
      setProfile(await fetchPublicFacilityProfile(id));
    } catch {
      setProfile(null);
    } finally {
      setProfileLoading(false);
    }
  }, []);

  if (profile) {
    return (
      <ScrollView contentContainerStyle={styles.body}>
        <Pressable onPress={() => setProfile(null)} style={styles.backRow}>
          <Ionicons name="arrow-back" size={18} color={GREEN} />
          <Text style={styles.backText}>Back to results</Text>
        </Pressable>
        <Card>
          <CardBody>
            <Text style={styles.resultName}>{profile.name ?? "Facility"}</Text>
            {profile.facilityType ? <Field label="Type" value={profile.facilityType} /> : null}
            {profile.level ? <Field label="Level" value={profile.level} /> : null}
            {profile.district ? <Field label="District" value={profile.district} /> : null}
            {profile.province ? <Field label="Province" value={profile.province} /> : null}
            {profile.status ? <Field label="Status" value={profile.status} /> : null}
          </CardBody>
        </Card>
      </ScrollView>
    );
  }

  return (
    <ScrollView testID="verify-facility" contentContainerStyle={styles.body}>
      <Text style={styles.help}>
        Search the national facility register. Public profiles show name, type, level and location
        only.
      </Text>
      <View style={styles.searchRow}>
        <View style={styles.searchField}>
          <TextField
            testID="verify-facility-input"
            value={q}
            onChangeText={setQ}
            placeholder="Facility name or area"
            autoCapitalize="none"
          />
        </View>
        <Button title="Search" size="md" onPress={run} disabled={loading} testID="verify-facility-go" />
      </View>

      {loading || profileLoading ? (
        <LoadingSpinner />
      ) : error ? (
        <ErrorState message={error} onRetry={run} />
      ) : searched && facilities.length === 0 ? (
        <EmptyState title="No facilities found" description="Try a different name or area." />
      ) : facilities.length > 0 ? (
        <Card>
          <CardBody>
            {facilities.map((f, idx) => (
              <Pressable
                key={String(f.id ?? idx)}
                testID={`verify-facility-row-${idx}`}
                onPress={() => void openProfile(f.id)}
                style={[styles.facilityRow, idx < facilities.length - 1 ? styles.facilityRowBorder : undefined]}
              >
                <View style={{ flex: 1 }}>
                  <Text style={styles.facilityName}>{f.name ?? "Facility"}</Text>
                  <Text style={styles.facilityMeta}>
                    {[f.facilityType, f.level, f.district, f.province].filter(Boolean).join(" · ")}
                  </Text>
                </View>
                {f.status ? <Badge label={f.status} variant="info" /> : null}
              </Pressable>
            ))}
          </CardBody>
        </Card>
      ) : null}
    </ScrollView>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <Text style={styles.fieldValue}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  tabRow: { flexDirection: "row", gap: 8, paddingHorizontal: 16, paddingTop: 8 },
  body: { padding: 16, gap: 12 },
  help: { fontSize: 13, color: colors.gray[500], lineHeight: 19 },
  searchRow: { flexDirection: "row", alignItems: "flex-end", gap: 8 },
  searchField: { flex: 1 },
  resultHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 8 },
  resultName: { fontSize: 16, fontWeight: "800", color: colors.gray[900], flexShrink: 1, paddingRight: 8 },
  field: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 6, borderTopWidth: 1, borderTopColor: colors.gray[100] },
  fieldLabel: { fontSize: 12, color: colors.gray[500] },
  fieldValue: { fontSize: 13, fontWeight: "600", color: colors.gray[900], flexShrink: 1, textAlign: "right", paddingLeft: 12 },
  facilityRow: { flexDirection: "row", alignItems: "center", gap: 8, paddingVertical: 12 },
  facilityRowBorder: { borderBottomWidth: 1, borderBottomColor: colors.gray[100] },
  facilityName: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  facilityMeta: { fontSize: 12, color: colors.gray[500], marginTop: 2 },
  backRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  backText: { color: GREEN, fontSize: 14, fontWeight: "600" },
});
