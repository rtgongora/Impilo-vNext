/**
 * PACSViewerScreen — DICOM imaging viewer for mobile.
 */
import React, { useState } from "react";
import { View, Text, Image, FlatList, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, LoadingSpinner, Badge } from "@impilo/mobile-design-system";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@impilo/mobile-api-client";

export function PACSViewerScreen() {
  const [selectedStudy, setSelectedStudy] = useState<string | null>(null);
  const [selectedInstance, setSelectedInstance] = useState<string | null>(null);

  const { data: studyIds = [], isLoading } = useQuery({
    queryKey: ["pacs-studies"],
    queryFn: async () => { const r = await apiClient.get<string[]>("/internal/v1/pacs/studies"); return r.data; },
  });

  const { data: studyDetail } = useQuery({
    queryKey: ["pacs-study", selectedStudy],
    queryFn: async () => { const r = await apiClient.get<Record<string, unknown>>(`/internal/v1/pacs/studies/${selectedStudy}`); return r.data; },
    enabled: !!selectedStudy,
  });

  if (isLoading) return <Screen><Header title="Imaging" /><LoadingSpinner /></Screen>;

  if (selectedInstance) {
    return (
      <Screen><Header title="DICOM Viewer" />
        <View style={styles.viewer}>
          <Image source={{ uri: `http://localhost:8160/internal/v1/pacs/instances/${selectedInstance}/preview` }} style={styles.dicomImage} resizeMode="contain" />
          <TouchableOpacity style={styles.backBtn} onPress={() => setSelectedInstance(null)}>
            <Text style={styles.backText}>Back to Series</Text>
          </TouchableOpacity>
        </View>
      </Screen>
    );
  }

  if (selectedStudy && studyDetail) {
    const series = ((studyDetail as Record<string, unknown>).Series ?? []) as string[];
    const tags = ((studyDetail as Record<string, unknown>).MainDicomTags ?? {}) as Record<string, unknown>;
    return (
      <Screen><Header title="Study Series" />
        <View style={styles.container}>
          <Text style={styles.title}>{String(tags.StudyDescription ?? "Study")}</Text>
          <FlatList data={series} keyExtractor={(s) => s} numColumns={2} renderItem={({ item }) => (
            <TouchableOpacity style={styles.seriesCard} onPress={() => setSelectedInstance(item)}>
              <View style={styles.seriesThumb}><Text style={styles.thumbText}>DICOM</Text></View>
              <Text style={styles.seriesId}>{item.slice(0, 8)}</Text>
            </TouchableOpacity>
          )} />
          <TouchableOpacity style={styles.backBtn} onPress={() => setSelectedStudy(null)}>
            <Text style={styles.backText}>Back to Studies</Text>
          </TouchableOpacity>
        </View>
      </Screen>
    );
  }

  return (
    <Screen><Header title="PACS Imaging" />
      <View style={styles.container}>
        <Text style={styles.title}>Imaging Studies ({studyIds.length})</Text>
        <FlatList data={studyIds.slice(0, 20)} keyExtractor={(s) => s} renderItem={({ item }) => (
          <TouchableOpacity style={styles.studyCard} onPress={() => setSelectedStudy(item)}>
            <Text style={styles.studyId}>{item.slice(0, 12)}...</Text>
            <Badge label="View" variant="info" />
          </TouchableOpacity>
        )} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16, gap: 8 },
  viewer: { flex: 1, backgroundColor: "#000" },
  dicomImage: { flex: 1, width: "100%" },
  title: { fontSize: 16, fontWeight: "700", color: "#111827" },
  studyCard: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", backgroundColor: "#F9FAFB", borderRadius: 8, padding: 14, marginBottom: 6 },
  studyId: { fontSize: 13, fontFamily: "monospace", color: "#374151" },
  seriesCard: { flex: 1, margin: 4, backgroundColor: "#1F2937", borderRadius: 8, padding: 8, alignItems: "center" },
  seriesThumb: { width: 80, height: 80, backgroundColor: "#374151", borderRadius: 8, alignItems: "center", justifyContent: "center" },
  thumbText: { color: "#6B7280", fontSize: 10 },
  seriesId: { color: "#9CA3AF", fontSize: 10, marginTop: 4 },
  backBtn: { padding: 12, alignItems: "center" },
  backText: { color: "#3B82F6", fontSize: 14, fontWeight: "600" },
});
