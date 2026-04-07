// Comprehensive Clerking Templates by Specialty and Cadre Level

export type CadreLevel = 'student' | 'intern' | 'registrar' | 'consultant';
export type Specialty =
  | 'general-medicine'
  | 'surgery'
  | 'obstetrics-gynecology'
  | 'pediatrics'
  | 'psychiatry'
  | 'emergency'
  | 'orthopedics'
  | 'cardiology'
  | 'neurology';

export interface ClerkingField {
  id: string;
  label: string;
  type: 'text' | 'textarea' | 'checkbox' | 'select' | 'number' | 'date' | 'checkbox-group';
  options?: string[];
  placeholder?: string;
  required?: boolean;
  defaultValue?: string | string[] | boolean;
  cadreLevel?: CadreLevel[];
}

export interface ClerkingSection {
  id: string;
  title: string;
  icon: string;
  fields: ClerkingField[];
  cadreLevel?: CadreLevel[];
}

export interface ClerkingTemplate {
  id: string;
  specialty: Specialty;
  name: string;
  description: string;
  sections: ClerkingSection[];
}

// Review of Systems checklist
const REVIEW_OF_SYSTEMS: ClerkingField[] = [
  { id: 'ros_general', label: 'General', type: 'checkbox-group', options: ['Fever', 'Weight loss', 'Fatigue', 'Night sweats', 'Appetite change'] },
  { id: 'ros_heent', label: 'HEENT', type: 'checkbox-group', options: ['Headache', 'Vision changes', 'Hearing loss', 'Sore throat', 'Nasal congestion'] },
  { id: 'ros_cardiovascular', label: 'Cardiovascular', type: 'checkbox-group', options: ['Chest pain', 'Palpitations', 'Orthopnea', 'PND', 'Leg swelling'] },
  { id: 'ros_respiratory', label: 'Respiratory', type: 'checkbox-group', options: ['Cough', 'Dyspnea', 'Wheezing', 'Hemoptysis', 'Pleuritic pain'] },
  { id: 'ros_gi', label: 'Gastrointestinal', type: 'checkbox-group', options: ['Nausea', 'Vomiting', 'Diarrhea', 'Constipation', 'Abdominal pain', 'Melena', 'Hematemesis'] },
  { id: 'ros_gu', label: 'Genitourinary', type: 'checkbox-group', options: ['Dysuria', 'Frequency', 'Urgency', 'Hematuria', 'Incontinence'] },
  { id: 'ros_msk', label: 'Musculoskeletal', type: 'checkbox-group', options: ['Joint pain', 'Muscle weakness', 'Back pain', 'Stiffness', 'Swelling'] },
  { id: 'ros_neuro', label: 'Neurological', type: 'checkbox-group', options: ['Numbness', 'Tingling', 'Weakness', 'Seizures', 'Dizziness', 'Syncope'] },
  { id: 'ros_psych', label: 'Psychiatric', type: 'checkbox-group', options: ['Depression', 'Anxiety', 'Sleep disturbance', 'Mood changes'] },
  { id: 'ros_skin', label: 'Skin', type: 'checkbox-group', options: ['Rash', 'Pruritus', 'Lesions', 'Color changes', 'Hair loss'] },
];

// Common sections shared across specialties
const BIODATA_SECTION: ClerkingSection = {
  id: 'biodata',
  title: 'Patient Biodata',
  icon: 'User',
  fields: [
    { id: 'informant', label: 'Informant', type: 'text', placeholder: 'Patient/Relative/Other' },
    { id: 'reliability', label: 'Reliability of History', type: 'select', options: ['Reliable', 'Fairly reliable', 'Unreliable'] },
    { id: 'occupation', label: 'Occupation', type: 'text' },
    { id: 'marital_status', label: 'Marital Status', type: 'select', options: ['Single', 'Married', 'Divorced', 'Widowed'] },
    { id: 'religion', label: 'Religion', type: 'text' },
    { id: 'residence', label: 'Residence', type: 'text' },
  ]
};

const PRESENTING_COMPLAINT: ClerkingSection = {
  id: 'presenting_complaint',
  title: 'Presenting Complaint',
  icon: 'MessageSquare',
  fields: [
    { id: 'chief_complaint', label: 'Chief Complaint', type: 'textarea', placeholder: 'Main complaint(s) with duration', required: true },
  ]
};

