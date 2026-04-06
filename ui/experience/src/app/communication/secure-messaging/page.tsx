"use client";

/**
 * Secure Messaging — Clinical messaging with conversations, threads, and attachments.
 * Route: /communication/secure-messaging | pageTitle: "Secure Messaging"
 */

import { useState } from "react";
import { MessageSquare, Loader2, Send, Paperclip, Search, Plus, CheckCheck, Check, AlertCircle, Star } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Message {
  id: string;
  sender: string;
  senderInitials: string;
  content: string;
  timestamp: string;
  isOwn: boolean;
  read: boolean;
  attachment: string | null;
}

interface Conversation {
  id: string;
  participantName: string;
  participantRole: string;
  participantInitials: string;
  lastMessage: string;
  lastMessageTime: string;
  unreadCount: number;
  priority: boolean;
  status: "online" | "offline" | "busy";
}

const MOCK_CONVERSATIONS: Conversation[] = [
  { id: "c-1", participantName: "Dr. T. Moyo", participantRole: "Cardiology", participantInitials: "TM", lastMessage: "The echocardiogram results look normal. LVEF is 55%. No valvular abnormalities.", lastMessageTime: "09:42", unreadCount: 2, priority: true, status: "online" },
  { id: "c-2", participantName: "Sr. N. Phiri", participantRole: "General Ward", participantInitials: "NP", lastMessage: "Patient in Bed 12 is complaining of increased pain. Current scale 7/10.", lastMessageTime: "09:30", unreadCount: 1, priority: true, status: "online" },
  { id: "c-3", participantName: "Lab Department", participantRole: "Pathology", participantInitials: "LB", lastMessage: "CBC results for patient Masuku are ready. WBC elevated at 14.2.", lastMessageTime: "09:15", unreadCount: 0, priority: false, status: "online" },
  { id: "c-4", participantName: "Dr. S. Chirwa", participantRole: "Psychiatry", participantInitials: "SC", lastMessage: "Thank you for the referral. I can see the patient on Wednesday.", lastMessageTime: "Yesterday", unreadCount: 0, priority: false, status: "offline" },
  { id: "c-5", participantName: "Pharmacy", participantRole: "Dispensary", participantInitials: "PH", lastMessage: "Amoxicillin 500mg is out of stock. Can we substitute with co-amoxiclav?", lastMessageTime: "Yesterday", unreadCount: 0, priority: false, status: "busy" },
  { id: "c-6", participantName: "Dr. K. Dlamini", participantRole: "Obstetrics", participantInitials: "KD", lastMessage: "Patient transferred to maternity ward as discussed.", lastMessageTime: "04 Apr", unreadCount: 0, priority: false, status: "offline" },
];

const MOCK_MESSAGES: Record<string, Message[]> = {
  "c-1": [
    { id: "m-1", sender: "You", senderInitials: "MN", content: "Hi Dr. Moyo, could you review the echocardiogram for patient Dube? Referred for assessment of suspected heart failure.", timestamp: "09:20", isOwn: true, read: true, attachment: null },
    { id: "m-2", sender: "Dr. T. Moyo", senderInitials: "TM", content: "Sure, I will review it now. Which bed number?", timestamp: "09:25", isOwn: false, read: true, attachment: null },
    { id: "m-3", sender: "You", senderInitials: "MN", content: "Bed 8, General Medical Ward. Patient has been experiencing progressive dyspnoea over 2 weeks.", timestamp: "09:28", isOwn: true, read: true, attachment: null },
    { id: "m-4", sender: "Dr. T. Moyo", senderInitials: "TM", content: "Reviewed the echo. LVEF is 55%, which is within normal range. No significant valvular abnormalities. Mild diastolic dysfunction noted — could explain symptoms in the context of HTN.", timestamp: "09:38", isOwn: false, read: true, attachment: "Echo_Report_Dube_2026-04-06.pdf" },
    { id: "m-5", sender: "Dr. T. Moyo", senderInitials: "TM", content: "The echocardiogram results look normal. LVEF is 55%. No valvular abnormalities. Recommend optimising BP control and consider BNP levels.", timestamp: "09:42", isOwn: false, read: false, attachment: null },
  ],
  "c-2": [
    { id: "m-6", sender: "Sr. N. Phiri", senderInitials: "NP", content: "Dr. Ndlovu, patient in Bed 12 (Mrs. Zimuto) is complaining of increased pain. Current pain scale 7/10. She received paracetamol 1g at 07:00.", timestamp: "09:30", isOwn: false, read: false, attachment: null },
  ],
};

const STATUS_COLORS: Record<string, string> = {
  online: "bg-green-400",
  offline: "bg-gray-300",
  busy: "bg-red-400",
};

