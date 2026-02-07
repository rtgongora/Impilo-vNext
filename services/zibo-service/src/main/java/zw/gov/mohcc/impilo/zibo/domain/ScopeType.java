package zw.gov.mohcc.impilo.zibo.domain;

/**
 * Scope levels at which a terminology pack can be assigned.
 *
 * <p>Assignments cascade downward: a {@link #TENANT}-level assignment
 * applies to all facilities and workspaces within that tenant unless
 * overridden by a more specific scope.</p>
 *
 * <ul>
 *   <li>{@link #TENANT} -- applies across the entire tenant organisation</li>
 *   <li>{@link #FACILITY} -- applies to a specific health facility</li>
 *   <li>{@link #WORKSPACE} -- applies to a specific clinical workspace within a facility</li>
 * </ul>
 */
public enum ScopeType {
    TENANT,
    FACILITY,
    WORKSPACE
}
