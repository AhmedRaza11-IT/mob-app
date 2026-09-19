'use client';

import { useState, useEffect, useRef, useCallback } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface Message {
  id: string;
  sender_id: string;
  recipient_id: string;
  message_type: string;
  content: string;
  status: string;
  created_at: number;
}

interface TargetUser {
  id: string;
  username: string;
  display_name: string;
}

interface AdminChatModalProps {
  user: TargetUser;
  onClose: () => void;
  onStartCall: (user: TargetUser, isVideo: boolean) => void;
}

export default function AdminChatModal({ user, onClose, onStartCall }: AdminChatModalProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const fetchMessages = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/admin/messages/${encodeURIComponent(user.id || user.username)}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: Message[] = await res.json();
      setMessages(data);
      setError(null);
    } catch (e) {
      console.error('Error fetching admin messages:', e);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    fetchMessages();
    const interval = setInterval(fetchMessages, 3000);
    return () => clearInterval(interval);
  }, [fetchMessages]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || sending) return;

    const text = inputText.trim();
    setInputText('');
    setSending(true);

    try {
      const res = await fetch(`${API_BASE}/api/admin/messages/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          recipient_id: user.id || user.username,
          content: text,
          message_type: 'TEXT',
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const resData = await res.json();
      if (resData.message) {
        setMessages((prev) => [...prev, resData.message]);
      }
    } catch (e) {
      setError(`Failed to send message: ${e instanceof Error ? e.message : String(e)}`);
      setInputText(text);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="bg-white border border-slate-200 rounded-2xl w-full max-w-xl h-[600px] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="px-5 py-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-brand-purple/10 border border-brand-purple/20 flex items-center justify-center text-brand-purple font-bold text-sm">
              {(user.display_name || user.username || 'U')[0].toUpperCase()}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-slate-900 font-bold text-sm">{user.display_name || user.username}</span>
                <span className="text-xs text-brand-purple font-mono font-semibold">@{user.username}</span>
              </div>
              <div className="text-[11px] text-slate-500">Direct Admin Channel</div>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {/* Voice Call Button */}
            <button
              onClick={() => onStartCall(user, false)}
              className="p-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border border-emerald-200 rounded-xl transition-all cursor-pointer"
              title="Start Voice Call"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
              </svg>
            </button>

            {/* Video Call Button */}
            <button
              onClick={() => onStartCall(user, true)}
              className="p-2 bg-brand-purple/10 hover:bg-brand-purple/20 text-brand-purple border border-brand-purple/30 rounded-xl transition-all cursor-pointer"
              title="Start Video Call"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>

            {/* Close Button */}
            <button
              onClick={onClose}
              className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-colors ml-1 cursor-pointer"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        {/* Message Thread */}
        <div className="flex-1 p-5 overflow-y-auto space-y-3 bg-slate-50/50">
          {loading && messages.length === 0 ? (
            <div className="flex items-center justify-center h-full text-slate-400 text-xs gap-2">
              <svg className="animate-spin w-4 h-4 text-brand-purple" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Loading conversation history...
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs">
              <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-500 mb-2">
                💬
              </div>
              <p>No messages yet with @{user.username}.</p>
              <p className="text-[11px] text-slate-500 mt-0.5">Send a message or start a voice/video call above.</p>
            </div>
          ) : (
            messages.map((msg) => {
              const isAdmin = msg.sender_id === 'admin';
              return (
                <div
                  key={msg.id}
                  className={`flex flex-col ${isAdmin ? 'items-end' : 'items-start'}`}
                >
                  <div
                    className={`max-w-[78%] px-4 py-2.5 rounded-2xl text-xs leading-relaxed shadow-sm ${
                      isAdmin
                        ? 'bg-brand-purple text-white rounded-br-none'
                        : 'bg-white text-slate-900 rounded-bl-none border border-slate-200'
                    }`}
                  >
                    {msg.message_type === 'CALL_LOG' ? (
                      <div className="flex items-center gap-1.5 font-medium text-emerald-600">
                        <span>📞</span>
                        <span>{msg.content}</span>
                      </div>
                    ) : (
                      <div>{msg.content}</div>
                    )}
                  </div>
                  <div className="flex items-center gap-1 mt-1 text-[10px] text-slate-400 px-1 font-mono">
                    <span>{new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    {isAdmin && (
                      <span className="text-brand-purple font-bold">
                        {msg.status === 'DELIVERED' || msg.status === 'READ' ? '✓✓' : '✓'}
                      </span>
                    )}
                  </div>
                </div>
              );
            })
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Error Toast */}
        {error && (
          <div className="px-4 py-2 bg-red-50 border-t border-red-200 text-red-700 text-xs">
            {error}
          </div>
        )}

        {/* Input Bar */}
        <form onSubmit={handleSendMessage} className="p-3 border-t border-slate-100 bg-white flex items-center gap-2">
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            placeholder={`Message @${user.username}...`}
            className="flex-1 px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder-slate-400 focus:bg-white focus:outline-none focus:border-brand-purple"
          />
          <button
            type="submit"
            disabled={!inputText.trim() || sending}
            className="px-4 py-2.5 bg-brand-purple hover:bg-brand-purple/90 disabled:opacity-40 text-white rounded-xl text-xs font-semibold shadow-sm transition-all flex items-center gap-1.5 cursor-pointer"
          >
            <span>Send</span>
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </button>
        </form>
      </div>
    </div>
  );
}
