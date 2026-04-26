/**
 * Intelligent Experience Layer — Health OS
 *
 * These components make the experience layer "magical":
 * - ProactiveAssistant: contextual alerts, reminders, conversational chat
 * - WorkspaceContextSwitcher: WORK / MY PROFESSIONAL / MY LIFE zone switching
 * - UnifiedSearch: legacy mount → opens shell search (see ShellChrome)
 * - SmartEncounterFlow: guided clinical workflow with CDS integration
 */

export { ProactiveAssistant } from "./ProactiveAssistant";
export { WorkspaceContextSwitcher } from "./WorkspaceContextSwitcher";
export { UnifiedSearch, useSearchShortcut } from "./UnifiedSearch";
export { SmartEncounterFlow } from "./SmartEncounterFlow";
export { NompiloHint } from "./NompiloHint";
