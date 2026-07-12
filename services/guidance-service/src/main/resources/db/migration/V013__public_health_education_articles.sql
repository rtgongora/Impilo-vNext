-- =============================================================================
-- guidance-service V013 — Public health-information starter articles
--
-- Gateway doctrine §2 pillar 3 (Health information): trusted public health
-- knowledge, prevention guidance and outbreak notices, readable at rung R0
-- (no identity). Per PD-4 (decided): guidance-service owns citizen-language
-- education; clinical-knowledge-platform owns clinical content — so this seed
-- is deliberately plain-language and contains NO clinical dosing instructions.
--
-- Served through the service-side PublicGuidanceController
-- (/v1/public/guidance/education**) and proxied by the experience-bff under
-- /internal/v1/public/gateway/guidance/education** (public-lane ADR:
-- allow-listed DTOs, no PII, no internal service names in seeded copy).
--
-- Taxonomy (category slugs are part of the public contract):
--   when-to-seek-care      — danger signs, choosing the right level of care
--   maternal-child-health  — pregnancy, newborn and child health
--   vaccination            — routine immunisation basics
--   chronic-conditions     — hypertension and diabetes self-care
--   mental-health          — mental health and wellbeing
--   medicines-safety       — safe use of medicines (no dosing guidance)
--   prevention-nutrition   — hygiene, water, food and healthy eating
--   outbreak-notices       — placeholder lane for official outbreak notices
--
-- Tenant: 'national-spine' (the pre-tenant public lane); status PUBLISHED.
-- =============================================================================

INSERT INTO guidance.knowledge_articles
    (tenant_id, title, summary, content, category, domain, tags, source, status)
VALUES

