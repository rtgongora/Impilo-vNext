import { redirect } from "next/navigation";

/** Legacy stub path — real restrictions list lives under the regulatory org tree. */
export default async function Page({
  params,
}: {
  params: Promise<{ regulatorId: string }>;
}) {
  const { regulatorId } = await params;
  redirect(`/work/regulatory/${encodeURIComponent(regulatorId)}/restrictions`);
}
