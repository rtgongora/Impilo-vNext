'use client';

import { useState, useEffect } from 'react';
import {
  Bell, BellOff, MessageSquare, ClipboardList, Megaphone,
  AlertTriangle, CheckCircle, Clock, ArrowRight, Send, X,
  Volume2, VolumeX, Shield, ArrowRightLeft, Phone, Loader2,
} from 'lucide-react';
import { useNotifications, useMarkNotificationRead } from '@/hooks/queries/useNotifications';

// ─── Types ───

type NotificationCategory = 'system' | 'handoff' | 'announcement' | 'message' | 'alert';
type FilterCategory = NotificationCategory | 'all';
type Priority = 'low' | 'normal' | 'high' | 'critical';

interface Notification {
  id: string;
  category: NotificationCategory;
  priority: Priority;
  title: string;
  body: string;
  timestamp: string;
  read: boolean;
  actionUrl?: string;
  actionLabel?: string;
  sender?: string;
  workspaceScoped?: boolean;
  requiresAck?: boolean;
}

// ─── Data fetched via useNotifications hook ───

// ─── Helpers ───

const CATEGORY_ICONS: Record<NotificationCategory, React.ComponentType<{ className?: string }>> = {
  system: Shield,
  handoff: ArrowRightLeft,
  announcement: Megaphone,
  message: MessageSquare,
  alert: AlertTriangle,
};

const PRIORITY_ICON_COLORS: Record<Priority, string> = {
  critical: 'bg-red-100 text-red-600',
  high: 'bg-amber-100 text-amber-600',
  normal: 'bg-impilo-100 text-impilo-500',
  low: 'bg-gray-100 text-gray-500',
};

const PRIORITY_BADGE_COLORS: Record<Priority, string> = {
  critical: 'bg-red-100 text-red-700 border border-red-200',
  high: 'bg-amber-100 text-amber-700 border border-amber-200',
  normal: 'bg-impilo-100 text-impilo-600 border border-impilo-200',
  low: 'bg-gray-100 text-gray-600 border border-gray-200',
};

const FILTER_TABS: { id: FilterCategory; label: string; icon?: React.ComponentType<{ className?: string }> }[] = [
  { id: 'all', label: 'All' },
  { id: 'alert', label: 'Alerts', icon: AlertTriangle },
  { id: 'handoff', label: 'Handoffs', icon: ClipboardList },
  { id: 'message', label: 'Messages', icon: MessageSquare },
  { id: 'announcement', label: 'Notices', icon: Megaphone },
  { id: 'system', label: 'System', icon: Shield },
];

// ─── Component ───

type PanelView = 'list' | 'compose' | 'page';

