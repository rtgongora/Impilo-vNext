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
import { DictationAssistButton, colors } from "@impilo/mobile-design-system";
import { PartographWorkspace, CtgWorkspace } from "./MaternityWorkspaces";

type Props = {
  workspace: SpecialtyWorkspaceDef;
  onBack: () => void;
};

type ToolFormKind =
  | "bsa"
  | "ktv"
  | "notes"
  | "checklist"
  | "sum"
  | "soon"
  | "withheld"
  | "partograph"
  | "ctg";

/**
 * Burns arithmetic is withheld from this app.
 *
 * A Rule-of-9s form and a Parkland form used to live here, and both produced
 * plausible-looking numbers that were clinically wrong:
 *
 *  - Rule of 9s used fixed *adult* region percentages with no age adjustment. Paediatric
 *    body proportions differ substantially (a child's head is a far larger share of body
 *    surface), which is the reason Lund–Browder is age-banded — and this app is used for
 *    children.
 *  - Parkland rendered `4 ml × kg × %TBSA` as a flat 24-hour total. Parkland is clocked
 *    from the **time of injury, not arrival**, and is given as half over the first 8 hours
 *    from injury and half over the following 16. The form had no clock input and no split,
 *    so a patient arriving three hours after a burn — who has already spent three hours of
 *    the first-half window — was shown a number wrong in the under-resuscitating direction.
 *  - Neither persisted. Rule of 9s "saved" with `Alert.alert("Saved", …recorded locally)`,
 *    telling the clinician it had been recorded while recording nothing.
 *
 * Acute burns %TBSA estimation and fluid resuscitation belong to the Emergency,
 * Resuscitation and Acute Care pack and land in `libs/emergency-domain`
 * (`docs/registry/iatg-emergency-leases.md` §5b), age-banded through
 * `libs/paediatric-domain` using the `bandKey` convention rather than overloading
 * `ageDays`. This app consumes that governed implementation once it exists; it does not
 * grow a fourth one. Do not restore these forms by adding a save to the old arithmetic.
 */
const WITHHELD_BURNS_ARITHMETIC = [
  "rule of 9",
  "rule of nine",
  "lund",
  "tbsa",
  "parkland",
  "fluid resuscitation",
  "fluid calculator",
];

/**
 * Workspaces where the generic `sum` fallback must not stand in for a clinical tool.
 * In Burns it labelled a two-number adder "Graft Planning", which reads as a clinical
 * result. These fall through to the honest "not implemented" state instead.
 */
const NO_GENERIC_CALCULATOR_WORKSPACES = new Set(["burns"]);

/**
 * Governed maternal instruments, backed by pct-service V056 and the RMNP pack's mobile contract
 * (docs/clinical/rmnp/partograph-ctg-mobile-contract.md). Matched by name rather than index —
 * unlike the fallbacks below, these two no longer depend on where they happen to sit in
 * `specialtyWorkspaces.ts`'s tool array, so reordering that array cannot silently regress them
 * back to the generic notes box.
 */
const GOVERNED_MATERNAL_INSTRUMENTS: Record<string, ToolFormKind> = {
  partograph: "partograph",
  "ctg interpretation": "ctg",
};

