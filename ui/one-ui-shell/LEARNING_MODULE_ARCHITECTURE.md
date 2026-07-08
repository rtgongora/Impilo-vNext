# Impilo Learning Module Architecture & Design Decisions

This document explains the key architectural concepts, design patterns, and implementation details of the Impilo Learning module UI/UX redesign.

---

## 1. Studio Colors: From Purple to Teal/Slate

### Decision: ✅ Completed
**Change**: The "Content Studio" header background has been updated from purple gradient to teal/slate gradient.

**Before**:
```tsx
<div className="rounded-lg bg-gradient-to-r from-purple-50 to-purple-100/50 border border-purple-200 p-4 sm:p-5">
  <h2 className="text-sm font-bold text-purple-900 uppercase tracking-wide">Content Studio</h2>
  <p className="text-xs text-purple-700 mt-1">Create and manage learning materials</p>
```

**After**:
```tsx
<div className="rounded-lg bg-gradient-to-r from-slate-50 to-slate-100/50 border border-slate-200 p-4 sm:p-5">
  <h2 className="text-sm font-bold text-slate-900 uppercase tracking-wide">Content Studio</h2>
  <p className="text-xs text-slate-600 mt-1">Create and manage learning materials</p>
```

**Rationale**:
- Maintains consistency with the overall teal/slate theme used throughout the learning module
- Teal is the primary action color (buttons, highlights, active states)
- Slate provides a neutral, professional appearance for content creation workflows
- Consistent color palette reduces cognitive load for elderly users

**File**: `src/components/learning/LearningWorkspace.tsx:714-716`

---

## 2. Learning Pathways: Architecture & Purpose

### What Are Pathways?

**Learning Pathways** are **structured collections/sequences of courses** designed to guide learners through a complete learning journey toward a specific competency or credential.

### Pathway Model

```
FundoPathwayEntity
├── id (UUID)
├── code (string) - Unique identifier, e.g., "PATH-001"
├── title (string) - e.g., "Nursing Fundamentals"
├── description (string)
├── targetCadres (string) - e.g., "nurse", "midwife"
├── targetRoles (string) - Role-based filtering
├── targetFacilityLevels (string) - E.g., "district", "provincial"
├── status (DRAFT | PUBLISHED | ARCHIVED)
└── items: FundoPathwayItemEntity[]
    ├── id (UUID)
    ├── pathwayId (FK)
    ├── courseId (FK)
    ├── sequenceNo (int) - Order in pathway
    ├── required (boolean) - Is course mandatory?
    └── prerequisiteCourseId (UUID, optional) - Required prior course
```

### Pathway vs. Course

| Aspect | Course | Pathway |
|--------|--------|---------|
| **Definition** | Single unit of learning content | Collection of courses in sequence |
| **Structure** | Standalone module | Ordered collection with prerequisites |
| **Duration** | Fixed, explicit | Sum of contained courses |
| **Purpose** | Teach one skill/topic | Achieve complete competency |
| **Example** | "Blood Pressure Monitoring" | "Clinical Assessment Fundamentals" (5 courses) |
| **Enrollment** | Direct course enrollment | Optional: enroll in pathway OR individual courses |

### Why Pathways Appear in Enrolment

When a user enrolls, they can specify an optional `pathwayId`. This serves two purposes:

1. **Contextual Learning**: Associate the enrolment with a learning pathway to provide structured progression
2. **Group Management**: Track which pathway/credential the learner is pursuing
3. **Prerequisite Enforcement**: Backend can use pathway structure to enforce course sequences

**Database Relationship**:
```sql
CREATE TABLE lrn_enrolment (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES lrn_fundo_catalog(id),
    pathway_id UUID REFERENCES lrn_fundo_pathway(id) ON DELETE SET NULL, -- Optional
    subject_id VARCHAR(255) NOT NULL,
    enrolment_type VARCHAR(32) DEFAULT 'SELF',
    -- ...
);
```

### Example Flow

