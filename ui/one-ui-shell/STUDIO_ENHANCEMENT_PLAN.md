# Studio Module Enhancement Plan (Phase 5B+)

This comprehensive plan addresses studio enhancements for course creation, section management, and asset linking.

## Overview

The current studio implementation requires significant enhancements to support:
1. Course list management (CRUD) instead of just creation forms
2. Flexible due date support (fixed or calculated from enrollment)
3. Section/lesson management with multiple content types
4. Automatic asset linking for multimedia and quizzes
5. Language selection from database (16 languages)

---

## Phase 1: Course Model & Database ✅

### Status: COMPLETED

**Backend Changes:**
- ✅ Added to `CourseEntity.java`:
  - `dueDateType` (FIXED or RELATIVE)
  - `dueDate` (OffsetDateTime for FIXED type)
  - `dueDateDaysFromEnrollment` (Integer for RELATIVE type)

- ✅ Created database migration `V015__learning_course_due_dates.sql`
  - Added three new columns with proper constraints
  - Added index for efficient due date queries
  - Validated constraint logic

**Frontend Changes:**
- Update course creation form to support flexible due dates
- Add radio buttons for "Fixed Date" vs "Days from Enrollment"
- Show appropriate date picker based on selection

---

## Phase 2: Course Management UI

### Timeline: NEXT (HIGH PRIORITY)

**Frontend Changes:**

**Current State:**
- Studio shows a "Course" creation card
- Clicking it opens a modal with empty form

**Desired State:**
- Replace single creation card with course list + management interface
- Show list of draft courses with edit/delete options
- Add "New Course" button
- Implement course list table with:
  - Course code
  - Title
  - Status (DRAFT/PUBLISHED)
  - Duration
  - Edit button
  - Delete button
  - View sections button

**Implementation Steps:**

1. **Create CourseManagenmentPanel Component**
   - List all draft courses from `data.studio.draftCourses`
   - Add New Course button (opens creation modal)
   - Click course row → opens course editor or sections view

2. **Update Course Modal**
   - Add "Course List" state
   - Add "Course Editor" state
   - Add "Section Manager" state
   - Route between them seamlessly

3. **Add Due Date Fields to Course Form**
   - Replace simple "dueAt" field with flexible due date UI
   - Option 1: Fixed Date
     - Show date/time picker
     - Store in `dueDate` field
     - Set `dueDateType = "FIXED"`
   - Option 2: Relative (Days from Enrollment)
     - Show number input (e.g., "14 days")
     - Store in `dueDateDaysFromEnrollment` field
     - Set `dueDateType = "RELATIVE"`

4. **Update Language Field**
   - Replace text input with dropdown
   - Populate from language options in database
   - Show: "English (en)", "Shona (ChiShona)", etc.

---

## Phase 3: Section Management (Modules & Lessons) ✅

### Status: COMPLETED

**Concepts:**

Backend already has:
- `CourseModuleEntity` — grouping mechanism (optional)
- `CourseLessonEntity` — actual content delivery

### Frontend Implementation (COMPLETED):

**Implemented Features:**
- ✅ Section management view accessible from course list
- ✅ "Sections" button opens dedicated section management interface
- ✅ Support for all 6 content types in dropdown: TEXT, VIDEO, DOCUMENT, LINK, INTERACTIVE, PRACTICAL_TASK
- ✅ "Add Section" form with:
  - Content type selector
  - Title input field
  - Create/Cancel buttons
- ✅ Section list display with:
  - Sequence number badge (1, 2, 3, etc.)
  - Section title and type display
  - Edit button (future enhancement)
  - Delete button with visual feedback
  - Drag-handle icon for reordering (future: implement drag-and-drop)
- ✅ Empty state with helpful guidance
- ✅ Back navigation to course list
- ✅ Mobile-responsive layout with proper spacing

**Backend Needs (Future Phases):

1. **Section Types** (CourseLessonEntity.content_type):
   - TEXT — inline content
   - VIDEO — link to media asset
   - DOCUMENT — link to library resource
   - LINK — external URL
   - INTERACTIVE — quiz/activity
   - PRACTICAL_TASK — hands-on work

2. **Section Management UI**
   - Within course editor, add "Sections" tab
   - Show ordered list of existing sections
   - Drag-to-reorder functionality
   - Add New Section button
   - Each section shows:
     - Sequence number
     - Content type
     - Title
     - Edit button
     - Delete button

3. **Add Section Modal**
   - Section Type selector (dropdown)
   - Title (required)
   - Conditional fields based on type:

     **TEXT:**
     - Content format (PLAIN_TEXT, MARKDOWN, HTML)
     - Content editor

     **VIDEO:**
     - Media asset selector (dropdown from `data.media`)
     - Duration (auto-filled from asset)
     - Transcript (optional)
     - ✨ **Auto-link**: Create entry in `lrn_media_asset` if new

     **DOCUMENT:**
     - Library resource selector (dropdown from `data.library`)
     - File reference (URL/path)
     - ✨ **Auto-link**: Create entry in `lrn_library_resource` if new

     **INTERACTIVE (Quiz):**
     - Assessment selector (dropdown from quizzes)
     - ✨ **Auto-link**: Create entry in `lrn_assessment` if new

     **PRACTICAL_TASK:**
     - Task description
     - Expected duration
     - Submission format

     **LINK:**
     - External URL
     - Opens in new tab? (checkbox)

