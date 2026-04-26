import { redirect } from "next/navigation";

export default async function ReferralsLegacyPage({
  params,
}: {
  params: Promise<{ patientId: string }>;
}) {
  const { patientId } = await params;
  redirect(`/ehr/${patientId}/consults?tab=referrals`);
}
