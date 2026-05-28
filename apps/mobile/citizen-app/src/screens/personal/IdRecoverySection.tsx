import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { TextField, LoadingSpinner } from "@impilo/mobile-design-system";
import {
  APP_GREEN, APP_GREEN_DARK, APP_GREEN_LIGHT, APP_GREEN_XLIGHT,
  APP_GOLD, APP_GOLD_LIGHT, APP_RED, APP_RED_LIGHT,
  APP_SURFACE, APP_BG, APP_TEXT, APP_TEXT_2, APP_TEXT_3, APP_BORDER,
} from "../../lib/colors";

type Step = "method" | "details" | "verify" | "complete";
type Method = "phone" | "nid" | "facility";

const METHOD_CFG: Record<Method, {
  icon: React.ComponentProps<typeof Ionicons>["name"];
  title: string;
  desc: string;
  color: string;
  bg: string;
}> = {
  phone:    { icon: "phone-portrait-outline", title: "Registered Phone",   desc: "Verify via SMS OTP sent to your registered number",      color: APP_GREEN,      bg: APP_GREEN_LIGHT  },
  nid:      { icon: "card-outline",           title: "National ID Number",  desc: "Provide your National ID to match against records",      color: APP_GREEN_DARK, bg: APP_GREEN_XLIGHT },
  facility: { icon: "business-outline",       title: "Visit a Facility",    desc: "Present yourself at any registered health facility",     color: APP_GOLD,       bg: APP_GOLD_LIGHT   },
};

const STEP_ORDER: Step[] = ["method", "details", "verify", "complete"];

function StepIndicator({ current }: { current: Step }) {
  const LABELS: Record<Step, string> = { method: "Method", details: "Details", verify: "Verify", complete: "Done" };
  const idx = STEP_ORDER.indexOf(current);
  return (
    <View style={si.row}>
      {STEP_ORDER.map((step, i) => {
        const done = i < idx; const active = i === idx;
        return (
          <React.Fragment key={step}>
            <View style={si.item}>
              <View style={[si.circle, done && si.done, active && si.active]}>
                {done ? <Ionicons name="checkmark" size={12} color="#FFFFFF" /> : <Text style={[si.num, active && si.numActive]}>{i + 1}</Text>}
              </View>
              <Text style={[si.lbl, active && si.lblActive]}>{LABELS[step]}</Text>
            </View>
            {i < STEP_ORDER.length - 1 ? <View style={[si.line, done && si.lineDone]} /> : null}
          </React.Fragment>
        );
      })}
    </View>
  );
}
const si = StyleSheet.create({
  row:      { flexDirection: "row", alignItems: "flex-start", paddingHorizontal: 20, paddingVertical: 14 },
  item:     { alignItems: "center", gap: 4 },
  circle:   { width: 28, height: 28, borderRadius: 14, backgroundColor: "#EEF2F2", alignItems: "center", justifyContent: "center" },
  done:     { backgroundColor: APP_GREEN },
  active:   { backgroundColor: APP_GREEN, shadowColor: APP_GREEN, shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.4, shadowRadius: 6, elevation: 4 },
  num:      { fontSize: 12, fontWeight: "700", color: APP_TEXT_3 },
  numActive:{ color: "#FFFFFF" },
  lbl:      { fontSize: 10, fontWeight: "600", color: APP_TEXT_3 },
  lblActive:{ color: APP_GREEN },
  line:     { flex: 1, height: 2, backgroundColor: "#EEF2F2", marginTop: 13, marginHorizontal: 4 },
  lineDone: { backgroundColor: APP_GREEN },
});