export default function SecureMessagingPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["secure-messages"],
    queryFn: async () => ({ conversations: MOCK_CONVERSATIONS, messages: MOCK_MESSAGES }),
  });

  const conversations = data?.conversations ?? [];
  const allMessages = data?.messages ?? {};
  const [selectedConvo, setSelectedConvo] = useState<string>("c-1");
  const [newMessage, setNewMessage] = useState("");
  const [searchQuery, setSearchQuery] = useState("");

  const currentConvo = conversations.find((c) => c.id === selectedConvo);
  const messages = allMessages[selectedConvo] ?? [];
  const filteredConvos = searchQuery
    ? conversations.filter((c) => c.participantName.toLowerCase().includes(searchQuery.toLowerCase()))
    : conversations;

  return (
    <AppLayout>
      <PageShell title="Secure Messaging" subtitle="Encrypted clinical messaging">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading messages...</span>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden" style={{ height: "calc(100vh - 220px)" }}>
            <div className="flex h-full">
              {/* Conversation List (left panel) */}
              <div className="w-80 border-r border-gray-200 flex flex-col shrink-0">
                <div className="p-3 border-b border-gray-200">
                  <div className="flex items-center justify-between mb-2">
                    <h3 className="font-medium text-gray-900 text-sm">Messages</h3>
                    <button className="p-1.5 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="relative">
                    <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="w-full pl-9 pr-3 py-2 rounded-lg border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="Search conversations..."
                    />
                  </div>
                </div>
                <div className="flex-1 overflow-y-auto">
                  {filteredConvos.map((convo) => (
                    <button
                      key={convo.id}
                      onClick={() => setSelectedConvo(convo.id)}
                      className={`w-full px-3 py-3 flex items-start gap-3 hover:bg-gray-50 transition-colors text-left border-b border-gray-50 ${selectedConvo === convo.id ? "bg-blue-50" : ""}`}
                    >
                      <div className="relative shrink-0">
                        <div className="w-10 h-10 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-sm font-semibold">
                          {convo.participantInitials}
                        </div>
                        <div className={`absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white ${STATUS_COLORS[convo.status]}`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium text-gray-900 truncate flex items-center gap-1">
                            {convo.participantName}
                            {convo.priority && <Star className="w-3 h-3 text-amber-500 fill-amber-500" />}
                          </span>
                          <span className="text-[10px] text-gray-400 shrink-0">{convo.lastMessageTime}</span>
                        </div>
                        <p className="text-xs text-gray-400">{convo.participantRole}</p>
                        <p className="text-xs text-gray-500 truncate mt-0.5">{convo.lastMessage}</p>
                      </div>
                      {convo.unreadCount > 0 && (
                        <span className="bg-blue-600 text-white text-[10px] font-bold rounded-full w-5 h-5 flex items-center justify-center shrink-0">
                          {convo.unreadCount}
                        </span>
                      )}
                    </button>
                  ))}
                </div>
              </div>

              {/* Message Thread (center) */}
              <div className="flex-1 flex flex-col min-w-0">
                {/* Thread Header */}
                {currentConvo && (
                  <div className="px-5 py-3 border-b border-gray-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-sm font-semibold">
                        {currentConvo.participantInitials}
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">{currentConvo.participantName}</p>
                        <p className="text-[10px] text-gray-400">{currentConvo.participantRole} &middot; {currentConvo.status}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 text-[10px] text-green-600">
                      <AlertCircle className="w-3 h-3" /> End-to-end encrypted
                    </div>
                  </div>
                )}

                {/* Messages */}
                <div className="flex-1 overflow-y-auto p-5 space-y-4">
                  {messages.map((msg) => (
                    <div key={msg.id} className={`flex ${msg.isOwn ? "justify-end" : "justify-start"}`}>
                      <div className={`max-w-md ${msg.isOwn ? "order-2" : ""}`}>
                        <div className={`rounded-lg px-4 py-2.5 ${msg.isOwn ? "bg-blue-600 text-white" : "bg-gray-100 text-gray-800"}`}>
                          <p className="text-sm leading-relaxed">{msg.content}</p>
                          {msg.attachment && (
                            <div className={`mt-2 flex items-center gap-1.5 text-xs ${msg.isOwn ? "text-blue-200" : "text-blue-600"}`}>
                              <Paperclip className="w-3 h-3" />
                              <span className="underline cursor-pointer">{msg.attachment}</span>
                            </div>
                          )}
                        </div>
                        <div className={`flex items-center gap-1 mt-1 ${msg.isOwn ? "justify-end" : ""}`}>
                          <span className="text-[10px] text-gray-400">{msg.timestamp}</span>
                          {msg.isOwn && (
                            msg.read ? <CheckCheck className="w-3 h-3 text-blue-500" /> : <Check className="w-3 h-3 text-gray-400" />
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Message Input */}
                <div className="px-5 py-3 border-t border-gray-200">
                  <div className="flex items-center gap-2">
                    <button className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 transition-colors">
                      <Paperclip className="w-5 h-5" />
                    </button>
                    <input
                      type="text"
                      value={newMessage}
                      onChange={(e) => setNewMessage(e.target.value)}
                      className="flex-1 rounded-lg border border-gray-200 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      placeholder="Type a message..."
                      onKeyDown={(e) => { if (e.key === "Enter" && newMessage.trim()) setNewMessage(""); }}
                    />
                    <button
                      disabled={!newMessage.trim()}
                      className="p-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      <Send className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>

              {/* Participant Info (right panel) */}
              {currentConvo && (
                <div className="w-64 border-l border-gray-200 p-4 hidden lg:block">
                  <div className="text-center mb-4">
                    <div className="w-16 h-16 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xl font-semibold mx-auto mb-2">
                      {currentConvo.participantInitials}
                    </div>
                    <p className="font-medium text-gray-900">{currentConvo.participantName}</p>
                    <p className="text-xs text-gray-500">{currentConvo.participantRole}</p>
                    <div className={`inline-flex items-center gap-1 mt-1 text-xs ${currentConvo.status === "online" ? "text-green-600" : "text-gray-400"}`}>
                      <div className={`w-2 h-2 rounded-full ${STATUS_COLORS[currentConvo.status]}`} />
                      {currentConvo.status}
                    </div>
                  </div>
                  <div className="space-y-3 text-xs text-gray-500">
                    <div>
                      <p className="font-medium text-gray-700">Shared Files</p>
                      <p className="mt-1">
                        {messages.filter((m) => m.attachment).length} attachment{messages.filter((m) => m.attachment).length !== 1 ? "s" : ""}
                      </p>
                    </div>
                    <div>
                      <p className="font-medium text-gray-700">Messages</p>
                      <p className="mt-1">{messages.length} in thread</p>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