-- ── when-to-seek-care ────────────────────────────────────────────────────────
('national-spine',
 'Danger signs: get medical help straight away',
 'Some symptoms should never wait. Learn the danger signs in adults and children that mean you should go to the nearest health facility or call for emergency help now.',
 'Most illnesses can safely be seen at your nearest clinic during opening hours. But some signs mean the body is in danger and care is needed immediately — day or night.

Get emergency help now (call 999 or 112, or go straight to the nearest hospital) if you or someone with you has: difficulty breathing; chest pain or pressure; severe bleeding that does not stop; sudden weakness on one side of the body, a drooping face, or trouble speaking; fits or convulsions; a serious burn or injury; or has been unconscious, even briefly.

For babies and young children, danger signs include: unable to drink or breastfeed; vomiting everything; fits; unusually sleepy or difficult to wake; fast or difficult breathing; and high fever in a baby under three months.

In pregnancy, treat heavy bleeding, severe headache with blurred vision, fits, severe abdominal pain, or the baby moving much less than usual as emergencies.

Do not wait to register, book, or sign in to anything before seeking emergency care. Go to the nearest facility — emergency care is never refused while you are stabilised.',
 'when-to-seek-care', 'public-health',
 '["danger signs", "emergency", "urgent care", "children", "pregnancy"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

('national-spine',
 'Where to go for care: from clinic to central hospital',
 'Zimbabwe''s health services are organised in levels — clinic, district, provincial and central hospital. Starting at the right level gets you seen faster and keeps referral letters simple.',
 'Health services are organised so that everyday care happens close to home and specialised care is concentrated where the equipment and specialists are.

Start at your nearest clinic or rural health centre for most needs: check-ups, common illnesses, antenatal visits, child health and vaccinations, family planning, wound care, and collecting routine medicines. Clinics know the community and hold your local records.

If the clinic finds something that needs more tests, a doctor, or admission, they refer you to the district hospital. District hospitals handle X-rays, laboratory tests, maternity complications, minor surgery and inpatient care.

Provincial hospitals take referrals from district hospitals for more complex care. Central hospitals — the largest referral hospitals in Harare and Bulawayo — handle the most specialised treatment, such as intensive care and specialist surgery.

When you are referred, you will be given a referral letter or note. Bring it, together with any identity document and your health card if you have them. In a genuine emergency you may go directly to any hospital — you do not need a referral letter first.',
 'when-to-seek-care', 'public-health',
 '["referral", "clinic", "district hospital", "levels of care"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── maternal-child-health ────────────────────────────────────────────────────
('national-spine',
 'Antenatal care: start early, go often',
 'Going for antenatal care early in pregnancy — and attending every visit — protects you and your baby. Here is what happens at the visits and why they matter.',
 'Antenatal care (ANC) is the care you receive at a clinic while you are pregnant. Book your first visit as soon as you know you are pregnant, ideally before 12 weeks. Early booking means problems can be found and treated while they are still small.

At your visits, health workers check your blood pressure, test for anaemia, HIV and syphilis, check the baby''s growth and position, and give you supplements and vaccinations that protect you both. You will also receive advice on nutrition, danger signs and planning for the birth.

Aim to attend all the visits you are given — the World Health Organization recommends at least eight contacts during pregnancy. More visits mean more chances to catch problems early.

Plan to deliver at a health facility with a skilled birth attendant. Discuss your birth plan — transport, who will accompany you, and what to bring — with the clinic well before your due date.

Antenatal care at public clinics is a core service. If you are told about a danger sign such as bleeding, severe headache, or reduced baby movement, treat it as an emergency and go to a facility immediately.',
 'maternal-child-health', 'public-health',
 '["pregnancy", "antenatal", "ANC", "birth plan"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

('national-spine',
 'After the birth: caring for your newborn',
 'The first weeks of life are precious and delicate. Learn the check-ups your newborn needs, how to keep a baby healthy at home, and the warning signs that need urgent care.',
 'Every newborn should be checked by a health worker within the first days of life, and again in the first weeks. These early checks find problems that are easy to miss and start your baby''s vaccination and growth records.

At home: keep the baby warm and clean, and wash your hands before handling the baby. Breastfeeding is the safest and most complete food for babies — exclusive breastfeeding is recommended for the first six months, with feeding on demand day and night.

Keep the child health card safe and bring it to every clinic visit. It records weight, vaccinations and development, and any clinic in the country can continue care from it.

Take a newborn to a health facility urgently if the baby: cannot feed or is feeding much less; has fast, noisy or difficult breathing; feels hot or unusually cold; has fits; is unusually floppy or sleepy; has redness or discharge around the cord; or develops yellow eyes or skin in the first days.

Registering the birth also matters — a birth certificate makes it easier to access health services and school later. Ask at the facility where you delivered about birth registration.',
 'maternal-child-health', 'public-health',
 '["newborn", "breastfeeding", "child health card", "danger signs"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── vaccination ──────────────────────────────────────────────────────────────
('national-spine',
 'Childhood vaccinations: what the schedule covers',
 'Routine childhood vaccinations are free at public clinics and protect against serious diseases like measles, polio and tuberculosis. Here is how the schedule works.',
 'Vaccines teach the body to fight dangerous infections before your child ever meets them. Zimbabwe''s routine immunisation programme protects against diseases including tuberculosis, polio, measles and rubella, diphtheria, tetanus, whooping cough, hepatitis B, pneumonia and diarrhoeal disease.

The schedule starts at birth and continues through infancy — with visits at birth, at six, ten and fourteen weeks, and again at nine months and eighteen months. Girls are also offered HPV vaccination later in childhood to protect against cervical cancer.

Every dose is recorded on your child''s health card. Bring the card to every visit, and keep it safe — it is the proof of vaccination used by clinics and schools anywhere in the country.

Missed a visit? Do not start again from the beginning — take your child to the nearest clinic and the health workers will simply continue from where the card stopped. Catch-up vaccination is normal and safe.

Routine childhood vaccination is free at public health facilities. If you are unsure whether your child is up to date, take the child health card to your nearest clinic and ask.',
 'vaccination', 'public-health',
 '["immunisation", "vaccines", "children", "child health card", "schedule"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── chronic-conditions ───────────────────────────────────────────────────────
('national-spine',
 'Living well with high blood pressure',
 'High blood pressure usually has no symptoms, but it quietly damages the heart, brain and kidneys. Regular checks and steady treatment let you live a full, normal life.',
 'High blood pressure (hypertension) means the pressure in your blood vessels stays too high. It usually causes no symptoms at all — many people feel completely well — which is why checking is the only way to know.

Untreated, high blood pressure raises the risk of stroke, heart attack and kidney disease. The good news: it can be controlled, and people with well-controlled blood pressure live full, active lives.

What you can do every day: eat less salt and fewer fried and processed foods; eat more vegetables and fruit; be active — even regular walking counts; keep a healthy weight; avoid smoking; and limit alcohol.

If you have been given treatment, take it every day as prescribed, even when you feel well. Blood pressure medicine only works while you keep taking it — stopping because you feel fine is the most common reason control is lost. Never stop or change your treatment without talking to a health worker.

Go for your review visits so your blood pressure can be measured and your treatment adjusted. Seek urgent care if you have a very severe headache, chest pain, sudden weakness or difficulty speaking, or blurred vision.',
 'chronic-conditions', 'public-health',
 '["hypertension", "blood pressure", "heart health", "self-care"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

('national-spine',
 'Living well with diabetes',
 'Diabetes can be managed well with the right daily habits, regular checks and steady treatment. Learn the basics of looking after yourself and the warning signs to act on.',
 'Diabetes means the level of sugar in your blood stays too high. Common warning signs include feeling very thirsty, passing urine often, losing weight without trying, tiredness, and wounds that heal slowly. If you notice these, ask for a blood sugar test at your nearest clinic.

Managing diabetes rests on daily habits: eat regular balanced meals with vegetables and whole grains; limit sugary drinks and snacks; be physically active most days; keep a healthy weight; and do not smoke.

If you have been prescribed treatment, take it consistently as directed and attend your review visits. Your health workers will guide you on your specific treatment and any monitoring you need — follow their instructions rather than advice from friends or unverified sources.

Look after your feet: check them regularly for cuts, blisters or sores, keep them clean and dry, and show any wound that is not healing to a health worker early.

Seek care urgently if you feel confused or unusually drowsy, are breathing fast, are vomiting and cannot keep fluids down, or have signs of a serious infection. These can signal dangerously high or low blood sugar and need immediate attention.',
 'chronic-conditions', 'public-health',
 '["diabetes", "blood sugar", "self-care", "foot care"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── mental-health ────────────────────────────────────────────────────────────
('national-spine',
 'Caring for your mental health',
 'Mental health is part of health. Stress, grief, anxiety and low mood are common and treatable — and support is available through your local clinic and community.',
 'Everyone has mental health, and like physical health it can be cared for. Feeling stressed, anxious, or low sometimes is part of life — but when these feelings last for weeks, disturb your sleep, or make daily life hard, it is worth getting support.

Things that protect mental wellbeing: talking honestly with someone you trust; keeping a regular sleep routine; staying physically active; spending time with family, friends, faith or community groups; and limiting alcohol, which worsens low mood over time.

Help is available. You can talk to a health worker at your nearest clinic — mental health support is part of primary care, and community-based talking support is available in many areas. Asking for help is a sign of strength, not weakness, and your conversation is treated with respect and privacy.

Watch for signs in yourself or others that need prompt help: hopelessness that does not lift, withdrawing completely from people, being unable to care for yourself or your children, hearing or seeing things others do not, or thoughts of self-harm.

If you or someone near you is in immediate danger of self-harm, treat it as an emergency: do not leave the person alone, and get help from the nearest health facility right away.',
 'mental-health', 'public-health',
 '["mental health", "stress", "depression", "anxiety", "support"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── medicines-safety ─────────────────────────────────────────────────────────
('national-spine',
 'Using medicines safely at home',
 'Medicines help when used correctly and can harm when used carelessly. Simple habits — right person, right instructions, safe storage — keep your household safe.',
 'Medicines are safe and effective when used the way they were prescribed or labelled — and risky when they are not. A few habits protect the whole household.

Take medicines exactly as the health worker or pharmacist explained, for as long as instructed — even if you start feeling better sooner. If instructions are unclear, ask before you leave the clinic or pharmacy; you have the right to understand your own treatment.

Never share prescription medicines. A medicine prescribed for one person''s condition, weight and history can seriously harm someone else, especially children and pregnant women.

Store all medicines out of children''s reach and sight, in a cool dry place, and in their original labelled containers. Do not use medicines past their expiry date; return expired or leftover medicines to a pharmacy rather than keeping or dumping them.

Buy medicines only from licensed pharmacies and health facilities. Medicines sold informally — on the street, in markets, or unverified online sellers — may be expired, fake or wrongly stored, and can be dangerous even when the packaging looks genuine.

If a medicine seems to cause a bad reaction — a rash, swelling, difficulty breathing, or feeling much worse — stop taking it and seek care promptly. Tell the health worker exactly what was taken and when.',
 'medicines-safety', 'public-health',
 '["medicines", "safe use", "storage", "pharmacy"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

('national-spine',
 'Antibiotics: use them wisely',
 'Antibiotics only work against bacteria — and misusing them makes infections harder to treat for everyone. Learn when antibiotics help and how to use them properly.',
 'Antibiotics are medicines that fight infections caused by bacteria. They do not work against viruses — so they do not cure colds, flu, or most coughs and sore throats, which usually get better on their own with rest and fluids.

When antibiotics are used wrongly — taken without need, shared, or stopped early — bacteria learn to resist them. Antibiotic resistance is growing worldwide and makes previously treatable infections dangerous. Using antibiotics wisely protects your family and your community.

Use antibiotics only when a health worker has assessed you and prescribed them. Never buy antibiotics without a prescription, never use leftover antibiotics from a previous illness, and never share them.

If you are prescribed an antibiotic, complete the full course as instructed even if you feel better after a day or two. Stopping early can leave the strongest bacteria alive.

If your symptoms are not improving, or they get worse while on treatment, go back to the health worker rather than adding other medicines yourself.',
 'medicines-safety', 'public-health',
 '["antibiotics", "resistance", "safe use"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── prevention-nutrition ─────────────────────────────────────────────────────
('national-spine',
 'Clean hands, safe water, safe food',
 'Handwashing, safe drinking water and careful food handling prevent cholera, typhoid and diarrhoeal disease — the everyday habits that protect whole families.',
 'Many of the most common serious illnesses — cholera, typhoid, dysentery and other diarrhoeal diseases — spread through dirty hands, unsafe water and contaminated food. Simple habits break that chain.

Wash your hands with soap and running water: before preparing or eating food, after using the toilet, after changing a baby''s nappy, and after caring for someone who is sick. If soap and water are not available, use ash and water rather than nothing.

Make drinking water safe: use treated tap water where available; otherwise boil water, or treat it with approved water-treatment tablets or solution. Store treated water in a clean, covered container with a narrow mouth, and pour rather than dipping cups or hands into it.

Keep food safe: wash fruit and vegetables with safe water; cook food thoroughly and eat it while hot; keep raw meat away from ready-to-eat food; and cover food to protect it from flies.

Use a toilet or latrine, keep it clean, and make sure children''s stools are disposed of in the toilet too. During a diarrhoeal illness, drink plenty of safe fluids and oral rehydration solution, and seek care quickly for anyone who becomes weak, dizzy or cannot keep fluids down — dehydration can become dangerous fast, especially in children.',
 'prevention-nutrition', 'public-health',
 '["hygiene", "handwashing", "safe water", "cholera", "food safety"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

('national-spine',
 'Healthy eating with everyday foods',
 'A healthy diet does not require expensive foods. Everyday staples — vegetables, beans, whole grains and fruit in season — can feed a family well on a budget.',
 'Healthy eating means variety and balance, not expensive products. The foods sold in most markets and grown in many gardens can build a strong, healthy diet.

Build meals around a mix of: starchy staples (sadza, rice, sweet potatoes — whole-grain where possible); plenty of vegetables (leafy greens like covo, rape and pumpkin leaves are inexpensive and rich in vitamins); proteins (beans, lentils, groundnuts, eggs, kapenta, meat when available); and fruit in season.

Cut down on sugar, sugary drinks, salt, and deep-fried foods. These add little nourishment and drive high blood pressure, diabetes and tooth decay. Water is the best everyday drink for both children and adults.

For babies: breastfeed exclusively for the first six months, then add mashed and soft family foods while continuing to breastfeed. Growing children need frequent, varied meals — attend growth monitoring at your clinic so any problem is caught early.

Home and community gardens are a powerful way to keep vegetables on the table year-round. Ask at your clinic or community health worker about nutrition support and cooking demonstrations in your area.',
 'prevention-nutrition', 'public-health',
 '["nutrition", "healthy eating", "budget", "children"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED'),

-- ── outbreak-notices (honest placeholder lane) ───────────────────────────────
('national-spine',
 'Outbreak notices: what appears here and when',
 'This section carries official public-health notices during disease outbreaks. There are no notices published on this channel right now — here is how it works and where else to check.',
 'During a disease outbreak — such as cholera, measles or influenza — the Ministry of Health and Child Care publishes official notices telling the public what is happening, which areas are affected, and what to do.

This section is the home for those notices on this platform. When a notice is active you will see it listed here with the date it was issued, the affected areas, and clear instructions in plain language.

There are currently no outbreak notices published on this channel. That means no notice has been issued here — always rely on the most recent official communication.

During any outbreak, the basics protect you while you wait for specific guidance: wash hands with soap, use safe water, cook food thoroughly, keep vaccinations up to date, and seek care early if you develop symptoms rather than waiting.

Official information also comes through national radio and television announcements, your nearest health facility, and community health workers. Be cautious with unverified messages circulating on social media — check them against an official source before acting or sharing.',
 'outbreak-notices', 'public-health',
 '["outbreak", "alerts", "official notices"]'::jsonb,
 'Ministry of Health & Child Care', 'PUBLISHED');
