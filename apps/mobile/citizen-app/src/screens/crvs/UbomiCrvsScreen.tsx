import React, { useState } from "react";
import { View, Text, ScrollView, TextInput, StyleSheet } from "react-native";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, CardBody, Header, LoadingSpinner, Screen } from "@impilo/mobile-design-system";
import {
  fetchUbomiStatus,
  listUbomiBirthNotifications,
  listUbomiDeathNotifications,
  submitUbomiBirthNotification,
  submitUbomiDeathNotification,
  verifyUbomiRegistration,
} from "../../services/ubomiService";

export function UbomiCrvsScreen({ onBack }: { onBack?: () => void }) {
  const [registrationNumber, setRegistrationNumber] = useState("");
  const [birthChildNames, setBirthChildNames] = useState("");
  const [birthSurname, setBirthSurname] = useState("");
  const [deathName, setDeathName] = useState("");
  const [deathDate, setDeathDate] = useState("");
  const qc = useQueryClient();

  const statusQ = useQuery({ queryKey: ["citizen-ubomi-status"], queryFn: fetchUbomiStatus });
  const birthsQ = useQuery({
    queryKey: ["citizen-ubomi-births"],
    queryFn: listUbomiBirthNotifications,
    enabled: statusQ.data?.available === true,
  });
  const deathsQ = useQuery({
    queryKey: ["citizen-ubomi-deaths"],
    queryFn: listUbomiDeathNotifications,
    enabled: statusQ.data?.available === true,
  });
  const verifyQ = useQuery({
    queryKey: ["citizen-ubomi-verify", registrationNumber],
    queryFn: () => verifyUbomiRegistration(registrationNumber),
    enabled: registrationNumber.trim().length >= 4,
  });

  const submitBirthM = useMutation({
    mutationFn: () =>
      submitUbomiBirthNotification({
        childGivenNames: birthChildNames.trim(),
        childSurname: birthSurname.trim(),
        dateOfBirth: new Date().toISOString().slice(0, 10),
      }),
    onSuccess: () => {
      setBirthChildNames("");
      setBirthSurname("");
      void qc.invalidateQueries({ queryKey: ["citizen-ubomi-births"] });
    },
  });

  const submitDeathM = useMutation({
    mutationFn: () =>
      submitUbomiDeathNotification({
        deceasedName: deathName.trim(),
        dateOfDeath: deathDate.trim() || new Date().toISOString().slice(0, 10),
      }),
    onSuccess: () => {
      setDeathName("");
      setDeathDate("");
      void qc.invalidateQueries({ queryKey: ["citizen-ubomi-deaths"] });
    },
  });

  return (
    <Screen>
      <Header
        title="UBOMI Civil Registry"
        subtitle="Birth/death notifications and registration verification"
        onBack={onBack}
      />
      <ScrollView contentContainerStyle={styles.content}>
        {statusQ.isLoading ? <LoadingSpinner /> : null}
        <Card>
          <CardBody>
            <Text style={styles.label}>Service status</Text>
            <Text style={styles.value}>
              {statusQ.data?.available ? "Reachable via BFF" : statusQ.data?.message ?? "Unavailable"}
            </Text>
          </CardBody>
        </Card>

        <Text style={styles.section}>Submit birth notification</Text>
        <TextInput style={styles.input} value={birthChildNames} onChangeText={setBirthChildNames} placeholder="Child given names" />
        <TextInput style={styles.input} value={birthSurname} onChangeText={setBirthSurname} placeholder="Child surname" />
        <Button
          title={submitBirthM.isPending ? "Submitting…" : "Submit birth notification"}
          onPress={() => submitBirthM.mutate()}
          disabled={!birthChildNames.trim() || !birthSurname.trim() || submitBirthM.isPending}
        />

        <Text style={styles.section}>Submit death notification</Text>
        <TextInput style={styles.input} value={deathName} onChangeText={setDeathName} placeholder="Deceased name" />
        <TextInput style={styles.input} value={deathDate} onChangeText={setDeathDate} placeholder="Date of death (YYYY-MM-DD)" />
        <Button
          title={submitDeathM.isPending ? "Submitting…" : "Submit death notification"}
          onPress={() => submitDeathM.mutate()}
          disabled={!deathName.trim() || submitDeathM.isPending}
        />

        <Text style={styles.section}>Verify registration</Text>
        <TextInput
          style={styles.input}
          value={registrationNumber}
          onChangeText={setRegistrationNumber}
          placeholder="Registration number"
          autoCapitalize="characters"
        />
        {verifyQ.data ? (
          <Text style={styles.meta}>
            {verifyQ.data.valid ? "Valid registration" : "Not verified"} · {verifyQ.data.status ?? "UNKNOWN"}
          </Text>
        ) : null}

        <Text style={styles.section}>Recent birth notifications</Text>
        {birthsQ.isLoading ? <LoadingSpinner /> : null}
        {(birthsQ.data ?? []).slice(0, 5).map((row, index) => {
          const item = row as Record<string, unknown>;
          return (
            <Card key={String(item.id ?? index)} style={styles.rowCard}>
              <CardBody>
                <Text style={styles.rowTitle}>{String(item.childGivenNames ?? item.reference ?? "Birth record")}</Text>
                <Text style={styles.meta}>{String(item.status ?? "DRAFT")}</Text>
              </CardBody>
            </Card>
          );
        })}

        <Text style={styles.section}>Recent death notifications</Text>
        {deathsQ.isLoading ? <LoadingSpinner /> : null}
        {(deathsQ.data ?? []).slice(0, 5).map((row, index) => {
          const item = row as Record<string, unknown>;
          return (
            <Card key={String(item.id ?? `death-${index}`)} style={styles.rowCard}>
              <CardBody>
                <Text style={styles.rowTitle}>{String(item.deceasedName ?? item.reference ?? "Death record")}</Text>
                <Text style={styles.meta}>{String(item.status ?? "DRAFT")}</Text>
              </CardBody>
            </Card>
          );
        })}

        {onBack ? <Button title="Back" variant="outline" onPress={onBack} /> : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 10 },
  label: { fontSize: 12, color: "#64748b", marginBottom: 4 },
  value: { fontSize: 14, color: "#0f172a" },
  section: { marginTop: 12, fontSize: 14, fontWeight: "600", color: "#0f172a" },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    backgroundColor: "#fff",
  },
  meta: { fontSize: 12, color: "#64748b" },
  rowCard: { marginTop: 6 },
  rowTitle: { fontSize: 14, fontWeight: "500", color: "#0f172a" },
});
