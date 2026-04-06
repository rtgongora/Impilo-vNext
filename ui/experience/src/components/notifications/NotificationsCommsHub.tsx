"use client";
import { useState } from "react";
import { Bell, MessageSquare, AlertTriangle, Info, CheckCircle2, X, Send, Clock, Shield, Users, Megaphone, ArrowRightLeft } from "lucide-react";

type NotificationCategory = "all" | "system" | "handoff" | "announcement" | "message" | "alert";
type Priority = "low" | "normal" | "high" | "critical";

interface Notification {
  id: string; category: Exclude<NotificationCategory, "all">; priority: Priority;
  title: string; body: string; timestamp: string; read: boolean; actionUrl?: string;
  sender?: string;
}

const MOCK_NOTIFICATIONS: Notification[] = [
  { id: "1", category: "alert", priority: "critical", title: "Critical Lab Result", body: "Patient MRN-1042: Potassium 6.8 mEq/L — requires immediate attention", timestamp: "2 min ago", read: false, actionUrl: "/ehr/pt-1042/results" },
  { id: "2", category: "handoff", priority: "high", title: "Shift Handoff — ICU", body: "3 critical patients, 1 pending intubation. Dr. Sibanda handing to Dr. Moyo.", timestamp: "15 min ago", read: false, sender: "Dr. Sibanda" },
  { id: "3", category: "message", priority: "normal", title: "Lab Results Ready", body: "FBC and U&E results for Patient Ndlovu are now available.", timestamp: "30 min ago", read: false, sender: "Lab" },
  { id: "4", category: "announcement", priority: "normal", title: "System Maintenance", body: "Scheduled downtime tonight 22:00-02:00 for database migration.", timestamp: "1 hr ago", read: true, sender: "IT" },
  { id: "5", category: "system", priority: "low", title: "Backup Complete", body: "Daily backup completed successfully at 03:00.", timestamp: "3 hrs ago", read: true },
  { id: "6", category: "alert", priority: "high", title: "Drug Interaction Warning", body: "Patient MRN-1015: Rifampicin + DTG interaction flagged by CDS.", timestamp: "45 min ago", read: false, actionUrl: "/ehr/pt-1015/medications" },
  { id: "7", category: "handoff", priority: "normal", title: "Ward Round Summary", body: "Medical Ward: 18 patients reviewed, 3 for discharge, 1 escalation.", timestamp: "2 hrs ago", read: true, sender: "Sr. Ndlovu" },
  { id: "8", category: "message", priority: "normal", title: "Referral Accepted", body: "Cardiology accepted referral for Patient Chirwa. Appointment Monday.", timestamp: "4 hrs ago", read: true, sender: "Cardiology" },
  { id: "9", category: "announcement", priority: "high", title: "New ART Guidelines", body: "Updated ART guidelines effective immediately. See attached protocol.", timestamp: "5 hrs ago", read: false, sender: "Clinical Governance" },
  { id: "10", category: "system", priority: "low", title: "Session Expiring", body: "Your session will expire in 15 minutes. Save your work.", timestamp: "12 min ago", read: false },
];

const categoryIcons: Record<string, React.ElementType> = { system: Shield, handoff: ArrowRightLeft, announcement: Megaphone, message: MessageSquare, alert: AlertTriangle };
const priorityColor: Record<Priority, string> = { critical: "bg-red-100 text-red-700 border-red-200", high: "bg-amber-100 text-amber-700 border-amber-200", normal: "bg-blue-100 text-blue-700 border-blue-200", low: "bg-gray-100 text-gray-600 border-gray-200" };

