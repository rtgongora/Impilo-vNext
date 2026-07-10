const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const FA = require("react-icons/fa");

// ---------- palette (Teal Trust + warm gold nod to national identity) ----------
const C = {
  DARK:  "0A3B37",  // deep pine-teal  (title/close bg)
  DARK2: "0F4A44",
  TEAL:  "0D8577",  // primary
  TEALD: "0B6B60",
  SEA:   "14B8A6",
  MINT:  "5EEAD4",
  AMBER: "E1A21A",  // warm accent
  CORAL: "DB5A34",  // sharp accent
  INK:   "12312E",  // dark text
  SLATE: "5B6E6B",  // muted text
  BG:    "F5F9F8",  // light background
  CARD:  "FFFFFF",
  TINT:  "EAF4F2",  // card tint
  LINE:  "D8E6E3",
  WHITE: "FFFFFF",
};

// ---------- icon rasterization (cached) ----------
const _iconCache = {};
async function icon(name, colorHex, size = 256) {
  const key = name + colorHex + size;
  if (_iconCache[key]) return _iconCache[key];
  const Comp = FA[name];
  if (!Comp) throw new Error("missing icon " + name);
  const svg = ReactDOMServer.renderToStaticMarkup(
    React.createElement(Comp, { color: "#" + colorHex, size: String(size) })
  );
  const png = await sharp(Buffer.from(svg)).png().toBuffer();
  const data = "image/png;base64," + png.toString("base64");
  _iconCache[key] = data;
  return data;
}

const sh = () => ({ type: "outer", color: "0A3B37", blur: 9, offset: 3, angle: 90, opacity: 0.13 });

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.author = "Impilo vNext Programme";
pres.title = "Introduction to Impilo vNext";
const W = 13.33, H = 7.5, M = 0.7;

// ---------- shared scaffolding ----------
function footer(slide, n) {
  slide.addText([
    { text: "Impilo vNext", options: { color: C.TEAL, bold: true } },
    { text: "   Health Operating System", options: { color: C.SLATE } },
  ], { x: M, y: H - 0.5, w: 6, h: 0.3, fontSize: 9, align: "left", margin: 0, fontFace: "Calibri" });
  slide.addText(String(n).padStart(2, "0"), {
    x: W - 1.4, y: H - 0.5, w: 0.7, h: 0.3, fontSize: 9, color: C.SLATE, align: "right", margin: 0, fontFace: "Calibri",
  });
}
function kicker(slide, text, color = C.TEAL) {
  slide.addText(text.toUpperCase(), {
    x: M, y: 0.5, w: W - 2 * M, h: 0.32, fontSize: 12.5, bold: true, color, charSpacing: 3, margin: 0, fontFace: "Calibri",
  });
}
function title(slide, text, opts = {}) {
  slide.addText(text, {
    x: M, y: 0.82, w: opts.w || W - 2 * M, h: opts.h || 0.95, fontSize: opts.fontSize || 33, bold: true,
    color: opts.color || C.INK, margin: 0, fontFace: "Cambria", valign: "top", lineSpacing: opts.lineSpacing || 36,
  });
}
async function iconCircle(slide, iconName, cx, cy, d, circleColor, iconColor) {
  slide.addShape(pres.shapes.OVAL, { x: cx, y: cy, w: d, h: d, fill: { color: circleColor }, line: { type: "none" } });
  const p = d * 0.28;
  slide.addImage({ data: await icon(iconName, iconColor), x: cx + p, y: cy + p, w: d - 2 * p, h: d - 2 * p });
}
function connect(slide, x1, y1, x2, y2, color, width) {
  const x = Math.min(x1, x2), y = Math.min(y1, y2), w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
  const flipV = (x1 < x2 && y1 > y2) || (x1 > x2 && y1 < y2);
  slide.addShape(pres.shapes.LINE, { x, y, w, h, line: { color, width }, flipV });
}

// ============================================================ SLIDE 1 — TITLE
(async () => {
let s = pres.addSlide();
s.background = { color: C.DARK };
// motif: soft concentric rings on right
s.addShape(pres.shapes.OVAL, { x: 9.6, y: -2.2, w: 7.6, h: 7.6, fill: { color: C.DARK2 }, line: { type: "none" } });
s.addShape(pres.shapes.OVAL, { x: 10.7, y: -1.1, w: 5.4, h: 5.4, fill: { color: C.TEALD }, line: { type: "none" } });
s.addShape(pres.shapes.OVAL, { x: 11.7, y: 4.6, w: 4.2, h: 4.2, fill: { color: C.TEALD }, line: { type: "none" } });
await iconCircle(s, "FaHeartbeat", 10.85, 2.35, 1.7, C.AMBER, C.DARK);
s.addText("MONTHLY PROGRAMME MEETING", { x: M, y: 1.75, w: 9, h: 0.4, fontSize: 14, bold: true, color: C.MINT, charSpacing: 3, margin: 0, fontFace: "Calibri" });
s.addText("Impilo vNext", { x: M, y: 2.2, w: 9, h: 1.4, fontSize: 66, bold: true, color: C.WHITE, margin: 0, fontFace: "Cambria" });
s.addText("A National Health Operating System for Zimbabwe", { x: M, y: 3.75, w: 8.6, h: 0.7, fontSize: 23, color: C.MINT, margin: 0, fontFace: "Calibri" });
s.addShape(pres.shapes.LINE, { x: M, y: 4.75, w: 3.4, h: 0, line: { color: C.AMBER, width: 2.5 } });
s.addText([
  { text: "One system  ·  many roles  ·  one person at the centre", options: { color: "CFE9E4" } },
], { x: M, y: 5.0, w: 9, h: 0.4, fontSize: 15, italic: true, margin: 0, fontFace: "Calibri" });
s.addText("[Date]   ·   [Presenter name & role]", { x: M, y: 6.55, w: 9, h: 0.4, fontSize: 13, color: "9FC4BE", margin: 0, fontFace: "Calibri" });
s.addNotes("Good morning everyone. Thank you for being here — facility teams, district and provincial colleagues, HQ leadership, and our partners. Over the next 20 minutes I want to give you a shared, plain-language picture of what Impilo vNext is, why we're building it this way, and most importantly what it means for you, wherever you sit in the system. This is an introduction, not a technical deep-dive. Questions welcome at the end.");

// ============================================================ SLIDE 2 — THE PROBLEM
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Why we are here", C.CORAL);
title(s, "Health information today is fragmented");
const pains = [
  ["FaPuzzlePiece", "Scattered systems", "A patient's story is split across many systems that don't talk to each other."],
  ["FaCopy", "Duplicate records", "The same person is registered many times, many ways — no single truth."],
  ["FaKeyboard", "Re-entered data", "Staff type the same details over and over; numbers are reconciled by hand."],
  ["FaClock", "Late, low-trust reports", "Managers wait weeks for figures — and can't fully trust them when they arrive."],
];
let px = M, pw = (W - 2 * M - 3 * 0.35) / 4, py = 2.35, ph = 3.0;
for (const [ic, head, body] of pains) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: px, y: py, w: pw, h: ph, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, px + 0.35, py + 0.4, 0.95, "FCEBE4", C.CORAL);
  s.addText(head, { x: px + 0.3, y: py + 1.55, w: pw - 0.6, h: 0.5, fontSize: 16, bold: true, color: C.INK, margin: 0, fontFace: "Cambria" });
  s.addText(body, { x: px + 0.3, y: py + 2.05, w: pw - 0.55, h: 0.85, fontSize: 12, color: C.SLATE, margin: 0, fontFace: "Calibri", lineSpacing: 15 });
  px += pw + 0.35;
}
s.addText([
  { text: "We are not short of systems — we are short of ", options: { color: C.INK } },
  { text: "coherence.", options: { color: C.CORAL, bold: true } },
], { x: M, y: 5.7, w: W - 2 * M, h: 0.6, fontSize: 20, italic: true, align: "center", fontFace: "Cambria" });
footer(s, 2);
s.addNotes("Let's start with something everyone recognises. Today a single patient can exist in a dozen registers that don't agree. Frontline staff re-enter the same data. Managers get reports late and can't fully trust them. Partners plug in their own tools creating yet another island. We're not short of systems — we're short of coherence. That's the problem Impilo vNext exists to solve.");