1. User browses pathways in "My Learning" → Browse Courses section
2. Finds "Nursing Fundamentals" pathway (contains: Assessment, Vital Signs, Documentation)
3. Clicks "Enroll in Pathway"
4. System creates 3 separate enrolments (one per course) with `pathway_id` set to the pathway ID
5. User can now see all 3 courses under the pathway context

### Backend API

```
GET /internal/v1/learning/fundo/pathways?status=PUBLISHED&limit=50
↓
Response includes:
{
  "pathways": [
    {
      "id": "uuid-1",
      "code": "PATH-001",
      "title": "Nursing Fundamentals",
      "items": [
        {
          "sequence": 1,
          "courseId": "course-1",
          "required": true,
          "course": { /* full course details */ }
        }
      ]
    }
  ]
}
```

---

## 3. Enrolment Types: Four Options, Not Just SELF

### Database Constraint

```sql
CONSTRAINT chk_lrn_enrolment_type CHECK (
    enrolment_type IN ('SELF','ASSIGNED','COHORT','SYSTEM')
)
```

### The Four Types

| Type | Purpose | Use Case | Set By | Example |
|------|---------|----------|--------|---------|
| **SELF** | User voluntarily enrolls | Learner chooses course | Learner | Nurse opts into "Advanced Assessment" |
| **ASSIGNED** | Admin/manager assigns course | Mandatory training | Manager | HR assigns "Compliance Training" to all staff |
| **COHORT** | Enrollment via group membership | Cohort-based learning | System | All members of "District A Nurses" auto-enroll in "Quarterly Update" |
| **SYSTEM** | Automated system enrollment | Pre-requisite or rule-based | System | "Clinical Ethics" auto-triggered after "Introduction" completion |

### UI Implementation

**Enrolment Modal** now shows all four types with descriptive help text:

```tsx
<FieldSelect
  name="enrolmentType"
  label="Enrollment Type"
  defaultValue="SELF"
  options={[
    {
      value: "SELF",
      label: "Self-Enroll",
      detail: "You choose to enroll in this course"
    },
    {
      value: "ASSIGNED",
      label: "Assigned",
      detail: "Admin assigns the course to you"
    },
    {
      value: "COHORT",
      label: "Cohort",
      detail: "Enrolled via your learning cohort group"
    },
    {
      value: "SYSTEM",
      label: "System",
      detail: "Automatically enrolled by the system"
    }
  ]}
/>
```

### Backend Behavior

**File**: `services/learning-service/.../FundoEnrolmentService.java:67-69`

```java
// When enrolmentType is NOT 'SELF':
if (!SELF.equals(enrolmentType)) {
    entity.setAssignedAt(OffsetDateTime.now());
    entity.setAssignedBy(authContext.getActorId());
}
```

**Key Point**: Non-SELF enrolments automatically set `assignedAt` timestamp and `assignedBy` actor ID for audit trail.

### Why Show All Four?

1. **Transparency**: Users understand the different ways courses can be assigned
2. **Elderly Users**: Simple dropdown with clear descriptions avoids confusion
3. **Future Admin Features**: When admin interfaces are built, managers need these options
4. **Audit Trail**: System tracks how each enrolment was created

**File**: `src/components/learning/LearningWorkspace.tsx:1431-1448`

---

## 4. ID Dropdown Fields: UX Pattern for Hidden IDs

### Problem: Text Fields for IDs

**Before**:
```tsx
<Field
  name="courseId"
  label="Course ID"
  defaultValue={courseId}
  required
/>
```

Result: User sees text field with UUID string like "550e8400-e29b-41d4-a716-446655440000"

**Problem**: Elderly users should not need to enter/see IDs

### Solution: Hidden IDs + Pre-filled Values

**After**:
```tsx
<Field
  name="courseId"
  label="Course ID"
  defaultValue={courseId}
  required
  hidden
/>
```

Result: Hidden input field (no visual display) - the ID is sent with the form but user never sees it.

### Implementation Pattern

#### For Course ID on Enrolment Modal

When user clicks "Enroll" on a course card:
```tsx
onClick={() => setModal("enrolment", row)}
```

