/**
 * ENGINEERING-AUTHORED pending clinical verification (MoHCC ratification) — Wave SB-4.
 * Anatomy simplified for clickable region selection, not for anatomical teaching.
 *
 * Head, neck and maxillofacial region maps for surgical site selection.
 *
 * A surgical site recorded as prose ("lesion on the jaw") cannot be counted, compared
 * against the consent form, or matched to the operation note. These maps turn the site into
 * a coded selection at the moment the surgeon points at it.
 *
 * TERMINOLOGY HONESTY — two binding kinds are used here, and the difference is deliberate:
 *
 *  - `code(...)` carries a SNOMED CT body-structure code that is verified and *true* of the
 *    region, though often at a coarser level than the region label (a parietal region binds
 *    to "Head structure"; both thyroid lobes bind to "Thyroid structure" and are separated by
 *    `laterality`). This is the idiom already used by clinical-maps.ts, where both lungs'
 *    upper zones share one lobe code and laterality carries the side.
 *  - `unmapped(...)` is used wherever no SNOMED code was verified for the structure. It binds
 *    to a clearly-marked local system URI rather than a plausible-looking SNOMED code, because
 *    a wrong code is far worse than an obviously-unbound one: it would be silently trusted
 *    downstream. These are the rows awaiting MoHCC terminology ratification.
 *
 * No region here invents a SNOMED code it could not verify.
 */

import { SNOMED_BODY_STRUCTURE, type BodyRegionDef, type RegionMapDef } from "../core/types";

const code = (id: string, display: string) => ({
  system: SNOMED_BODY_STRUCTURE,
  code: id,
  display,
});

/**
 * System URI for regions with no verified SNOMED CT binding yet. Deliberately not a SNOMED
 * URI, so that a consumer cannot mistake a placeholder for a terminology-bound location.
 */
export const UNMAPPED_BODY_STRUCTURE = "urn:impilo:body-structure-unmapped";

const unmapped = (localCode: string, display: string) => ({
  system: UNMAPPED_BODY_STRUCTURE,
  code: localCode,
  display,
});

/**
 * Scalp and skull, vertex view with an inferior view of the skull base beneath it.
 *
 * Two views in one map because a skull-base region cannot be reached from a vertex outline,
 * and a surgeon selecting "posterior fossa" should not have to change instruments to do it.
 */