// ============================================================ SLIDE 3 — WHAT IS IMPILO
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "The one-line answer");
title(s, "Impilo is a Health Operating System");
// left: statement card
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: M, y: 2.2, w: 6.5, h: 4.05, rectRadius: 0.1, fill: { color: C.DARK }, line: { type: "none" }, shadow: sh() });
s.addText([
  { text: "A trusted, governed, interoperable, person-centred national digital environment where health ", options: { color: "D7ECE7" } },
  { text: "identities, records, workflows, services and communities", options: { color: C.MINT, bold: true } },
  { text: " operate as ", options: { color: "D7ECE7" } },
  { text: "one coherent whole.", options: { color: C.WHITE, bold: true } },
], { x: M + 0.5, y: 2.65, w: 5.5, h: 2.2, fontSize: 20, fontFace: "Cambria", lineSpacing: 28, valign: "top" });
s.addText("Not “another app” — a shared foundation that all health apps run on.", { x: M + 0.5, y: 5.25, w: 5.5, h: 0.7, fontSize: 13.5, italic: true, color: C.AMBER, fontFace: "Calibri", margin: 0 });
// right: phone-OS analogy
s.addText("THINK OF YOUR PHONE", { x: 7.7, y: 2.25, w: 5, h: 0.35, fontSize: 12, bold: true, color: C.TEAL, charSpacing: 2, margin: 0, fontFace: "Calibri" });
const analogy = [
  ["FaMobileAlt", "One operating system", "Android / iOS provides identity, rules, security."],
  ["FaThLarge", "Many apps on top", "Different apps — but one login, one trust model."],
  ["FaLayerGroup", "Impilo = the health OS", "Facility, programme & partner tools become governed apps, not islands."],
];
let ay = 2.75;
for (const [ic, head, body] of analogy) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 7.7, y: ay, w: 4.93, h: 1.02, rectRadius: 0.08, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, 7.9, ay + 0.24, 0.55, C.TINT, C.TEAL);
  s.addText(head, { x: 8.6, y: ay + 0.14, w: 3.9, h: 0.35, fontSize: 14.5, bold: true, color: C.INK, margin: 0, fontFace: "Cambria" });
  s.addText(body, { x: 8.6, y: ay + 0.5, w: 3.95, h: 0.45, fontSize: 11, color: C.SLATE, margin: 0, fontFace: "Calibri", lineSpacing: 13 });
  ay += 1.16;
}
footer(s, 3);
s.addNotes("Here's the single most important idea today. Impilo is not another application competing with your tools. Think of your phone: it has an operating system, and many apps run on top of it. The apps differ, but they share one identity, one set of rules, one secure foundation. Impilo vNext is the operating system for health in Zimbabwe. Facility systems, programme tools, partner applications become apps on a common, governed foundation instead of disconnected islands.");

// ============================================================ SLIDE 4 — NORTH STAR (dark)
s = pres.addSlide();
s.background = { color: C.DARK };
s.addShape(pres.shapes.OVAL, { x: -1.8, y: 4.4, w: 5, h: 5, fill: { color: C.DARK2 }, line: { type: "none" } });
s.addShape(pres.shapes.OVAL, { x: 11.2, y: -1.9, w: 4.4, h: 4.4, fill: { color: C.DARK2 }, line: { type: "none" } });
s.addText("OUR NORTH STAR", { x: 0, y: 1.35, w: W, h: 0.4, fontSize: 14, bold: true, color: C.AMBER, align: "center", charSpacing: 4, fontFace: "Calibri" });
s.addText([
  { text: "One Health Operating System.\n", options: { color: C.WHITE } },
  { text: "One experience. One person anchor.\n", options: { color: C.WHITE } },
  { text: "Many roles, many contexts — ", options: { color: C.MINT } },
  { text: "one governed runtime.", options: { color: C.AMBER } },
], { x: 1.2, y: 2.15, w: W - 2.4, h: 3.2, fontSize: 34, bold: true, align: "center", fontFace: "Cambria", lineSpacing: 46 });
s.addText("Everything else in this session is simply unpacking this sentence.", { x: 0, y: 5.9, w: W, h: 0.4, fontSize: 14, italic: true, color: "9FC4BE", align: "center", fontFace: "Calibri" });
s.addNotes("This is our guiding sentence. Read slowly it says: there is one system and one look-and-feel; every record ties back to one real person; but that person can be a patient in one moment and a provider in another; facilities, districts and programmes are all contexts; and everything runs under one governed, auditable engine. Keep this in mind — everything else today unpacks it.");

