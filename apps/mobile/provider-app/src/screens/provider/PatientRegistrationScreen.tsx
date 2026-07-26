/**
 * PatientRegistrationScreen — New patient intake aligned with BFF / Vito + registry geo.
 */
import React, { useEffect, useState } from "react";
import { View, Text, TextInput, ScrollView, StyleSheet, Alert, TouchableOpacity } from "react-native";
import { Screen, Header, Button, colors } from "@impilo/mobile-design-system";
import { useMutation, useQuery } from "@tanstack/react-query";
import { fetchZwDistricts, fetchZwWards, registerPatient } from "../../services/queueService";

export interface PatientRegistrationScreenProps {
  embedded?: boolean;
  onRegistered?: () => void;
}

function rowCode(r: Record<string, unknown>, ...keys: string[]): string {
  for (const k of keys) {
    const v = r[k];
    if (v != null && String(v).length > 0) return String(v);
  }
  return "";
}

function rowName(r: Record<string, unknown>): string {
  return String(r.name ?? r.display ?? "");
}

export function PatientRegistrationScreen({ embedded, onRegistered }: PatientRegistrationScreenProps = {}) {
  const [form, setForm] = useState({
    givenName: "",
    familyName: "",
    dateOfBirth: "",
    sex: "MALE",
    nationalId: "",
    phone: "",
    email: "",
    countryAlpha2: "ZW",
    provinceCode: "",
    districtCode: "",
    wardCode: "",
    localityGazetteerId: "",
    localityProposal: "",
    registrationMode: "HEALTH_WORKER_INITIATED",
    initiatingContext: "FACILITY",
  });

  const districtsQ = useQuery({
    queryKey: ["zw-districts", form.provinceCode],
    queryFn: () => fetchZwDistricts(form.provinceCode),
    enabled: form.countryAlpha2 === "ZW" && form.provinceCode.length > 0,
  });

  const wardsQ = useQuery({
    queryKey: ["zw-wards", form.districtCode],
    queryFn: () => fetchZwWards(form.districtCode),
    enabled: form.countryAlpha2 === "ZW" && form.districtCode.length > 0,
  });

  useEffect(() => {
    setForm((f) => ({ ...f, districtCode: "", wardCode: "" }));
  }, [form.provinceCode]);

  useEffect(() => {
    setForm((f) => ({ ...f, wardCode: "" }));
  }, [form.districtCode]);

  const mutation = useMutation({
    mutationFn: () =>
      registerPatient({
        givenName: form.givenName,
        familyName: form.familyName,
        dateOfBirth: form.dateOfBirth,
        sex: form.sex,
        nationalId: form.nationalId,
        phone: form.phone,
        email: form.email,
        country_alpha2: form.countryAlpha2,
        province_code: form.countryAlpha2 === "ZW" ? form.provinceCode : undefined,
        district_code: form.countryAlpha2 === "ZW" ? form.districtCode : undefined,
        ward_code: form.countryAlpha2 === "ZW" ? form.wardCode : undefined,
        locality_gazetteer_id: form.localityGazetteerId || undefined,
        locality_proposal_text: form.localityProposal || undefined,
        registration_mode: form.registrationMode,
        initiating_actor: "PROVIDER",
        initiating_context: form.initiatingContext,
        assurance_level: "ASSISTED_UNVERIFIED",
        identity_state: "PROVISIONAL",
        consent_status: "OBTAINED",
        purpose_of_use: "TREATMENT",
        source_workflow: "MOBILE_PROVIDER_REGISTRATION",
      }),
    onSuccess: (data) => {
      Alert.alert("Registered", `Patient ID: ${data.id}`);
      onRegistered?.();
    },
    onError: (e: Error) => {
      Alert.alert("Registration failed", e.message ?? "Unknown error");
    },
  });

  const update = (key: string, value: string) => setForm({ ...form, [key]: value });

  const body = (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.label}>Given Name *</Text>
      <TextInput style={styles.input} value={form.givenName} onChangeText={(v) => update("givenName", v)} placeholder="First name" />
      <Text style={styles.label}>Family Name *</Text>
      <TextInput style={styles.input} value={form.familyName} onChangeText={(v) => update("familyName", v)} placeholder="Surname" />
      <Text style={styles.label}>Date of Birth *</Text>
      <TextInput style={styles.input} value={form.dateOfBirth} onChangeText={(v) => update("dateOfBirth", v)} placeholder="YYYY-MM-DD" />
      <Text style={styles.label}>Sex</Text>
      <View style={styles.row}>
        {["MALE", "FEMALE", "OTHER"].map((s) => (
          <Button key={s} title={s} size="sm" variant={form.sex === s ? "default" : "outline"} onPress={() => update("sex", s)} />
        ))}
      </View>
      <Text style={styles.label}>National ID</Text>
      <TextInput style={styles.input} value={form.nationalId} onChangeText={(v) => update("nationalId", v)} placeholder="National ID number" />
      <Text style={styles.label}>Phone</Text>
      <TextInput style={styles.input} value={form.phone} onChangeText={(v) => update("phone", v)} placeholder="+263..." keyboardType="phone-pad" />
      <Text style={styles.label}>Email</Text>
      <TextInput style={styles.input} value={form.email} onChangeText={(v) => update("email", v)} placeholder="email@example.com" keyboardType="email-address" />

      <Text style={styles.section}>Zimbabwe location (Tuso-backed)</Text>
      <Text style={styles.hint}>Province code (Admin 1 — same codes as web seed, e.g. HRE)</Text>
      <TextInput style={styles.input} value={form.provinceCode} onChangeText={(v) => update("provinceCode", v.toUpperCase())} placeholder="Province code" autoCapitalize="characters" />
      <Text style={styles.hint}>District</Text>
      <ScrollView horizontal style={styles.chips}>
        {(districtsQ.data ?? []).map((r) => {
          const code = rowCode(r, "districtCode", "code");
          const label = rowName(r) || code;
          if (!code) return null;
          return (
            <TouchableOpacity
              key={code}
              style={[styles.chip, form.districtCode === code && styles.chipOn]}
              onPress={() => update("districtCode", code)}
            >
              <Text style={styles.chipText}>{label}</Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
      <Text style={styles.hint}>Ward</Text>
      <ScrollView horizontal style={styles.chips}>
        {(wardsQ.data ?? []).map((r) => {
          const code = rowCode(r, "wardCode", "code");
          const label = rowName(r) || code;
          if (!code) return null;
          return (
            <TouchableOpacity
              key={code}
              style={[styles.chip, form.wardCode === code && styles.chipOn]}
              onPress={() => update("wardCode", code)}
            >
              <Text style={styles.chipText}>{label}</Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
      <Text style={styles.label}>Locality gazetteer ID (optional)</Text>
      <TextInput style={styles.input} value={form.localityGazetteerId} onChangeText={(v) => update("localityGazetteerId", v)} />
      <Text style={styles.label}>Locality proposal text (optional)</Text>
      <TextInput style={styles.input} value={form.localityProposal} onChangeText={(v) => update("localityProposal", v)} />

      <Button title={mutation.isPending ? "Registering..." : "Register Patient"} onPress={() => mutation.mutate()} disabled={!form.givenName || !form.familyName || !form.dateOfBirth || mutation.isPending} />
    </ScrollView>
  );

  if (embedded) {
    return body;
  }

  return (
    <Screen>
      <Header title="Patient Registration" />
      {body}
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 8 },
  label: { fontSize: 13, fontWeight: "600", color: colors.gray[700] },
  hint: { fontSize: 11, color: colors.gray[500] },
  section: { marginTop: 8, fontSize: 14, fontWeight: "700", color: colors.gray[900] },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 12, fontSize: 14 },
  row: { flexDirection: "row", gap: 8 },
  chips: { flexGrow: 0, marginVertical: 4 },
  chip: { paddingHorizontal: 10, paddingVertical: 6, borderRadius: 16, backgroundColor: colors.gray[100], marginRight: 8 },
  chipOn: { backgroundColor: "#DBEAFE" },
  chipText: { fontSize: 12, color: colors.gray[800] },
});
