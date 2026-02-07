package zw.gov.mohcc.impilo.oros.domain;

/**
 * Target system for order routing decisions.
 *
 * <ul>
 *   <li>{@code INTERNAL} — fulfilled within the OROS service (manual workstep progression)</li>
 *   <li>{@code LIMS} — routed to an external Laboratory Information Management System</li>
 *   <li>{@code PACS} — routed to a Picture Archiving and Communication System (Orthanc)</li>
 *   <li>{@code PHARMACY_EXT} — routed to an external pharmacy management system</li>
 *   <li>{@code OTHER} — routed to another external system via integration hub</li>
 * </ul>
 */
public enum RouteTarget {
    INTERNAL,
    LIMS,
    PACS,
    PHARMACY_EXT,
    OTHER
}