export const SCALP_SKULL_MAP: RegionMapDef = {
  id: "scalp-skull",
  title: "Scalp and skull",
  viewBox: "0 0 200 300",
  silhouettePaths: [
    "M100 20 a70 90 0 0 1 0 180 a70 90 0 0 1 0 -180 Z",
    "M100 20 v180",
    "M100 196 a64 48 0 0 1 0 96 a64 48 0 0 1 0 -96 Z",
  ],
  regions: [
    { id: "frontal-region", label: "Frontal region", group: "calvarium", svgPath: "M60 26 h80 v44 h-80 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "parietal-right", label: "Right parietal region", group: "calvarium", svgPath: "M56 74 h42 v56 h-42 Z", laterality: "right", locationCode: code("69536005", "Head") },
    { id: "parietal-left", label: "Left parietal region", group: "calvarium", svgPath: "M102 74 h42 v56 h-42 Z", laterality: "left", locationCode: code("69536005", "Head") },
    { id: "temporal-right", label: "Right temporal region", group: "calvarium", svgPath: "M32 76 h22 v52 h-22 Z", laterality: "right", locationCode: code("69536005", "Head") },
    { id: "temporal-left", label: "Left temporal region", group: "calvarium", svgPath: "M146 76 h22 v52 h-22 Z", laterality: "left", locationCode: code("69536005", "Head") },
    { id: "occipital-region", label: "Occipital region", group: "calvarium", svgPath: "M60 134 h80 v56 h-80 Z", laterality: "midline", locationCode: code("69536005", "Head") },
    { id: "vertex-scalp", label: "Vertex scalp", group: "scalp", svgPath: "M78 82 h44 v40 h-44 Z", laterality: "midline", locationCode: code("41695006", "Scalp") },
    { id: "anterior-cranial-fossa", label: "Anterior cranial fossa", group: "skull base", svgPath: "M62 202 h76 v24 h-76 Z", laterality: "midline", locationCode: unmapped("anterior-cranial-fossa", "Anterior cranial fossa") },
    { id: "middle-cranial-fossa-right", label: "Right middle cranial fossa", group: "skull base", svgPath: "M36 230 h60 v26 h-60 Z", laterality: "right", locationCode: unmapped("middle-cranial-fossa", "Middle cranial fossa") },
    { id: "middle-cranial-fossa-left", label: "Left middle cranial fossa", group: "skull base", svgPath: "M104 230 h60 v26 h-60 Z", laterality: "left", locationCode: unmapped("middle-cranial-fossa", "Middle cranial fossa") },
    { id: "posterior-cranial-fossa", label: "Posterior cranial fossa", group: "skull base", svgPath: "M62 260 h76 v28 h-76 Z", laterality: "midline", locationCode: unmapped("posterior-cranial-fossa", "Posterior cranial fossa") },
  ],
};

/** Face and facial skeleton, front view — the working map for maxillofacial trauma and excision. */
export const FACE_MAXILLOFACIAL_MAP: RegionMapDef = {
  id: "face-maxillofacial",
  title: "Face and maxillofacial skeleton",
  viewBox: "0 0 200 230",
  silhouettePaths: [
    "M100 10 a62 100 0 0 1 0 200 a62 100 0 0 1 0 -200 Z",
    "M100 10 v200",
  ],
  regions: [
    { id: "forehead", label: "Forehead", group: "upper face", svgPath: "M56 24 h88 v40 h-88 Z", laterality: "midline", locationCode: code("89545001", "Face") },
    { id: "orbit-right", label: "Right orbit and periorbital region", group: "upper face", svgPath: "M52 70 h38 v26 h-38 Z", laterality: "right", locationCode: code("81745001", "Eye") },
    { id: "orbit-left", label: "Left orbit and periorbital region", group: "upper face", svgPath: "M110 70 h38 v26 h-38 Z", laterality: "left", locationCode: code("81745001", "Eye") },
    { id: "nose-external", label: "External nose", group: "mid face", svgPath: "M90 74 h20 v50 h-20 Z", laterality: "midline", locationCode: unmapped("external-nose", "External nose") },
    { id: "zygoma-right", label: "Right zygoma and malar region", group: "mid face", svgPath: "M46 100 h40 v20 h-40 Z", laterality: "right", locationCode: unmapped("zygomatic-bone", "Zygomatic bone") },
    { id: "zygoma-left", label: "Left zygoma and malar region", group: "mid face", svgPath: "M114 100 h40 v20 h-40 Z", laterality: "left", locationCode: unmapped("zygomatic-bone", "Zygomatic bone") },
    { id: "maxilla", label: "Maxilla", group: "mid face", svgPath: "M62 126 h76 v26 h-76 Z", laterality: "midline", locationCode: unmapped("maxilla", "Maxilla") },
    { id: "mandible-ramus-right", label: "Right mandibular ramus", group: "lower face", svgPath: "M38 100 h18 v38 h-18 Z", laterality: "right", locationCode: unmapped("mandibular-ramus", "Ramus of mandible") },
    { id: "mandible-ramus-left", label: "Left mandibular ramus", group: "lower face", svgPath: "M144 100 h18 v38 h-18 Z", laterality: "left", locationCode: unmapped("mandibular-ramus", "Ramus of mandible") },
    { id: "mandible-angle-right", label: "Right mandibular angle", group: "lower face", svgPath: "M40 140 h20 v34 h-20 Z", laterality: "right", locationCode: unmapped("mandibular-angle", "Angle of mandible") },
    { id: "mandible-angle-left", label: "Left mandibular angle", group: "lower face", svgPath: "M140 140 h20 v34 h-20 Z", laterality: "left", locationCode: unmapped("mandibular-angle", "Angle of mandible") },
    { id: "mandible-body-right", label: "Right mandibular body", group: "lower face", svgPath: "M56 156 h40 v22 h-40 Z", laterality: "right", locationCode: unmapped("mandibular-body", "Body of mandible") },
    { id: "mandible-body-left", label: "Left mandibular body", group: "lower face", svgPath: "M104 156 h40 v22 h-40 Z", laterality: "left", locationCode: unmapped("mandibular-body", "Body of mandible") },
    { id: "chin-mentum", label: "Chin (mentum)", group: "lower face", svgPath: "M84 182 h32 v24 h-32 Z", laterality: "midline", locationCode: unmapped("mentum", "Chin") },
  ],
};

/**
 * Neck triangles, with the carotid zones overlaid.
 *
 * The triangles and the zones deliberately overlap: they answer different questions. A
 * triangle says where the lump is; zone I/II/III says how a penetrating injury will be
 * approached, and that decision is made before the patient reaches theatre.
 */
export const NECK_TRIANGLES_MAP: RegionMapDef = {
  id: "neck-triangles",
  title: "Neck triangles and carotid zones",
  viewBox: "0 0 200 240",
  silhouettePaths: [
    "M40 20 h120 v190 h-120 Z",
    "M70 20 L118 200",
    "M130 20 L82 200",
    "M100 20 v190",
  ],
  regions: [
    { id: "neck-midline", label: "Anterior midline of neck", group: "triangles", svgPath: "M92 30 h16 v170 h-16 Z", laterality: "midline", locationCode: code("45048000", "Neck") },
    { id: "anterior-triangle-right", label: "Right anterior triangle", group: "triangles", svgPath: "M62 40 h28 v130 h-28 Z", laterality: "right", locationCode: code("45048000", "Neck") },
    { id: "anterior-triangle-left", label: "Left anterior triangle", group: "triangles", svgPath: "M110 40 h28 v130 h-28 Z", laterality: "left", locationCode: code("45048000", "Neck") },
    { id: "posterior-triangle-right", label: "Right posterior triangle", group: "triangles", svgPath: "M30 40 h28 v130 h-28 Z", laterality: "right", locationCode: code("45048000", "Neck") },
    { id: "posterior-triangle-left", label: "Left posterior triangle", group: "triangles", svgPath: "M142 40 h28 v130 h-28 Z", laterality: "left", locationCode: code("45048000", "Neck") },
    { id: "thyroid-region", label: "Thyroid region", group: "midline structures", svgPath: "M78 108 h44 v34 h-44 Z", laterality: "midline", locationCode: code("69748006", "Thyroid structure") },
    { id: "cricoid-region", label: "Cricoid cartilage", group: "midline structures", svgPath: "M88 92 h24 v14 h-24 Z", laterality: "midline", locationCode: unmapped("cricoid-cartilage", "Cricoid cartilage") },
    { id: "suprasternal-notch", label: "Suprasternal notch", group: "midline structures", svgPath: "M88 190 h24 v18 h-24 Z", laterality: "midline", locationCode: unmapped("suprasternal-notch", "Suprasternal notch") },
    { id: "carotid-zone-i", label: "Carotid zone I (clavicle to cricoid)", group: "carotid zones", svgPath: "M60 150 h80 v40 h-80 Z", laterality: "midline", locationCode: unmapped("neck-zone-i", "Neck zone I") },
    { id: "carotid-zone-ii", label: "Carotid zone II (cricoid to angle of mandible)", group: "carotid zones", svgPath: "M60 80 h80 v66 h-80 Z", laterality: "midline", locationCode: unmapped("neck-zone-ii", "Neck zone II") },
    { id: "carotid-zone-iii", label: "Carotid zone III (angle of mandible to skull base)", group: "carotid zones", svgPath: "M60 34 h80 v42 h-80 Z", laterality: "midline", locationCode: unmapped("neck-zone-iii", "Neck zone III") },
  ],
};

/**
 * Thyroid and parathyroid glands.
 *
 * Parathyroid positions are drawn as fixed superior/inferior pairs. Real parathyroids are
 * variable in position and number; the four boxes are where a surgeon looks first, not a
 * claim about where they are.
 */
export const THYROID_PARATHYROID_MAP: RegionMapDef = {
  id: "thyroid-parathyroid",
  title: "Thyroid and parathyroid",
  viewBox: "0 0 200 200",
  silhouettePaths: [
    "M92 20 h16 v160 h-16 Z",
    "M46 50 h108 v96 h-108 Z",
  ],
  regions: [
    { id: "thyroid-lobe-right", label: "Right thyroid lobe", group: "thyroid", svgPath: "M46 50 h40 v96 h-40 Z", laterality: "right", locationCode: code("69748006", "Thyroid structure") },
    { id: "thyroid-lobe-left", label: "Left thyroid lobe", group: "thyroid", svgPath: "M114 50 h40 v96 h-40 Z", laterality: "left", locationCode: code("69748006", "Thyroid structure") },
    { id: "thyroid-isthmus", label: "Thyroid isthmus", group: "thyroid", svgPath: "M86 92 h28 v30 h-28 Z", laterality: "midline", locationCode: code("69748006", "Thyroid structure") },
    { id: "thyroid-pyramidal-lobe", label: "Pyramidal lobe", group: "thyroid", svgPath: "M92 46 h16 v42 h-16 Z", laterality: "midline", locationCode: code("69748006", "Thyroid structure") },
    { id: "parathyroid-superior-right", label: "Right superior parathyroid", group: "parathyroid", svgPath: "M52 62 h18 v16 h-18 Z", laterality: "right", locationCode: unmapped("superior-parathyroid", "Superior parathyroid gland") },
    { id: "parathyroid-inferior-right", label: "Right inferior parathyroid", group: "parathyroid", svgPath: "M52 118 h18 v16 h-18 Z", laterality: "right", locationCode: unmapped("inferior-parathyroid", "Inferior parathyroid gland") },
    { id: "parathyroid-superior-left", label: "Left superior parathyroid", group: "parathyroid", svgPath: "M130 62 h18 v16 h-18 Z", laterality: "left", locationCode: unmapped("superior-parathyroid", "Superior parathyroid gland") },
    { id: "parathyroid-inferior-left", label: "Left inferior parathyroid", group: "parathyroid", svgPath: "M130 118 h18 v16 h-18 Z", laterality: "left", locationCode: unmapped("inferior-parathyroid", "Inferior parathyroid gland") },
  ],
};

/**
 * Ear: both sides, with the tympanic membrane drawn as its own enlarged circle beneath each
 * pinna. A TM quadrant cannot be hit reliably at pinna scale on a phone, and the quadrant is
 * the whole point of recording a perforation.
 */
export const EAR_MAP: RegionMapDef = {
  id: "ear",
  title: "Ear (both sides)",
  viewBox: "0 0 240 200",
  silhouettePaths: [
    "M50 20 a26 44 0 0 1 0 88 a26 44 0 0 1 0 -88 Z",
    "M180 20 a26 44 0 0 1 0 88 a26 44 0 0 1 0 -88 Z",
    "M52 126 a26 26 0 0 1 0 52 a26 26 0 0 1 0 -52 Z",
    "M182 126 a26 26 0 0 1 0 52 a26 26 0 0 1 0 -52 Z",
  ],
  regions: [
    { id: "helix-right", label: "Right helix", group: "right pinna", svgPath: "M28 26 h44 v18 h-44 Z", laterality: "right", locationCode: code("117590005", "Ear") },
    { id: "concha-right", label: "Right concha", group: "right pinna", svgPath: "M38 54 h24 v26 h-24 Z", laterality: "right", locationCode: code("117590005", "Ear") },
    { id: "lobule-right", label: "Right lobule", group: "right pinna", svgPath: "M40 90 h20 v18 h-20 Z", laterality: "right", locationCode: code("117590005", "Ear") },
    { id: "external-canal-right", label: "Right external auditory canal", group: "right ear", svgPath: "M64 60 h14 v16 h-14 Z", laterality: "right", locationCode: unmapped("external-auditory-canal", "External auditory canal") },
    { id: "mastoid-right", label: "Right mastoid", group: "right ear", svgPath: "M82 60 h24 v34 h-24 Z", laterality: "right", locationCode: unmapped("mastoid", "Mastoid process") },
    { id: "tm-anterosuperior-right", label: "Right TM anterosuperior quadrant", group: "right tympanic membrane", svgPath: "M26 126 h26 v26 h-26 Z", laterality: "right", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "tm-posterosuperior-right", label: "Right TM posterosuperior quadrant", group: "right tympanic membrane", svgPath: "M52 126 h26 v26 h-26 Z", laterality: "right", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "tm-anteroinferior-right", label: "Right TM anteroinferior quadrant", group: "right tympanic membrane", svgPath: "M26 152 h26 v26 h-26 Z", laterality: "right", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "tm-posteroinferior-right", label: "Right TM posteroinferior quadrant", group: "right tympanic membrane", svgPath: "M52 152 h26 v26 h-26 Z", laterality: "right", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "helix-left", label: "Left helix", group: "left pinna", svgPath: "M158 26 h44 v18 h-44 Z", laterality: "left", locationCode: code("117590005", "Ear") },
    { id: "concha-left", label: "Left concha", group: "left pinna", svgPath: "M168 54 h24 v26 h-24 Z", laterality: "left", locationCode: code("117590005", "Ear") },
    { id: "lobule-left", label: "Left lobule", group: "left pinna", svgPath: "M170 90 h20 v18 h-20 Z", laterality: "left", locationCode: code("117590005", "Ear") },
    { id: "external-canal-left", label: "Left external auditory canal", group: "left ear", svgPath: "M152 60 h14 v16 h-14 Z", laterality: "left", locationCode: unmapped("external-auditory-canal", "External auditory canal") },
    { id: "mastoid-left", label: "Left mastoid", group: "left ear", svgPath: "M124 60 h24 v34 h-24 Z", laterality: "left", locationCode: unmapped("mastoid", "Mastoid process") },
    { id: "tm-anterosuperior-left", label: "Left TM anterosuperior quadrant", group: "left tympanic membrane", svgPath: "M182 126 h26 v26 h-26 Z", laterality: "left", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "tm-posterosuperior-left", label: "Left TM posterosuperior quadrant", group: "left tympanic membrane", svgPath: "M156 126 h26 v26 h-26 Z", laterality: "left", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "tm-anteroinferior-left", label: "Left TM anteroinferior quadrant", group: "left tympanic membrane", svgPath: "M182 152 h26 v26 h-26 Z", laterality: "left", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
    { id: "tm-posteroinferior-left", label: "Left TM posteroinferior quadrant", group: "left tympanic membrane", svgPath: "M156 152 h26 v26 h-26 Z", laterality: "left", locationCode: unmapped("tympanic-membrane", "Tympanic membrane") },
  ],
};

/** Eye and orbit, both sides: lids, canthi, ocular surface, scleral quadrants, lacrimal apparatus. */
export const EYE_ORBIT_MAP: RegionMapDef = {
  id: "eye-orbit",
  title: "Eye and orbit (both sides)",
  viewBox: "0 0 240 160",
  silhouettePaths: [
    "M64 44 a46 36 0 0 1 0 72 a46 36 0 0 1 0 -72 Z",
    "M176 44 a46 36 0 0 1 0 72 a46 36 0 0 1 0 -72 Z",
    "M114 40 h12 v80 h-12 Z",
  ],
  regions: [
    { id: "upper-lid-right", label: "Right upper eyelid", group: "right adnexa", svgPath: "M22 44 h84 v16 h-84 Z", laterality: "right", locationCode: unmapped("upper-eyelid", "Upper eyelid") },
    { id: "lower-lid-right", label: "Right lower eyelid", group: "right adnexa", svgPath: "M22 100 h84 v16 h-84 Z", laterality: "right", locationCode: unmapped("lower-eyelid", "Lower eyelid") },
    { id: "medial-canthus-right", label: "Right medial canthus", group: "right adnexa", svgPath: "M104 70 h14 v20 h-14 Z", laterality: "right", locationCode: unmapped("medial-canthus", "Medial canthus") },
    { id: "lateral-canthus-right", label: "Right lateral canthus", group: "right adnexa", svgPath: "M12 70 h14 v20 h-14 Z", laterality: "right", locationCode: unmapped("lateral-canthus", "Lateral canthus") },
    { id: "conjunctiva-right", label: "Right bulbar conjunctiva", group: "right ocular surface", svgPath: "M26 62 h76 v36 h-76 Z", laterality: "right", locationCode: code("81745001", "Eye") },
    { id: "cornea-right", label: "Right cornea", group: "right ocular surface", svgPath: "M52 68 h24 v24 h-24 Z", laterality: "right", locationCode: unmapped("cornea", "Cornea") },
    { id: "sclera-superotemporal-right", label: "Right sclera, superotemporal", group: "right sclera", svgPath: "M28 64 h24 v16 h-24 Z", laterality: "right", locationCode: unmapped("sclera", "Sclera") },
    { id: "sclera-superonasal-right", label: "Right sclera, superonasal", group: "right sclera", svgPath: "M76 64 h24 v16 h-24 Z", laterality: "right", locationCode: unmapped("sclera", "Sclera") },
    { id: "sclera-inferotemporal-right", label: "Right sclera, inferotemporal", group: "right sclera", svgPath: "M28 82 h24 v16 h-24 Z", laterality: "right", locationCode: unmapped("sclera", "Sclera") },
    { id: "sclera-inferonasal-right", label: "Right sclera, inferonasal", group: "right sclera", svgPath: "M76 82 h24 v16 h-24 Z", laterality: "right", locationCode: unmapped("sclera", "Sclera") },
    { id: "lacrimal-gland-right", label: "Right lacrimal gland", group: "right lacrimal apparatus", svgPath: "M20 46 h20 v14 h-20 Z", laterality: "right", locationCode: unmapped("lacrimal-gland", "Lacrimal gland") },
    { id: "lacrimal-drainage-right", label: "Right puncta, sac and nasolacrimal duct", group: "right lacrimal apparatus", svgPath: "M100 92 h16 v18 h-16 Z", laterality: "right", locationCode: unmapped("lacrimal-drainage", "Lacrimal drainage system") },
    { id: "upper-lid-left", label: "Left upper eyelid", group: "left adnexa", svgPath: "M134 44 h84 v16 h-84 Z", laterality: "left", locationCode: unmapped("upper-eyelid", "Upper eyelid") },
    { id: "lower-lid-left", label: "Left lower eyelid", group: "left adnexa", svgPath: "M134 100 h84 v16 h-84 Z", laterality: "left", locationCode: unmapped("lower-eyelid", "Lower eyelid") },
    { id: "medial-canthus-left", label: "Left medial canthus", group: "left adnexa", svgPath: "M122 70 h14 v20 h-14 Z", laterality: "left", locationCode: unmapped("medial-canthus", "Medial canthus") },
    { id: "lateral-canthus-left", label: "Left lateral canthus", group: "left adnexa", svgPath: "M214 70 h14 v20 h-14 Z", laterality: "left", locationCode: unmapped("lateral-canthus", "Lateral canthus") },
    { id: "conjunctiva-left", label: "Left bulbar conjunctiva", group: "left ocular surface", svgPath: "M138 62 h76 v36 h-76 Z", laterality: "left", locationCode: code("81745001", "Eye") },
    { id: "cornea-left", label: "Left cornea", group: "left ocular surface", svgPath: "M164 68 h24 v24 h-24 Z", laterality: "left", locationCode: unmapped("cornea", "Cornea") },
    { id: "sclera-superonasal-left", label: "Left sclera, superonasal", group: "left sclera", svgPath: "M140 64 h24 v16 h-24 Z", laterality: "left", locationCode: unmapped("sclera", "Sclera") },
    { id: "sclera-superotemporal-left", label: "Left sclera, superotemporal", group: "left sclera", svgPath: "M188 64 h24 v16 h-24 Z", laterality: "left", locationCode: unmapped("sclera", "Sclera") },
    { id: "sclera-inferonasal-left", label: "Left sclera, inferonasal", group: "left sclera", svgPath: "M140 82 h24 v16 h-24 Z", laterality: "left", locationCode: unmapped("sclera", "Sclera") },
    { id: "sclera-inferotemporal-left", label: "Left sclera, inferotemporal", group: "left sclera", svgPath: "M188 82 h24 v16 h-24 Z", laterality: "left", locationCode: unmapped("sclera", "Sclera") },
    { id: "lacrimal-gland-left", label: "Left lacrimal gland", group: "left lacrimal apparatus", svgPath: "M200 46 h20 v14 h-20 Z", laterality: "left", locationCode: unmapped("lacrimal-gland", "Lacrimal gland") },
    { id: "lacrimal-drainage-left", label: "Left puncta, sac and nasolacrimal duct", group: "left lacrimal apparatus", svgPath: "M124 92 h16 v18 h-16 Z", laterality: "left", locationCode: unmapped("lacrimal-drainage", "Lacrimal drainage system") },
  ],
};

/**
 * FDI tooth numbering, generated rather than hand-listed.
 *
 * Thirty-two literal rows would be thirty-two chances to mistype a tooth number, and the
 * numbering is a rule (quadrant × position), not a set of facts. Quadrants 1 and 4 are the
 * patient's right; 2 and 3 the patient's left.
 */
const UPPER_ARCH_FDI = [18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28];
const LOWER_ARCH_FDI = [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38];

const toothRegion = (fdi: number, index: number, y: number): BodyRegionDef => {
  const quadrant = Math.floor(fdi / 10);
  const x = (20 + index * 12.5).toFixed(1);
  return {
    id: `tooth-${fdi}`,
    label: `Tooth ${fdi} (FDI)`,
    group: `FDI quadrant ${quadrant}`,
    svgPath: `M${x} ${y} h11 v26 h-11 Z`,
    laterality: quadrant === 1 || quadrant === 4 ? "right" : "left",
    // Tooth identity is carried by the FDI number itself; a SNOMED per-tooth binding was not
    // verified, so the number is not dressed up as one.
    locationCode: unmapped(`fdi-${fdi}`, `Tooth ${fdi} (FDI notation)`),
  };
};

/** Oral cavity and dentition: FDI teeth plus the soft-tissue sites an oral surgeon records. */
export const ORAL_DENTAL_MAP: RegionMapDef = {
  id: "oral-dental",
  title: "Oral cavity and dentition (FDI)",
  viewBox: "0 0 240 260",
  silhouettePaths: [
    "M20 30 h200 v34 h-200 Z",
    "M20 212 h200 v34 h-200 Z",
    "M64 64 h112 v148 h-112 Z",
  ],
  regions: [
    ...UPPER_ARCH_FDI.map((fdi, index) => toothRegion(fdi, index, 34)),
    ...LOWER_ARCH_FDI.map((fdi, index) => toothRegion(fdi, index, 216)),
    { id: "hard-palate", label: "Hard palate", group: "palate", svgPath: "M64 64 h112 v26 h-112 Z", laterality: "midline", locationCode: code("21082005", "Palate") },
    { id: "soft-palate", label: "Soft palate", group: "palate", svgPath: "M78 92 h84 v20 h-84 Z", laterality: "midline", locationCode: code("21082005", "Palate") },
    { id: "tonsil-right", label: "Right tonsil", group: "oropharynx", svgPath: "M50 94 h24 v26 h-24 Z", laterality: "right", locationCode: unmapped("palatine-tonsil", "Palatine tonsil") },
    { id: "tonsil-left", label: "Left tonsil", group: "oropharynx", svgPath: "M166 94 h24 v26 h-24 Z", laterality: "left", locationCode: unmapped("palatine-tonsil", "Palatine tonsil") },
    { id: "tongue-anterior-third", label: "Tongue, anterior third", group: "tongue", svgPath: "M78 118 h84 v24 h-84 Z", laterality: "midline", locationCode: code("21974007", "Tongue") },
    { id: "tongue-middle-third", label: "Tongue, middle third", group: "tongue", svgPath: "M78 142 h84 v24 h-84 Z", laterality: "midline", locationCode: code("21974007", "Tongue") },
    { id: "tongue-posterior-third", label: "Tongue, posterior third", group: "tongue", svgPath: "M78 166 h84 v24 h-84 Z", laterality: "midline", locationCode: code("21974007", "Tongue") },
    { id: "floor-of-mouth", label: "Floor of mouth", group: "oral cavity", svgPath: "M64 192 h112 v20 h-112 Z", laterality: "midline", locationCode: code("74262004", "Oral cavity") },
  ],
};

/**
 * Cranial and neurosurgical map: lobe projections onto a vertex view, plus the standard entry
 * points. Burr-hole and shunt sites are procedure sites rather than body structures, so they
 * are never given a body-structure code they do not have.
 */
export const CRANIAL_NEURO_MAP: RegionMapDef = {
  id: "cranial-neuro",
  title: "Cranial lobes and neurosurgical entry sites",
  viewBox: "0 0 200 300",
  silhouettePaths: [
    "M100 20 a70 90 0 0 1 0 180 a70 90 0 0 1 0 -180 Z",
    "M100 20 v180",
    "M100 198 a52 40 0 0 1 0 80 a52 40 0 0 1 0 -80 Z",
  ],
  regions: [
    { id: "frontal-lobe-right", label: "Right frontal lobe (projection)", group: "lobes", svgPath: "M58 34 h40 v56 h-40 Z", laterality: "right", locationCode: code("12738006", "Brain") },
    { id: "frontal-lobe-left", label: "Left frontal lobe (projection)", group: "lobes", svgPath: "M102 34 h40 v56 h-40 Z", laterality: "left", locationCode: code("12738006", "Brain") },
    { id: "parietal-lobe-right", label: "Right parietal lobe (projection)", group: "lobes", svgPath: "M60 94 h38 v58 h-38 Z", laterality: "right", locationCode: code("12738006", "Brain") },
    { id: "parietal-lobe-left", label: "Left parietal lobe (projection)", group: "lobes", svgPath: "M102 94 h38 v58 h-38 Z", laterality: "left", locationCode: code("12738006", "Brain") },
    { id: "temporal-lobe-right", label: "Right temporal lobe (projection)", group: "lobes", svgPath: "M32 94 h26 v56 h-26 Z", laterality: "right", locationCode: code("12738006", "Brain") },
    { id: "temporal-lobe-left", label: "Left temporal lobe (projection)", group: "lobes", svgPath: "M142 94 h26 v56 h-26 Z", laterality: "left", locationCode: code("12738006", "Brain") },
    { id: "occipital-lobe-right", label: "Right occipital lobe (projection)", group: "lobes", svgPath: "M60 156 h38 v40 h-38 Z", laterality: "right", locationCode: code("12738006", "Brain") },
    { id: "occipital-lobe-left", label: "Left occipital lobe (projection)", group: "lobes", svgPath: "M102 156 h38 v40 h-38 Z", laterality: "left", locationCode: code("12738006", "Brain") },
    { id: "posterior-fossa-neuro", label: "Posterior fossa (cerebellum)", group: "posterior fossa", svgPath: "M62 202 h76 v34 h-76 Z", laterality: "midline", locationCode: unmapped("posterior-cranial-fossa", "Posterior cranial fossa") },
    { id: "burr-hole-kocher-right", label: "Right Kocher's point", group: "burr-hole sites", svgPath: "M74 40 h16 v16 h-16 Z", laterality: "right", locationCode: unmapped("kocher-point", "Kocher's point (frontal burr hole site)") },
    { id: "burr-hole-kocher-left", label: "Left Kocher's point", group: "burr-hole sites", svgPath: "M110 40 h16 v16 h-16 Z", laterality: "left", locationCode: unmapped("kocher-point", "Kocher's point (frontal burr hole site)") },
    { id: "burr-hole-keen-right", label: "Right Keen's point", group: "burr-hole sites", svgPath: "M64 110 h16 v16 h-16 Z", laterality: "right", locationCode: unmapped("keen-point", "Keen's point (parietal burr hole site)") },
    { id: "burr-hole-keen-left", label: "Left Keen's point", group: "burr-hole sites", svgPath: "M120 110 h16 v16 h-16 Z", laterality: "left", locationCode: unmapped("keen-point", "Keen's point (parietal burr hole site)") },
    { id: "burr-hole-frazier-right", label: "Right Frazier's point", group: "burr-hole sites", svgPath: "M70 168 h16 v16 h-16 Z", laterality: "right", locationCode: unmapped("frazier-point", "Frazier's point (parieto-occipital burr hole site)") },
    { id: "burr-hole-frazier-left", label: "Left Frazier's point", group: "burr-hole sites", svgPath: "M114 168 h16 v16 h-16 Z", laterality: "left", locationCode: unmapped("frazier-point", "Frazier's point (parieto-occipital burr hole site)") },
    { id: "shunt-valve-site-right", label: "Right retroauricular shunt valve site", group: "shunt sites", svgPath: "M36 156 h20 v18 h-20 Z", laterality: "right", locationCode: unmapped("shunt-valve-site", "Retroauricular shunt valve site") },
    { id: "shunt-valve-site-left", label: "Left retroauricular shunt valve site", group: "shunt sites", svgPath: "M144 156 h20 v18 h-20 Z", laterality: "left", locationCode: unmapped("shunt-valve-site", "Retroauricular shunt valve site") },
  ],
};

/** Every head-and-neck surgical map in this module, for registration and tests. */
export const SURGICAL_HEAD_NECK_MAPS: RegionMapDef[] = [
  SCALP_SKULL_MAP,
  FACE_MAXILLOFACIAL_MAP,
  NECK_TRIANGLES_MAP,
  THYROID_PARATHYROID_MAP,
  EAR_MAP,
  EYE_ORBIT_MAP,
  ORAL_DENTAL_MAP,
  CRANIAL_NEURO_MAP,
];
