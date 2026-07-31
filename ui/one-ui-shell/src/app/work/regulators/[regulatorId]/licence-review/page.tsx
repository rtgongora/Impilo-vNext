import { redirect } from "next/navigation";

/** Legacy stub — licence/application review uses the registration-review surface. */
export default async function Page({
  params,
}: {
  params: Promise<{ regulatorId: string }>;
}) {
  const { regulatorId } = await params;
  redirect(`/work/regulators/${encodeURIComponent(regulatorId)}/registration-review`);
}
