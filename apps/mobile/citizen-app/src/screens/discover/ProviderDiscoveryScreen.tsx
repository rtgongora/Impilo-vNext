/**
 * ProviderDiscoveryScreen — Discover and search healthcare providers.
 * Maps to web: /discover/providers
 * Priority: MEDIUM
 */

import React, { useState, useEffect, useCallback } from "react";
import { View, Text, Pressable, StyleSheet, ScrollView, TextInput } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardBody,
  Button,
  FeatureMaturityBadge,
  LoadingSpinner,
  EmptyState,
  ErrorState,
  Avatar,
} from "@impilo/mobile-design-system";
import type { Provider } from "../../types";
import { discoverProviders } from "../../services/personalHealthService";

const SPECIALTY_VARIANTS: Record<string, string> = {
  DOCTOR: "primary",
  NURSE: "secondary",
  PHARMACIST: "warning",
  SPECIALIST: "success",
};

interface ProviderDiscoveryScreenProps {
  onSelectProvider?: (provider: Provider) => void;
}

export function ProviderDiscoveryScreen({ onSelectProvider }: ProviderDiscoveryScreenProps) {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSpecialty, setSelectedSpecialty] = useState<string | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await discoverProviders(searchQuery, selectedSpecialty ?? undefined);
      setProviders(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsLoading(false);
    }
  }, [searchQuery, selectedSpecialty]);

  useEffect(() => {
    load();
  }, [load]);

  const filteredProviders = providers.filter((p) => {
    const matchesSearch = !searchQuery || 
      p.displayName?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.specialty?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesSpecialty = !selectedSpecialty || p.title === selectedSpecialty;
    return matchesSearch && matchesSpecialty;
  });

  return (
    <Screen>
      <Header title="Find Providers" />
      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        <View style={styles.statusRow}>
          <FeatureMaturityBadge
            status={providers.length > 0 ? "connected" : "partial"}
            detail="Provider discovery is composed from /internal/v1/mobile/citizen/services/discover."
          />
          <Text style={styles.statusText}>
            {providers.length > 0
              ? "Provider discovery is now loaded from citizen service-discovery APIs."
              : "No providers matched the current search from service-discovery APIs."}
          </Text>
        </View>

        {/* Search Bar */}
        <TextInput
          style={styles.searchInput}
          placeholder="Search by name, specialty..."
          value={searchQuery}
          onChangeText={setSearchQuery}
        />

        {/* Filter Chips */}
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.filters}>
          <Pressable 
            style={[styles.filterChip, !selectedSpecialty && styles.filterChipActive]}
            onPress={() => setSelectedSpecialty(null)}
          >
            <Text style={[styles.filterChipText, !selectedSpecialty && styles.filterChipTextActive]}>
              All
            </Text>
          </Pressable>
          {Object.keys(SPECIALTY_VARIANTS).map((specialty) => (
            <Pressable 
              key={specialty}
              style={[styles.filterChip, selectedSpecialty === specialty && styles.filterChipActive]}
              onPress={() => setSelectedSpecialty(specialty)}
            >
              <Text style={[styles.filterChipText, selectedSpecialty === specialty && styles.filterChipTextActive]}>
                {specialty}
              </Text>
            </Pressable>
          ))}
        </ScrollView>

        {/* Results */}
        {isLoading ? (
          <LoadingSpinner size="sm" />
        ) : error ? (
          <ErrorState message={error.message} onRetry={load} />
        ) : filteredProviders.length === 0 ? (
          <EmptyState
            title="No providers found"
            description="Try adjusting your search or filters."
          />
        ) : (
          filteredProviders.map((provider) => (
            <Card key={provider.id}>
              <CardBody>
                <View style={styles.providerRow}>
                  <Avatar name={provider.displayName} size="lg" />
                  <View style={styles.providerInfo}>
                    <Text style={styles.providerName}>{provider.displayName}</Text>
                    <Text style={styles.providerSpecialty}>{provider.specialty ?? provider.title ?? "Provider"}</Text>
                    {provider.facilityName && (
                      <Text style={styles.providerFacility}>{provider.facilityName}</Text>
                    )}
                  </View>
                  <Button
                    title="View"
                    variant="secondary"
                    size="sm"
                    onPress={() => onSelectProvider?.(provider)}
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
  content: { flex: 1 },
  contentContainer: { padding: 16, gap: 16 },
  statusRow: {
    gap: 8,
  },
  statusText: {
    fontSize: 12,
    color: "#6B7280",
  },
  searchInput: {
    backgroundColor: "#F3F4F6",
    padding: 12,
    borderRadius: 8,
    fontSize: 14,
  },
  filters: {
    flexDirection: "row",
  },
  filterChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: "#F3F4F6",
    marginRight: 8,
  },
  filterChipActive: {
    backgroundColor: "#4F46E5",
  },
  filterChipText: {
    fontSize: 13,
    color: "#4B5563",
  },
  filterChipTextActive: {
    color: "#FFFFFF",
  },
  providerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  providerInfo: {
    flex: 1,
  },
  providerName: {
    fontSize: 14,
    fontWeight: "600",
  },
  providerSpecialty: {
    fontSize: 12,
    color: "#6B7280",
  },
  providerFacility: {
    fontSize: 11,
    color: "#9CA3AF",
  },
});
