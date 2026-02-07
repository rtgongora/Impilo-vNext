package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Validation policy mode governing how strictly terminology bindings are enforced.
 *
 * <ul>
 *   <li>{@link #STRICT} -- validation errors are raised for any code not
 *       present in the assigned terminology pack; non-conforming submissions
 *       are rejected.</li>
 *   <li>{@link #LENIENT} -- validation warnings are generated for unrecognised
 *       codes but submissions are still accepted. This is the recommended
 *       starting mode for new facility rollouts.</li>
 * </ul>
 */
public enum PolicyMode {
    STRICT,
    LENIENT
}