// ============================================================ SLIDE 5 — OS vs APP
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Why the word “operating system” matters");
title(s, "A foundation that grows more coherent — not more fragmented");
const rows = [
  ["FaCube", "Scope", "Solves one problem", "A foundation for many capabilities"],
  ["FaFingerprint", "Identity & data", "Its own login and patient list", "One identity, one shared trust model"],
  ["FaPlus", "Adding capability", "New tool = a new island", "New tool = a governed extension"],
  ["FaShieldAlt", "Trust", "Assumed, bolted on later", "Enforced on every single request"],
];
const catX = M, catW = 1.5;
const colL = catX + catW + 0.2, colLW = 4.96;
const colR = colL + colLW + 0.3, colRW = 4.96;
// column headers
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: colL, y: 2.25, w: colLW, h: 0.6, rectRadius: 0.08, fill: { color: "E4E1DD" }, line: { type: "none" } });
s.addText("A single application", { x: colL, y: 2.25, w: colLW, h: 0.6, fontSize: 15, bold: true, color: "6B6560", align: "center", valign: "middle", fontFace: "Cambria" });
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: colR, y: 2.25, w: colRW, h: 0.6, rectRadius: 0.08, fill: { color: C.TEAL }, line: { type: "none" } });
s.addText("A Health OS (Impilo)", { x: colR, y: 2.25, w: colRW, h: 0.6, fontSize: 15, bold: true, color: C.WHITE, align: "center", valign: "middle", fontFace: "Cambria" });
let ry = 3.0;
for (const [ic, label, left, right] of rows) {
  // category pill
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: catX, y: ry, w: catW, h: 0.82, rectRadius: 0.07, fill: { color: C.DARK }, line: { type: "none" } });
  s.addImage({ data: await icon(ic, C.MINT), x: catX + 0.2, y: ry + 0.25, w: 0.33, h: 0.33 });
  s.addText(label, { x: catX + 0.6, y: ry, w: catW - 0.65, h: 0.82, fontSize: 10.5, bold: true, color: C.WHITE, align: "left", valign: "middle", fontFace: "Calibri", margin: 0, lineSpacing: 12 });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: colL, y: ry, w: colLW, h: 0.82, rectRadius: 0.07, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 } });
  s.addText(left, { x: colL + 0.25, y: ry, w: colLW - 0.45, h: 0.82, fontSize: 13, color: "8A857F", align: "left", valign: "middle", fontFace: "Calibri", margin: 0 });
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: colR, y: ry, w: colRW, h: 0.82, rectRadius: 0.07, fill: { color: C.TINT }, line: { color: C.SEA, width: 1 } });
  s.addText(right, { x: colR + 0.25, y: ry, w: colRW - 0.45, h: 0.82, fontSize: 13, bold: true, color: C.INK, align: "left", valign: "middle", fontFace: "Calibri", margin: 0 });
  ry += 0.97;
}
footer(s, 5);
s.addNotes("Why insist on the word operating system? Because it changes how we grow. When someone needs a new capability we don't build a new island with its own login and patient list. We add a governed module that already knows who the patient is, already enforces consent, already writes to the shared record and produces an audit trail. That's the difference between a system that gets more coherent as it grows versus one that fragments. Impilo gets stronger as we add to it.");

// ============================================================ SLIDE 6 — ONE PERSON, ONE HEALTH ID
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Person-centred identity");
title(s, "One person, one Health ID");
s.addText("Every person has a single, lifelong anchor. Everything attaches to it — but there is always one person.", { x: M, y: 1.72, w: W - 2 * M, h: 0.5, fontSize: 14.5, color: C.SLATE, fontFace: "Calibri", margin: 0 });
const cx = 6.65, cy = 4.55;
const sats = [
  ["FaNotesMedical", "Records &\nresults", 2.35, 3.15],
  ["FaUserMd", "Roles &\nproviders", 6.65, 2.75],
  ["FaHospital", "Facilities &\ncontexts", 10.95, 3.15],
  ["FaClipboardList", "Programme\nenrolments", 9.85, 5.95],
  ["FaLock", "Consent &\nlegal basis", 3.45, 5.95],
];
for (const [, , sx, sy] of sats) connect(s, cx, cy, sx, sy, C.SEA, 2);
// satellites
for (const [ic, label, sx, sy] of sats) {
  const d = 1.35;
  s.addShape(pres.shapes.OVAL, { x: sx - d / 2, y: sy - d / 2, w: d, h: d, fill: { color: C.CARD }, line: { color: C.SEA, width: 1.5 }, shadow: sh() });
  s.addImage({ data: await icon(ic, C.TEAL), x: sx - 0.24, y: sy - 0.42, w: 0.48, h: 0.48 });
  s.addText(label, { x: sx - d / 2, y: sy + 0.08, w: d, h: 0.55, fontSize: 10, bold: true, color: C.INK, align: "center", valign: "top", fontFace: "Calibri", margin: 0, lineSpacing: 11 });
}
// center hub
const hd = 1.95;
s.addShape(pres.shapes.OVAL, { x: cx - hd / 2, y: cy - hd / 2, w: hd, h: hd, fill: { color: C.DARK }, line: { color: C.AMBER, width: 3 }, shadow: sh() });
s.addImage({ data: await icon("FaIdCard", C.AMBER), x: cx - 0.35, y: cy - 0.55, w: 0.7, h: 0.7 });
s.addText("HEALTH ID", { x: cx - hd / 2, y: cy + 0.18, w: hd, h: 0.3, fontSize: 13, bold: true, color: C.WHITE, align: "center", fontFace: "Cambria", margin: 0 });
s.addText("one person", { x: cx - hd / 2, y: cy + 0.48, w: hd, h: 0.3, fontSize: 10, italic: true, color: C.MINT, align: "center", fontFace: "Calibri", margin: 0 });
footer(s, 6);
s.addNotes("At the heart of the OS is the person. Each individual gets one Health ID — a single permanent anchor. Everything else — visits, prescriptions, labs, programme enrolments — attaches to that one anchor. This ends the same patient being registered five times. It's also the foundation of a true longitudinal record: for the first time a person's health story can follow them from a rural clinic to a provincial hospital to a pharmacy, as one continuous thread.");

