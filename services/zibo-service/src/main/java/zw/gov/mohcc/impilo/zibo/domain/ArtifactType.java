package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Types of FHIR terminology and conformance artifacts managed by ZIBO.
 *
 * <p>Each type corresponds to a FHIR R4 resource type:</p>
 * <ul>
 *   <li>{@link #CODE_SYSTEM} -- defines a set of coded concepts</li>
 *   <li>{@link #VALUE_SET} -- selects concepts from one or more code systems</li>
 *   <li>{@link #CONCEPT_MAP} -- maps concepts between code systems</li>
 *   <li>{@link #NAMING_SYSTEM} -- defines identifier systems (OIDs, URIs)</li>
 *   <li>{@link #STRUCTURE_DEFINITION} -- profiles and extensions</li>
 *   <li>{@link #IMPLEMENTATION_GUIDE} -- bundles of conformance resources</li>
 *   <li>{@link #OBSERVATION_DEFINITION} -- governed reference intervals
 *       (FHIR R4 ObservationDefinition.qualifiedInterval), keyed by
 *       age/sex/gestation/condition, for context-aware interpretation of
 *       vitals, standard analytes and derived values</li>
 * </ul>
 *
 * <p><strong>Append-only:</strong> this enum is persisted by ordinal in some
 * historical rows; never reorder or insert values — only append.</p>
 */
public enum ArtifactType {
    CODE_SYSTEM,
    VALUE_SET,
    CONCEPT_MAP,
    NAMING_SYSTEM,
    STRUCTURE_DEFINITION,
    IMPLEMENTATION_GUIDE,
    OBSERVATION_DEFINITION
}