export function IdRecoverySection() {
  const [step, setStep]       = useState<Step>("method");
  const [method, setMethod]   = useState<Method | null>(null);
  const [firstName, setFirst] = useState("");
  const [lastName, setLast]   = useState("");
  const [dob, setDob]         = useState("");
  const [identifier, setId]   = useState("");
  const [otp, setOtp]         = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState<string | null>(null);

  const goBack = () => {
    setError(null);
    const idx = STEP_ORDER.indexOf(step);
    if (idx > 0) setStep(STEP_ORDER[idx - 1]!);
  };

  const handleContinue = async () => {
    if (step === "method" && method) { setStep("details"); return; }
    if (step === "details") {
      if (!firstName.trim() || !lastName.trim() || !dob.trim()) { setError("Please fill in all required fields."); return; }
      setError(null); setStep("verify"); return;
    }
    if (step === "verify") {
      setLoading(true);
      await new Promise((r) => setTimeout(r, 1500));
      setLoading(false);
      setStep("complete");
    }
  };

  const reset = () => { setStep("method"); setMethod(null); setFirst(""); setLast(""); setDob(""); setId(""); setOtp(""); setError(null); };

  const canContinue =
    (step === "method"  && !!method) ||
    (step === "details" && firstName.trim() && lastName.trim() && dob.trim()) ||
    (step === "verify"  && method !== "phone") ||
    (step === "verify"  && method === "phone" && otp.trim().length >= 4);

  return (
    <View style={s.root}>
      <View style={s.stepBarWrap}><StepIndicator current={step} /></View>

      <ScrollView style={s.scroll} contentContainerStyle={s.content} showsVerticalScrollIndicator={false}>

        {step === "method" ? (
          <>
            <Text style={s.stepTitle}>Recover Your Health ID</Text>
            <Text style={s.stepDesc}>Choose how you'd like to verify your identity.</Text>
            {(Object.entries(METHOD_CFG) as [Method, typeof METHOD_CFG[Method]][]).map(([key, cfg]) => (
              <Pressable key={key} onPress={() => setMethod(key)} style={[s.methodCard, method === key && s.methodCardActive]}>
                <View style={[s.methodIcon, { backgroundColor: cfg.bg }]}>
                  <Ionicons name={cfg.icon} size={22} color={cfg.color} />
                </View>
                <View style={s.methodText}>
                  <Text style={s.methodTitle}>{cfg.title}</Text>
                  <Text style={s.methodDesc}>{cfg.desc}</Text>
                </View>
                <View style={[s.radio, method === key && s.radioActive]}>
                  {method === key ? <View style={s.radioDot} /> : null}
                </View>
              </Pressable>
            ))}
          </>
        ) : null}

        {step === "details" ? (
          <>
            <Text style={s.stepTitle}>Personal Details</Text>
            <Text style={s.stepDesc}>Enter your details exactly as they appear on official identification.</Text>
            <View style={s.formCard}>
              <TextField label="First Name *" value={firstName} onChange={setFirst} placeholder="As on official ID" />
              <TextField label="Surname *" value={lastName} onChange={setLast} placeholder="As on official ID" />
              <TextField label="Date of Birth *" value={dob} onChange={setDob} placeholder="YYYY-MM-DD" />
              {method === "nid" ? <TextField label="National ID Number *" value={identifier} onChange={setId} placeholder="63-123456A78" /> : null}
              {method === "phone" ? <TextField label="Registered Phone *" value={identifier} onChange={setId} placeholder="+263 7X XXX XXXX" /> : null}
              {error ? (
                <View style={s.errorBanner}><Ionicons name="alert-circle-outline" size={14} color={APP_RED} /><Text style={s.errorText}>{error}</Text></View>
              ) : null}
            </View>
          </>
        ) : null}

        {step === "verify" ? (
          <>
            <Text style={s.stepTitle}>
              {method === "phone" ? "Enter OTP" : method === "facility" ? "Visit a Facility" : "Confirm Identity"}
            </Text>
            {method === "phone" ? (
              <>
                <Text style={s.stepDesc}>An OTP has been sent to your registered number. Enter it below.</Text>
                <View style={s.formCard}>
                  <TextField label="6-digit OTP" value={otp} onChange={setOtp} placeholder="e.g. 482915" />
                </View>
              </>
            ) : method === "facility" ? (
              <View style={s.stepsCard}>
                {[
                  "Visit any registered Impilo health facility with a valid photo ID.",
                  "Ask reception to initiate a Health ID recovery on your behalf.",
                  "Your Health ID will be restored and you'll receive an SMS confirmation.",
                ].map((text, i) => (
                  <View key={i} style={s.instrRow}>
                    <View style={s.instrNum}><Text style={s.instrNumText}>{i + 1}</Text></View>
                    <Text style={s.instrText}>{text}</Text>
                  </View>
                ))}
              </View>
            ) : (
              <View style={s.confirmCard}>
                <Ionicons name="person-circle-outline" size={44} color={APP_GREEN} />
                <Text style={s.confirmName}>{firstName} {lastName}</Text>
                <Text style={s.confirmDob}>{dob}</Text>
                <Text style={s.confirmNote}>Please confirm this is correct before proceeding.</Text>
              </View>
            )}
          </>
        ) : null}

        {step === "complete" ? (
          <View style={s.completeCard}>
            <View style={s.completeIconWrap}><Ionicons name="checkmark-circle" size={52} color={APP_GREEN} /></View>
            <Text style={s.completeTitle}>Recovery Initiated</Text>
            <Text style={s.completeDesc}>
              {method === "facility"
                ? "Visit your nearest facility with valid ID to complete the recovery process."
                : "Your identity has been verified. Health ID access should be restored shortly. You'll receive an SMS confirmation."}
            </Text>
            <View style={s.completeNote}>
              <Ionicons name="information-circle-outline" size={14} color={APP_GOLD} />
              <Text style={s.completeNoteText}>If you don't regain access within 24 hours, contact the Impilo helpdesk on 0800 IMPILO.</Text>
            </View>
            <Pressable onPress={reset} style={s.restartBtn}>
              <Text style={s.restartBtnText}>Start New Recovery</Text>
            </Pressable>
          </View>
        ) : null}
      </ScrollView>

      {step !== "complete" ? (
        <View style={s.footer}>
          {step !== "method" ? (
            <Pressable onPress={goBack} style={s.backBtn}>
              <Ionicons name="arrow-back" size={16} color={APP_TEXT_2} />
              <Text style={s.backBtnText}>Back</Text>
            </Pressable>
          ) : null}
          <Pressable
            onPress={() => void handleContinue()}
            disabled={loading || !canContinue}
            style={[s.continueBtn, (!canContinue || loading) && s.continueBtnDisabled, step !== "method" && { flex: 1 }]}
          >
            {loading ? <LoadingSpinner size="sm" /> : (
              <>
                <Text style={s.continueBtnText}>
                  {step === "verify" && method === "facility" ? "I've visited a facility" : "Continue"}
                </Text>
                <Ionicons name="arrow-forward" size={15} color="#FFFFFF" />
              </>
            )}
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: APP_BG },
  stepBarWrap: { backgroundColor: APP_SURFACE, borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: APP_BORDER },
  scroll: { flex: 1 },
  content: { padding: 16, gap: 14, paddingBottom: 24 },

  stepTitle: { fontSize: 20, fontWeight: "800", color: APP_TEXT },
  stepDesc: { fontSize: 13, color: APP_TEXT_2, lineHeight: 19, marginTop: -6 },

  methodCard: {
    flexDirection: "row", alignItems: "center", gap: 14,
    backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16,
    borderWidth: 2, borderColor: "transparent",
    shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.05, shadowRadius: 6, elevation: 2,
  },
  methodCardActive: { borderColor: APP_GREEN },
  methodIcon: { width: 48, height: 48, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  methodText: { flex: 1 },
  methodTitle: { fontSize: 15, fontWeight: "700", color: APP_TEXT },
  methodDesc: { fontSize: 12, color: APP_TEXT_2, marginTop: 3, lineHeight: 17 },
  radio: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: APP_BORDER, alignItems: "center", justifyContent: "center" },
  radioActive: { borderColor: APP_GREEN },
  radioDot: { width: 10, height: 10, borderRadius: 5, backgroundColor: APP_GREEN },

  formCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 16, gap: 12, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  errorBanner: { flexDirection: "row", alignItems: "center", gap: 8, backgroundColor: APP_RED_LIGHT, borderRadius: 10, padding: 10 },
  errorText: { fontSize: 13, color: APP_RED, flex: 1 },

  stepsCard: { backgroundColor: APP_SURFACE, borderRadius: 18, padding: 18, gap: 16, shadowColor: "#000", shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.06, shadowRadius: 8, elevation: 3 },
  instrRow: { flexDirection: "row", alignItems: "flex-start", gap: 14 },
  instrNum: { width: 28, height: 28, borderRadius: 14, backgroundColor: APP_GREEN, alignItems: "center", justifyContent: "center" },
  instrNumText: { fontSize: 13, fontWeight: "800", color: "#FFFFFF" },
  instrText: { flex: 1, fontSize: 14, color: APP_TEXT_2, lineHeight: 20 },

  confirmCard: { alignItems: "center", backgroundColor: APP_GREEN_XLIGHT, borderRadius: 18, padding: 24, gap: 8 },
  confirmName: { fontSize: 20, fontWeight: "800", color: APP_TEXT },
  confirmDob: { fontSize: 14, color: APP_TEXT_2 },
  confirmNote: { fontSize: 13, color: APP_TEXT_2, textAlign: "center", marginTop: 4 },

  completeCard: { alignItems: "center", paddingVertical: 20, gap: 14 },
  completeIconWrap: { width: 96, height: 96, borderRadius: 48, backgroundColor: APP_GREEN_XLIGHT, alignItems: "center", justifyContent: "center" },
  completeTitle: { fontSize: 22, fontWeight: "800", color: APP_TEXT },
  completeDesc: { fontSize: 14, color: APP_TEXT_2, textAlign: "center", lineHeight: 21, paddingHorizontal: 12 },
  completeNote: { flexDirection: "row", alignItems: "flex-start", gap: 8, backgroundColor: APP_GOLD_LIGHT, borderRadius: 12, padding: 12, alignSelf: "stretch" },
  completeNoteText: { fontSize: 12, color: APP_GOLD, flex: 1, lineHeight: 17 },
  restartBtn: { backgroundColor: APP_GREEN_LIGHT, paddingHorizontal: 24, paddingVertical: 12, borderRadius: 14 },
  restartBtnText: { fontSize: 14, fontWeight: "700", color: APP_GREEN_DARK },

  footer: { flexDirection: "row", gap: 10, padding: 16, paddingBottom: 24, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: APP_BORDER, backgroundColor: APP_SURFACE },
  backBtn: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 16, paddingVertical: 14, borderRadius: 14, backgroundColor: "#EEF2F2" },
  backBtnText: { fontSize: 14, fontWeight: "600", color: APP_TEXT_2 },
  continueBtn: { flex: 1, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, backgroundColor: APP_GREEN, paddingVertical: 14, borderRadius: 14 },
  continueBtnDisabled: { opacity: 0.5 },
  continueBtnText: { fontSize: 15, fontWeight: "700", color: "#FFFFFF" },
});
