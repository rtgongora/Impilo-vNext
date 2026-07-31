/**
 * EmergencyHubScreen — first-class emergency workspace: Episodes | ED | Trauma | Resus | MH.
 */
import React, { useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, StyleSheet } from "react-native";
import { Screen, Header, colors } from "@impilo/mobile-design-system";
import { EmergencyEpisodeBoardScreen } from "./EmergencyEpisodeBoardScreen";
import { EdVisitScreen } from "./EdVisitScreen";
import { TraumaScreen } from "./TraumaScreen";
import { ResuscitationScreen } from "./ResuscitationScreen";
import { MentalHealthQueueScreen } from "./MentalHealthQueueScreen";

type HubTab = "episodes" | "ed" | "trauma" | "resus" | "mh";

const TABS: { id: HubTab; label: string }[] = [
  { id: "episodes", label: "Episodes" },
  { id: "ed", label: "ED Visit" },
  { id: "trauma", label: "Trauma" },
  { id: "resus", label: "Resus" },
  { id: "mh", label: "MH" },
];

export function EmergencyHubScreen() {
  const [tab, setTab] = useState<HubTab>("episodes");
  const [episodeId, setEpisodeId] = useState<string | null>(null);
  const [mhReferralId, setMhReferralId] = useState<string | null>(null);

  return (
    <Screen>
      <Header title="Emergency" subtitle="Episodes · ED · Trauma · Resus · Mental health" />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.tabBar} contentContainerStyle={s.tabPad}>
        {TABS.map((t) => (
          <TouchableOpacity key={t.id} onPress={() => setTab(t.id)} style={[s.tab, tab === t.id && s.activeTab]}>
            <Text style={[s.tabText, tab === t.id && s.activeTabText]}>{t.label}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
      <View style={s.content}>
        {tab === "episodes" && (
          <EmergencyEpisodeBoardScreen
            selectedEpisodeId={episodeId}
            onSelectEpisode={setEpisodeId}
          />
        )}
        {tab === "ed" && <EdVisitScreen embedded />}
        {tab === "trauma" && <TraumaScreen />}
        {tab === "resus" && <ResuscitationScreen />}
        {tab === "mh" && (
          <MentalHealthQueueScreen
            selectedReferralId={mhReferralId}
            onSelectReferral={setMhReferralId}
          />
        )}
      </View>
    </Screen>
  );
}

const s = StyleSheet.create({
  tabBar: { maxHeight: 44, borderBottomWidth: 1, borderColor: colors.gray[200] },
  tabPad: { paddingHorizontal: 8, alignItems: "center" },
  tab: { paddingHorizontal: 14, paddingVertical: 10, marginRight: 4 },
  activeTab: { borderBottomWidth: 2, borderColor: colors.ui.error.main },
  tabText: { fontSize: 13, color: colors.gray[500], fontWeight: "500" },
  activeTabText: { color: colors.ui.error.main, fontWeight: "700" },
  content: { flex: 1 },
});