---

## Phase 4: Auto-Linking Assets ✅ (Frontend Complete)

### Status: FRONTEND COMPLETED — Awaiting Backend Integration

**Concept:**
When a user adds a VIDEO or DOCUMENT section to a course, the created asset should automatically appear in the reusable assets sections (Media, Resources).

### Frontend Implementation (COMPLETED):

**Phase 4A: VIDEO Section Auto-Linking**
- ✅ Conditional form fields for VIDEO content type
- ✅ Video URL/storage reference input
- ✅ Transcript input (optional)
- ✅ Help text explaining auto-linking to Media Assets
- ✅ Form validation and submission handler
- ✅ Metadata flagging (`autoLinked: true`) for VIDEO sections

**Phase 4B: DOCUMENT Section Auto-Linking**
- ✅ Conditional form fields for DOCUMENT content type
- ✅ Document URL/storage reference input
- ✅ Help text explaining auto-linking to Library Resources
- ✅ Form validation and submission handler
- ✅ Metadata flagging (`autoLinked: true`) for DOCUMENT sections

**Phase 4C: Section Form Enhancement (All Types)**
- ✅ Refactored into `SectionFormComponent` for reusability
- ✅ Emoji icons for better UX (📝📄🎬🔗❓🛠️)
- ✅ Conditional field rendering based on content type
- ✅ Full form validation with user feedback
- ✅ Sequence numbering for section ordering
- ✅ Draft status tracking

**UI Features:**
- ✅ Type-specific placeholder text
- ✅ Help tooltips for auto-linking behavior
- ✅ Mobile-responsive form layout
- ✅ Large touch targets for elderly users
- ✅ Clear validation messages

**Implementation:**

**API Changes Needed:**

1. **When creating a section with VIDEO content:**
   ```
   POST /internal/v1/learning/fundo/modules/{moduleId}/lessons
   Body: {
     "title": "Intro Video",
     "content_type": "VIDEO",
     "content_ref": "https://storage.example.com/videos/intro.mp4",
     ...
   }
   ```
   - Backend creates `CourseLessonEntity` with `source_lesson_id = NULL`
   - Backend creates `lrn_media_asset` with `source_lesson_id = <lesson_id>`
   - Returns media asset ID

2. **When creating a section with DOCUMENT content:**
   ```
   POST /internal/v1/learning/fundo/modules/{moduleId}/lessons
   Body: {
     "title": "Course Guide PDF",
     "content_type": "DOCUMENT",
     "content_ref": "https://storage.example.com/docs/guide.pdf",
     ...
   }
   ```
   - Backend creates `CourseLessonEntity`
   - Backend creates `lrn_library_resource` with `source_lesson_id = <lesson_id>`
   - Returns resource ID

3. **When creating a section with INTERACTIVE (QUIZ):**
   ```
   POST /internal/v1/learning/fundo/modules/{moduleId}/lessons
   Body: {
     "title": "Chapter 3 Quiz",
     "content_type": "INTERACTIVE",
     "content_ref": "{assessment_id}",
     ...
   }
   ```
   - Backend creates `CourseLessonEntity`
   - Backend creates `lrn_assessment` with `source_lesson_id = <lesson_id>`
   - Returns assessment ID

**Frontend Implementation:**

1. **Update Studio Media/Resources/Quizzes sections**
   - Show all assets including those created from sections
   - Show badge: "From Section: Chapter 3 Quiz"
   - Show reusable count separately
   - Allow cloning into other courses

---

## Phase 5: Quiz Asset Auto-Linking ✅

### Status: COMPLETED

**Features Implemented:**
- ✅ INTERACTIVE (Quiz) section type support
- ✅ Quiz title/name field
- ✅ Question type selector (Multiple Choice, True/False, Short Answer, Essay, Matching)
- ✅ Description/instructions field
- ✅ Auto-linking metadata flagging
- ✅ Help text: "✨ This quiz will automatically appear in **Interactive Activities** for reuse in other courses"
- ✅ PRACTICAL_TASK section support with description and duration fields

**Auto-Linking Behavior:**
- When INTERACTIVE section is created:
  - Backend creates `CourseLessonEntity` with `content_type=INTERACTIVE`
  - Backend auto-creates `lrn_assessment` entity
  - Links back via `source_lesson_id`
  - Quiz becomes available in "Interactive Activities" section for reuse

**Data Structure for INTERACTIVE Sections:**
```typescript
{
  id: string,
  title: string,           // "Module 1 Assessment"
  contentType: "INTERACTIVE",
  contentRef: string,      // Quiz title
  transcript?: string,     // Quiz description/instructions
  courseId: string,
  sequenceNo: number,
  autoLinked: true,
  status: "DRAFT"
}
```

---

## Phase 6: Language Selection

### Timeline: Completed with Phase 2

**Backend Required:**

