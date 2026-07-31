/**
 * Specialty workspace panel.
 *
 * Tool behaviour comes from SPECIALTY_TOOL_REGISTRY, never from a label's position in the
 * workspace array. The previous mechanism picked a form by index — index >= 4 meant "coming
 * soon", index === 3 meant a generic two-number adder, anything else meant a free-text notes
 * box — so Partograph rendered as a notes box because it was first in its list, and
 * "Risk Assessment" rendered an arbitrary sum under a suicide-risk label. That positional
 * mechanism is gone; see the registry for why it was the root defect rather than the labels.
 *
 * Nothing in this panel computes a clinical value. Where a real implementation exists it is
 * rendered or pointed at; where one does not, the entry says so and names the lane and wave
 * that owns it.
 */

import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, TouchableOpacity, Modal, StyleSheet, Alert } from "react-native";
import type { SpecialtyWorkspaceDef } from "../../data/specialtyWorkspaces";
import { colors } from "@impilo/mobile-design-system";
import { dispositionForTool, type ToolDisposition } from "../../data/specialtyToolRegistry";
import { APGARScreen } from "./APGARScreen";
import { PartographWorkspace, CtgWorkspace, MaternalNearMissWorkspace, PphProtocolWorkspace, EclampsiaProtocolWorkspace } from "./MaternityWorkspaces";

type Props = {
  workspace: SpecialtyWorkspaceDef;
  onBack: () => void;
};

/**
 * The real surfaces this panel can render.
 *
 * A WIRED or CONSOLIDATED entry names a key here rather than carrying its own copy of an
 * instrument, which is what makes the reachability rule checkable: the guard test asserts every
 * declared surface exists in this map and every entry in this map is claimed by some tool, so a
 * registry entry cannot advertise a surface that was never built, and a surface cannot be left
 * stranded with nothing pointing at it.
 */
export const RENDERED_SURFACES: Record<string, () => React.ReactElement> = {
  APGARScreen: () => <APGARScreen />,
  PartographWorkspace: () => <PartographWorkspace />,
  CtgWorkspace: () => <CtgWorkspace />,
  MaternalNearMissWorkspace: () => <MaternalNearMissWorkspace />,
  PphProtocolWorkspace: () => <PphProtocolWorkspace />,
  EclampsiaProtocolWorkspace: () => <EclampsiaProtocolWorkspace />,
};

/**
 * Resolves a tool label to its declared disposition. Exported for the guard test.
 * Takes only the label — position is deliberately not a parameter.
 */
export function resolveTool(toolName: string): ToolDisposition | undefined {
  return dispositionForTool(toolName);
}

function hintFor(d: ToolDisposition | undefined): { text: string; warn: boolean } {
  if (!d) return { text: "Not registered", warn: true };
  if (d.state === "WIRED") return { text: "Open", warn: false };
  if (d.state === "CONSOLIDATED") return { text: "Open", warn: false };
  if (d.withdrawnForSafety) return { text: `Withdrawn — ${d.owner} ${d.wave}`, warn: true };
  return { text: `In development — ${d.owner === "UNASSIGNED" ? "owner being assigned" : `${d.owner} ${d.wave}`}`, warn: false };
}

