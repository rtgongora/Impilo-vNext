/**
 * Accessibility utilities for WCAG 2.1 AA compliance.
 *
 * Provides:
 * - Focus management for SPA navigation
 * - Screen reader announcements
 * - Skip navigation link support
 * - Keyboard navigation helpers
 * - Color contrast utilities
 */

/**
 * Announce a message to screen readers via a live region.
 * Creates an aria-live region if one doesn't exist.
 */
export function announce(message: string, priority: 'polite' | 'assertive' = 'polite'): void {
  let liveRegion = document.getElementById(`sr-${priority}`);
  if (!liveRegion) {
    liveRegion = document.createElement('div');
    liveRegion.id = `sr-${priority}`;
    liveRegion.setAttribute('aria-live', priority);
    liveRegion.setAttribute('aria-atomic', 'true');
    liveRegion.setAttribute('role', priority === 'assertive' ? 'alert' : 'status');
    liveRegion.className = 'sr-only';
    liveRegion.style.cssText =
      'position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);border:0;';
    document.body.appendChild(liveRegion);
  }
  // Clear then set to ensure announcement even for repeated text
  liveRegion.textContent = '';
  requestAnimationFrame(() => {
    liveRegion!.textContent = message;
  });
}

/**
 * Focus the main content area after SPA navigation.
 * Call this from your route change handler.
 */
export function focusMainContent(): void {
  const main = document.querySelector('main') || document.getElementById('main-content');
  if (main) {
    main.setAttribute('tabindex', '-1');
    main.focus({ preventScroll: false });
    // Remove tabindex after focus to avoid visual outline on click
    main.addEventListener(
      'blur',
      () => main.removeAttribute('tabindex'),
      { once: true }
    );
  }
}

/**
 * Trap focus within a container (for modals/dialogs).
 * Returns a cleanup function to restore normal focus behavior.
 */
export function trapFocus(container: HTMLElement): () => void {
  const focusableSelectors = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
  ].join(', ');

  const focusableElements = container.querySelectorAll<HTMLElement>(focusableSelectors);
  const first = focusableElements[0];
  const last = focusableElements[focusableElements.length - 1];

  function handleKeyDown(e: KeyboardEvent) {
    if (e.key !== 'Tab') return;

    if (e.shiftKey) {
      if (document.activeElement === first) {
        e.preventDefault();
        last?.focus();
      }
    } else {
      if (document.activeElement === last) {
        e.preventDefault();
        first?.focus();
      }
    }
  }

  container.addEventListener('keydown', handleKeyDown);
  first?.focus();

  return () => {
    container.removeEventListener('keydown', handleKeyDown);
  };
}

/**
 * Check if a color combination meets WCAG AA contrast ratio (4.5:1 for normal text).
 */
export function meetsContrastRatio(
  foreground: string,
  background: string,
  level: 'AA' | 'AAA' = 'AA'
): boolean {
  const fgLum = relativeLuminance(parseHexColor(foreground));
  const bgLum = relativeLuminance(parseHexColor(background));
  const ratio = contrastRatio(fgLum, bgLum);
  return level === 'AA' ? ratio >= 4.5 : ratio >= 7;
}

function parseHexColor(hex: string): [number, number, number] {
  const clean = hex.replace('#', '');
  return [
    parseInt(clean.substring(0, 2), 16) / 255,
    parseInt(clean.substring(2, 4), 16) / 255,
    parseInt(clean.substring(4, 6), 16) / 255,
  ];
}

function relativeLuminance([r, g, b]: [number, number, number]): number {
  const [rs, gs, bs] = [r, g, b].map((c) =>
    c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  );
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

function contrastRatio(l1: number, l2: number): number {
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Generate a unique ID for aria-labelledby / aria-describedby associations.
 */
let idCounter = 0;
export function generateA11yId(prefix: string = 'a11y'): string {
  return `${prefix}-${++idCounter}`;
}

/**
 * CSS class that visually hides content but keeps it accessible to screen readers.
 * Use via className="sr-only" with Tailwind, or apply this inline style.
 */
export const srOnlyStyle: React.CSSProperties = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  padding: 0,
  margin: '-1px',
  overflow: 'hidden',
  clip: 'rect(0, 0, 0, 0)',
  whiteSpace: 'nowrap',
  borderWidth: 0,
};