const HPI_SECTION: ClerkingSection = {
  id: 'hpi',
  title: 'History of Present Illness',
  icon: 'FileText',
  fields: [
    { id: 'hpi_narrative', label: 'Narrative', type: 'textarea', placeholder: 'Chronological account of symptoms...', required: true },
    { id: 'onset', label: 'Onset', type: 'text', placeholder: 'When and how symptoms started' },
    { id: 'character', label: 'Character/Nature', type: 'text', placeholder: 'Describe the nature of symptoms' },
    { id: 'radiation', label: 'Radiation', type: 'text', placeholder: 'If pain, does it radiate?' },
    { id: 'associations', label: 'Associated Symptoms', type: 'textarea', placeholder: 'Other associated symptoms' },
    { id: 'timing', label: 'Timing/Duration', type: 'text', placeholder: 'Duration, frequency, pattern' },
    { id: 'exacerbating', label: 'Exacerbating Factors', type: 'text', placeholder: 'What makes it worse?' },
    { id: 'relieving', label: 'Relieving Factors', type: 'text', placeholder: 'What makes it better?' },
    { id: 'severity', label: 'Severity (1-10)', type: 'number', placeholder: '1-10 scale' },
    { id: 'previous_episodes', label: 'Previous Similar Episodes', type: 'textarea' },
    { id: 'treatment_tried', label: 'Treatment Tried', type: 'textarea' },
  ]
};

const PMH_SECTION: ClerkingSection = {
  id: 'pmh',
  title: 'Past Medical History',
  icon: 'History',
  fields: [
    { id: 'pmh_conditions', label: 'Known Medical Conditions', type: 'textarea', placeholder: 'List chronic conditions with dates of diagnosis...' },
    { id: 'pmh_hospitalizations', label: 'Previous Hospitalizations', type: 'textarea', placeholder: 'List previous admissions with reasons and dates...' },
    { id: 'pmh_surgeries', label: 'Previous Surgeries', type: 'textarea', placeholder: 'List surgical procedures with dates...' },
    { id: 'pmh_transfusions', label: 'Blood Transfusions', type: 'textarea', placeholder: 'Any previous transfusions and reactions...' },
    { id: 'pmh_tb', label: 'TB History', type: 'select', options: ['None', 'Previous TB - Completed treatment', 'Current on treatment', 'Unknown'] },
    { id: 'pmh_hiv', label: 'HIV Status', type: 'select', options: ['Negative', 'Positive - On ART', 'Positive - Not on ART', 'Unknown'] },
    { id: 'pmh_diabetes', label: 'Diabetes', type: 'checkbox', defaultValue: false },
    { id: 'pmh_hypertension', label: 'Hypertension', type: 'checkbox', defaultValue: false },
    { id: 'pmh_cardiac', label: 'Cardiac Disease', type: 'checkbox', defaultValue: false },
    { id: 'pmh_asthma', label: 'Asthma/COPD', type: 'checkbox', defaultValue: false },
    { id: 'pmh_epilepsy', label: 'Epilepsy', type: 'checkbox', defaultValue: false },
  ]
};

const DRUG_HISTORY: ClerkingSection = {
  id: 'drug_history',
  title: 'Drug History',
  icon: 'Pill',
  fields: [
    { id: 'current_medications', label: 'Current Medications', type: 'textarea', placeholder: 'List all current medications with doses and frequency...' },
    { id: 'allergies', label: 'Drug Allergies', type: 'textarea', placeholder: 'List allergies and type of reaction...', required: true },
    { id: 'traditional_medicine', label: 'Traditional/Herbal Medicine', type: 'textarea', placeholder: 'Any traditional medicine use...' },
    { id: 'otc_medications', label: 'OTC Medications', type: 'textarea', placeholder: 'Over-the-counter medications...' },
  ]
};

const FAMILY_HISTORY: ClerkingSection = {
  id: 'family_history',
  title: 'Family History',
  icon: 'Users',
  fields: [
    { id: 'fhx_parents', label: 'Parents Health', type: 'textarea', placeholder: 'Health status of parents, if deceased cause of death...' },
    { id: 'fhx_siblings', label: 'Siblings Health', type: 'textarea', placeholder: 'Health status of siblings...' },
    { id: 'fhx_diabetes', label: 'Family History of Diabetes', type: 'checkbox', defaultValue: false },
    { id: 'fhx_hypertension', label: 'Family History of Hypertension', type: 'checkbox', defaultValue: false },
    { id: 'fhx_cardiac', label: 'Family History of Heart Disease', type: 'checkbox', defaultValue: false },
    { id: 'fhx_cancer', label: 'Family History of Cancer', type: 'checkbox', defaultValue: false },
    { id: 'fhx_tb', label: 'Family History of TB', type: 'checkbox', defaultValue: false },
    { id: 'fhx_mental', label: 'Family History of Mental Illness', type: 'checkbox', defaultValue: false },
    { id: 'fhx_other', label: 'Other Hereditary Conditions', type: 'textarea' },
  ]
};

