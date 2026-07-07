"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCreateGroup } from "@/hooks/queries/useSimbaSocial";

/** Create a new wellness-social group. */
export default function NewSocialGroupPage() {
  const router = useRouter();
  const create = useCreateGroup();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [topic, setTopic] = useState("");
  const [visibility, setVisibility] = useState("PUBLIC");
  const [joinPolicy, setJoinPolicy] = useState("OPEN");
  const [sensitive, setSensitive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function submit() {
    if (!name.trim()) {
      setError("A group name is required.");
      return;
    }
    setError(null);
    create.mutate(
      {
        name: name.trim(),
        description: description.trim() || undefined,
        topic: topic.trim() || undefined,
        visibility,
        join_policy: joinPolicy,
        sensitive_flag: sensitive,
      },
      {
        onSuccess: (res) => router.push(`/wellness/social/groups/${res.data.groupId}`),
        onError: (e) => setError(e.message),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="New group"
        subtitle="Start a supportive wellness group. Sensitive groups are consent-gated."
        icon={<Users className="h-6 w-6" />}
      >
        <div className="max-w-xl space-y-4">
          <label className="block">
            <span className="text-sm font-medium">Name</span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              placeholder="e.g. Morning Walkers"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium">Description</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium">Topic</span>
            <input
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              placeholder="e.g. fitness, diabetes support"
            />
          </label>
          <div className="grid grid-cols-2 gap-4">
            <label className="block">
              <span className="text-sm font-medium">Visibility</span>
              <select
                value={visibility}
                onChange={(e) => setVisibility(e.target.value)}
                className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="PUBLIC">Public</option>
                <option value="PRIVATE">Private</option>
                <option value="INVITE_ONLY">Invite only</option>
              </select>
            </label>
            <label className="block">
              <span className="text-sm font-medium">Join policy</span>
              <select
                value={joinPolicy}
                onChange={(e) => setJoinPolicy(e.target.value)}
                className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="OPEN">Open</option>
                <option value="REQUEST">Request to join</option>
                <option value="CLOSED">Closed</option>
              </select>
            </label>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={sensitive}
              onChange={(e) => setSensitive(e.target.checked)}
            />
            Sensitive group (e.g. mental-health support — consent-gated reads)
          </label>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex gap-2">
            <button
              onClick={submit}
              disabled={create.isPending}
              className="inline-flex items-center gap-1 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
            >
              {create.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Create group
            </button>
            <button
              onClick={() => router.push("/wellness/social/groups")}
              className="rounded-lg border border-border px-4 py-2 text-sm"
            >
              Cancel
            </button>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
