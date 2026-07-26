/**
 * EmergencySOSSection — Emergency contacts and one-tap SOS.
 */
import React, { useState } from "react";
import { View, Text, TouchableOpacity, TextInput, StyleSheet, Alert } from "react-native";
import { Button, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { fetchEmergencyContacts, addEmergencyContact, deleteEmergencyContact, triggerSOS } from "../../services/sosService";
import { callWardStaff } from "../../services/wardAlertService";
import { useAppStore } from "../../stores/appStore";

export function EmergencySOSSection() {
  const profile = useAppStore((s) => s.profile);
  const patientId = profile?.cpid ?? "";
  const queryClient = useQueryClient();
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState({ name: "", relationship: "", phone: "" });

  const { data: contacts = [], isLoading } = useQuery({
    queryKey: ["sos-contacts", patientId], queryFn: () => fetchEmergencyContacts(patientId), enabled: !!patientId,
  });

  const addMutation = useMutation({
    mutationFn: () => addEmergencyContact({ patientId, ...form }),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["sos-contacts"] }); setShowAdd(false); setForm({ name: "", relationship: "", phone: "" }); },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteEmergencyContact(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sos-contacts"] }),
  });

  const sosMutation = useMutation({
    mutationFn: () => triggerSOS({ patientId, alertType: "MEDICAL" }),
    onSuccess: (data) => Alert.alert("SOS Activated", data.message),
  });

  const wardAlertMutation = useMutation({
    mutationFn: () => callWardStaff({ patientId, alertType: "CALL_NURSE", message: "Patient requested ward assistance" }),
    onSuccess: (data) => Alert.alert("Ward notified", data.message),
    onError: () => Alert.alert("Could not reach ward", "Try the SOS button if this is an emergency."),
  });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.sosButton} onPress={() => {
        Alert.alert("Emergency SOS", "This will alert emergency services and your emergency contacts. Continue?",
          [{ text: "Cancel", style: "cancel" }, { text: "Activate SOS", style: "destructive", onPress: () => sosMutation.mutate() }]);
      }}>
        <Text style={styles.sosText}>SOS</Text>
        <Text style={styles.sosSubtext}>Tap to activate emergency alert</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.wardButton} onPress={() => wardAlertMutation.mutate()} disabled={!patientId || wardAlertMutation.isPending}>
        <Text style={styles.wardButtonText}>Call ward nurse</Text>
        <Text style={styles.wardSubtext}>Alert bedside staff while you are admitted</Text>
      </TouchableOpacity>

      <View style={styles.contactsHeader}>
        <Text style={styles.sectionTitle}>Emergency Contacts</Text>
        <TouchableOpacity onPress={() => setShowAdd(!showAdd)}><Text style={styles.addLink}>+ Add</Text></TouchableOpacity>
      </View>

      {showAdd && (
        <View style={styles.addForm}>
          <TextInput style={styles.input} placeholder="Name" value={form.name} onChangeText={(v) => setForm({ ...form, name: v })} />
          <TextInput style={styles.input} placeholder="Relationship" value={form.relationship} onChangeText={(v) => setForm({ ...form, relationship: v })} />
          <TextInput style={styles.input} placeholder="Phone" value={form.phone} onChangeText={(v) => setForm({ ...form, phone: v })} keyboardType="phone-pad" />
          <Button title="Save Contact" onPress={() => addMutation.mutate()} disabled={!form.name || !form.phone || addMutation.isPending} />
        </View>
      )}

      {contacts.length === 0 ? (
        <Text style={styles.empty}>No emergency contacts added yet</Text>
      ) : (
        contacts.map((c) => (
          <View key={c.id} style={styles.contactCard}>
            <View style={{ flex: 1 }}>
              <Text style={styles.contactName}>{c.name} {c.isPrimary ? "⭐" : ""}</Text>
              <Text style={styles.contactDetail}>{c.relationship} · {c.phone}</Text>
            </View>
            <TouchableOpacity onPress={() => deleteMutation.mutate(c.id)}><Text style={styles.deleteText}>Remove</Text></TouchableOpacity>
          </View>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 16 },
  sosButton: { backgroundColor: colors.ui.error.main, borderRadius: 24, padding: 32, alignItems: "center", gap: 4 },
  sosText: { color: "#FFFFFF", fontSize: 48, fontWeight: "900" },
  sosSubtext: { color: "#FCA5A5", fontSize: 13 },
  wardButton: { backgroundColor: "#2563EB", borderRadius: 16, padding: 20, alignItems: "center", gap: 4 },
  wardButtonText: { color: "#FFFFFF", fontSize: 18, fontWeight: "800" },
  wardSubtext: { color: "#BFDBFE", fontSize: 12 },
  contactsHeader: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: colors.gray[900] },
  addLink: { color: "#2563EB", fontSize: 14, fontWeight: "600" },
  addForm: { gap: 8, backgroundColor: colors.gray[50], borderRadius: 12, padding: 16 },
  input: { borderWidth: 1, borderColor: colors.gray[300], borderRadius: 8, padding: 10, fontSize: 14 },
  empty: { color: colors.gray[400], fontSize: 14, textAlign: "center", paddingVertical: 20 },
  contactCard: { flexDirection: "row", alignItems: "center", backgroundColor: colors.gray[50], borderRadius: 12, padding: 16 },
  contactName: { fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  contactDetail: { fontSize: 13, color: colors.gray[500] },
  deleteText: { color: colors.ui.error.main, fontSize: 13 },
});
