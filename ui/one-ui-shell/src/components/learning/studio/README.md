# Learning Studio Components (Modular)

This directory contains modular, reusable components for the Learning Studio module management feature. Each component is focused on a single responsibility and can be composed together.

## Architecture

```
StudioPanel (Main)
└── ModuleManagementPanel (Orchestrator)
    ├── SearchPanel
    ├── ModuleCard (Repeated)
    │   └── Handles: Expand/collapse, section list, edit/delete actions
    └── Integration with SectionFormComponent (parent level)
```

## Components

### SearchPanel.tsx

**Purpose**: Real-time search across modules and sections

**Props**:
- `modules: Module[]` - Array of modules with lessons
- `onSelectModule(moduleId)` - Callback when module selected
- `onSelectSection(moduleId, sectionId)` - Callback when section selected

**Features**:
- Real-time filtering
- Results show module hierarchy
- Auto-clears after selection
- Keyboard-accessible

**Usage**:
```tsx
<SearchPanel
  modules={modules}
  onSelectModule={handleModuleSelect}
  onSelectSection={handleSectionSelect}
/>
```

---

### ModuleCard.tsx

**Purpose**: Display a single module with its sections in an expandable card

**Props**:
- `module` - Module object with title, description, lessons
- `index` - Module index (for numbering)
- `isExpanded` - Whether sections are visible
- `onToggle()` - Toggle expand/collapse
- `onEdit()` - Edit module
- `onDelete()` - Delete module
- `onAddSection()` - Add new section
- `onEditSection(sectionId)` - Edit specific section
- `onDeleteSection(sectionId)` - Delete specific section

**Features**:
- Collapsible header with module info
- Section count badge
- Always-visible edit/delete actions
- Full-width add section button when expanded
- Smooth animations

**Usage**:
```tsx
<ModuleCard
  module={module}
  index={0}
  isExpanded={true}
  onToggle={() => setExpanded(!expanded)}
  onEdit={() => handleEdit(module.id)}
  onDelete={() => handleDelete(module.id)}
  onAddSection={() => handleAddSection(module.id)}
  onEditSection={(secId) => handleEditSection(module.id, secId)}
  onDeleteSection={(secId) => handleDeleteSection(module.id, secId)}
/>
```

---

### ModuleManagementPanel.tsx

**Purpose**: Orchestrate the full module management experience

**Props**:
- `courseTitle` - Title of the course being edited
- `modules` - Array of all modules
- `onBack()` - Navigate back to courses
- `onAddModule()` - Create new module
- `onEditModule(moduleId)` - Edit module
- `onDeleteModule(moduleId)` - Delete module
- `onAddSection(moduleId)` - Add section to module
- `onEditSection(moduleId, sectionId)` - Edit section
- `onDeleteSection(moduleId, sectionId)` - Delete section
- `error?` - Error message to display

**Features**:
- Back button with course title
- Error banner
- Search panel with jump-to functionality
- Responsive grid (1-3 columns)
- Integrates SearchPanel + ModuleCard
- Centralized state management (expanded module)

**Usage**:
```tsx
<ModuleManagementPanel
  courseTitle="Biology 101"
  modules={modules}
  onBack={() => navigate(-1)}
  onAddModule={handleAddModule}
  onEditModule={handleEditModule}
  onDeleteModule={handleDeleteModule}
  onAddSection={handleAddSection}
  onEditSection={handleEditSection}
  onDeleteSection={handleDeleteSection}
  error={errorMessage}
/>
```

---

## Layout & Responsiveness

### Desktop (>1024px)
- Grid: 3 columns
- Each ModuleCard width: minmax(300px, 1fr)
- Search panel above all cards

### Tablet (768-1024px)
- Grid: 2 columns
- Better spacing

### Mobile (<768px)
- Grid: 1 column
- Full-width cards
- Touch-friendly buttons

---

## Styling

All components use:
- **Tailwind CSS** for styling
- **Lucide React** icons
- **Color scheme**: Teal (#0f766e) + Slate grays
- **Consistency**: Matches existing Impilo design system

### Key Classes
- `.bg-teal-25` - Expanded module background
- `.border-teal-200` - Teal borders
- `.text-teal-700` - Teal text
- `.bg-teal-700` - Primary action buttons

---

## Integration with StudioPanel

The `ModuleManagementPanel` is imported and used in `StudioPanel.tsx` when a course is selected:

```tsx
if (courseView === "sections" && selectedCourseForSections) {
  return (
    <ModuleManagementPanel
      courseTitle={asText(selectedCourseForSections.title, "Course")}
      modules={modules}
      onBack={() => setCourseView(null)}
      // ... handlers
    />
  );
}
```

---

## Future Enhancements

1. **Drag & Drop**: Implement module/section reordering
2. **Batch Operations**: Multi-select and bulk delete
3. **Inline Editing**: Edit module title without modal
4. **Module Duplication**: Clone module with sections
5. **Section Templates**: Predefined section type forms
6. **Pagination**: Large course with 100+ sections
7. **Context Menu**: Right-click actions on cards

---

## File Structure

```
learning/
├── studio/
│   ├── README.md (this file)
│   ├── SearchPanel.tsx
│   ├── ModuleCard.tsx
│   └── ModuleManagementPanel.tsx
├── SectionFormComponent.tsx (shared)
├── StudioPanel.tsx (main entry point)
└── ... other components
```

---

## Notes

- All components are client-side (`"use client"`)
- Use TypeScript interfaces for type safety
- SearchPanel integrates with ModuleManagementPanel state
- ModuleCard handles individual module state (expand/collapse)
- Parent (StudioPanel) manages modules array and API calls
