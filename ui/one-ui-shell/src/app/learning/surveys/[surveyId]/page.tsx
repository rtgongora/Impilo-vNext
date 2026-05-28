import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LearningSurveyPage({ params }: { params: { surveyId: string } }) {
  return (
    <AppLayout>
      <PageShell title="Learning Survey" subtitle="Survey details, purpose and response options.">
        <div className="rounded border border-gray-200 bg-white p-4 text-sm text-gray-700">
          Survey id: <span className="font-mono">{params.surveyId}</span>
          <div className="mt-2">
            <Link href={`/learning/surveys/${params.surveyId}/respond`} className="text-teal-700 hover:underline">
              Respond now
            </Link>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
