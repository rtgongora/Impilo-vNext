"use client";

/**
 * Ask — Conversational Health Assistant (Health OS §2a)
 *
 * "The experience layer is intelligent: it listens, understands, searches,
 *  responds, guides, reminds, alerts, and acts within governed boundaries."
 *
 * Chat-based interaction surface for health, wellness, lifestyle, diet, sleep,
 * service discovery, and general guidance. Context-aware, consent-aware, and
 * proportionate to the user's role and assurance level.
 */

import { useState, useRef, useEffect } from "react";
import { MessageSquare, Send, Loader2, Sparkles } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
}

export default function AskPage() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: "welcome",
      role: "assistant",
      content: "Hello! I can help you with health, wellness, lifestyle, diet, sleep, service discovery, and general guidance. What would you like to know?",
      timestamp: new Date().toISOString(),
    },
  ]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  function handleSend(e: React.FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || sending) return;

    const userMsg: Message = {
      id: crypto.randomUUID(),
      role: "user",
      content: text,
      timestamp: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setSending(true);

    // Placeholder — will be replaced with BFF /internal/v1/guidance/ask endpoint
    setTimeout(() => {
      const assistantMsg: Message = {
        id: crypto.randomUUID(),
        role: "assistant",
        content: `Thank you for your question about "${text.slice(0, 60)}". The conversational guidance service is being connected to governed health knowledge repositories. This will provide evidence-based, context-aware responses within your consent settings and safety guardrails.`,
        timestamp: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, assistantMsg]);
      setSending(false);
    }, 1200);
  }

  return (
    <AppLayout>
      <PageShell title="Ask" subtitle="Conversational health and wellness guidance" icon={<MessageSquare className="h-6 w-6" />}>
        <div className="flex flex-col h-[calc(100vh-220px)] max-w-2xl mx-auto">
          {/* Messages */}
          <div className="flex-1 overflow-y-auto space-y-4 pb-4">
            {messages.map((msg) => (
              <div key={msg.id} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                <div className={`max-w-[80%] rounded-2xl px-4 py-3 text-sm ${
                  msg.role === "user"
                    ? "bg-blue-600 text-white rounded-br-md"
                    : "bg-gray-100 text-gray-900 rounded-bl-md"
                }`}>
                  {msg.role === "assistant" && (
                    <div className="flex items-center gap-1 mb-1 text-xs text-gray-500">
                      <Sparkles className="h-3 w-3" /> Health OS Guidance
                    </div>
                  )}
                  <p className="whitespace-pre-wrap">{msg.content}</p>
                </div>
              </div>
            ))}
            {sending && (
              <div className="flex justify-start">
                <div className="bg-gray-100 rounded-2xl rounded-bl-md px-4 py-3 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin inline mr-1" /> Thinking...
                </div>
              </div>
            )}
            <div ref={endRef} />
          </div>

          {/* Input */}
          <form onSubmit={handleSend} className="flex gap-2 border-t border-gray-200 pt-4">
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask about health, wellness, diet, sleep, services..."
              className="flex-1 rounded-full border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-200"
              disabled={sending}
            />
            <button
              type="submit"
              disabled={sending || !input.trim()}
              className="rounded-full bg-blue-600 p-2.5 text-white hover:bg-blue-700 disabled:opacity-50 transition-colors"
            >
              <Send className="h-4 w-4" />
            </button>
          </form>
        </div>
      </PageShell>
    </AppLayout>
  );
}
