import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  TextInput,
  Modal,
  StyleSheet,
  Switch,
  Alert,
} from "react-native";
import type { SpecialtyWorkspaceDef } from "../../data/specialtyWorkspaces";
import { DictationAssistButton } from "@impilo/mobile-design-system";

type Props = {
  workspace: SpecialtyWorkspaceDef;
  onBack: () => void;
};

type ToolFormKind = "rule9" | "bsa" | "parkland" | "ktv" | "notes" | "checklist" | "sum" | "soon";

function formKindForTool(toolName: string, index: number): ToolFormKind {
  if (index >= 4) return "soon";
  const t = toolName.toLowerCase();
  if (t.includes("rule of 9")) return "rule9";
  if (t.includes("bsa")) return "bsa";
  if (t.includes("parkland")) return "parkland";
  if (t.includes("kt/v") || t.includes("ktv")) return "ktv";
  if (t.includes("checklist") || t.includes("pre-chemo")) return "checklist";
  if (index === 3) return "sum";
  return "notes";
}

const RULE9_REGIONS: { key: string; label: string; pct: number }[] = [
  { key: "head", label: "Head / neck", pct: 9 },
  { key: "ra", label: "Right arm", pct: 9 },
  { key: "la", label: "Left arm", pct: 9 },
  { key: "torso", label: "Chest / abdomen", pct: 18 },
  { key: "back", label: "Back", pct: 18 },
  { key: "rl", label: "Right leg", pct: 18 },
  { key: "ll", label: "Left leg", pct: 18 },
];

