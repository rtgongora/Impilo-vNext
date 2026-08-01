package zw.gov.mohcc.impilo.tshepo.contracts.v1.adapter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Checkpoint-2 compatibility adapter. Adapters exist only for Checkpoint-1 proven
 * callers, preserve legacy wire shapes, and must not broaden authority.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CompatibilityAdapterMeta {
    String owner();
    String[] provenCallers();
    String legacyShape();
    String canonicalShape();
    String removalCondition();
}
