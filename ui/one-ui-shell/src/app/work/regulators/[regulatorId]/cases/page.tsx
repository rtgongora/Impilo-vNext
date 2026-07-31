import { redirect } from "next/navigation";

/** Legacy stub — disciplinary / cases live under registration-adjacent regulator surfaces. */
export default async function Page({
  params,
}: {
  params: Promise<{ regulatorId: string }>;
}) {
  const { regulatorId } = await params;
  redirect(`/work/regulators/${encodeURIComponent(regulatorId)}/disciplinary`);
}