export function SpecialtyWorkspacePanel({ workspace, onBack }: Props) {
  const [modalTool, setModalTool] = useState<{ name: string; index: number } | null>(null);

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
            <Text style={styles.sub}>{workspace.tools.length} tools · {workspace.icon}</Text>
          </View>
        </View>
      </View>

      <Text style={styles.sectionLabel}>Quick actions</Text>
      <View style={styles.quickRow}>
        {quickActions.map((q) => (
          <TouchableOpacity key={q.id} style={styles.quickChip} onPress={() => Alert.alert(q.label, "Workflow stub — connect to live tasks when wired.")}>
            <Text style={styles.quickChipText}>{q.label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <Text style={styles.sectionLabel}>Specialty tools</Text>
      <ScrollView style={styles.toolList} contentContainerStyle={{ paddingBottom: 24 }}>
        {workspace.tools.map((tool, index) => (
          <TouchableOpacity key={`${tool}-${index}`} style={styles.toolCard} onPress={() => setModalTool({ name: tool, index })}>
            <Text style={styles.toolTitle}>{tool}</Text>
            <Text style={styles.toolHint}>{index < 4 ? "Tap for workspace form" : "Coming soon overview"}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <Modal visible={!!modalTool} animationType="slide" transparent onRequestClose={() => setModalTool(null)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalSheet}>
            {modalTool && (
              <ToolModalBody
                workspaceName={workspace.name}
                toolName={modalTool.name}
                toolIndex={modalTool.index}
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
  toolIndex,
  onClose,
}: {
  workspaceName: string;
  toolName: string;
  toolIndex: number;
  onClose: () => void;
}) {
  const kind = formKindForTool(toolName, toolIndex);

  if (kind === "soon") {
    return (
      <>
        <Text style={styles.modalTitle}>{toolName}</Text>
        <Text style={styles.modalDesc}>Full clinical workflow for this tool is not implemented yet. Use the experience EHR or document in SOAP for now.</Text>
        <TouchableOpacity style={styles.primaryBtn} onPress={onClose}>
          <Text style={styles.primaryBtnText}>Close</Text>
        </TouchableOpacity>
      </>
    );
  }

  return (
    <>
      <Text style={styles.modalTitle}>{toolName}</Text>
      <Text style={styles.modalMeta}>{workspaceName}</Text>
      <ScrollView style={{ maxHeight: 360 }}>
        {kind === "rule9" && <RuleOf9Form toolName={toolName} />}
        {kind === "bsa" && <BsaForm />}
        {kind === "parkland" && <ParklandForm />}
        {kind === "ktv" && <KtVForm />}
        {kind === "checklist" && <ChecklistForm />}
        {kind === "sum" && <SumForm />}
        {kind === "notes" && <NotesForm />}
      </ScrollView>
      <TouchableOpacity style={styles.secondaryBtn} onPress={onClose}>
        <Text style={styles.secondaryBtnText}>Close</Text>
      </TouchableOpacity>
    </>
  );
}

function RuleOf9Form({ toolName }: { toolName: string }) {
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const pct = useMemo(
    () => RULE9_REGIONS.filter((r) => selected[r.key]).reduce((s, r) => s + r.pct, 0),
    [selected],
  );
  return (
    <View style={styles.formBlock}>
      <Text style={styles.formLabel}>{toolName}</Text>
      {RULE9_REGIONS.map((r) => (
        <View key={r.key} style={styles.switchRow}>
          <Text style={styles.switchLabel}>{r.label} ({r.pct}%)</Text>
          <Switch value={!!selected[r.key]} onValueChange={(v) => setSelected((prev) => ({ ...prev, [r.key]: v }))} />
        </View>
      ))}
      <Text style={styles.result}>Estimated TBSA: {pct}%</Text>
      <TouchableOpacity style={styles.primaryBtn} onPress={() => Alert.alert("Saved", `TBSA ${pct}% recorded locally.`)}>
        <Text style={styles.primaryBtnText}>Save snapshot</Text>
      </TouchableOpacity>
    </View>
  );
}

function BsaForm() {
  const [h, setH] = useState("");
  const [w, setW] = useState("");
  const bsa = useMemo(() => {
    const heightCm = parseFloat(h);
    const weightKg = parseFloat(w);
    if (!Number.isFinite(heightCm) || !Number.isFinite(weightKg) || heightCm <= 0 || weightKg <= 0) return null;
    return Math.sqrt((heightCm * weightKg) / 3600);
  }, [h, w]);
  return (
    <View style={styles.formBlock}>
      <Text style={styles.formLabel}>BSA (Mosteller-style)</Text>
      <Text style={styles.inputLabel}>Height (cm)</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={h} onChangeText={setH} placeholder="170" />
      <Text style={styles.inputLabel}>Weight (kg)</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={w} onChangeText={setW} placeholder="70" />
      <Text style={styles.result}>{bsa != null ? `BSA ≈ ${bsa.toFixed(2)} m²` : "Enter height and weight"}</Text>
      <TouchableOpacity style={styles.primaryBtn} onPress={() => Alert.alert("BSA", bsa != null ? `${bsa.toFixed(2)} m²` : "Incomplete")}>
        <Text style={styles.primaryBtnText}>Save</Text>
      </TouchableOpacity>
    </View>
  );
}

function ParklandForm() {
  const [tbsa, setTbsa] = useState("");
  const [weight, setWeight] = useState("");
  const fluid = useMemo(() => {
    const p = parseFloat(tbsa);
    const kg = parseFloat(weight);
    if (!Number.isFinite(p) || !Number.isFinite(kg) || p <= 0 || kg <= 0) return null;
    return 4 * kg * p;
  }, [tbsa, weight]);
  return (
    <View style={styles.formBlock}>
      <Text style={styles.formLabel}>Parkland (24h crystalloid estimate)</Text>
      <Text style={styles.inputLabel}>TBSA %</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={tbsa} onChangeText={setTbsa} />
      <Text style={styles.inputLabel}>Weight (kg)</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={weight} onChangeText={setWeight} />
      <Text style={styles.result}>
        {fluid != null ? `First 24h ringers lactate ≈ ${Math.round(fluid)} ml (4 ml × kg × %TBSA)` : "Enter TBSA % and weight"}
      </Text>
    </View>
  );
}

function KtVForm() {
  const [pre, setPre] = useState("");
  const [post, setPost] = useState("");
  const ktv = useMemo(() => {
    const a = parseFloat(pre);
    const b = parseFloat(post);
    if (!Number.isFinite(a) || !Number.isFinite(b) || a <= 0 || b <= 0 || b >= a) return null;
    return Math.log(a / b);
  }, [pre, post]);
  return (
    <View style={styles.formBlock}>
      <Text style={styles.formLabel}>Single-pool Kt/V (simplified)</Text>
      <Text style={styles.inputLabel}>Pre BUN (mg/dL)</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={pre} onChangeText={setPre} />
      <Text style={styles.inputLabel}>Post BUN (mg/dL)</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={post} onChangeText={setPost} />
      <Text style={styles.result}>{ktv != null ? `Kt/V ≈ ${ktv.toFixed(2)}` : "Enter valid pre/post BUN"}</Text>
    </View>
  );
}

function ChecklistForm() {
  const [a, setA] = useState(false);
  const [b, setB] = useState(false);
  const [c, setC] = useState(false);
  const done = a && b && c;
  return (
    <View style={styles.formBlock}>
      <Text style={styles.formLabel}>Pre-session checklist</Text>
      <View style={styles.switchRow}>
        <Text style={styles.switchLabel}>Consents / ID verified</Text>
        <Switch value={a} onValueChange={setA} />
      </View>
      <View style={styles.switchRow}>
        <Text style={styles.switchLabel}>Labs reviewed</Text>
        <Switch value={b} onValueChange={setB} />
      </View>
      <View style={styles.switchRow}>
        <Text style={styles.switchLabel}>Emergency meds available</Text>
        <Switch value={c} onValueChange={setC} />
      </View>
      <Text style={styles.result}>{done ? "Ready to proceed" : "Complete all items"}</Text>
    </View>
  );
}

function SumForm() {
  const [x, setX] = useState("");
  const [y, setY] = useState("");
  const total = useMemo(() => {
    const a = parseFloat(x);
    const b = parseFloat(y);
    if (!Number.isFinite(a) || !Number.isFinite(b)) return null;
    return a + b;
  }, [x, y]);
  return (
    <View style={styles.formBlock}>
      <Text style={styles.formLabel}>Quick calculator</Text>
      <TextInput style={styles.input} keyboardType="decimal-pad" value={x} onChangeText={setX} placeholder="Value A" />
      <TextInput style={styles.input} keyboardType="decimal-pad" value={y} onChangeText={setY} placeholder="Value B" />
      <Text style={styles.result}>{total != null ? `Result: ${total}` : "Enter two numbers"}</Text>
    </View>
  );
}

function NotesForm() {
  const [notes, setNotes] = useState("");
  return (
    <View style={styles.formBlock}>
      <View style={styles.notesLabelRow}>
        <Text style={styles.formLabel}>Structured notes</Text>
        <DictationAssistButton fieldLabel="structured notes" testID="specialty-notes-dictation-assist" />
      </View>
      <TextInput style={[styles.input, { minHeight: 100 }]} multiline value={notes} onChangeText={setNotes} placeholder="Clinical findings, plan..." />
      <TouchableOpacity style={styles.primaryBtn} onPress={() => Alert.alert("Saved", notes ? "Note captured locally." : "Nothing to save")}>
        <Text style={styles.primaryBtnText}>Save</Text>
      </TouchableOpacity>
    </View>
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
  title: { fontSize: 18, fontWeight: "700", color: "#111827" },
  sub: { fontSize: 12, color: "#6B7280", marginTop: 2 },
  sectionLabel: { fontSize: 13, fontWeight: "600", color: "#374151", marginTop: 4 },
  quickRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  quickChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 20,
    backgroundColor: "#EEF2FF",
  },
  quickChipText: { fontSize: 12, fontWeight: "600", color: "#4338CA" },
  toolList: { flex: 1 },
  toolCard: {
    backgroundColor: "#F9FAFB",
    borderRadius: 12,
    padding: 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  toolTitle: { fontSize: 14, fontWeight: "600", color: "#111827" },
  toolHint: { fontSize: 12, color: "#6B7280", marginTop: 4 },
  modalOverlay: { flex: 1, backgroundColor: "rgba(0,0,0,0.45)", justifyContent: "flex-end" },
  modalSheet: {
    backgroundColor: "#fff",
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    gap: 12,
    maxHeight: "88%",
  },
  modalTitle: { fontSize: 17, fontWeight: "700", color: "#111827" },
  modalMeta: { fontSize: 12, color: "#6B7280" },
  modalDesc: { fontSize: 14, color: "#4B5563", lineHeight: 20 },
  formBlock: { gap: 10 },
  notesLabelRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  formLabel: { fontSize: 15, fontWeight: "600", color: "#111827" },
  inputLabel: { fontSize: 12, fontWeight: "600", color: "#374151" },
  input: {
    borderWidth: 1,
    borderColor: "#D1D5DB",
    borderRadius: 8,
    padding: 10,
    fontSize: 14,
  },
  switchRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
  switchLabel: { flex: 1, fontSize: 13, color: "#374151" },
  result: { fontSize: 14, fontWeight: "600", color: "#009739" },
  primaryBtn: { backgroundColor: "#2563EB", borderRadius: 10, paddingVertical: 12, alignItems: "center", marginTop: 4 },
  primaryBtnText: { color: "#fff", fontWeight: "700", fontSize: 15 },
  secondaryBtn: { paddingVertical: 10, alignItems: "center" },
  secondaryBtnText: { color: "#2563EB", fontWeight: "600", fontSize: 15 },
});