The `row` (course data) is passed as defaults:
```tsx
export function ModalFields({ kind, defaults = {} }: { kind: ModalKey; defaults?: Row }) {
  const courseId = asText(
    defaults.courseId ??
    defaults.course_id ??
    defaults.id ??
    defaults.code,
    ""
  );

  if (kind === "enrolment") return (
    <>
      <Field
        name="courseId"
        label="Course ID"
        defaultValue={courseId}
        required
        hidden  // ← ID is never shown to user
      />
      {/* Other visible fields */}
    </>
  );
}
```

### When to Use Dropdowns (Future Enhancement)

For **optional** ID fields where user needs to **select** from available options:

```tsx
function FieldSelect({
  name,
  label,
  options,  // Array of { id, label, detail }
  wide = false
}: { ... }) {
  return (
    <label className={wide ? "sm:col-span-2" : ""}>
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <select name={name} className={className}>
        {options.map((opt) => (
          <option key={opt.id} value={opt.id} title={opt.detail}>
            {opt.label} — {opt.detail}
          </option>
        ))}
      </select>
    </label>
  );
}
```

### Example: Pathway Selection (Future)

```tsx
const pathways = asArray(data.pathways.items);
const pathwayOptions = pathways.map(p => ({
  id: p.id,
  label: p.title,
  detail: asText(p.description, "")
}));

<FieldSelect
  name="pathwayId"
  label="Select Learning Pathway (optional)"
  options={pathwayOptions}
/>
```

User sees:
```
Select Learning Pathway (optional)
┌─────────────────────────────────────────────────┐
│ None Selected                          ▼        │
│ • Nursing Fundamentals — 5-week program         │
│ • Clinical Leadership — 8-week program          │
│ • Infection Control — 3-day workshop            │
└─────────────────────────────────────────────────┘
```

### Benefits for Elderly Users

✅ **No UUID strings**: Users never see cryptic IDs
✅ **Pre-populated**: When possible, IDs are already filled from context
✅ **Dropdown selection**: When choice is needed, users pick from meaningful labels
✅ **Reduced errors**: No typos or copy-paste mistakes
✅ **Clear intent**: Labels explain what each field represents

### Implementation Checklist

- [x] Hidden input for auto-populated IDs (courseId in enrolment)
- [x] FieldSelect component for future dropdown fields
- [x] Backward compatibility with existing form data
- [ ] Populate available pathways dropdown when backend data loads
- [ ] Populate available cohorts dropdown
- [ ] Populate available resources dropdown (for activity linking)

**File**: `src/components/learning/LearningWorkspace.tsx:1458-1485`

---

## Integration Summary

| Issue | Status | Solution | File |
|-------|--------|----------|------|
| Studio purple colors | ✅ Fixed | Changed to teal/slate | `LearningWorkspace.tsx:714-716` |
| Enrolment types (SELF only) | ✅ Fixed | Added dropdown with 4 types | `LearningWorkspace.tsx:1431-1448` |
| ID text fields | ✅ Fixed | Hidden field + FieldSelect component | `LearningWorkspace.tsx:1458-1485` |
| Pathways explanation | ✅ Documented | Added to "Browse Courses" section | `learningUtils.ts` + this doc |

---

## Architecture Benefits

### For Elderly Users
- ✅ No UUIDs in forms
- ✅ Clear enrollment type descriptions
- ✅ Consistent teal/slate theme
- ✅ Larger touch targets and labels

### For Developers
- ✅ Type-safe ID handling
- ✅ Reusable FieldSelect component
- ✅ Clear separation of concerns (Pathway vs. Course)
- ✅ Audit trail via enrolment types

### For Administrators
- ✅ Flexible enrolment modes (SELF, ASSIGNED, COHORT, SYSTEM)
- ✅ Structured learning via pathways
- ✅ Pre-population reduces user errors
- ✅ Dropdown options keep data consistent

---

## Next Steps

1. **Populate pathway dropdown** when browsing courses
2. **Add cohort selection** in enrolment modal
3. **Link resources/activities** to courses via dropdown
4. **Test with elderly users** on tablet devices
5. **Document enrolment workflow** in user guide