const SOCIAL_HISTORY: ClerkingSection = {
  id: 'social_history',
  title: 'Social History',
  icon: 'Home',
  fields: [
    { id: 'occupation_details', label: 'Occupation & Work Environment', type: 'textarea', placeholder: 'Current and previous occupations, hazards...' },
    { id: 'smoking', label: 'Smoking', type: 'select', options: ['Never smoked', 'Ex-smoker', 'Current smoker'] },
    { id: 'smoking_pack_years', label: 'Pack Years (if applicable)', type: 'number' },
    { id: 'alcohol', label: 'Alcohol Use', type: 'select', options: ['None', 'Occasional', 'Moderate', 'Heavy', 'Previous heavy use'] },
    { id: 'alcohol_details', label: 'Alcohol Details', type: 'text', placeholder: 'Type, amount, frequency...' },
    { id: 'recreational_drugs', label: 'Recreational Drug Use', type: 'text' },
    { id: 'exercise', label: 'Exercise/Physical Activity', type: 'select', options: ['Sedentary', 'Light', 'Moderate', 'Active'] },
    { id: 'diet', label: 'Diet', type: 'text', placeholder: 'Dietary habits...' },
    { id: 'living_situation', label: 'Living Situation', type: 'textarea', placeholder: 'Who they live with, housing conditions...' },
    { id: 'sexual_history', label: 'Sexual History', type: 'textarea', placeholder: 'Partners, contraception, STIs...', cadreLevel: ['registrar', 'consultant'] },
  ]
};

const REVIEW_OF_SYSTEMS_SECTION: ClerkingSection = {
  id: 'ros',
  title: 'Review of Systems',
  icon: 'ClipboardList',
  fields: REVIEW_OF_SYSTEMS
};

// General Physical Examination
const GENERAL_EXAMINATION: ClerkingSection = {
  id: 'general_exam',
  title: 'General Examination',
  icon: 'Stethoscope',
  fields: [
    { id: 'general_appearance', label: 'General Appearance', type: 'textarea', placeholder: 'Alert, comfortable, distressed, wasted, etc...' },
    { id: 'consciousness', label: 'Level of Consciousness', type: 'select', options: ['Alert', 'Drowsy', 'Confused', 'Obtunded', 'Comatose'] },
    { id: 'gcs', label: 'GCS (if applicable)', type: 'text', placeholder: 'E_V_M_' },
    { id: 'pallor', label: 'Pallor', type: 'select', options: ['Absent', 'Mild', 'Moderate', 'Severe'] },
    { id: 'jaundice', label: 'Jaundice', type: 'select', options: ['Absent', 'Present'] },
    { id: 'cyanosis', label: 'Cyanosis', type: 'select', options: ['Absent', 'Peripheral', 'Central'] },
    { id: 'clubbing', label: 'Clubbing', type: 'select', options: ['Absent', 'Present'] },
    { id: 'edema', label: 'Edema', type: 'textarea', placeholder: 'Location, pitting, grade...' },
    { id: 'lymphadenopathy', label: 'Lymphadenopathy', type: 'textarea', placeholder: 'Location, size, consistency...' },
    { id: 'dehydration', label: 'Dehydration', type: 'select', options: ['None', 'Mild', 'Moderate', 'Severe'] },
    { id: 'nutritional_status', label: 'Nutritional Status', type: 'select', options: ['Well nourished', 'Mildly malnourished', 'Moderately malnourished', 'Severely malnourished'] },
  ]
};

const CVS_EXAMINATION: ClerkingSection = {
  id: 'cvs_exam',
  title: 'Cardiovascular Examination',
  icon: 'Heart',
  fields: [
    { id: 'cvs_pulse', label: 'Pulse Rate/Rhythm/Volume/Character', type: 'text' },
    { id: 'cvs_bp', label: 'Blood Pressure', type: 'text' },
    { id: 'cvs_jvp', label: 'JVP', type: 'text', placeholder: 'Normal/Elevated/Not visible' },
    { id: 'cvs_apex', label: 'Apex Beat', type: 'text', placeholder: 'Location, character...' },
    { id: 'cvs_thrills', label: 'Thrills', type: 'text' },
    { id: 'cvs_heart_sounds', label: 'Heart Sounds', type: 'textarea', placeholder: 'S1, S2, added sounds...' },
    { id: 'cvs_murmurs', label: 'Murmurs', type: 'textarea', placeholder: 'Timing, location, radiation, grade...' },
    { id: 'cvs_peripheral_pulses', label: 'Peripheral Pulses', type: 'textarea' },
    { id: 'cvs_capillary_refill', label: 'Capillary Refill Time', type: 'text' },
  ]
};

