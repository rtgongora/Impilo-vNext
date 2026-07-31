/**
 * Loads the TeaVM-compiled WHO IITT engine (`/emergency/iitt-engine.js`) and evaluates facts.
 * TypeScript must not re-derive acuity — it only presents the engine's JSON result.
 *
 * clinical-logic-guard: loader only; WhoIittEngine in libs/emergency-domain owns all criteria
 */

export type IittFactsInput = {
  ageDays?: number | null;
  pregnant?: boolean | null;
  signs?: Record<string, boolean>;
  vitals?: Record<string, number>;
};

export type IittEngineResult = {
  priority: string;
  chart: string;
  destination: string;
  requiresClinicianReview: boolean;
  triggeringFindings: string[];
  highRiskVitalSigns: string[];
  unassessedInputs: string[];
  triggeringCriteriaCodes: string[];
  error?: boolean;
  message?: string;
};

type EngineApi = {
  ready?: boolean;
  evaluate?: (factsJson: string) => string;
};

declare global {
  interface Window {
    __IITT_ENGINE__?: EngineApi;
    main?: (args: string[]) => void;
  }
}

let loadPromise: Promise<EngineApi | null> | null = null;

function bootstrapEngine(): EngineApi | null {
  // TeaVM UMD exports main(); the evaluate export is installed when main runs.
  if (typeof window.main === "function" && !window.__IITT_ENGINE__?.evaluate) {
    try {
      window.main([]);
    } catch {
      // evaluate install may still have succeeded before a thrown path
    }
  }
  return window.__IITT_ENGINE__?.evaluate ? window.__IITT_ENGINE__ : null;
}

export function loadIittBrowserEngine(): Promise<EngineApi | null> {
  if (typeof window === "undefined") {
    return Promise.resolve(null);
  }
  const already = bootstrapEngine();
  if (already) {
    return Promise.resolve(already);
  }
  if (loadPromise) {
    return loadPromise;
  }

  loadPromise = new Promise((resolve) => {
    const finish = () => resolve(bootstrapEngine());
    const existing = document.querySelector<HTMLScriptElement>('script[data-iitt-engine="1"]');
    if (existing) {
      existing.addEventListener("load", finish);
      existing.addEventListener("error", () => resolve(null));
      // Script may already have loaded before listeners attached.
      finish();
      return;
    }
    const script = document.createElement("script");
    script.src = "/emergency/iitt-engine.js";
    script.async = true;
    script.dataset.iittEngine = "1";
    script.onload = finish;
    script.onerror = () => resolve(null);
    document.head.appendChild(script);
  });

  return loadPromise;
}

export async function evaluateIittInBrowser(facts: IittFactsInput): Promise<IittEngineResult | null> {
  const engine = await loadIittBrowserEngine();
  if (!engine?.evaluate) {
    return null;
  }
  const raw = engine.evaluate(JSON.stringify(facts));
  try {
    return JSON.parse(raw) as IittEngineResult;
  } catch {
    return null;
  }
}