export function NotificationsCommsHub() {
  const { data: notificationsData, isLoading, isError } = useNotifications();
  const markReadMutation = useMarkNotificationRead();
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<FilterCategory>('all');
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [isMuted, setIsMuted] = useState(false);

  // Sync remote data into local state for optimistic UI updates
  useEffect(() => {
    if (notificationsData?.data) {
      setNotifications(notificationsData.data as Notification[]);
    }
  }, [notificationsData]);
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyText, setReplyText] = useState('');
  const [panelView, setPanelView] = useState<PanelView>('list');
  // Compose state
  const [composeRecipient, setComposeRecipient] = useState('');
  const [composeSubject, setComposeSubject] = useState('');
  const [composeBody, setComposeBody] = useState('');
  const [composePriority, setComposePriority] = useState<Priority>('normal');
  // Page state
  const [pageRecipient, setPageRecipient] = useState('');
  const [pageUrgency, setPageUrgency] = useState<'routine' | 'urgent' | 'stat'>('routine');
  const [pageMessage, setPageMessage] = useState('');
  const [pageCallback, setPageCallback] = useState('');

  const unreadCount = notifications.filter(n => !n.read).length;
  const hasCritical = notifications.some(n => !n.read && (n.priority === 'critical' || n.priority === 'high'));
  const filtered = filter === 'all' ? notifications : notifications.filter(n => n.category === filter);

  // Per-category unread counts
  const categoryUnread: Record<string, number> = {
    alert: notifications.filter(n => n.category === 'alert' && !n.read).length,
    handoff: notifications.filter(n => n.category === 'handoff' && !n.read).length,
    message: notifications.filter(n => n.category === 'message' && !n.read).length,
    announcement: notifications.filter(n => n.category === 'announcement' && !n.read).length,
    system: notifications.filter(n => n.category === 'system' && !n.read).length,
  };

  const handleSendMessage = () => {
    if (!composeRecipient || !composeBody) return;
    setNotifications(prev => [{
      id: `sent-${Date.now()}`, category: 'message' as const, priority: composePriority, title: composeSubject || 'Message', body: composeBody,
      timestamp: 'Just now', read: true, sender: 'You → ' + composeRecipient, workspaceScoped: false,
    }, ...prev]);
    setComposeRecipient(''); setComposeSubject(''); setComposeBody(''); setComposePriority('normal');
    setPanelView('list');
  };

  const handleSendPage = () => {
    if (!pageRecipient || !pageMessage) return;
    setNotifications(prev => [{
      id: `page-${Date.now()}`, category: 'alert' as const, priority: pageUrgency === 'stat' ? 'critical' as const : pageUrgency === 'urgent' ? 'high' as const : 'normal' as const,
      title: `Page: ${pageRecipient}`, body: pageMessage + (pageCallback ? ` [Callback: ${pageCallback}]` : ''),
      timestamp: 'Just now', read: true, sender: 'You', workspaceScoped: false,
    }, ...prev]);
    setPageRecipient(''); setPageMessage(''); setPageCallback(''); setPageUrgency('routine');
    setPanelView('list');
  };

  const markRead = (id: string) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
    markReadMutation.mutate(id);
  };

  const markAllRead = () => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  };

  const dismiss = (id: string) => {
    setNotifications(prev => prev.filter(n => n.id !== id));
  };

  const acknowledge = (id: string) => {
    setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true, requiresAck: false } : n));
  };

  const sendReply = () => {
    setReplyTo(null);
    setReplyText('');
  };

  return (
    <div className="relative">
      {/* Bell trigger */}
      <button
        onClick={() => setOpen(!open)}
        className={`relative inline-flex items-center gap-1.5 px-2 py-1.5 rounded-lg hover:bg-gray-100 transition-colors ${hasCritical && !isMuted ? 'text-red-600' : 'text-gray-600'}`}
      >
        <Bell className="h-4 w-4" />
        <span className="text-xs font-medium">Comms</span>
        {unreadCount > 0 && (
          <span className={`h-4 min-w-[1rem] px-1 rounded-full text-[9px] font-bold flex items-center justify-center bg-red-500 text-white ${hasCritical && !isMuted ? 'animate-pulse' : ''}`}>
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Popover */}
      {open && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-full mt-2 w-[400px] bg-white rounded-lg shadow-xl border border-gray-200 z-50 overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-gray-200">
              <div className="flex items-center gap-2">
                <h4 className="text-sm font-semibold">Notifications & Comms</h4>
                {unreadCount > 0 && (
                  <span className="px-2 py-0.5 rounded-full bg-gray-100 text-[10px] font-medium">{unreadCount} new</span>
                )}
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={markAllRead}
                  className="text-xs text-impilo-500 hover:underline mr-2"
                >
                  Mark all read
                </button>
                <button
                  onClick={() => setIsMuted(!isMuted)}
                  className="h-7 w-7 flex items-center justify-center rounded hover:bg-gray-100"
                  title={isMuted ? 'Unmute' : 'Mute sounds'}
                >
                  {isMuted ? <VolumeX className="h-3.5 w-3.5 text-gray-400" /> : <Volume2 className="h-3.5 w-3.5" />}
                </button>
              </div>
            </div>

            {/* Filter tabs */}
            <div className="flex gap-1 px-3 py-2 border-b border-gray-200 overflow-x-auto">
              {FILTER_TABS.map(tab => {
                const Icon = tab.icon;
                return (
                  <button
                    key={tab.id}
                    onClick={() => setFilter(tab.id)}
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap transition-colors ${
                      filter === tab.id ? 'bg-impilo-100 text-impilo-600' : 'text-gray-500 hover:bg-gray-100'
                    }`}
                  >
                    {Icon && <Icon className="h-3 w-3" />}
                    {tab.label}
                    {tab.id !== 'all' && (categoryUnread[tab.id] || 0) > 0 && (
                      <span className="ml-0.5 h-4 min-w-[1rem] px-0.5 rounded-full bg-impilo-500 text-white text-[9px] flex items-center justify-center">
                        {categoryUnread[tab.id]}
                      </span>
                    )}
                  </button>
                );
              })}
            </div>

            {/* Action buttons */}
            <div className="flex gap-2 px-3 py-2 border-b border-gray-200">
              <button onClick={() => setPanelView(panelView === 'compose' ? 'list' : 'compose')} className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${panelView === 'compose' ? 'bg-impilo-100 text-impilo-600' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                <Send className="w-3 h-3" /> New Message
              </button>
              <button onClick={() => setPanelView(panelView === 'page' ? 'list' : 'page')} className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${panelView === 'page' ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}>
                <Phone className="w-3 h-3" /> Send Page
              </button>
            </div>

            {/* Compose Message Panel */}
            {panelView === 'compose' && (
              <div className="p-3 border-b border-gray-200 bg-impilo-50/30 space-y-2">
                <h4 className="text-xs font-semibold text-gray-700">New Message</h4>
                <input value={composeRecipient} onChange={e => setComposeRecipient(e.target.value)} placeholder="To (name or role)..." className="w-full px-2.5 py-1.5 text-xs border rounded-lg" />
                <input value={composeSubject} onChange={e => setComposeSubject(e.target.value)} placeholder="Subject (optional)" className="w-full px-2.5 py-1.5 text-xs border rounded-lg" />
                <textarea value={composeBody} onChange={e => setComposeBody(e.target.value)} placeholder="Message..." rows={3} className="w-full px-2.5 py-1.5 text-xs border rounded-lg" />
                <div className="flex items-center justify-between">
                  <select value={composePriority} onChange={e => setComposePriority(e.target.value as Priority)} className="px-2 py-1 text-xs border rounded">
                    <option value="low">Low</option><option value="normal">Normal</option><option value="high">High</option><option value="critical">Critical</option>
                  </select>
                  <div className="flex gap-2">
                    <button onClick={() => setPanelView('list')} className="px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 rounded">Cancel</button>
                    <button onClick={handleSendMessage} disabled={!composeRecipient || !composeBody} className="px-3 py-1.5 text-xs bg-impilo-500 text-white rounded-lg hover:bg-impilo-600 disabled:opacity-50 flex items-center gap-1"><Send className="w-3 h-3" /> Send</button>
                  </div>
                </div>
              </div>
            )}

            {/* Send Page Panel */}
            {panelView === 'page' && (
              <div className="p-3 border-b border-gray-200 bg-amber-50/30 space-y-2">
                <h4 className="text-xs font-semibold text-gray-700">Clinical Page</h4>
                <input value={pageRecipient} onChange={e => setPageRecipient(e.target.value)} placeholder="Page to (Dr. Name / On-call team)..." className="w-full px-2.5 py-1.5 text-xs border rounded-lg" />
                <div className="flex gap-2">
                  {(['routine', 'urgent', 'stat'] as const).map(u => (
                    <button key={u} onClick={() => setPageUrgency(u)} className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${pageUrgency === u ? u === 'stat' ? 'bg-red-100 text-red-700' : u === 'urgent' ? 'bg-amber-100 text-amber-700' : 'bg-impilo-100 text-impilo-600' : 'bg-gray-100 text-gray-500'}`}>{u.toUpperCase()}</button>
                  ))}
                </div>
                <textarea value={pageMessage} onChange={e => setPageMessage(e.target.value)} placeholder="Message..." rows={2} className="w-full px-2.5 py-1.5 text-xs border rounded-lg" />
                <input value={pageCallback} onChange={e => setPageCallback(e.target.value)} placeholder="Callback number (optional)" className="w-full px-2.5 py-1.5 text-xs border rounded-lg" />
                <div className="flex justify-end gap-2">
                  <button onClick={() => setPanelView('list')} className="px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 rounded">Cancel</button>
                  <button onClick={handleSendPage} disabled={!pageRecipient || !pageMessage} className="px-3 py-1.5 text-xs bg-amber-600 text-white rounded-lg hover:bg-amber-700 disabled:opacity-50 flex items-center gap-1"><Phone className="w-3 h-3" /> Send Page</button>
                </div>
              </div>
            )}

            {/* Notification list */}
            <div className="max-h-[380px] overflow-y-auto divide-y divide-gray-100">
              {isLoading ? (
                <div className="flex flex-col items-center justify-center py-10 text-gray-400">
                  <Loader2 className="h-6 w-6 animate-spin mb-2" />
                  <p className="text-sm">Loading notifications...</p>
                </div>
              ) : isError ? (
                <div className="flex flex-col items-center justify-center py-10 text-gray-400">
                  <AlertTriangle className="h-6 w-6 mb-2 text-amber-400" />
                  <p className="text-sm">Failed to load notifications</p>
                  <p className="text-xs">Please try again later</p>
                </div>
              ) : filtered.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-gray-400">
                  <CheckCircle className="h-8 w-8 mb-2 opacity-40" />
                  <p className="text-sm">All caught up</p>
                  <p className="text-xs">No new notifications</p>
                </div>
              ) : (
                filtered.map(n => {
                  const CatIcon = CATEGORY_ICONS[n.category];
                  return (
                    <div
                      key={n.id}
                      className={`px-4 py-3 hover:bg-gray-50 transition-colors cursor-pointer ${!n.read ? 'bg-impilo-50/30' : ''}`}
                      onClick={() => markRead(n.id)}
                    >
                      <div className="flex items-start gap-3">
                        {/* Icon */}
                        <div className={`mt-0.5 h-7 w-7 rounded-full flex items-center justify-center shrink-0 ${PRIORITY_ICON_COLORS[n.priority]}`}>
                          <CatIcon className="h-3.5 w-3.5" />
                        </div>

                        {/* Content */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <p className={`text-sm truncate ${!n.read ? 'font-semibold text-gray-900' : 'font-medium text-gray-700'}`}>
                              {n.title}
                            </p>
                            {n.workspaceScoped && (
                              <span className="px-1.5 py-0 rounded border border-gray-200 text-[9px] text-gray-500 shrink-0">Workspace</span>
                            )}
                            {!n.read && <div className="h-2 w-2 rounded-full bg-impilo-500 shrink-0" />}
                          </div>
                          <p className="text-xs text-gray-500 line-clamp-2 mt-0.5">{n.body}</p>

                          {/* Meta row */}
                          <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                            <span className="text-[10px] text-gray-400 flex items-center gap-1">
                              <Clock className="h-2.5 w-2.5" />
                              {n.timestamp}
                            </span>
                            {n.sender && <span className="text-[10px] text-gray-400">&middot; {n.sender}</span>}
                            <span className={`px-1.5 py-0 rounded text-[9px] font-medium ${PRIORITY_BADGE_COLORS[n.priority]}`}>
                              {n.priority}
                            </span>

                            {/* Acknowledge button */}
                            {n.requiresAck && !n.read && (
                              <button
                                onClick={e => { e.stopPropagation(); acknowledge(n.id); }}
                                className="px-2 py-0 text-[10px] border border-gray-200 rounded hover:bg-gray-100"
                              >
                                Acknowledge
                              </button>
                            )}

                            {/* Action button */}
                            {n.actionUrl && n.actionLabel && (
                              <button
                                onClick={e => { e.stopPropagation(); }}
                                className="text-[10px] text-impilo-500 hover:underline flex items-center gap-0.5"
                              >
                                {n.actionLabel}
                                <ArrowRight className="h-2.5 w-2.5" />
                              </button>
                            )}
                          </div>

                          {/* Quick reply for messages */}
                          {n.category === 'message' && (
                            <div className="mt-2">
                              {replyTo === n.id ? (
                                <div className="flex gap-1">
                                  <input
                                    value={replyText}
                                    onChange={e => setReplyText(e.target.value)}
                                    placeholder="Type reply..."
                                    className="flex-1 px-2 py-1 text-xs border border-gray-200 rounded focus:outline-none focus:ring-1 focus:ring-impilo-400"
                                    onClick={e => e.stopPropagation()}
                                  />
                                  <button
                                    onClick={e => { e.stopPropagation(); sendReply(); }}
                                    className="px-2 py-1 bg-impilo-500 text-white rounded text-xs hover:bg-impilo-600"
                                  >
                                    <Send className="h-3 w-3" />
                                  </button>
                                </div>
                              ) : (
                                <button
                                  onClick={e => { e.stopPropagation(); setReplyTo(n.id); }}
                                  className="text-xs text-impilo-500 hover:underline"
                                >
                                  Reply
                                </button>
                              )}
                            </div>
                          )}
                        </div>

                        {/* Dismiss */}
                        <button
                          onClick={e => { e.stopPropagation(); dismiss(n.id); }}
                          className="p-1 rounded hover:bg-gray-200 shrink-0"
                        >
                          <X className="h-3 w-3 text-gray-400" />
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Quick Actions Footer */}
            <div className="border-t border-gray-200 px-3 py-2">
              <div className="flex items-center gap-1.5 mb-2">
                <button className="flex-1 inline-flex items-center justify-center gap-1.5 px-2 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50">
                  <MessageSquare className="h-3.5 w-3.5" />Messages
                </button>
                <button className="flex-1 inline-flex items-center justify-center gap-1.5 px-2 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50">
                  <Bell className="h-3.5 w-3.5" />Pages
                </button>
              </div>
              <div className="flex items-center justify-between">
                <button className="text-xs text-gray-500 hover:text-gray-700 flex items-center gap-1">
                  {isMuted ? <BellOff className="h-3 w-3" /> : <Bell className="h-3 w-3 text-impilo-500" />}
                  {isMuted ? 'Unmute' : 'Notifications on'}
                </button>
                <button
                  onClick={() => setOpen(false)}
                  className="text-xs text-gray-500 hover:text-gray-700 flex items-center gap-1"
                >
                  Open Full Hub
                  <ArrowRight className="h-3 w-3" />
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