const RESPIRATORY_EXAMINATION: ClerkingSection = {
  id: 'resp_exam',
  title: 'Respiratory Examination',
  icon: 'Wind',
  fields: [
    { id: 'resp_rate', label: 'Respiratory Rate', type: 'number' },
    { id: 'resp_pattern', label: 'Breathing Pattern', type: 'select', options: ['Normal', 'Tachypnea', 'Bradypnea', 'Kussmaul', 'Cheyne-Stokes', 'Apneic spells'] },
    { id: 'resp_chest_shape', label: 'Chest Shape/Symmetry', type: 'text' },
    { id: 'resp_chest_expansion', label: 'Chest Expansion', type: 'text' },
    { id: 'resp_trachea', label: 'Trachea Position', type: 'select', options: ['Central', 'Deviated left', 'Deviated right'] },
    { id: 'resp_percussion', label: 'Percussion Note', type: 'textarea', placeholder: 'Areas of dullness, hyperresonance...' },
    { id: 'resp_breath_sounds', label: 'Breath Sounds', type: 'textarea', placeholder: 'Vesicular, bronchial, reduced...' },
    { id: 'resp_added_sounds', label: 'Added Sounds', type: 'textarea', placeholder: 'Crackles, wheeze, pleural rub...' },
    { id: 'resp_vocal_resonance', label: 'Vocal Resonance', type: 'textarea' },
    { id: 'resp_spo2', label: 'SpO2', type: 'text' },
  ]
};

const ABDOMINAL_EXAMINATION: ClerkingSection = {
  id: 'abdo_exam',
  title: 'Abdominal Examination',
  icon: 'Activity',
  fields: [
    { id: 'abdo_inspection', label: 'Inspection', type: 'textarea', placeholder: 'Distension, scars, masses, peristalsis...' },
    { id: 'abdo_tenderness', label: 'Tenderness', type: 'textarea', placeholder: 'Location, guarding, rebound...' },
    { id: 'abdo_liver', label: 'Liver', type: 'textarea', placeholder: 'Size, consistency, tenderness...' },
    { id: 'abdo_spleen', label: 'Spleen', type: 'text', placeholder: 'Palpable/Not palpable' },
    { id: 'abdo_kidneys', label: 'Kidneys', type: 'text', placeholder: 'Ballotable/Not ballotable' },
    { id: 'abdo_masses', label: 'Masses', type: 'textarea' },
    { id: 'abdo_bowel_sounds', label: 'Bowel Sounds', type: 'select', options: ['Normal', 'Hyperactive', 'Hypoactive', 'Absent'] },
    { id: 'abdo_ascites', label: 'Ascites', type: 'select', options: ['Absent', 'Mild', 'Moderate', 'Tense'] },
    { id: 'abdo_rectal_exam', label: 'Rectal Examination', type: 'textarea', placeholder: 'Findings if performed...', cadreLevel: ['registrar', 'consultant'] },
  ]
};

const NEURO_EXAMINATION: ClerkingSection = {
  id: 'neuro_exam',
  title: 'Neurological Examination',
  icon: 'Brain',
  fields: [
    { id: 'neuro_mental_status', label: 'Mental Status', type: 'textarea', placeholder: 'Orientation, memory, cognition...' },
    { id: 'neuro_cranial_nerves', label: 'Cranial Nerves', type: 'textarea', placeholder: 'CN I-XII findings...' },
    { id: 'neuro_motor_upper', label: 'Upper Limb Motor', type: 'textarea', placeholder: 'Tone, power, reflexes...' },
    { id: 'neuro_motor_lower', label: 'Lower Limb Motor', type: 'textarea', placeholder: 'Tone, power, reflexes...' },
    { id: 'neuro_sensory', label: 'Sensory', type: 'textarea', placeholder: 'Light touch, pain, vibration, proprioception...' },
    { id: 'neuro_coordination', label: 'Coordination', type: 'textarea', placeholder: 'Finger-nose, heel-shin, dysdiadochokinesia...' },
    { id: 'neuro_gait', label: 'Gait', type: 'textarea', placeholder: 'Normal, ataxic, shuffling, hemiplegic...' },
    { id: 'neuro_meningism', label: 'Signs of Meningism', type: 'textarea', placeholder: 'Neck stiffness, Kernig, Brudzinski...' },
  ]
};