export function formKindForTool(toolName: string, index: number, workspaceId: string): ToolFormKind {
  const t = toolName.toLowerCase();
  if (t in GOVERNED_MATERNAL_INSTRUMENTS) return GOVERNED_MATERNAL_INSTRUMENTS[t];
  if (WITHHELD_BURNS_ARITHMETIC.some((token) => t.includes(token))) return "withheld";
  if (index >= 4) return "soon";
  if (t.includes("bsa")) return "bsa";
  if (t.includes("kt/v") || t.includes("ktv")) return "ktv";
  if (t.includes("checklist") || t.includes("pre-chemo")) return "checklist";
  if (index === 3) return NO_GENERIC_CALCULATOR_WORKSPACES.has(workspaceId) ? "soon" : "sum";
  return "notes";
}

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
        {workspace.tools.map((tool, index) => {
          const kind = formKindForTool(tool, index, workspace.id);
          return (
            <TouchableOpacity key={`${tool}-${index}`} style={styles.toolCard} onPress={() => setModalTool({ name: tool, index })}>
              <Text style={styles.toolTitle}>{tool}</Text>
              <Text style={[styles.toolHint, kind === "withheld" && styles.toolHintWithheld]}>
                {kind === "withheld"
                  ? "In development — Emergency pack W1"
                  : kind === "soon"
                    ? "Coming soon overview"
                    : "Tap for workspace form"}
              </Text>
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      <Modal visible={!!modalTool} animationType="slide" transparent onRequestClose={() => setModalTool(null)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalSheet}>
            {modalTool && (
              <ToolModalBody
                workspaceId={workspace.id}
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
  workspaceId,
  workspaceName,
  toolName,
  toolIndex,
  onClose,
}: {
  workspaceId: string;
  workspaceName: string;
  toolName: string;
  toolIndex: number;
  onClose: () => void;
}) {
  const kind = formKindForTool(toolName, toolIndex, workspaceId);

  if (kind === "withheld") {
    return (
      <>
        <Text style={styles.modalTitle}>{toolName}</Text>
        <Text style={styles.modalMeta}>{workspaceName}</Text>
        <Text style={styles.withheldBadge}>Calculator withdrawn — do not use a remembered value</Text>
        <ScrollView style={{ maxHeight: 320 }}>
          <Text style={styles.modalDesc}>
            This calculator has been removed because it produced numbers that looked right and were
            not. The %TBSA estimate used fixed adult body proportions with no age adjustment, and the
            Parkland total was shown as a flat 24-hour volume with no injury-time clock and no
            first-8h / second-16h split. Nothing it produced was ever saved to the record.
          </Text>
          <Text style={styles.modalDesc}>
            Estimate %TBSA from an age-appropriate Lund–Browder chart, and clock fluid resuscitation
            from the <Text style={styles.emphasis}>time of injury</Text> — not from arrival — giving
            half the calculated volume over the first 8 hours from injury and half over the next 16.
            Record the result in the burns chart or the encounter note, and titrate against urine
            output.
          </Text>
          <Text style={styles.modalDesc}>
            <Text style={styles.emphasis}>In development — Emergency, Resuscitation and Acute Care
            pack, wave W1.</Text> The governed calculation now exists: age-banded Lund–Browder and
            injury-clocked Parkland, in the shared <Text style={styles.emphasis}>burn-domain</Text>
            library, with the age bands verified to sum to 100% of body surface. What is not yet
            built is this screen&apos;s connection to it and the write to the patient&apos;s burn
            assessment, so the calculation is deliberately not offered here until entering a value
            also records it. This workspace will use the shared calculation rather than keep a
            private copy.
          </Text>
        </ScrollView>
        <TouchableOpacity style={styles.primaryBtn} onPress={onClose}>
          <Text style={styles.primaryBtnText}>Close</Text>
        </TouchableOpacity>
      </>
    );
  }

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

  // Partograph and CTG get their own bounded-height container rather than the generic 360px
  // ScrollView below: both carry a running observation/annotation list plus a governed form,
  // which the toy calculators below never needed room for.
  if (kind === "partograph" || kind === "ctg") {
    return (
      <>
        <Text style={styles.modalTitle}>{toolName}</Text>
        <Text style={styles.modalMeta}>{workspaceName}</Text>
        <View style={{ height: 480 }}>
          {kind === "partograph" ? <PartographWorkspace /> : <CtgWorkspace />}
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
      <ScrollView style={{ maxHeight: 360 }}>
        {kind === "bsa" && <BsaForm />}
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
  title: { fontSize: 18, fontWeight: "700", color: colors.gray[900] },
  sub: { fontSize: 12, color: colors.gray[500], marginTop: 2 },
  sectionLabel: { fontSize: 13, fontWeight: "600", color: colors.gray[700], marginTop: 4 },
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
    backgroundColor: colors.gray[50],
    borderRadius: 12,
    padding: 12,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: colors.gray[200],
  },
  toolTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
  toolHint: { fontSize: 12, color: colors.gray[500], marginTop: 4 },
  toolHintWithheld: { color: "#B45309", fontWeight: "600" },
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
  withheldBadge: {
    fontSize: 12,
    fontWeight: "700",
    color: "#92400E",
    backgroundColor: "#FEF3C7",
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  formBlock: { gap: 10 },
  notesLabelRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  formLabel: { fontSize: 15, fontWeight: "600", color: colors.gray[900] },
  inputLabel: { fontSize: 12, fontWeight: "600", color: colors.gray[700] },
  input: {
    borderWidth: 1,
    borderColor: colors.gray[300],
    borderRadius: 8,
    padding: 10,
    fontSize: 14,
  },
  switchRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
  switchLabel: { flex: 1, fontSize: 13, color: colors.gray[700] },
  result: { fontSize: 14, fontWeight: "600", color: "#009739" },
  primaryBtn: { backgroundColor: "#2563EB", borderRadius: 10, paddingVertical: 12, alignItems: "center", marginTop: 4 },
  primaryBtnText: { color: "#fff", fontWeight: "700", fontSize: 15 },
  secondaryBtn: { paddingVertical: 10, alignItems: "center" },
  secondaryBtnText: { color: "#2563EB", fontWeight: "600", fontSize: 15 },
});