// ============================================================ SLIDE 7 — MANY ROLES / CONTEXTS
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Identity & role activation");
title(s, "Sign in as a person → practise as a provider");
const cols = [
  ["FaUser", "WHO", "you are", ["Health ID", "Provider ID", "Staff ID"], C.TEAL],
  ["FaMapMarkerAlt", "WHERE", "you are working", ["Facility", "Department / Ward", "Programme / Workspace"], C.SEA],
  ["FaTasks", "WHAT", "you may do", ["Depends on all of it", "Checked every request", "Not just once at login"], C.AMBER],
];
let ccx = M, ccw = (W - 2 * M - 2 * 0.5) / 3;
for (const [ic, big, sub, items, col] of cols) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: ccx, y: 2.35, w: ccw, h: 3.55, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, ccx + ccw / 2 - 0.55, 2.7, 1.1, col, C.WHITE);
  s.addText(big, { x: ccx, y: 3.95, w: ccw, h: 0.45, fontSize: 26, bold: true, color: C.INK, align: "center", fontFace: "Cambria", margin: 0 });
  s.addText(sub, { x: ccx, y: 4.42, w: ccw, h: 0.35, fontSize: 13, italic: true, color: C.SLATE, align: "center", fontFace: "Calibri", margin: 0 });
  s.addText(items.map((t, i) => ({ text: t, options: { bullet: false, breakLine: true, color: C.TEALD, align: "center", paraSpaceAfter: 4 } })),
    { x: ccx + 0.3, y: 4.9, w: ccw - 0.6, h: 0.9, fontSize: 11.5, fontFace: "Calibri", margin: 0 });
  ccx += ccw + 0.5;
}
s.addText([
  { text: "To prescribe or open a record, the system weighs identity + licence + organisation + facility + purpose + consent — ", options: { color: C.INK } },
  { text: "every time.", options: { color: C.TEAL, bold: true } },
], { x: M, y: 6.15, w: W - 2 * M, h: 0.5, fontSize: 14, italic: true, align: "center", fontFace: "Calibri" });
footer(s, 7);
s.addNotes("A real person is more than one thing. A nurse is a citizen with her own health record and a provider when she's on shift. Impilo separates who you are as a person from the professional capacity you're acting in. To prescribe or open a patient's record the system checks the whole picture: identity, licence, organisation, facility, purpose, consent, workflow state — every single time, not once at login. That's what makes it both flexible and safe.");

// ============================================================ SLIDE 8 — 7 PLANES (infographic)
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "The architecture, simply");
title(s, "Seven coherent planes — each with a clear job");
const planes = [
  ["FaShieldAlt", "Trust", "Identity, consent — “are you allowed?”", C.TEAL],
  ["FaAddressBook", "Registry", "Canonical lists of people, facilities, products", C.TEALD],
  ["FaStethoscope", "Clinical", "The health record and care workflows", C.SEA],
  ["FaChartBar", "Data", "Longitudinal records, analytics, reporting", C.TEAL],
  ["FaPlug", "Integration", "Standards-based links to other systems", C.TEALD],
  ["FaDesktop", "Experience", "The unified shell everyone actually uses", C.SEA],
  ["FaLandmark", "Enterprise", "Audit, governance, operations", C.TEAL],
];
function planeCard(x, y, w, h, ic, name, desc, col, num) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w, h, rectRadius: 0.09, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  s.addText(String(num), { x: x + w - 0.6, y: y + 0.12, w: 0.5, h: 0.4, fontSize: 15, bold: true, color: C.MINT, align: "right", fontFace: "Cambria", margin: 0 });
  return iconCircle(s, ic, x + 0.28, y + 0.28, 0.82, col, C.WHITE).then(() => {
    s.addText(name, { x: x + 0.28, y: y + 1.15, w: w - 0.5, h: 0.35, fontSize: 15.5, bold: true, color: C.INK, fontFace: "Cambria", margin: 0 });
    s.addText(desc, { x: x + 0.28, y: y + 1.5, w: w - 0.45, h: 0.42, fontSize: 10.5, color: C.SLATE, fontFace: "Calibri", margin: 0, lineSpacing: 12.5 });
  });
}
const gw = (W - 2 * M - 3 * 0.3) / 4, gh = 1.92;
// top row 4
for (let i = 0; i < 4; i++) { const [ic, n, d, col] = planes[i]; await planeCard(M + i * (gw + 0.3), 2.2, gw, gh, ic, n, d, col, i + 1); }
// bottom row 3 centred
const bStart = (W - (3 * gw + 2 * 0.3)) / 2;
for (let i = 0; i < 3; i++) { const [ic, n, d, col] = planes[4 + i]; await planeCard(bStart + i * (gw + 0.3), 4.32, gw, gh, ic, n, d, col, 5 + i); }
s.addText("You don't need to memorise these — the point is a deliberate, non-negotiable structure, not a pile of features.", { x: M, y: 6.5, w: W - 2 * M, h: 0.4, fontSize: 13, italic: true, color: C.SLATE, align: "center", fontFace: "Calibri" });
footer(s, 8);
s.addNotes("For those who like to see the machine — seven planes, each with a clear job. Trust decides who's allowed. Registry keeps the master lists so we all mean the same facility or medicine. Clinical is the actual care and records. Data turns it into insight. Integration speaks standard languages like FHIR to partners and national systems. Experience is the screen we touch. Enterprise is audit and governance underneath. You don't need to memorise these — the point is a deliberate structure, not a pile of features.");