// Specialty-specific sections
const OBS_HISTORY: ClerkingSection = {
  id: 'obs_history',
  title: 'Obstetric History',
  icon: 'Baby',
  fields: [
    { id: 'lmp', label: 'LMP', type: 'date', required: true },
    { id: 'edd', label: 'EDD', type: 'date' },
    { id: 'gestational_age', label: 'Gestational Age', type: 'text' },
    { id: 'gravidity', label: 'Gravidity (G)', type: 'number' },
    { id: 'parity', label: 'Parity (P)', type: 'text', placeholder: 'e.g., 2+1 (term + preterm + abortions + living)' },
    { id: 'previous_pregnancies', label: 'Previous Pregnancies Details', type: 'textarea', placeholder: 'Year, mode of delivery, outcome, complications...' },
    { id: 'current_pregnancy', label: 'Current Pregnancy', type: 'textarea', placeholder: 'ANC visits, complications, investigations...' },
    { id: 'fetal_movements', label: 'Fetal Movements', type: 'select', options: ['Present and normal', 'Decreased', 'Absent'] },
    { id: 'bleeding_pv', label: 'Bleeding PV', type: 'text' },
    { id: 'discharge_pv', label: 'Discharge PV', type: 'text' },
    { id: 'contractions', label: 'Contractions', type: 'text' },
  ]
};

const OBS_EXAMINATION: ClerkingSection = {
  id: 'obs_exam',
  title: 'Obstetric Examination',
  icon: 'Baby',
  fields: [
    { id: 'fundal_height', label: 'Fundal Height', type: 'text' },
    { id: 'lie', label: 'Lie', type: 'select', options: ['Longitudinal', 'Oblique', 'Transverse'] },
    { id: 'presentation', label: 'Presentation', type: 'select', options: ['Cephalic', 'Breech', 'Shoulder', 'Undetermined'] },
    { id: 'position', label: 'Position', type: 'text', placeholder: 'LOA, ROA, etc...' },
    { id: 'engagement', label: 'Engagement (5ths palpable)', type: 'text' },
    { id: 'fetal_heart', label: 'Fetal Heart Rate', type: 'text' },
    { id: 'contractions_exam', label: 'Contractions (freq/duration)', type: 'text' },
    { id: 'vaginal_exam', label: 'Vaginal Examination', type: 'textarea', placeholder: 'Cervix dilatation, effacement, station, membranes...', cadreLevel: ['registrar', 'consultant'] },
  ]
};

const GYNAE_HISTORY: ClerkingSection = {
  id: 'gynae_history',
  title: 'Gynaecological History',
  icon: 'HeartPulse',
  fields: [
    { id: 'menstrual_lmp', label: 'LMP', type: 'date' },
    { id: 'menstrual_cycle', label: 'Cycle Length', type: 'text', placeholder: 'e.g., 28 days' },
    { id: 'menstrual_duration', label: 'Duration of Flow', type: 'text', placeholder: 'e.g., 5 days' },
    { id: 'menstrual_regularity', label: 'Regularity', type: 'select', options: ['Regular', 'Irregular'] },
    { id: 'menstrual_flow', label: 'Flow', type: 'select', options: ['Light', 'Normal', 'Heavy'] },
    { id: 'dysmenorrhea', label: 'Dysmenorrhea', type: 'select', options: ['None', 'Mild', 'Moderate', 'Severe'] },
    { id: 'intermenstrual_bleeding', label: 'Intermenstrual Bleeding', type: 'text' },
    { id: 'postcoital_bleeding', label: 'Postcoital Bleeding', type: 'text' },
    { id: 'menopause', label: 'Menopausal Status', type: 'select', options: ['Premenopausal', 'Perimenopausal', 'Postmenopausal'] },
    { id: 'contraception', label: 'Contraception', type: 'text' },
    { id: 'pap_smear', label: 'Last Pap Smear', type: 'text' },
    { id: 'sexual_history_gynae', label: 'Sexual History', type: 'textarea' },
  ]
};

