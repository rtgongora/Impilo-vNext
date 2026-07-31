package zw.gov.mohcc.impilo.emergency.triage.corpus;

import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;

import java.util.List;

/**
 * TeaVM entry point for the W16a spike: load the shared corpus in the browser and report.
 *
 * <p>Invoked as the TeaVM {@code mainClass}. Writes a machine-readable line to the page title and
 * to {@code window.__IITT_CORPUS_RESULT__} so a headless runner can scrape pass/fail, scenario
 * count, and elapsed milliseconds without parsing console noise.
 */
public final class IittBrowserCorpusMain {

    private IittBrowserCorpusMain() {}

    public static void main(String[] args) {
        long started = System.currentTimeMillis();
        int count = IittCorpusRunner.scenarioCount();
        List<String> failures = IittCorpusRunner.runAll();
        long elapsed = System.currentTimeMillis() - started;

        String status = failures.isEmpty() ? "PASS" : "FAIL";
        String summary = status
                + " scenarios=" + count
                + " failures=" + failures.size()
                + " elapsedMs=" + elapsed;
        if (!failures.isEmpty()) {
            summary = summary + " first=" + failures.get(0);
        }

        publish(summary);
        if (!failures.isEmpty()) {
            throw new RuntimeException(summary + " :: " + String.join(" | ", failures));
        }
    }

    private static void publish(String summary) {
        try {
            Window.current().getDocument().setTitle(summary);
        } catch (Throwable ignored) {
            // headless / no DOM — still set the JS global below
        }
        setResult(summary);
    }

    @JSBody(params = { "value" }, script = "window.__IITT_CORPUS_RESULT__ = value;")
    private static native void setResult(String value);
}
