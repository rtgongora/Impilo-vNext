/** Pure redirect target for the `/my-life` shim. Kept out of `page.tsx` so Next.js
 * page-export rules stay happy — a page module may only export the page itself. */
export function resolveMyLifeTarget(searchParams: URLSearchParams): string {
  const query = searchParams.toString();
  return query ? `/home?${query}` : "/home";
}
