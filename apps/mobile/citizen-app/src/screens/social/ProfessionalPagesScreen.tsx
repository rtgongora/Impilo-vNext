/**
 * ProfessionalPagesScreen — Healthcare provider profiles.
 */
import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Badge, LoadingSpinner, colors } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { fetchProviders } from "../../services/professionalPagesService";

export function ProfessionalPagesScreen() {
  const { data: providers = [], isLoading } = useQuery({ queryKey: ["providers"], queryFn: () => fetchProviders() });

  if (isLoading) return <LoadingSpinner />;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Healthcare Providers</Text>
      {providers.map((p) => (
        <View key={p.id} style={styles.card}>
          <View style={styles.avatar}><Text style={styles.avatarText}>{p.displayName.charAt(0)}</Text></View>
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={styles.name}>{p.displayName}</Text>
            {p.title ? <Text style={styles.role}>{p.title}</Text> : null}
            {p.specialty ? <Badge label={p.specialty} variant="info" /> : null}
            {p.facilityName ? <Text style={styles.facility}>🏥 {p.facilityName}</Text> : null}
            <View style={styles.metaRow}>
              {p.rating ? <Text style={styles.rating}>⭐ {p.rating} ({p.reviewCount})</Text> : null}
              {p.yearsExperience ? <Text style={styles.exp}>{p.yearsExperience} yrs exp</Text> : null}
              {p.consultationFee ? <Text style={styles.fee}>{p.currency} {p.consultationFee}</Text> : null}
            </View>
            {p.bio ? <Text style={styles.bio} numberOfLines={2}>{p.bio}</Text> : null}
            {p.isAccepting && <Badge label="Accepting patients" variant="success" />}
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  card: { flexDirection: "row", backgroundColor: colors.gray[50], borderRadius: 12, padding: 16, gap: 12 },
  avatar: { width: 48, height: 48, borderRadius: 24, backgroundColor: "#DBEAFE", alignItems: "center", justifyContent: "center" },
  avatarText: { fontSize: 20, fontWeight: "700", color: "#2563EB" },
  name: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  role: { fontSize: 13, color: colors.gray[500] },
  facility: { fontSize: 12, color: colors.gray[500] },
  metaRow: { flexDirection: "row", gap: 12, flexWrap: "wrap" },
  rating: { fontSize: 12, color: colors.ui.warning.main, fontWeight: "600" },
  exp: { fontSize: 12, color: colors.gray[500] },
  fee: { fontSize: 12, color: "#009739", fontWeight: "600" },
  bio: { fontSize: 12, color: colors.gray[600], marginTop: 4 },
});
