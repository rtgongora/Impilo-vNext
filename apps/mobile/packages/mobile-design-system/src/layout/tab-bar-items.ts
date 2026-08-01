/** Keep the bottom bar calm while preserving every destination under More. */
export function partitionTabItems<T>(items: T[], maxVisibleItems = 5) {
  if (maxVisibleItems < 2) throw new Error("TabBar needs room for a destination and More");
  if (items.length <= maxVisibleItems) {
    return { primaryTabs: items, overflowTabs: [] as T[] };
  }
  const primaryCount = maxVisibleItems - 1;
  return {
    primaryTabs: items.slice(0, primaryCount),
    overflowTabs: items.slice(primaryCount),
  };
}