// ============================================================ SLIDE 9 — TRUST-FIRST
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Trust-first by design");
title(s, "Privacy and consent are enforced — not assumed");
// flow: request -> trust check (10 dims) -> service ; with no-PII + audit callouts
const fy = 2.6;
function flowNode(x, w, ic, head, sub, col) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: fy, w, h: 1.5, rectRadius: 0.1, fill: { color: col }, line: { type: "none" }, shadow: sh() });
  return iconCircle(s, ic, x + 0.28, fy + 0.32, 0.85, "FFFFFF", col).then(() => {
    s.addText(head, { x: x + 1.25, y: fy + 0.32, w: w - 1.4, h: 0.4, fontSize: 15, bold: true, color: C.WHITE, fontFace: "Cambria", margin: 0 });
    s.addText(sub, { x: x + 1.25, y: fy + 0.75, w: w - 1.4, h: 0.55, fontSize: 10.5, color: "E6F5F1", fontFace: "Calibri", margin: 0, lineSpacing: 12 });
  });
}
await flowNode(M, 3.5, "FaPaperPlane", "Any request", "A user or service asks to act", C.SLATE);
await flowNode(M + 4.05, 3.9, "FaShieldAlt", "Trust plane check", "Weighs 10 dimensions before anything happens", C.TEAL);
await flowNode(M + 8.5, 3.4, "FaCheckCircle", "Service acts", "Only if allowed — and it's logged", C.TEALD);
// arrows
s.addImage({ data: await icon("FaChevronRight", C.SEA), x: M + 3.6, y: fy + 0.55, w: 0.4, h: 0.4 });
s.addImage({ data: await icon("FaChevronRight", C.SEA), x: M + 8.05, y: fy + 0.55, w: 0.4, h: 0.4 });
// bottom 3 callouts
const cal = [
  ["FaSlidersH", "10 access dimensions", "identity, role, org, consent, purpose, context, assurance, workflow…"],
  ["FaUserSecret", "No PII in the shared record", "The clinical store uses a coded person ID; names stay behind a stricter gate."],
  ["FaHistory", "Everything is audited", "Every meaningful action leaves a traceable trail."],
];
let qx = M, qw = (W - 2 * M - 2 * 0.4) / 3;
for (const [ic, head, body] of cal) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: qx, y: 4.55, w: qw, h: 1.75, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, qx + 0.3, 4.8, 0.72, C.TINT, C.TEAL);
  s.addText(head, { x: qx + 1.1, y: 4.85, w: qw - 1.3, h: 0.6, fontSize: 13.5, bold: true, color: C.INK, fontFace: "Cambria", margin: 0, valign: "middle" });
  s.addText(body, { x: qx + 0.3, y: 5.55, w: qw - 0.55, h: 0.65, fontSize: 10.5, color: C.SLATE, fontFace: "Calibri", margin: 0, lineSpacing: 12.5 });
  qx += qw + 0.4;
}
footer(s, 9);
s.addNotes("Partners and HQ, pay special attention here. Privacy in Impilo is not a policy on paper — it's enforced by the software on every request. Before any service does anything, the request is checked against ten dimensions: identity, role, organisation, consent, purpose and more. We deliberately keep identifying details separate from the shared clinical record — the record uses a coded person ID and the names live behind a stricter gate. Everything meaningful is logged. This is how we earn the public's trust to run a national system.");

// ============================================================ SLIDE 10 — EXPERIENCE SHELL
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "The unified experience shell");
title(s, "One experience — adapted to your role and context");
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: M, y: 2.3, w: 4.4, h: 3.9, rectRadius: 0.1, fill: { color: C.DARK }, line: { type: "none" }, shadow: sh() });
await iconCircle(s, "FaWindowRestore", M + 0.4, 2.7, 1.0, C.AMBER, C.DARK);
s.addText("One coherent shell", { x: M + 0.4, y: 3.85, w: 3.6, h: 0.5, fontSize: 20, bold: true, color: C.WHITE, fontFace: "Cambria", margin: 0 });
s.addText("Not a different portal for every programme. One learnable experience, one governed trust model underneath.", { x: M + 0.4, y: 4.4, w: 3.65, h: 1.2, fontSize: 13, color: "D7ECE7", fontFace: "Calibri", margin: 0, lineSpacing: 17 });
s.addText("“Unified” ≠ identical", { x: M + 0.4, y: 5.65, w: 3.6, h: 0.4, fontSize: 13, italic: true, bold: true, color: C.MINT, fontFace: "Calibri", margin: 0 });
// right: role-adaptive views
const views = [
  ["FaUserNurse", "A facility nurse", "sees her patients and her tasks"],
  ["FaChartLine", "A district manager", "sees her facilities and indicators"],
  ["FaHandshake", "A partner", "sees only the slice they're authorised for"],
];
let vy = 2.35;
for (const [ic, head, body] of views) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: 5.15, y: vy, w: 7.48, h: 1.2, rectRadius: 0.09, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, 5.45, vy + 0.3, 0.62, C.TINT, C.TEAL);
  s.addText([
    { text: head + "  ", options: { bold: true, color: C.INK } },
    { text: body, options: { color: C.SLATE } },
  ], { x: 6.3, y: vy, w: 6.15, h: 1.2, fontSize: 15, valign: "middle", fontFace: "Calibri", margin: 0 });
  vy += 1.36;
}
s.addText("Same foundation, same rules, tailored view — so training is easier and moving between roles doesn't mean a new system.", { x: 5.15, y: 6.42, w: 7.48, h: 0.4, fontSize: 12, italic: true, color: C.SLATE, fontFace: "Calibri", margin: 0 });
footer(s, 10);
s.addNotes("Everybody works in one shell — one consistent, learnable experience — rather than a different portal for every programme. But unified doesn't mean identical. The shell adapts: a facility nurse sees her patients and tasks; a district manager sees her facilities and indicators; a partner sees the slice they're authorised for. Same foundation, same rules, tailored view. Training is easier, mistakes fewer, and moving between roles or facilities doesn't mean learning a whole new system.");

