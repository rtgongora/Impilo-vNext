package zw.gov.mohcc.impilo.emergency.triage.corpus;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

/**
 * TeaVM resource plugin: embed the shared IITT corpus in the browser bundle.
 *
 * <p>TeaVM omits classpath resources by default (third-party jars would otherwise
 * balloon the JS). Registering a {@link ResourceSupplier} via ServiceLoader is the
 * supported way to opt a specific file in — no path-literal hardcoding in callers.
 *
 * <p>Classpath-relative path (no leading slash), matching
 * {@code ClassLoader#getResourceAsStream}.
 */
public final class IittCorpusResourceSupplier implements ResourceSupplier {

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        return new String[] {"iitt-corpus/scenarios.json"};
    }
}