const PEDIATRIC_HISTORY: ClerkingSection = {
  id: 'peds_history',
  title: 'Pediatric History',
  icon: 'Baby',
  fields: [
    { id: 'birth_history', label: 'Birth History', type: 'textarea', placeholder: 'Gestation, mode of delivery, birth weight, APGAR, complications...' },
    { id: 'neonatal_period', label: 'Neonatal Period', type: 'textarea', placeholder: 'NICU admission, jaundice, feeding problems...' },
    { id: 'feeding_history', label: 'Feeding History', type: 'textarea', placeholder: 'Breastfeeding, formula, weaning, current diet...' },
    { id: 'developmental_milestones', label: 'Developmental Milestones', type: 'textarea', placeholder: 'Gross motor, fine motor, language, social...' },
    { id: 'immunization_status', label: 'Immunization Status', type: 'textarea', placeholder: 'Up to date? Which vaccines received?' },
    { id: 'growth_parameters', label: 'Growth Parameters', type: 'text', placeholder: 'Weight/Height/Head circumference percentiles...' },
    { id: 'school_performance', label: 'School Performance', type: 'text' },
    { id: 'behavior', label: 'Behavior', type: 'textarea' },
  ]
};

const PEDIATRIC_EXAMINATION: ClerkingSection = {
  id: 'peds_exam',
  title: 'Pediatric Examination',
  icon: 'Baby',
  fields: [
    { id: 'peds_weight', label: 'Weight (kg)', type: 'number' },
    { id: 'peds_height', label: 'Height (cm)', type: 'number' },
    { id: 'peds_head_circ', label: 'Head Circumference (cm)', type: 'number' },
    { id: 'peds_muac', label: 'MUAC (cm)', type: 'number' },
    { id: 'peds_fontanelle', label: 'Anterior Fontanelle', type: 'select', options: ['Closed', 'Open and flat', 'Bulging', 'Sunken'] },
    { id: 'peds_hydration', label: 'Hydration Status', type: 'textarea' },
    { id: 'peds_activity', label: 'Activity Level', type: 'select', options: ['Alert and active', 'Irritable', 'Lethargic', 'Unresponsive'] },
    { id: 'peds_cry', label: 'Cry', type: 'select', options: ['Normal', 'Weak', 'High-pitched', 'Absent'] },
    { id: 'peds_developmental_exam', label: 'Developmental Assessment', type: 'textarea' },
  ]
};

const SURGICAL_HISTORY: ClerkingSection = {
  id: 'surgical_history',
  title: 'Surgical History',
  icon: 'Scissors',
  fields: [
    { id: 'pre_op_diagnosis', label: 'Pre-operative Diagnosis', type: 'textarea' },
    { id: 'previous_surgeries', label: 'Previous Surgeries', type: 'textarea', placeholder: 'Surgery, date, complications, anesthesia type...' },
    { id: 'anesthesia_history', label: 'Anesthesia History', type: 'textarea', placeholder: 'Previous anesthesia, complications, family history of malignant hyperthermia...' },
    { id: 'fasting_status', label: 'Fasting Status', type: 'text', placeholder: 'Last meal/drink...' },
    { id: 'anticoagulation', label: 'Anticoagulation', type: 'textarea', placeholder: 'Medications, when last taken...' },
    { id: 'implants', label: 'Implants/Prostheses', type: 'textarea' },
  ]
};

const MSK_EXAMINATION: ClerkingSection = {
  id: 'msk_exam',
  title: 'Musculoskeletal Examination',
  icon: 'Bone',
  fields: [
    { id: 'msk_inspection', label: 'Inspection', type: 'textarea', placeholder: 'Swelling, deformity, wasting, skin changes...' },
    { id: 'msk_palpation', label: 'Palpation', type: 'textarea', placeholder: 'Tenderness, warmth, crepitus...' },
    { id: 'msk_rom', label: 'Range of Motion', type: 'textarea', placeholder: 'Active and passive ROM...' },
    { id: 'msk_power', label: 'Power', type: 'textarea', placeholder: 'MRC grading for relevant muscle groups...' },
    { id: 'msk_stability', label: 'Stability', type: 'textarea', placeholder: 'Joint stability tests...' },
    { id: 'msk_special_tests', label: 'Special Tests', type: 'textarea', placeholder: 'Specific tests performed and findings...' },
    { id: 'msk_neurovascular', label: 'Neurovascular Status', type: 'textarea', placeholder: 'Distal pulses, sensation, motor function...' },
  ]
};