// ============================================================ SLIDE 11 — WELLNESS + GRADUATED FRICTION (warm)
s = pres.addSlide();
s.background = { color: "FBF7EF" };
kicker(s, "More than clinical", C.CORAL);
title(s, "A consumer-grade wellness layer — with the right rigour");
// wellness pillars
const pill = [["FaAppleAlt", "Diet"], ["FaBed", "Sleep"], ["FaRunning", "Fitness"], ["FaUsers", "Clubs & coaching"]];
let wx = M, ww = (W - 2 * M - 3 * 0.4) / 4;
for (const [ic, name] of pill) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: wx, y: 2.25, w: ww, h: 1.35, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: "EAD9B8", width: 1 }, shadow: sh() });
  await iconCircle(s, ic, wx + ww / 2 - 0.4, 2.45, 0.8, "FCEFD6", C.AMBER);
  s.addText(name, { x: wx, y: 3.28, w: ww, h: 0.3, fontSize: 13.5, bold: true, color: C.INK, align: "center", fontFace: "Cambria", margin: 0 });
  wx += ww + 0.4;
}
// graduated friction gradient bar
s.addText("GRADUATED FRICTION", { x: M, y: 3.95, w: 6, h: 0.35, fontSize: 12.5, bold: true, color: C.CORAL, charSpacing: 2, fontFace: "Calibri", margin: 0 });
const gx = M, gW = W - 2 * M, gyy = 4.45, segN = 6;
const grad = ["14B8A6", "3FB09A", "6BA97E", "C79A3E", "D97A34", "DB5A34"];
for (let i = 0; i < segN; i++) {
  s.addShape(pres.shapes.RECTANGLE, { x: gx + i * (gW / segN), y: gyy, w: gW / segN + 0.02, h: 0.6, fill: { color: grad[i] }, line: { type: "none" } });
}
s.addText("MINIMAL", { x: gx + 0.15, y: gyy + 0.12, w: 2, h: 0.36, fontSize: 12, bold: true, color: C.WHITE, fontFace: "Calibri", margin: 0 });
s.addText("MAXIMUM", { x: gx + gW - 2.15, y: gyy + 0.12, w: 2, h: 0.36, fontSize: 12, bold: true, color: C.WHITE, align: "right", fontFace: "Calibri", margin: 0 });
// endpoint chips
const chips = [
  ["Wellness & search", "light-touch, effortless", C.SEA, gx],
  ["Booking & records", "moderate checks", C.AMBER, gx + gW / 2 - 1.5],
  ["Prescribing & claims", "maximum rigour", C.CORAL, gx + gW - 3.0],
];
for (const [head, body, col, x] of chips) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y: 5.35, w: 3.0, h: 0.95, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: col, width: 1.5 }, shadow: sh() });
  s.addText(head, { x: x + 0.2, y: 5.45, w: 2.65, h: 0.35, fontSize: 12.5, bold: true, color: C.INK, fontFace: "Cambria", margin: 0 });
  s.addText(body, { x: x + 0.2, y: 5.8, w: 2.65, h: 0.4, fontSize: 10.5, italic: true, color: col, fontFace: "Calibri", margin: 0 });
}
s.addText("Browsing wellness should feel effortless; prescribing a controlled medicine should be deliberate. Same system — appropriate rigour for the moment.", { x: M, y: 6.55, w: W - 2 * M, h: 0.4, fontSize: 12.5, italic: true, color: C.SLATE, align: "center", fontFace: "Calibri" });
footer(s, 11);
s.addNotes("Impilo isn't only for the clinic. Much of health happens in daily life, so the OS includes a real consumer-grade wellness layer — diet, sleep, fitness, community clubs. And it's smart about friction: browsing wellness content should feel effortless, while prescribing a controlled medicine or submitting a claim should be deliberate and tightly controlled. Same system, appropriate rigour for the moment. This is how we move from a system people are made to use to one they want to use.");

// ============================================================ SLIDE 12 — WHAT IT MEANS FOR YOU (2x2)
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "What it means for you");
title(s, "One platform — a distinct win for every seat in the room");
const quad = [
  ["FaClinicMedical", "Facility teams", "Less re-typing, more care", ["Register once, find instantly", "Guided workflows with safety checks", "Built for offline / weak connectivity"], C.TEAL],
  ["FaChartBar", "District & provincial", "Trusted numbers, near real-time", ["Indicators roll up from real transactions", "See your facilities & gaps in one place", "One version of the truth — traceable"], C.TEALD],
  ["FaLandmark", "HQ & national leadership", "Strategic, governed infrastructure", ["National identity, registries, data", "Governance & security built in", "New programmes are modules, not silos"], C.SEA],
  ["FaHandshake", "Partners", "Build on it — don't rebuild it", ["Standard FHIR integration points", "Inherit identity, consent & audit", "Extend without creating an island"], C.AMBER],
];
const qgx = M, qgy = 2.2, qgw = (W - 2 * M - 0.4) / 2, qgh = 2.15;
let idx = 0;
for (let r = 0; r < 2; r++) for (let cc = 0; cc < 2; cc++) {
  const [ic, head, tag, items, col] = quad[idx++];
  const x = qgx + cc * (qgw + 0.4), y = qgy + r * (qgh + 0.3);
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: qgw, h: qgh, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, x + 0.32, y + 0.32, 0.95, col, C.WHITE);
  s.addText(head, { x: x + 1.45, y: y + 0.3, w: qgw - 1.65, h: 0.4, fontSize: 17, bold: true, color: C.INK, fontFace: "Cambria", margin: 0 });
  s.addText(tag, { x: x + 1.45, y: y + 0.72, w: qgw - 1.65, h: 0.35, fontSize: 12, italic: true, color: col, fontFace: "Calibri", margin: 0 });
  s.addText(items.map((t) => ({ text: t, options: { bullet: { code: "2022", indent: 12 }, breakLine: true, paraSpaceAfter: 3 } })),
    { x: x + 0.35, y: y + 1.28, w: qgw - 0.6, h: 0.8, fontSize: 11, color: C.SLATE, fontFace: "Calibri", margin: 0 });
  idx;
}
footer(s, 12);
s.addNotes("Now the part that matters most to this room. Facility teams: less time re-typing, more time with patients — register once, find instantly, guided safely, and it works when the network doesn't. District and provincial managers: indicators roll up automatically from real transactions, one version of the truth you can trace back to source. HQ and leadership: national identity, registries and data with governance built in, and new programmes arrive as modules not silos. Partners: build on the trusted foundation, inherit identity, consent and audit, and extend without creating another island.");

