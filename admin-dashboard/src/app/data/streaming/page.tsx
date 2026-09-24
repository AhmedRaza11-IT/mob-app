'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import Link from 'next/link';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface StreamItem {
  id: string;
  device_id: string;
  device_model: string;
  user_id: string | null;
  username: string;
  display_name: string;
  avatar_url: string | null;
  stream_type: 'video' | 'audio';
  channel_name: string | null;
  filename: string;
  size_bytes: number;
  size_formatted: string;
  duration_seconds: number;
  duration_formatted: string;
  mime_type: string;
  created_at: number;
  download_url: string;
  play_url: string;
}

interface UserStreamSummary {
  username: string;
  display_name: string;
  avatar_url: string | null;
  device_id: string;
  device_model: string;
  total_streams: number;
  video_count: number;
  audio_count: number;
  total_bytes: number;
  total_bytes_formatted: string;
  last_recorded_at: number;
}

interface StreamsSummary {
  total_streams: number;
  video_streams: number;
  audio_streams: number;
  total_bytes: number;
  total_storage_formatted: string;
  user_summary: UserStreamSummary[];
}

export default function StreamingDataPage() {
  const [streams, setStreams] = useState<StreamItem[]>([]);
  const [summary, setSummary] = useState<StreamsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  // Filters
  const [selectedUser, setSelectedUser] = useState<string>('ALL');
  const [streamTypeFilter, setStreamTypeFilter] = useState<'ALL' | 'video' | 'audio'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Bulk selection
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isDeletingBulk, setIsDeletingBulk] = useState(false);

  // Playback Modal state
  const [activeMediaModal, setActiveMediaModal] = useState<StreamItem | null>(null);

  const fetchStreamData = useCallback(async () => {
    try {
      const [streamsRes, summaryRes] = await Promise.all([
        fetch(`${API_BASE}/api/admin/streams`),
        fetch(`${API_BASE}/api/admin/streams/summary`),
      ]);

      if (!streamsRes.ok) throw new Error(`Streams HTTP ${streamsRes.status}`);
      if (!summaryRes.ok) throw new Error(`Summary HTTP ${summaryRes.status}`);

      const streamsData = await streamsRes.json();
      const summaryData = await summaryRes.json();

      setStreams(streamsData);
      setSummary(summaryData);
      setError(null);
    } catch (e: unknown) {
      setError(`Failed to load streaming data: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStreamData();
  }, [fetchStreamData]);

  // Real-time WebSocket updates
  useEffect(() => {
    const WS_BASE = API_BASE.replace(/^http/, 'ws');
    let ws: WebSocket | null = null;
    let reconnectTimer: NodeJS.Timeout;

    const connect = () => {
      try {
        ws = new WebSocket(`${WS_BASE}/ws?user_id=admin_streaming_dashboard`);
        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (
              data.type === 'STREAM_RECORDING_SAVED' ||
              data.type === 'STREAM_RECORDING_DELETED' ||
              data.type === 'STREAM_RECORDINGS_BULK_DELETED'
            ) {
              fetchStreamData();
            }
          } catch {
            // ignore non-json
          }
        };
        ws.onclose = () => {
          reconnectTimer = setTimeout(connect, 3000);
        };
      } catch (err) {
        console.error('Streaming page WS error:', err);
      }
    };

    connect();
    return () => {
      if (ws) ws.close();
      clearTimeout(reconnectTimer);
    };
  }, [fetchStreamData]);

  const showToast = (msg: string) => {
    setActionMessage(msg);
    setTimeout(() => setActionMessage(null), 5000);
  };

  // Filtered streams
  const filteredStreams = useMemo(() => {
    return streams.filter((item) => {
      const matchesUser = selectedUser === 'ALL' || item.username === selectedUser;
      const matchesType =
        streamTypeFilter === 'ALL' || item.stream_type.toLowerCase() === streamTypeFilter.toLowerCase();
      const q = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !q ||
        item.username.toLowerCase().includes(q) ||
        item.display_name.toLowerCase().includes(q) ||
        item.filename.toLowerCase().includes(q) ||
        item.device_id.toLowerCase().includes(q) ||
        item.device_model.toLowerCase().includes(q);

      return matchesUser && matchesType && matchesSearch;
    });
  }, [streams, selectedUser, streamTypeFilter, searchQuery]);

  // Unique users list for dropdown filter
  const uniqueUsersList = useMemo(() => {
    const map = new Map<string, { username: string; display_name: string }>();
    streams.forEach((s) => {
      if (!map.has(s.username)) {
        map.set(s.username, { username: s.username, display_name: s.display_name || s.username });
      }
    });
    return Array.from(map.values());
  }, [streams]);

  const isAllSelected = useMemo(() => {
    return filteredStreams.length > 0 && filteredStreams.every((s) => selectedIds.has(s.id));
  }, [filteredStreams, selectedIds]);

  const toggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredStreams.map((s) => s.id)));
    }
  };

  const toggleSelectOne = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleDeleteStream = async (stream: StreamItem) => {
    if (!confirm(`Are you sure you want to delete this recorded ${stream.stream_type} stream (${stream.filename})?`)) {
      return;
    }
    try {
      const res = await fetch(`${API_BASE}/api/admin/streams/${encodeURIComponent(stream.id)}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast(`Deleted ${stream.stream_type} stream recording.`);
      fetchStreamData();
    } catch (e: unknown) {
      alert(`Delete failed: ${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const handleBulkDelete = async () => {
    if (selectedIds.size === 0) return;
    if (!confirm(`Permanently delete ${selectedIds.size} selected stream recording(s)?`)) return;

    setIsDeletingBulk(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/streams/bulk-delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ stream_ids: Array.from(selectedIds) }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setSelectedIds(new Set());
      showToast(`Successfully deleted ${data.deleted_count || selectedIds.size} recordings.`);
      fetchStreamData();
    } catch (e: unknown) {
      alert(`Bulk delete failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setIsDeletingBulk(false);
    }
  };

  const handleDownload = (stream: StreamItem) => {
    const fullUrl = `${API_BASE}${stream.download_url}`;
    const a = document.createElement('a');
    a.href = fullUrl;
    a.download = stream.filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  };

  const formatDate = (ts: number) => {
    if (!ts) return 'Unknown date';
    const d = new Date(ts);
    return d.toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-slate-900">Streaming Data Management</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-100 text-brand-purple border border-purple-200">
              User-Wise Surveillance Streams
            </span>
          </div>
          <p className="text-slate-500 mt-1 text-sm font-medium">
            Browse, preview, and download recorded video and audio streams categorized user-by-user.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            href="/surveillance"
            className="flex items-center gap-2 px-3.5 py-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-xl font-semibold text-xs border border-emerald-300 transition-colors shadow-sm"
          >
            <span>📹 Launch Surveillance</span>
          </Link>
          <button
            onClick={fetchStreamData}
            className="flex items-center gap-2 px-4 py-2 bg-white hover:bg-slate-50 text-slate-700 rounded-xl font-semibold text-xs border border-slate-200 shadow-sm transition-colors cursor-pointer"
          >
            <svg className="w-3.5 h-3.5 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
        </div>
      </div>

      {/* Sub-Navigation Tabs */}
      <div className="flex items-center gap-4 border-b border-slate-200">
        <Link
          href="/data"
          className="pb-3 px-2 text-sm font-semibold border-b-2 border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300 transition-colors flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
          </svg>
          Stored Files & Device Backups
        </Link>
        <Link
          href="/data/streaming"
          className="pb-3 px-2 text-sm font-bold border-b-2 border-brand-purple text-brand-purple flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 00-2 2v4a2 2 0 002 2h8a2 2 0 002-2v-4a2 2 0 00-2-2H3z" />
          </svg>
          Live Stream Data (User-Wise)
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-brand-purple">
            Active
          </span>
        </Link>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-xl text-xs font-medium">
          {error}
        </div>
      )}

      {actionMessage && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl text-xs font-medium flex items-center gap-2 shadow-sm">
          <svg className="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
          {actionMessage}
        </div>
      )}

      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Streams */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Total Recorded Streams</span>
            <div className="w-8 h-8 rounded-xl bg-purple-100 text-brand-purple flex items-center justify-center font-bold text-sm">
              🎬
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-black text-slate-900">{summary?.total_streams || 0}</span>
            <span className="text-xs text-slate-500 font-medium">files saved</span>
          </div>
          <div className="mt-2 text-xs text-slate-400">
            {summary?.video_streams || 0} Video &bull; {summary?.audio_streams || 0} Audio
          </div>
        </div>

        {/* Video Streams */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Camera Video Feeds</span>
            <div className="w-8 h-8 rounded-xl bg-emerald-100 text-emerald-700 flex items-center justify-center font-bold text-sm">
              📹
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-black text-emerald-700">{summary?.video_streams || 0}</span>
            <span className="text-xs text-slate-500 font-medium">video recordings</span>
          </div>
          <div className="mt-2 text-xs text-slate-400">WebM / MP4 video captures</div>
        </div>

        {/* Audio Streams */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Ambient Audio Feeds</span>
            <div className="w-8 h-8 rounded-xl bg-amber-100 text-amber-700 flex items-center justify-center font-bold text-sm">
              🎙️
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-black text-amber-700">{summary?.audio_streams || 0}</span>
            <span className="text-xs text-slate-500 font-medium">audio recordings</span>
          </div>
          <div className="mt-2 text-xs text-slate-400">Microphone ambient logs</div>
        </div>

        {/* Total Storage */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Storage Consumed</span>
            <div className="w-8 h-8 rounded-xl bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-sm">
              💾
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-2xl font-black text-slate-900">{summary?.total_storage_formatted || '0.0 KB'}</span>
          </div>
          <div className="mt-2 text-xs text-slate-400">
            Across {summary?.user_summary?.length || 0} registered user(s)
          </div>
        </div>
      </div>

      {/* User-Wise Summary Carousel / Cards */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            <span>👥</span> Users with Stream Recordings
          </h2>
          {selectedUser !== 'ALL' && (
            <button
              onClick={() => setSelectedUser('ALL')}
              className="text-xs font-semibold text-brand-purple hover:underline"
            >
              Clear User Filter (Showing: @{selectedUser})
            </button>
          )}
        </div>

        {summary?.user_summary && summary.user_summary.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {summary.user_summary.map((u) => {
              const isSelected = selectedUser === u.username;
              return (
                <div
                  key={u.username}
                  onClick={() => setSelectedUser(isSelected ? 'ALL' : u.username)}
                  className={`p-4 rounded-2xl border transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-purple-50/70 border-brand-purple shadow-md ring-2 ring-brand-purple/20'
                      : 'bg-white border-slate-200 hover:border-slate-300 hover:shadow-sm'
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <div className="w-12 h-12 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center overflow-hidden shrink-0 font-bold text-slate-700 text-base">
                      {u.avatar_url ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={`${API_BASE}${u.avatar_url}`}
                          alt={u.username}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        u.display_name.slice(0, 2).toUpperCase()
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-sm text-slate-900 truncate">
                          {u.display_name}
                        </span>
                        <span className="text-[11px] font-mono font-bold text-brand-purple bg-purple-100 px-2 py-0.5 rounded-full">
                          {u.total_streams} {u.total_streams === 1 ? 'stream' : 'streams'}
                        </span>
                      </div>
                      <div className="text-xs text-slate-500 font-mono truncate">@{u.username}</div>
                      <div className="text-[11px] text-slate-400 mt-1 flex items-center gap-2">
                        <span>📱 {u.device_model || 'Android Device'}</span>
                        <span>&bull;</span>
                        <span>{u.total_bytes_formatted}</span>
                      </div>
                    </div>
                  </div>

                  <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-3 text-slate-600">
                      <span>📹 {u.video_count} video</span>
                      <span>🎙️ {u.audio_count} audio</span>
                    </div>
                    <span className="text-[11px] text-slate-400">
                      Last: {formatDate(u.last_recorded_at)}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="bg-slate-50 border border-slate-200 border-dashed rounded-2xl p-6 text-center text-xs text-slate-500">
            No stream recordings yet. Start a Live Camera or Audio session in{' '}
            <Link href="/surveillance" className="text-brand-purple font-semibold underline">
              Surveillance
            </Link>{' '}
            with &quot;Auto-Save Stream&quot; enabled to store stream recordings here.
          </div>
        )}
      </div>

      {/* Filter and Action Toolbar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm space-y-4">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4">
          {/* User selector & Type chips */}
          <div className="flex flex-wrap items-center gap-3 w-full md:w-auto">
            {/* User Dropdown */}
            <div className="flex items-center gap-2">
              <label className="text-xs font-bold text-slate-600 uppercase">User:</label>
              <select
                value={selectedUser}
                onChange={(e) => setSelectedUser(e.target.value)}
                className="bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 text-xs font-semibold text-slate-800 focus:outline-none focus:ring-2 focus:ring-brand-purple"
              >
                <option value="ALL">All Users ({streams.length})</option>
                {uniqueUsersList.map((u) => (
                  <option key={u.username} value={u.username}>
                    {u.display_name} (@{u.username})
                  </option>
                ))}
              </select>
            </div>

            {/* Stream Type Filter Chips */}
            <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-xl">
              <button
                onClick={() => setStreamTypeFilter('ALL')}
                className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  streamTypeFilter === 'ALL'
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                All Types
              </button>
              <button
                onClick={() => setStreamTypeFilter('video')}
                className={`flex items-center gap-1 px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  streamTypeFilter === 'video'
                    ? 'bg-emerald-600 text-white shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                📹 Video Feeds
              </button>
              <button
                onClick={() => setStreamTypeFilter('audio')}
                className={`flex items-center gap-1 px-3 py-1 rounded-lg text-xs font-semibold transition ${
                  streamTypeFilter === 'audio'
                    ? 'bg-amber-600 text-white shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                🎙️ Audio Feeds
              </button>
            </div>
          </div>

          {/* Search Bar */}
          <div className="relative w-full md:w-72">
            <svg
              className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search streams or device..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-9 pr-4 py-1.5 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-purple"
            />
          </div>
        </div>

        {/* Bulk Selection Bar */}
        {selectedIds.size > 0 && (
          <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
            <span className="font-semibold text-slate-700">
              {selectedIds.size} stream(s) selected
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setSelectedIds(new Set())}
                className="px-3 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-semibold transition"
              >
                Clear Selection
              </button>
              <button
                onClick={handleBulkDelete}
                disabled={isDeletingBulk}
                className="px-3 py-1 bg-red-600 hover:bg-red-700 text-white rounded-lg font-semibold transition disabled:opacity-50 flex items-center gap-1.5"
              >
                <span>🗑️ Delete Selected ({selectedIds.size})</span>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Streams Table / Grid */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-12 text-center text-slate-400 text-sm">
            <div className="animate-spin text-2xl mb-2">⌛</div>
            Loading recorded stream repository...
          </div>
        ) : filteredStreams.length === 0 ? (
          <div className="p-12 text-center space-y-3">
            <div className="text-4xl text-slate-300">📡</div>
            <p className="text-slate-800 font-semibold text-sm">No Stream Recordings Found</p>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              {streams.length === 0
                ? 'No video or audio surveillance feeds have been recorded yet. Click "Launch Surveillance" to connect to a device and record live feeds.'
                : 'No recordings match the active user or type filter.'}
            </p>
            {streams.length > 0 && (
              <button
                onClick={() => {
                  setSelectedUser('ALL');
                  setStreamTypeFilter('ALL');
                  setSearchQuery('');
                }}
                className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold"
              >
                Reset All Filters
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 border-b border-slate-200 text-slate-500 font-semibold uppercase text-[10px] tracking-wider">
                <tr>
                  <th className="py-3 px-4 w-10">
                    <input
                      type="checkbox"
                      checked={isAllSelected}
                      onChange={toggleSelectAll}
                      className="rounded text-brand-purple focus:ring-brand-purple"
                    />
                  </th>
                  <th className="py-3 px-4">User</th>
                  <th className="py-3 px-4">Type</th>
                  <th className="py-3 px-4">Device</th>
                  <th className="py-3 px-4">Duration</th>
                  <th className="py-3 px-4">Size</th>
                  <th className="py-3 px-4">Recorded Date</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredStreams.map((s) => {
                  const isChecked = selectedIds.has(s.id);
                  const isVideo = s.stream_type.toLowerCase() === 'video';
                  return (
                    <tr
                      key={s.id}
                      className={`hover:bg-slate-50/70 transition-colors ${
                        isChecked ? 'bg-purple-50/40' : ''
                      }`}
                    >
                      <td className="py-3 px-4">
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggleSelectOne(s.id)}
                          className="rounded text-brand-purple focus:ring-brand-purple"
                        />
                      </td>

                      {/* User Column */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center font-bold text-slate-700 overflow-hidden shrink-0 text-xs">
                            {s.avatar_url ? (
                              // eslint-disable-next-line @next/next/no-img-element
                              <img
                                src={`${API_BASE}${s.avatar_url}`}
                                alt={s.username}
                                className="w-full h-full object-cover"
                              />
                            ) : (
                              s.display_name.slice(0, 2).toUpperCase()
                            )}
                          </div>
                          <div>
                            <div className="font-bold text-slate-900">{s.display_name}</div>
                            <div className="text-[11px] text-slate-500 font-mono">@{s.username}</div>
                          </div>
                        </div>
                      </td>

                      {/* Stream Type */}
                      <td className="py-3 px-4">
                        {isVideo ? (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                            📹 Video Stream
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                            🎙️ Audio Stream
                          </span>
                        )}
                      </td>

                      {/* Device */}
                      <td className="py-3 px-4">
                        <div className="font-medium text-slate-800">{s.device_model}</div>
                        <div className="text-[10px] text-slate-400 font-mono">
                          {s.device_id.slice(0, 14)}…
                        </div>
                      </td>

                      {/* Duration */}
                      <td className="py-3 px-4 font-mono font-semibold text-slate-700">
                        {s.duration_formatted}
                      </td>

                      {/* Size */}
                      <td className="py-3 px-4 font-mono font-medium text-slate-600">
                        {s.size_formatted}
                      </td>

                      {/* Date */}
                      <td className="py-3 px-4 text-slate-500">{formatDate(s.created_at)}</td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Play / Preview */}
                          <button
                            onClick={() => setActiveMediaModal(s)}
                            className="px-2.5 py-1 rounded-lg bg-brand-purple/10 hover:bg-brand-purple text-brand-purple hover:text-white font-semibold transition text-xs flex items-center gap-1"
                            title="Play in browser"
                          >
                            <span>▶</span>
                            <span>Play</span>
                          </button>

                          {/* Download */}
                          <button
                            onClick={() => handleDownload(s)}
                            className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold transition text-xs flex items-center gap-1"
                            title="Download stream file"
                          >
                            <span>⬇</span>
                            <span>Download</span>
                          </button>

                          {/* Delete */}
                          <button
                            onClick={() => handleDeleteStream(s)}
                            className="p-1 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition"
                            title="Delete recording"
                          >
                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Built-in Stream Playback Modal */}
      {activeMediaModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4">
          <div className="bg-white border border-slate-200 rounded-2xl shadow-2xl w-full max-w-2xl overflow-hidden flex flex-col animate-fadeIn">
            {/* Modal Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
              <div className="flex items-center gap-3">
                <span className="text-xl">
                  {activeMediaModal.stream_type === 'video' ? '📹' : '🎙️'}
                </span>
                <div>
                  <h3 className="font-bold text-slate-900 text-sm">
                    {activeMediaModal.stream_type === 'video' ? 'Video Stream' : 'Audio Stream'} &mdash;{' '}
                    {activeMediaModal.display_name} (@{activeMediaModal.username})
                  </h3>
                  <p className="text-xs text-slate-500 font-mono">
                    {activeMediaModal.filename} &bull; {activeMediaModal.duration_formatted} &bull;{' '}
                    {activeMediaModal.size_formatted}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setActiveMediaModal(null)}
                className="text-slate-400 hover:text-slate-700 text-2xl leading-none px-2 rounded-lg hover:bg-slate-200 transition"
              >
                ×
              </button>
            </div>

            {/* Modal Body / Media Viewport */}
            <div className="p-6 bg-slate-900 flex flex-col items-center justify-center min-h-[300px]">
              {activeMediaModal.stream_type === 'video' ? (
                <video
                  controls
                  autoPlay
                  src={`${API_BASE}${activeMediaModal.play_url}`}
                  className="w-full max-h-[420px] rounded-xl bg-black object-contain shadow-lg"
                />
              ) : (
                <div className="w-full max-w-md bg-slate-800 p-6 rounded-2xl border border-slate-700 text-center space-y-4 shadow-xl">
                  <div className="w-16 h-16 mx-auto rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center text-3xl animate-pulse">
                    🎙️
                  </div>
                  <div>
                    <h4 className="text-white font-bold text-sm">Ambient Audio Recording</h4>
                    <p className="text-xs text-slate-400 font-mono mt-1">
                      Recorded on {formatDate(activeMediaModal.created_at)}
                    </p>
                  </div>
                  <audio
                    controls
                    autoPlay
                    src={`${API_BASE}${activeMediaModal.play_url}`}
                    className="w-full mt-2"
                  />
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="px-6 py-3.5 bg-slate-50 border-t border-slate-200 flex items-center justify-between">
              <span className="text-xs text-slate-500 font-mono">
                Device: {activeMediaModal.device_model} ({activeMediaModal.device_id.slice(0, 16)}…)
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleDownload(activeMediaModal)}
                  className="px-4 py-2 bg-brand-purple hover:bg-brand-purple/90 text-white font-semibold rounded-xl text-xs shadow-sm transition flex items-center gap-1.5"
                >
                  <span>⬇</span>
                  <span>Download Recording</span>
                </button>
                <button
                  onClick={() => setActiveMediaModal(null)}
                  className="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold rounded-xl text-xs transition"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