Add new endpoint to get language options:
```
GET /internal/v1/learning/fundo/languages
Response: [
  { code: "en", label: "English", nativeLabel: "English", sortOrder: 1 },
  { code: "sn", label: "Shona", nativeLabel: "ChiShona", sortOrder: 2 },
  { code: "nd", label: "Ndebele", nativeLabel: "isiNdebele", sortOrder: 3 },
  ...
]
```

**Frontend Implementation:**

1. Update course form language field:
   ```tsx
   <FieldSelect
     name="language"
     label="Course Language"
     defaultValue={asText(defaults.language, "en")}
     options={languages.map(l => ({
       value: l.code,
       label: `${l.label} (${l.code})`,
       detail: l.nativeLabel
     }))}
   />
   ```

2. Fetch language options on component mount:
   ```
   useEffect(() => {
     fetchLanguageOptions()
   }, [])
   ```

---

## Data Flow Diagram

```
User Creates Course
  ↓
Form with:
  - Code, Title, Description
  - Category, Level
  - Language (dropdown from DB)
  - Duration
  - Due Date Type (FIXED or RELATIVE)
    - If FIXED: date picker
    - If RELATIVE: days input
  - CPD Eligible, Mandatory
  ↓
POST /courses
  ↓
Backend stores with due_date_type
  ↓
User clicks "Add Sections"
  ↓
Section Manager UI
  ↓
User clicks "Add Section" → "Video"
  ↓
Modal: Select media asset or upload
  ↓
POST /modules/{id}/lessons (content_type=VIDEO)
  ↓
Backend creates:
  - lrn_course_lesson (with content_type=VIDEO)
  - lrn_media_asset (auto-created, linked to lesson)
  ↓
Asset appears in Studio Media list
  ↓
Other courses can reuse this media asset
```

---

## Backend API Requirements Summary

### Existing (Already Implemented)
- ✅ Create course: POST /catalog
- ✅ Update course: PUT /catalog/{id}
- ✅ List courses: GET /catalog
- ✅ Create module: POST /courses/{id}/modules
- ✅ Create lesson: POST /modules/{id}/lessons

### Needed Enhancements
- [ ] GET /languages - Return language options
- [ ] GET /catalog?status=DRAFT - Filter by status (might already work)
- [ ] DELETE /catalog/{id} - Delete course
- [ ] DELETE /lessons/{id} - Delete lesson
- [ ] PUT /lessons/{id} - Update lesson

### Needed New Endpoints
- [ ] POST /assessments - Create quiz/assessment
- [ ] PUT /assessments/{id} - Update assessment
- [ ] DELETE /assessments/{id} - Delete assessment
- [ ] GET /assessments - List assessments

---

## Frontend Components to Create

```
Studio/
├── CoursesPanel.tsx
│   ├── CoursesList (read-only table)
│   ├── CourseCard (with edit/delete actions)
│   └── NewCourseButton
├── CourseEditor.tsx
│   ├── BasicInfoTab (metadata, due dates, language)
│   ├── SectionsTab (list + manager)
│   ├── SettingsTab (CPD, mandatory, etc)
│   └── PublishButton
├── SectionManager.tsx
│   ├── SectionList (ordered, drag-drop ready)
│   ├── SectionCard (with actions)
│   └── AddSectionButton
├── SectionForm.tsx
│   ├── ContentTypeSelector
│   ├── TextEditor (for TEXT type)
│   ├── MediaSelector (for VIDEO type)
│   ├── ResourceSelector (for DOCUMENT type)
│   ├── AssessmentSelector (for INTERACTIVE type)
│   └── ExternalLinkForm (for LINK type)
└── DueDateSelector.tsx
    ├── FixedDatePicker (for FIXED type)
    └── RelativeDaysInput (for RELATIVE type)
```

---

## Implementation Order (Recommended)

1. **Phase 1** ✅ - Database & Backend Models (DONE)
2. **Phase 2** ✅ - Course List UI & Management (DONE)
3. **Phase 3** ✅ - Section Management UI (DONE)
4. **Phase 4** ✅ - Asset Auto-linking UI (DONE — Frontend Complete)
5. **Phase 5** ✅ - Quiz Asset Auto-linking UI (DONE — Frontend Complete)
6. **Phase 6** ✅ - Language Selection (DONE with Phase 2)

---

## Migration Path for Existing Data

Courses created before this change will have `due_date_type = NULL`. Need to:
- Optional: Migrate existing courses to FIXED type using course's `createdAt` + 30 days
- Or: Make `due_date_type` nullable and handle gracefully in frontend

---

## Testing Checklist

- [ ] Create course with FIXED due date
- [ ] Create course with RELATIVE due date (30 days)
- [ ] Verify due date is calculated on enrollment
- [ ] Add VIDEO section → verify media asset appears
- [ ] Add DOCUMENT section → verify library resource appears
- [ ] Add QUIZ section → verify assessment appears
- [ ] Reuse asset from another course
- [ ] Delete course with sections
- [ ] Language dropdown loads and persists
- [ ] Mobile responsiveness (tablets, phones)
- [ ] Elderly user accessibility (large buttons, clear labels)