const PSYCHIATRIC_HISTORY: ClerkingSection = {
  id: 'psych_history',
  title: 'Psychiatric History',
  icon: 'Brain',
  fields: [
    { id: 'psych_presenting', label: 'Presenting Complaint', type: 'textarea' },
    { id: 'psych_onset', label: 'Onset & Course', type: 'textarea' },
    { id: 'psych_precipitants', label: 'Precipitating Factors', type: 'textarea' },
    { id: 'psych_previous_episodes', label: 'Previous Psychiatric History', type: 'textarea' },
    { id: 'psych_admissions', label: 'Previous Psychiatric Admissions', type: 'textarea' },
    { id: 'psych_medications', label: 'Previous/Current Psychiatric Medications', type: 'textarea' },
    { id: 'psych_substance', label: 'Substance Use History', type: 'textarea', placeholder: 'Detailed substance use...' },
    { id: 'psych_forensic', label: 'Forensic History', type: 'textarea' },
    { id: 'psych_personality', label: 'Premorbid Personality', type: 'textarea' },
    { id: 'psych_risk', label: 'Risk Assessment', type: 'textarea', placeholder: 'Suicidal ideation, self-harm, harm to others...', required: true },
  ]
};

const MENTAL_STATE_EXAM: ClerkingSection = {
  id: 'mse',
  title: 'Mental State Examination',
  icon: 'Brain',
  fields: [
    { id: 'mse_appearance', label: 'Appearance & Behavior', type: 'textarea', placeholder: 'Dress, grooming, posture, eye contact, motor activity...' },
    { id: 'mse_speech', label: 'Speech', type: 'textarea', placeholder: 'Rate, rhythm, volume, tone...' },
    { id: 'mse_mood', label: 'Mood (Subjective)', type: 'text', placeholder: 'In patient\'s own words...' },
    { id: 'mse_affect', label: 'Affect (Objective)', type: 'textarea', placeholder: 'Type, range, congruence, reactivity...' },
    { id: 'mse_thought_form', label: 'Thought Form', type: 'textarea', placeholder: 'Tangential, circumstantial, flight of ideas, loosening of associations...' },
    { id: 'mse_thought_content', label: 'Thought Content', type: 'textarea', placeholder: 'Delusions, overvalued ideas, preoccupations, suicidal/homicidal ideation...' },
    { id: 'mse_perceptions', label: 'Perceptions', type: 'textarea', placeholder: 'Hallucinations, illusions...' },
    { id: 'mse_cognition', label: 'Cognition', type: 'textarea', placeholder: 'Orientation, attention, memory, executive function...' },
    { id: 'mse_insight', label: 'Insight', type: 'select', options: ['Full', 'Partial', 'Poor', 'Absent'] },
    { id: 'mse_judgment', label: 'Judgment', type: 'select', options: ['Intact', 'Impaired'] },
  ]
};

const ASSESSMENT_SECTION: ClerkingSection = {
  id: 'assessment',
  title: 'Assessment & Diagnosis',
  icon: 'FileText',
  fields: [
    { id: 'working_diagnosis', label: 'Working Diagnosis', type: 'textarea', required: true },
    { id: 'differential_diagnosis', label: 'Differential Diagnoses', type: 'textarea', placeholder: 'List in order of likelihood...' },
    { id: 'icd_codes', label: 'ICD-10 Codes', type: 'text' },
    { id: 'problem_list', label: 'Problem List', type: 'textarea' },
  ]
};

const PLAN_SECTION: ClerkingSection = {
  id: 'plan',
  title: 'Management Plan',
  icon: 'ClipboardList',
  fields: [
    { id: 'investigations', label: 'Investigations', type: 'textarea', placeholder: 'Labs, imaging, other tests...' },
    { id: 'treatment', label: 'Treatment', type: 'textarea', placeholder: 'Medications, procedures, interventions...' },
    { id: 'referrals', label: 'Referrals', type: 'textarea' },
    { id: 'patient_education', label: 'Patient Education', type: 'textarea' },
    { id: 'follow_up', label: 'Follow-up Plan', type: 'textarea' },
    { id: 'disposition', label: 'Disposition', type: 'select', options: ['Admit', 'Discharge home', 'Transfer', 'Refer outpatient', 'Day case'] },
  ]
};

