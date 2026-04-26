import { redirect } from "next/navigation";

export default async function TeleconsultsLegacyPage({
  params,
}: {
  params: Promise<{ patientId: string }>;
}) {
  const { patientId } = await params;
  redirect(`/ehr/${patientId}/consults?tab=teleconsults`);
}
