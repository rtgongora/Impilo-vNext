/**
 * PatientRegistrationScreen — New patient intake and ID creation.
 */
import React, { useState } from "react";
import { View, Text, TextInput, ScrollView, StyleSheet, Alert } from "react-native";
import { Screen, Header, Button } from "@impilo/mobile-design-system";
import { useMutation } from "@tanstack/react-query";
import { registerPatient } from "../../services/queueService";

export function PatientRegistrationScreen() {
  const [form, setForm] = useState({ givenName: "", familyName: "", dateOfBirth: "", sex: "MALE", nationalId: "", phone: "", email: "" });

  const mutation = useMutation({
    mutationFn: () => registerPatient(form),
    onSuccess: (data) => Alert.alert("Registered", `Patient ID: ${data.id}`),
  });

  const update = (key: string, value: string) => setForm({ ...form, [key]: value });

  return (
    <Screen><Header title="Patient Registration" />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <Text style={styles.label}>Given Name *</Text>
        <TextInput style={styles.input} value={form.givenName} onChangeText={(v) => update("givenName", v)} placeholder="First name" />
        <Text style={styles.label}>Family Name *</Text>
        <TextInput style={styles.input} value={form.familyName} onChangeText={(v) => update("familyName", v)} placeholder="Surname" />
        <Text style={styles.label}>Date of Birth</Text>
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
        <Button title={mutation.isPending ? "Registering..." : "Register Patient"} onPress={() => mutation.mutate()} disabled={!form.givenName || !form.familyName || mutation.isPending} />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 16, gap: 8 },
  label: { fontSize: 13, fontWeight: "600", color: "#374151" },
  input: { borderWidth: 1, borderColor: "#D1D5DB", borderRadius: 8, padding: 12, fontSize: 14 },
  row: { flexDirection: "row", gap: 8 },
});