// Template definitions
export const CLERKING_TEMPLATES: ClerkingTemplate[] = [
  {
    id: 'general-medicine',
    specialty: 'general-medicine',
    name: 'General Medicine Clerking',
    description: 'Complete medical clerking for general internal medicine cases',
    sections: [
      BIODATA_SECTION,
      PRESENTING_COMPLAINT,
      HPI_SECTION,
      PMH_SECTION,
      DRUG_HISTORY,
      FAMILY_HISTORY,
      SOCIAL_HISTORY,
      REVIEW_OF_SYSTEMS_SECTION,
      GENERAL_EXAMINATION,
      CVS_EXAMINATION,
      RESPIRATORY_EXAMINATION,
      ABDOMINAL_EXAMINATION,
      NEURO_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
  {
    id: 'surgery',
    specialty: 'surgery',
    name: 'Surgical Clerking',
    description: 'Complete surgical clerking with pre-operative assessment',
    sections: [
      BIODATA_SECTION,
      PRESENTING_COMPLAINT,
      HPI_SECTION,
      SURGICAL_HISTORY,
      PMH_SECTION,
      DRUG_HISTORY,
      FAMILY_HISTORY,
      SOCIAL_HISTORY,
      REVIEW_OF_SYSTEMS_SECTION,
      GENERAL_EXAMINATION,
      CVS_EXAMINATION,
      RESPIRATORY_EXAMINATION,
      ABDOMINAL_EXAMINATION,
      MSK_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
  {
    id: 'obstetrics-gynecology',
    specialty: 'obstetrics-gynecology',
    name: 'Obstetrics & Gynaecology Clerking',
    description: 'Complete O&G clerking with obstetric and gynaecological history',
    sections: [
      BIODATA_SECTION,
      PRESENTING_COMPLAINT,
      HPI_SECTION,
      OBS_HISTORY,
      GYNAE_HISTORY,
      PMH_SECTION,
      DRUG_HISTORY,
      FAMILY_HISTORY,
      SOCIAL_HISTORY,
      GENERAL_EXAMINATION,
      CVS_EXAMINATION,
      RESPIRATORY_EXAMINATION,
      ABDOMINAL_EXAMINATION,
      OBS_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
  {
    id: 'pediatrics',
    specialty: 'pediatrics',
    name: 'Pediatric Clerking',
    description: 'Complete pediatric clerking with developmental assessment',
    sections: [
      BIODATA_SECTION,
      PRESENTING_COMPLAINT,
      HPI_SECTION,
      PEDIATRIC_HISTORY,
      PMH_SECTION,
      DRUG_HISTORY,
      FAMILY_HISTORY,
      SOCIAL_HISTORY,
      REVIEW_OF_SYSTEMS_SECTION,
      GENERAL_EXAMINATION,
      PEDIATRIC_EXAMINATION,
      CVS_EXAMINATION,
      RESPIRATORY_EXAMINATION,
      ABDOMINAL_EXAMINATION,
      NEURO_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
  {
    id: 'psychiatry',
    specialty: 'psychiatry',
    name: 'Psychiatric Clerking',
    description: 'Complete psychiatric assessment with MSE',
    sections: [
      BIODATA_SECTION,
      PSYCHIATRIC_HISTORY,
      PMH_SECTION,
      DRUG_HISTORY,
      FAMILY_HISTORY,
      SOCIAL_HISTORY,
      MENTAL_STATE_EXAM,
      GENERAL_EXAMINATION,
      NEURO_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
  {
    id: 'emergency',
    specialty: 'emergency',
    name: 'Emergency Assessment',
    description: 'Rapid emergency department assessment',
    sections: [
      BIODATA_SECTION,
      PRESENTING_COMPLAINT,
      HPI_SECTION,
      PMH_SECTION,
      DRUG_HISTORY,
      GENERAL_EXAMINATION,
      CVS_EXAMINATION,
      RESPIRATORY_EXAMINATION,
      ABDOMINAL_EXAMINATION,
      NEURO_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
  {
    id: 'orthopedics',
    specialty: 'orthopedics',
    name: 'Orthopedic Clerking',
    description: 'Complete orthopedic/musculoskeletal assessment',
    sections: [
      BIODATA_SECTION,
      PRESENTING_COMPLAINT,
      HPI_SECTION,
      SURGICAL_HISTORY,
      PMH_SECTION,
      DRUG_HISTORY,
      SOCIAL_HISTORY,
      GENERAL_EXAMINATION,
      MSK_EXAMINATION,
      NEURO_EXAMINATION,
      ASSESSMENT_SECTION,
      PLAN_SECTION,
    ]
  },
];

export function getTemplateBySpecialty(specialty: Specialty): ClerkingTemplate | undefined {
  return CLERKING_TEMPLATES.find(t => t.specialty === specialty);
}

export function filterSectionsByRole(sections: ClerkingSection[], role: CadreLevel): ClerkingSection[] {
  return sections.map(section => ({
    ...section,
    fields: section.fields.filter(field => {
      if (!field.cadreLevel) return true;
      return field.cadreLevel.includes(role);
    })
  })).filter(section => {
    if (!section.cadreLevel) return true;
    return section.cadreLevel.includes(role);
  });
}