// ============================================================ SLIDE 13 — GOVERNANCE GUARDRAILS
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Governance & safety guardrails");
title(s, "Coherence by design — rules the platform enforces on itself");
const guards = [
  ["FaCheckDouble", "Single source of truth", "One patient, provider and facility model — no duplicates allowed to creep in."],
  ["FaClipboardCheck", "Production-grade or it doesn't ship", "Every capability: authorised, audited, observable, tested."],
  ["FaRobot", "Guidance supports, never replaces", "Nompilo advice is advisory and always auditable — professionals stay accountable."],
  ["FaBalanceScale", "Person & consent first", "Person-centred and consent-driven at every layer."],
];
let ggy = 2.35, gg2 = 0;
for (let r = 0; r < 2; r++) for (let cc = 0; cc < 2; cc++) {
  const [ic, head, body] = guards[gg2++];
  const gwid = (W - 2 * M - 0.5) / 2;
  const x = M + cc * (gwid + 0.5), y = ggy + r * 1.85;
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x, y, w: gwid, h: 1.6, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  await iconCircle(s, ic, x + 0.32, y + 0.35, 0.92, C.TINT, C.TEAL);
  s.addText(head, { x: x + 1.45, y: y + 0.28, w: gwid - 1.65, h: 0.7, fontSize: 15.5, bold: true, color: C.INK, fontFace: "Cambria", margin: 0, valign: "top", lineSpacing: 17 });
  s.addText(body, { x: x + 1.45, y: y + 0.9, w: gwid - 1.7, h: 0.6, fontSize: 11.5, color: C.SLATE, fontFace: "Calibri", margin: 0, lineSpacing: 13.5 });
}
footer(s, 13);
s.addNotes("A word on discipline, because it's what keeps a national system from decaying. We enforce single sources of truth — one patient model, one provider model, one facility model, no duplicates. Every production capability must be authorised, audited, observable and tested before it ships. Any intelligent guidance is advisory and fully traceable — it supports professionals, never replaces their accountability. These aren't slogans; they're rules the platform enforces on itself.");

// ============================================================ SLIDE 14 — WHERE WE ARE (stats)
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "Where we are today");
title(s, "From doctrine to a working system");
const stats = [
  ["7", "planes", "built & running in preview", C.TEAL],
  ["1", "person anchor", "identity + trust live end-to-end", C.TEALD],
  ["Live", "wellness", "citizen & care surfaces in preview", C.AMBER],
];
let stx = M, stw = (W - 2 * M - 2 * 0.4) / 3;
for (const [big, unit, body, col] of stats) {
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: stx, y: 2.35, w: stw, h: 2.0, rectRadius: 0.1, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  s.addText(big, { x: stx + 0.3, y: 2.5, w: stw - 0.6, h: 0.95, fontSize: 52, bold: true, color: col, fontFace: "Cambria", margin: 0 });
  s.addText(unit, { x: stx + 0.32, y: 3.42, w: stw - 0.6, h: 0.35, fontSize: 15, bold: true, color: C.INK, fontFace: "Cambria", margin: 0 });
  s.addText(body, { x: stx + 0.32, y: 3.78, w: stw - 0.55, h: 0.5, fontSize: 11.5, color: C.SLATE, fontFace: "Calibri", margin: 0, lineSpacing: 13 });
  stx += stw + 0.4;
}
// method line
s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: M, y: 4.65, w: W - 2 * M, h: 1.35, rectRadius: 0.1, fill: { color: C.DARK }, line: { type: "none" }, shadow: sh() });
await iconCircle(s, "FaShippingFast", M + 0.4, 4.95, 0.8, C.AMBER, C.DARK);
s.addText([
  { text: "Delivered in small, verified increments  ", options: { bold: true, color: C.WHITE } },
  { text: "— every step safe, auditable, and reversible.", options: { color: C.MINT } },
], { x: M + 1.5, y: 4.65, w: W - 2 * M - 1.8, h: 1.35, fontSize: 17, valign: "middle", fontFace: "Calibri", margin: 0 });
s.addText("Presenter: insert your latest concrete milestone or metric here — one real proof point lands harder than any slide.", { x: M, y: 6.25, w: W - 2 * M, h: 0.4, fontSize: 11.5, italic: true, color: C.SLATE, align: "center", fontFace: "Calibri" });
footer(s, 14);
s.addNotes("So where are we? This is not a concept on a whiteboard. The core planes and services are built and running in preview — identity and trust, clinical records, the data layer, the experience shell and the wellness surfaces. We deliver in small verified increments so every step is safe and auditable. Presenter: drop in your latest concrete milestone here — number of services live, environments certified, or a recent demo — the audience will remember one real proof point more than any slide.");