export function NotificationsCommsHub() {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<NotificationCategory>("all");
  const [notifications, setNotifications] = useState(MOCK_NOTIFICATIONS);
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");

  const unread = notifications.filter(n => !n.read).length;
  const filtered = filter === "all" ? notifications : notifications.filter(n => n.category === filter);

  const markRead = (id: string) => setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
  const dismiss = (id: string) => setNotifications(prev => prev.filter(n => n.id !== id));

  const categories: { id: NotificationCategory; label: string }[] = [
    { id: "all", label: "All" }, { id: "alert", label: "Alerts" }, { id: "handoff", label: "Handoffs" },
    { id: "message", label: "Messages" }, { id: "announcement", label: "Notices" }, { id: "system", label: "System" },
  ];

  return (
    <div className="relative">
      <button onClick={() => setOpen(!open)} className="relative p-2 rounded-lg hover:bg-gray-100 transition-colors">
        <Bell className="w-5 h-5 text-gray-600" />
        {unread > 0 && <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">{unread}</span>}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-2 w-96 bg-white rounded-lg shadow-xl border z-50 overflow-hidden">
            <div className="px-4 py-3 border-b flex items-center justify-between">
              <div><h3 className="text-sm font-semibold text-gray-900">Notifications</h3><p className="text-xs text-gray-500">{unread} unread</p></div>
              <button onClick={() => setNotifications(prev => prev.map(n => ({ ...n, read: true })))} className="text-xs text-blue-600 hover:underline">Mark all read</button>
            </div>

            <div className="flex gap-1 px-3 py-2 border-b overflow-x-auto">
              {categories.map(c => (
                <button key={c.id} onClick={() => setFilter(c.id)} className={`px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap ${filter === c.id ? "bg-blue-100 text-blue-700" : "text-gray-500 hover:bg-gray-100"}`}>{c.label}</button>
              ))}
            </div>

            <div className="max-h-[420px] overflow-y-auto divide-y divide-gray-100">
              {filtered.length === 0 ? (
                <div className="p-8 text-center"><Bell className="w-8 h-8 text-gray-300 mx-auto mb-2" /><p className="text-sm text-gray-400">No notifications</p></div>
              ) : filtered.map(n => {
                const Icon = categoryIcons[n.category] || Info;
                return (
                  <div key={n.id} className={`px-4 py-3 hover:bg-gray-50 transition-colors ${!n.read ? "bg-blue-50/30" : ""}`} onClick={() => markRead(n.id)}>
                    <div className="flex items-start gap-3">
                      <div className={`mt-0.5 w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${n.priority === "critical" ? "bg-red-100" : n.priority === "high" ? "bg-amber-100" : "bg-gray-100"}`}>
                        <Icon className={`w-4 h-4 ${n.priority === "critical" ? "text-red-600" : n.priority === "high" ? "text-amber-600" : "text-gray-500"}`} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <p className={`text-sm ${!n.read ? "font-semibold text-gray-900" : "font-medium text-gray-700"}`}>{n.title}</p>
                          <button onClick={(e) => { e.stopPropagation(); dismiss(n.id); }} className="p-1 rounded hover:bg-gray-200"><X className="w-3 h-3 text-gray-400" /></button>
                        </div>
                        <p className="text-xs text-gray-600 mt-0.5 line-clamp-2">{n.body}</p>
                        <div className="flex items-center gap-2 mt-1.5">
                          <span className="text-[10px] text-gray-400 flex items-center gap-1"><Clock className="w-3 h-3" />{n.timestamp}</span>
                          {n.sender && <span className="text-[10px] text-gray-400">&middot; {n.sender}</span>}
                          <span className={`px-1.5 py-0 rounded text-[9px] font-medium ${priorityColor[n.priority]}`}>{n.priority}</span>
                        </div>
                        {n.category === "message" && (
                          <div className="mt-2">
                            {replyTo === n.id ? (
                              <div className="flex gap-1"><input value={replyText} onChange={e => setReplyText(e.target.value)} placeholder="Type reply..." className="flex-1 px-2 py-1 text-xs border rounded" /><button onClick={() => { setReplyTo(null); setReplyText(""); }} className="px-2 py-1 bg-blue-600 text-white rounded text-xs"><Send className="w-3 h-3" /></button></div>
                            ) : <button onClick={(e) => { e.stopPropagation(); setReplyTo(n.id); }} className="text-xs text-blue-600 hover:underline">Reply</button>}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
