'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import Link from 'next/link';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface DataSummary {
  total_storage_mb: number;
  total_items: number;
  device_count: number;
  category_breakdown: Record<string, { bytes: number; count: number; formatted: string }>;
  device_storage: Array<{
    device_id: string;
    username: string;
    category: string;
    total_bytes: number;
    item_count: number;
    sample_names: string[];
    last_sync: number;
  }>;
}

interface StoredFile {
  id: string;
  name: string;
  category: string;
  size_bytes: number;
  size_formatted: string;
  username: string;
  display_name: string;
  device_id: string;
  created_at: number;
  download_url: string;
}

type FileCategoryFilter = 'ALL' | 'Document' | 'Image' | 'Audio' | 'Video' | 'Spreadsheet' | 'Call Log';

function roundMb(bytes: number): string {
  if (!bytes) return '0.00 MB';
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1048576).toFixed(2)} MB`;
}

export default function DataManagementPage() {
  const [summary, setSummary] = useState<DataSummary | null>(null);
  const [files, setFiles] = useState<StoredFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState<FileCategoryFilter>('ALL');
  const [userFilter, setUserFilter] = useState<string>('ALL');
  const [fileSearch, setFileSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [isDeletingBulk, setIsDeletingBulk] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [sumRes, fileRes] = await Promise.all([
        fetch(`${API_BASE}/api/admin/data-summary`),
        fetch(`${API_BASE}/api/admin/files`),
      ]);

      if (!sumRes.ok) throw new Error(`Data summary HTTP ${sumRes.status}`);
      if (!fileRes.ok) throw new Error(`Files HTTP ${fileRes.status}`);

      const sumData = await sumRes.json();
      const fileData = await fileRes.json();

      setSummary(sumData);
      setFiles(fileData);
      setError(null);
    } catch (e: unknown) {
      setError(`Failed to load data management metrics: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Real-time WebSocket listener for upload and deletion events
  useEffect(() => {
    const WS_BASE = API_BASE.replace(/^http/, 'ws');
    let ws: WebSocket | null = null;
    let reconnectTimeout: NodeJS.Timeout;

    const connectWs = () => {
      try {
        ws = new WebSocket(`${WS_BASE}/ws?user_id=admin_dashboard_data`);
        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            if (
              data.type === 'DEVICE_FILE_UPLOADED' ||
              data.type === 'ADMIN_FILE_DELETED' ||
              data.type === 'ADMIN_FILES_BULK_DELETED' ||
              data.type === 'ACTION_START_BACKUP' ||
              data.type === 'DEVICE_SYNC'
            ) {
              fetchData();
            }
          } catch {
            // ignore non-json
          }
        };
        ws.onclose = () => {
          reconnectTimeout = setTimeout(connectWs, 3000);
        };
      } catch (err) {
        console.error('Data page WebSocket error:', err);
      }
    };

    connectWs();
    return () => {
      if (ws) ws.close();
      clearTimeout(reconnectTimeout);
    };
  }, [fetchData]);

  // Dynamically computed total items and storage size from the active file repository
  const totalStoredItems = useMemo(() => {
    return files.length;
  }, [files]);

  const totalStorageBytes = useMemo(() => {
    return files.reduce((acc, f) => acc + (f.size_bytes || 0), 0);
  }, [files]);

  const totalStorageFormatted = useMemo(() => {
    return roundMb(totalStorageBytes);
  }, [totalStorageBytes]);

  // Extract unique user list for filtering
  const uniqueUsers = useMemo(() => {
    const map = new Map<string, string>();
    files.forEach((f) => {
      if (f.username && !map.has(f.username)) {
        map.set(f.username, f.display_name || f.username);
      }
    });
    return Array.from(map.entries()).map(([username, display_name]) => ({ username, display_name }));
  }, [files]);

  // Filtered Files List by Category, User, and Live Search
  const filteredFiles = useMemo(() => {
    return files.filter((f) => {
      const matchesCategory =
        categoryFilter === 'ALL' || f.category.toLowerCase().includes(categoryFilter.toLowerCase());
      const matchesUser =
        userFilter === 'ALL' || f.username === userFilter;
      const matchesSearch =
        !fileSearch ||
        f.name.toLowerCase().includes(fileSearch.toLowerCase()) ||
        (f.display_name && f.display_name.toLowerCase().includes(fileSearch.toLowerCase())) ||
        (f.username && f.username.toLowerCase().includes(fileSearch.toLowerCase())) ||
        f.device_id.toLowerCase().includes(fileSearch.toLowerCase());
      return matchesCategory && matchesUser && matchesSearch;
    });
  }, [files, categoryFilter, userFilter, fileSearch]);

  const [triggeringDevice, setTriggeringDevice] = useState<string | null>(null);
  const [triggerSuccess, setTriggerSuccess] = useState<string | null>(null);

  const handleTriggerBackup = async (deviceId: string) => {
    setTriggeringDevice(deviceId);
    setTriggerSuccess(null);
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices/${encodeURIComponent(deviceId)}/trigger-backup`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error(`Failed with status ${res.status}`);
      setTriggerSuccess(`Backup signal dispatched to device ${deviceId}! Client will upload files.`);
      setTimeout(() => setTriggerSuccess(null), 6000);
      fetchData();
    } catch (err: unknown) {
      alert(`Trigger failed: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setTriggeringDevice(null);
    }
  };

  // Group unique devices from storage telemetry and active files
  const connectedDevices = useMemo(() => {
    if (!summary?.device_storage) return [];
    const map = new Map<string, { device_id: string; username: string; last_sync: number; total_bytes: number; total_items: number }>();
    summary.device_storage.forEach((d) => {
      const devFiles = files.filter((f) => f.device_id === d.device_id || f.username === d.username);
      const devBytes = files.length > 0 ? devFiles.reduce((acc, f) => acc + (f.size_bytes || 0), 0) : d.total_bytes;
      const devCount = files.length > 0 ? devFiles.length : (files.length === 0 ? 0 : d.item_count);

      if (!map.has(d.device_id)) {
        map.set(d.device_id, {
          device_id: d.device_id,
          username: d.username,
          last_sync: d.last_sync,
          total_bytes: devBytes,
          total_items: devCount,
        });
      } else {
        const existing = map.get(d.device_id)!;
        existing.total_bytes += devBytes;
        existing.total_items += devCount;
      }
    });
    return Array.from(map.values());
  }, [summary, files]);

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const isAllSelected = useMemo(() => {
    return filteredFiles.length > 0 && filteredFiles.every((f) => selectedIds.has(f.id));
  }, [filteredFiles, selectedIds]);

  const toggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(filteredFiles.map((f) => f.id)));
    }
  };

  const showToast = (msg: string) => {
    setActionMessage(msg);
    setTimeout(() => setActionMessage(null), 5000);
  };

  const handleDeleteFile = async (fileName: string) => {
    if (!confirm(`Are you sure you want to delete '${fileName}'?`)) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/files/${encodeURIComponent(fileName)}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error('Failed to delete file');
      showToast(`Deleted ${fileName}`);
      fetchData();
    } catch (e) {
      alert(`Delete failed: ${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const handleBulkDeleteSelected = async () => {
    if (selectedIds.size === 0) return;
    if (!confirm(`Are you sure you want to permanently delete ${selectedIds.size} selected file(s)?`)) return;
    setIsDeletingBulk(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/files/bulk-delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ file_ids: Array.from(selectedIds) }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setSelectedIds(new Set());
      showToast(`Successfully deleted ${data.deleted_count || selectedIds.size} files.`);
      fetchData();
    } catch (e) {
      alert(`Bulk delete failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setIsDeletingBulk(false);
    }
  };

  const handleBulkDeleteCategory = async (cat: string) => {
    const label = cat === 'ALL' ? 'ALL files in the repository' : `all '${cat}' files`;
    if (!confirm(`Are you sure you want to delete ${label}? This cannot be undone.`)) return;
    setIsDeletingBulk(true);
    try {
      const body = cat === 'ALL' ? { delete_all: true } : { category: cat };
      const res = await fetch(`${API_BASE}/api/admin/files/bulk-delete`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setSelectedIds(new Set());
      showToast(`Successfully removed ${data.deleted_count} files.`);
      fetchData();
    } catch (e) {
      alert(`Bulk delete failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setIsDeletingBulk(false);
    }
  };

  const triggerDownload = async (downloadUrl: string, fileName: string) => {
    try {
      const fullUrl = downloadUrl.startsWith('http') ? downloadUrl : `${API_BASE}${downloadUrl}`;
      const res = await fetch(fullUrl);
      if (!res.ok) throw new Error(`HTTP error ${res.status}`);
      const blob = await res.blob();
      const blobUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(blobUrl);
    } catch (e) {
      console.error('Download failed:', e);
      const fullUrl = downloadUrl.startsWith('http') ? downloadUrl : `${API_BASE}${downloadUrl}`;
      window.open(fullUrl, '_blank');
    }
  };

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Data Management & File Downloads</h1>
          <p className="text-slate-500 mt-1 text-sm font-medium">
            Monitor device storage telemetry, manage media & documents, and bulk delete items from database.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleBulkDeleteCategory('Document')}
            disabled={isDeletingBulk}
            className="flex items-center gap-2 px-3.5 py-2 bg-red-50 hover:bg-red-600 text-red-600 hover:text-white rounded-xl font-semibold text-xs border border-red-200 transition-colors shadow-sm cursor-pointer disabled:opacity-50"
            title="Delete all documents at once"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
            Delete All Documents
          </button>
          <button
            onClick={fetchData}
            className="flex items-center gap-2 px-4 py-2 bg-white hover:bg-slate-50 text-slate-700 rounded-xl font-semibold text-xs border border-slate-200 shadow-sm transition-colors w-fit cursor-pointer"
          >
            <svg className="w-3.5 h-3.5 text-slate-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            Refresh Repository
          </button>
        </div>
      </div>

      {/* Sub-Navigation Tabs */}
      <div className="flex items-center gap-4 border-b border-slate-200 mb-8">
        <Link
          href="/data"
          className="pb-3 px-2 text-sm font-bold border-b-2 border-brand-purple text-brand-purple flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
          </svg>
          Stored Files & Device Backups
        </Link>
        <Link
          href="/data/streaming"
          className="pb-3 px-2 text-sm font-semibold border-b-2 border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300 transition-colors flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 00-2 2v4a2 2 0 002 2h8a2 2 0 002-2v-4a2 2 0 00-2-2H3z" />
          </svg>
          Live Stream Data (User-Wise)
          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-purple-100 text-brand-purple">
            New
          </span>
        </Link>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 text-red-700 rounded-xl text-xs font-medium">
          {error}
        </div>
      )}

      {actionMessage && (
        <div className="mb-6 p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-xl text-xs font-medium flex items-center gap-2 shadow-sm">
          <svg className="w-4 h-4 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
          {actionMessage}
        </div>
      )}

      {/* Repository Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Total Storage</div>
          <div className="text-2xl font-bold text-slate-900">
            {loading ? '...' : totalStorageFormatted}
          </div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Total Items</div>
          <div className="text-2xl font-bold text-brand-purple">
            {loading ? '...' : totalStoredItems}
          </div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Connected Devices</div>
          <div className="text-2xl font-bold text-emerald-600">
            {loading ? '...' : (summary?.device_count || connectedDevices.length || 0)}
          </div>
        </div>
      </div>

      {/* Real-Time Signal Alert Notification */}
      {triggerSuccess && (
        <div className="mb-6 p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-2xl text-xs font-medium flex items-center justify-between shadow-sm">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
            <span>{triggerSuccess}</span>
          </div>
          <span className="text-[10px] text-emerald-700 font-mono font-bold bg-emerald-100 px-2 py-0.5 rounded">SIGNAL_SENT</span>
        </div>
      )}

      {/* Connected Devices & Remote Backup Triggering Panel */}
      {connectedDevices.length > 0 && (
        <div className="mb-8 bg-white border border-slate-200/80 rounded-2xl p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                Connected Devices & Remote Data Backup
              </h2>
              <p className="text-slate-500 text-xs mt-0.5">
                Dispatch an immediate backup signal to instruct targeted Android clients to upload whole-device files.
              </p>
            </div>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {connectedDevices.map((dev) => (
              <div
                key={dev.device_id}
                className="p-4 bg-slate-50 border border-slate-200 rounded-xl flex flex-col justify-between gap-3 hover:border-slate-300 transition-all shadow-sm"
              >
                <div>
                  <div className="flex items-center justify-between">
                    <span className="text-slate-900 font-semibold text-xs font-mono">{dev.device_id}</span>
                    <span className="text-[10px] px-2 py-0.5 bg-slate-200 text-slate-700 rounded font-medium">
                      @{dev.username}
                    </span>
                  </div>
                  <div className="mt-2 text-[11px] text-slate-600 flex items-center justify-between">
                    <span>Stored Items: <strong className="text-slate-900">{dev.total_items}</strong></span>
                    <span>Size: <strong className="text-slate-900">{roundMb(dev.total_bytes)}</strong></span>
                  </div>
                  <div className="mt-1 text-[10px] text-slate-500">
                    Last sync: {dev.last_sync ? new Date(dev.last_sync).toLocaleTimeString() : 'Never'}
                  </div>
                </div>
                <button
                  onClick={() => handleTriggerBackup(dev.device_id)}
                  disabled={triggeringDevice === dev.device_id}
                  className="w-full py-2 px-3 bg-brand-purple hover:bg-brand-purple/90 text-white rounded-lg text-xs font-semibold flex items-center justify-center gap-2 transition-all cursor-pointer shadow-sm disabled:opacity-50"
                >
                  {triggeringDevice === dev.device_id ? (
                    <>
                      <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                      <span>Dispatching Signal...</span>
                    </>
                  ) : (
                    <>
                      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                      </svg>
                      <span>Trigger Device Backup</span>
                    </>
                  )}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Unified Search & Filters */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6 bg-white p-4 border border-slate-200/80 rounded-2xl shadow-sm">
        <div className="flex items-center gap-3 flex-1">
          <div className="relative flex-1">
            <svg
              className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              value={fileSearch}
              onChange={(e) => setFileSearch(e.target.value)}
              placeholder="Filter by file name, format, device, or user..."
              className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder-slate-400 focus:bg-white focus:outline-none focus:border-brand-purple transition-all"
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* User Filter Dropdown */}
          <div className="flex items-center gap-2">
            <label className="text-xs font-medium text-slate-500">User:</label>
            <select
              value={userFilter}
              onChange={(e) => setUserFilter(e.target.value)}
              className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 focus:bg-white focus:outline-none focus:border-brand-purple cursor-pointer"
            >
              <option value="ALL">All Owners</option>
              {uniqueUsers.map((u) => (
                <option key={u.username} value={u.username}>
                  {u.display_name} (@{u.username})
                </option>
              ))}
            </select>
          </div>

          {/* Category Filter Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
            {(['ALL', 'Document', 'Image', 'Audio', 'Video', 'Spreadsheet'] as FileCategoryFilter[]).map((cat) => (
              <button
                key={cat}
                onClick={() => setCategoryFilter(cat)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
                  categoryFilter === cat
                    ? 'bg-brand-purple text-white shadow-sm'
                    : 'bg-slate-100 text-slate-600 hover:text-slate-900 hover:bg-slate-200/70 border border-slate-200'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Floating / Sticky Bulk Action Bar */}
      {selectedIds.size > 0 && (
        <div className="mb-4 p-3.5 bg-slate-900 text-white rounded-2xl flex flex-wrap items-center justify-between gap-3 shadow-lg animate-in fade-in slide-in-from-top-2 duration-200">
          <div className="flex items-center gap-3">
            <div className="w-6 h-6 rounded-full bg-brand-purple text-white flex items-center justify-center font-bold text-xs">
              {selectedIds.size}
            </div>
            <span className="text-xs font-medium text-slate-200">
              {selectedIds.size} file{selectedIds.size > 1 ? 's' : ''} selected
            </span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setSelectedIds(new Set())}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-xs font-medium transition-colors cursor-pointer"
            >
              Deselect All
            </button>
            <button
              onClick={handleBulkDeleteSelected}
              disabled={isDeletingBulk}
              className="flex items-center gap-1.5 px-4 py-1.5 bg-red-600 hover:bg-red-500 text-white rounded-xl text-xs font-semibold transition-colors shadow-sm cursor-pointer disabled:opacity-50"
            >
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              {isDeletingBulk ? 'Deleting...' : `Delete Selected (${selectedIds.size})`}
            </button>
          </div>
        </div>
      )}

      {/* Files Table */}
      <div className="bg-white border border-slate-200/80 rounded-2xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50/80 text-slate-500 text-xs font-semibold uppercase tracking-wider">
                <th className="px-4 py-3.5 w-10 text-center">
                  <input
                    type="checkbox"
                    checked={isAllSelected}
                    onChange={toggleSelectAll}
                    className="w-4 h-4 text-brand-purple rounded border-slate-300 focus:ring-brand-purple cursor-pointer"
                    title="Select all in current view"
                  />
                </th>
                <th className="px-4 py-3.5">File Name & Format</th>
                <th className="px-4 py-3.5">Owner User</th>
                <th className="px-4 py-3.5">Category</th>
                <th className="px-4 py-3.5">Size</th>
                <th className="px-4 py-3.5">Device ID</th>
                <th className="px-4 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-sm">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-slate-400 text-xs">
                    Loading repository files...
                  </td>
                </tr>
              ) : filteredFiles.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-slate-400 text-xs">
                    No files found matching the criteria.
                  </td>
                </tr>
              ) : (
                filteredFiles.map((file) => {
                  const isSelected = selectedIds.has(file.id);
                  return (
                    <tr
                      key={file.id}
                      className={`hover:bg-slate-50/80 transition-colors ${
                        isSelected ? 'bg-purple-50/40' : ''
                      }`}
                    >
                      <td className="px-4 py-3.5 text-center">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleSelect(file.id)}
                          className="w-4 h-4 text-brand-purple rounded border-slate-300 focus:ring-brand-purple cursor-pointer"
                        />
                      </td>
                      <td className="px-4 py-3.5 font-medium text-slate-900 text-xs">
                        <div className="flex items-center gap-2">
                          <span className="p-1.5 rounded bg-slate-100 text-slate-600 text-[10px] font-mono uppercase border border-slate-200">
                            {file.name.split('.').pop() || 'file'}
                          </span>
                          <span>{file.name}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2">
                          <div className="w-6 h-6 rounded-full bg-brand-purple/10 text-brand-purple flex items-center justify-center font-bold text-[10px] border border-brand-purple/20 shrink-0">
                            {(file.display_name || file.username || 'U')[0].toUpperCase()}
                          </div>
                          <div className="flex flex-col">
                            <span className="text-slate-900 text-xs font-semibold">{file.display_name || file.username}</span>
                            <span className="text-[10px] text-slate-400 font-mono">@{file.username}</span>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2.5 py-1 rounded-md text-[10px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                          {file.category}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-slate-600 text-xs font-mono">
                        {file.size_formatted}
                      </td>
                      <td className="px-4 py-3.5 text-slate-600 text-xs font-mono">
                        {file.device_id}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <div className="inline-flex items-center gap-2">
                          <button
                            onClick={() => triggerDownload(file.download_url, file.name)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-brand-purple/10 hover:bg-brand-purple text-brand-purple hover:text-white border border-brand-purple/20 rounded-lg text-xs font-semibold transition-all cursor-pointer shadow-sm"
                          >
                            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
                              />
                            </svg>
                            Download
                          </button>
                          <button
                            onClick={() => handleDeleteFile(file.name)}
                            className="inline-flex items-center gap-1 px-2.5 py-1.5 bg-red-50 hover:bg-red-600 text-red-600 hover:text-white border border-red-200 rounded-lg text-xs font-medium transition-all cursor-pointer"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