// ============================================================ SLIDE 15 — ROADMAP (timeline)
s = pres.addSlide();
s.background = { color: C.BG };
kicker(s, "The roadmap ahead");
title(s, "What comes next");
const steps = [
  ["FaProjectDiagram", "Close the loop", "Live end-to-end workflows across services"],
  ["FaClinicMedical", "Broaden rollout", "Onboard real users at more facilities"],
  ["FaPlug", "Open to partners", "More integrations on standard interfaces"],
  ["FaWifi", "Harden the last mile", "Stronger offline / federated operation"],
];
const tly = 4.1, tlx0 = M + 1.35, tlx1 = W - M - 1.35;
s.addShape(pres.shapes.LINE, { x: tlx0, y: tly, w: tlx1 - tlx0, h: 0, line: { color: C.SEA, width: 3 } });
const nStep = steps.length, stepGap = (tlx1 - tlx0) / (nStep - 1);
for (let i = 0; i < nStep; i++) {
  const [ic, head, body] = steps[i];
  const nx = tlx0 + i * stepGap;
  const up = i % 2 === 0;
  // node
  s.addShape(pres.shapes.OVAL, { x: nx - 0.45, y: tly - 0.45, w: 0.9, h: 0.9, fill: { color: C.DARK }, line: { color: C.AMBER, width: 2.5 } });
  s.addImage({ data: await icon(ic, C.MINT), x: nx - 0.23, y: tly - 0.23, w: 0.46, h: 0.46 });
  // card above or below
  const cardW = 2.7, cardH = 1.35;
  const cyc = up ? tly - 0.7 - cardH : tly + 0.7;
  s.addShape(pres.shapes.ROUNDED_RECTANGLE, { x: nx - cardW / 2, y: cyc, w: cardW, h: cardH, rectRadius: 0.09, fill: { color: C.CARD }, line: { color: C.LINE, width: 1 }, shadow: sh() });
  s.addText(head, { x: nx - cardW / 2 + 0.2, y: cyc + 0.18, w: cardW - 0.4, h: 0.4, fontSize: 14, bold: true, color: C.TEAL, fontFace: "Cambria", margin: 0 });
  s.addText(body, { x: nx - cardW / 2 + 0.2, y: cyc + 0.6, w: cardW - 0.35, h: 0.65, fontSize: 11, color: C.SLATE, fontFace: "Calibri", margin: 0, lineSpacing: 13 });
}
s.addText("We'll keep bringing progress to this meeting — honestly, including the gaps. Trust is built by being straight about where we are.", { x: M, y: 6.5, w: W - 2 * M, h: 0.4, fontSize: 12.5, italic: true, color: C.SLATE, align: "center", fontFace: "Calibri" });
footer(s, 15);
s.addNotes("Looking ahead, our priorities are: closing the loop on live end-to-end workflows across services; broadening rollout and onboarding real users at facilities; opening the door wider to partner integrations; and hardening the last-mile experience so the system is dependable where connectivity is hardest. We'll keep bringing progress to this meeting — honestly, including the gaps — because trust is built by being straight about where we are.");

// ============================================================ SLIDE 16 — WHY IT MATTERS (dark)
s = pres.addSlide();
s.background = { color: C.DARK };
s.addShape(pres.shapes.OVAL, { x: 10.4, y: 3.6, w: 5.6, h: 5.6, fill: { color: C.DARK2 }, line: { type: "none" } });
await iconCircle(s, "FaGlobeAfrica", 11.15, 1.05, 1.4, C.AMBER, C.DARK);
kicker(s, "Why this matters for Zimbabwe", C.MINT);
s.addText("One coherent national health environment", { x: M, y: 1.0, w: 9.5, h: 1.4, fontSize: 34, bold: true, color: C.WHITE, fontFace: "Cambria", margin: 0, lineSpacing: 38 });
const whys = [
  ["FaRoute", "A citizen's health story follows them, safely, wherever they go"],
  ["FaChartLine", "Managers lead with information they can trust, closer to real time"],
  ["FaHandshake", "Partners strengthen the system instead of splintering it"],
  ["FaShieldAlt", "A digital foundation we own, govern and grow — for the long term"],
];
let yy = 2.75;
for (const [ic, text] of whys) {
  await iconCircle(s, ic, M, yy, 0.72, C.TEALD, C.MINT);
  s.addText(text, { x: M + 1.0, y: yy - 0.05, w: 9.0, h: 0.82, fontSize: 17, color: "E4F2EE", valign: "middle", fontFace: "Calibri", margin: 0, lineSpacing: 20 });
  yy += 1.02;
}
footer(s, 16);
s.addNotes("Let me zoom back out. What we're really building is a country where a person's health story follows them safely from clinic to hospital to pharmacy; where managers at every level lead with information they can trust; where partners strengthen the system instead of splintering it; and where the digital foundation of our health system is something we own and govern for the long term. That's the promise of a Health Operating System — and it's within reach.");

// ============================================================ SLIDE 17 — CLOSE (dark)
s = pres.addSlide();
s.background = { color: C.DARK };
s.addShape(pres.shapes.OVAL, { x: -2.0, y: -2.2, w: 5.4, h: 5.4, fill: { color: C.DARK2 }, line: { type: "none" } });
s.addShape(pres.shapes.OVAL, { x: 10.6, y: 4.2, w: 5, h: 5, fill: { color: C.DARK2 }, line: { type: "none" } });
await iconCircle(s, "FaHeartbeat", 6.16, 0.95, 1.0, C.AMBER, C.DARK);
s.addText("Impilo vNext", { x: 0, y: 2.15, w: W, h: 0.9, fontSize: 46, bold: true, color: C.WHITE, align: "center", fontFace: "Cambria", margin: 0 });
s.addText("One system  ·  many roles  ·  one person at the centre", { x: 0, y: 3.15, w: W, h: 0.5, fontSize: 20, italic: true, color: C.MINT, align: "center", fontFace: "Calibri", margin: 0 });
s.addShape(pres.shapes.LINE, { x: W / 2 - 1.7, y: 3.95, w: 3.4, h: 0, line: { color: C.AMBER, width: 2.5 } });
s.addText("Wherever you sit — facility, district, province, HQ or partner — there is a place for you in this.", { x: 1.5, y: 4.25, w: W - 3, h: 0.7, fontSize: 15, color: "CFE9E4", align: "center", fontFace: "Calibri", margin: 0 });
s.addText("Questions & discussion welcome", { x: 0, y: 5.25, w: W, h: 0.4, fontSize: 16, bold: true, color: C.WHITE, align: "center", fontFace: "Calibri", margin: 0 });
s.addText("[Presenter name · role · email]     ·     Learn more: internal doctrine & architecture docs", { x: 0, y: 6.5, w: W, h: 0.4, fontSize: 12, color: "9FC4BE", align: "center", fontFace: "Calibri", margin: 0 });
s.addNotes("To close on the sentence we started with: one Health Operating System, one experience, one person at the centre — many roles, many contexts, one governed runtime. Wherever you sit — facility, district, province, HQ or partner — there's a place for you in this, and a benefit for you from it. Thank you. I'd love your questions and your thinking on where we go next.");

await pres.writeFile({ fileName: "Introduction-to-Impilo-vNext.pptx" });
console.log("WROTE deck");
})();