export function SpecialtyWorkspacePanel({ workspace, onBack }: Props) {
  const [modalTool, setModalTool] = useState<string | null>(null);

  const quickActions = useMemo(
    () => [
      { id: "handoff", label: "Handoff summary" },
      { id: "orders", label: "Pending orders" },
      { id: "alerts", label: "Safety alerts" },
    ],
    [],
  );

  return (
    <View style={styles.wrap}>
      <View style={styles.header}>
        <TouchableOpacity onPress={onBack} style={styles.backBtn}>
          <Text style={styles.backBtnText}>← All specialties</Text>
        </TouchableOpacity>
        <View style={styles.titleRow}>
          <View style={styles.iconBadge}>
            <Text style={styles.iconBadgeText}>{workspace.name.charAt(0)}</Text>
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>{workspace.name}</Text>
            <Text style={styles.sub}>
              {workspace.tools.length} tools · {workspace.icon}
            </Text>
          </View>
        </View>
      </View>

      <Text style={styles.sectionLabel}>Quick actions</Text>
      <View style={styles.quickRow}>
        {quickActions.map((q) => (
          <TouchableOpacity
            key={q.id}
            style={styles.quickChip}
            onPress={() => Alert.alert(q.label, "Workflow stub — connect to live tasks when wired.")}
          >
            <Text style={styles.quickChipText}>{q.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <Text style={styles.sectionLabel}>Specialty tools</Text>
      <ScrollView style={styles.toolList} contentContainerStyle={{ paddingBottom: 24 }}>
        {workspace.tools.map((tool, index) => {
          const hint = hintFor(resolveTool(tool));
          return (
            <TouchableOpacity
              key={`${tool}-${index}`}
              style={styles.toolCard}
              testID={`specialty-tool-${tool}`}
              onPress={() => setModalTool(tool)}
            >
              <Text style={styles.toolTitle}>{tool}</Text>
              <Text style={[styles.toolHint, hint.warn && styles.toolHintWarn]}>{hint.text}</Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      <Modal visible={!!modalTool} animationType="slide" transparent onRequestClose={() => setModalTool(null)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalSheet}>
            {modalTool && (
              <ToolModalBody
                workspaceName={workspace.name}
                toolName={modalTool}
                onClose={() => setModalTool(null)}
              />
            )}
          </View>
        </View>
      </Modal>
    </View>
  );
}

function ToolModalBody({
  workspaceName,
  toolName,
  onClose,
}: {
  workspaceName: string;
  toolName: string;
  onClose: () => void;
}) {
  const d = resolveTool(toolName);

  if (d?.state === "WIRED" || d?.state === "CONSOLIDATED") {
    const Surface = RENDERED_SURFACES[d.surface];
    // Governed instruments carry a running observation list alongside their form, so they get a
    // bounded fixed-height container rather than a short scroll view.
    return (
      <>
        <Text style={styles.modalTitle}>{toolName}</Text>
        <Text style={styles.modalMeta}>
          {workspaceName}
          {d.state === "CONSOLIDATED" ? ` · ${d.route}` : ""}
        </Text>
        <View style={{ height: 480 }}>
          {Surface ? <Surface /> : <Text style={styles.modalDesc}>{d.note}</Text>}
        </View>
        <TouchableOpacity style={styles.secondaryBtn} onPress={onClose}>
          <Text style={styles.secondaryBtnText}>Close</Text>
        </TouchableOpacity>
      </>
    );
  }

  return (
    <>
      <Text style={styles.modalTitle}>{toolName}</Text>
      <Text style={styles.modalMeta}>{workspaceName}</Text>
      {d?.state === "IN_DEVELOPMENT" && d.withdrawnForSafety && (
        <Text style={styles.warnBadge}>Withdrawn on safety grounds — do not use a remembered value</Text>
      )}
      <ScrollView style={{ maxHeight: 340 }}>
        {d?.state === "IN_DEVELOPMENT" ? (
          <>
            <Text style={styles.modalDesc}>{d.note}</Text>
            <Text style={styles.modalDesc}>
              <Text style={styles.emphasis}>
                {d.owner === "UNASSIGNED"
                  ? "Owning lane is being assigned by the coordinator."
                  : `In development — ${d.owner}, ${d.wave}.`}
              </Text>{" "}
              This entry stays here so the gap is visible and tracked rather than hidden. Record what you
              need in the encounter note meanwhile.
            </Text>
            {d.evidence && <Text style={styles.evidence}>Completion of record: {d.evidence}</Text>}
          </>
        ) : (
          <Text style={styles.modalDesc}>
            This tool is not registered, so the app cannot say what it does. Treat it as unavailable and
            report it — an unregistered label is a defect, not a feature.
          </Text>
        )}
      </ScrollView>
      <TouchableOpacity style={styles.primaryBtn} onPress={onClose}>
        <Text style={styles.primaryBtnText}>Close</Text>
      </TouchableOpacity>
    </>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, gap: 10 },
  header: { gap: 8 },
  backBtn: { alignSelf: "flex-start", paddingVertical: 4 },
  backBtnText: { fontSize: 14, color: "#2563EB", fontWeight: "600" },
  titleRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  iconBadge: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
  },
  iconBadgeText: { fontSize: 18, fontWeight: "800", color: "#1D4ED8" },
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  sub: { fontSize: 12, color: colors.gray[500], marginTop: 2 },
  sectionLabel: { fontSize: 13, fontWeight: "600", color: colors.gray[700], marginTop: 4 },
  quickRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  quickChip: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 20, backgroundColor: "#EEF2FF" },
  quickChipText: { fontSize: 12, fontWeight: "600", color: "#4338CA" },
  toolList: { flex: 1 },
  toolCard: {
    backgroundColor: colors.gray[50],
    borderRadius: 12,
    padding: 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  toolTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  toolHint: { fontSize: 12, color: colors.gray[500], marginTop: 4 },
  toolHintWarn: { color: "#B45309", fontWeight: "600" },
  modalOverlay: { flex: 1, backgroundColor: "rgba(0,0,0,0.45)", justifyContent: "flex-end" },
  modalSheet: {
    backgroundColor: "#fff",
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    gap: 12,
    maxHeight: "88%",
  },
  modalTitle: { fontSize: 17, fontWeight: "700", color: colors.gray[900] },
  modalMeta: { fontSize: 12, color: colors.gray[500] },
  modalDesc: { fontSize: 14, color: colors.gray[600], lineHeight: 20, marginBottom: 10 },
  emphasis: { fontWeight: "700", color: colors.gray[800] },
  evidence: { fontSize: 12, color: colors.gray[500], fontStyle: "italic" },
  warnBadge: {
    fontSize: 12,
    fontWeight: "700",
    color: "#92400E",
    backgroundColor: "#FEF3C7",
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  primaryBtn: { backgroundColor: "#2563EB", borderRadius: 10, paddingVertical: 12, alignItems: "center", marginTop: 4 },
  primaryBtnText: { color: "#fff", fontWeight: "700", fontSize: 15 },
  secondaryBtn: { paddingVertical: 10, alignItems: "center" },
  secondaryBtnText: { color: "#2563EB", fontWeight: "600", fontSize: 15 },
});
