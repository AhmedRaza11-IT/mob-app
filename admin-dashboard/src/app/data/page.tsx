'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';

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

export default function DataManagementPage() {
  const [summary, setSummary] = useState<DataSummary | null>(null);
  const [files, setFiles] = useState<StoredFile[]>([]);
  const [loading, setLoading] = useState(true);
  const [categoryFilter, setCategoryFilter] = useState<FileCategoryFilter>('ALL');
  const [userFilter, setUserFilter] = useState<string>('ALL');
  const [fileSearch, setFileSearch] = useState('');
  const [error, setError] = useState<string | null>(null);

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

  const handleDeleteFile = async (fileName: string) => {
    if (!confirm(`Are you sure you want to delete '${fileName}'?`)) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/files/${encodeURIComponent(fileName)}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error('Failed to delete file');
      fetchData();
    } catch (e) {
      alert(`Delete failed: ${e instanceof Error ? e.message : String(e)}`);
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
          <h1 className="text-2xl font-bold text-white">Data Management & File Downloads</h1>
          <p className="text-slate-400 mt-1 text-sm">
            Monitor device storage telemetry, manage media files, and delete items from database.
          </p>
        </div>
        <button
          onClick={fetchData}
          className="flex items-center gap-2 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-xl font-medium text-xs border border-slate-700 transition-colors w-fit"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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

      {error && (
        <div className="mb-6 p-4 bg-red-900/50 border border-red-700 text-red-200 rounded-xl text-xs">
          {error}
        </div>
      )}

      {/* Repository Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Total Storage</div>
          <div className="text-2xl font-bold text-white">
            {summary && typeof summary.total_storage_mb === 'number'
              ? `${summary.total_storage_mb.toFixed(2)} MB`
              : '0.00 MB'}
          </div>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Total Items</div>
          <div className="text-2xl font-bold text-brand-purple">{summary ? summary.total_items : '...'}</div>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">Connected Devices</div>
          <div className="text-2xl font-bold text-brand-teal">{summary ? summary.device_count : '...'}</div>
        </div>
      </div>

      {/* Unified Search & Filters */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 mb-6 bg-slate-900/60 p-4 border border-slate-800 rounded-2xl">
        <div className="flex items-center gap-3 flex-1">
          <div className="relative flex-1">
            <svg
              className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500"
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
              className="w-full pl-10 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none focus:border-brand-purple transition-all"
            />
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* User Filter Dropdown */}
          <div className="flex items-center gap-2">
            <label className="text-xs font-medium text-slate-400">User:</label>
            <select
              value={userFilter}
              onChange={(e) => setUserFilter(e.target.value)}
              className="px-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-brand-purple cursor-pointer"
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
                className={`px-3 py-1.5 rounded-xl text-xs font-medium transition-all ${
                  categoryFilter === cat
                    ? 'bg-brand-purple text-white shadow-md'
                    : 'bg-slate-950 text-slate-400 hover:text-white border border-slate-800'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Files Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-slate-400 text-xs font-semibold uppercase tracking-wider">
                <th className="px-4 py-3.5">File Name & Format</th>
                <th className="px-4 py-3.5">Owner User</th>
                <th className="px-4 py-3.5">Category</th>
                <th className="px-4 py-3.5">Size</th>
                <th className="px-4 py-3.5">Device ID</th>
                <th className="px-4 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-sm">
              {loading ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500 text-xs">
                    Loading repository files...
                  </td>
                </tr>
              ) : filteredFiles.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500 text-xs">
                    No files found matching the criteria.
                  </td>
                </tr>
              ) : (
                filteredFiles.map((file) => (
                  <tr key={file.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="px-4 py-3.5 font-medium text-white text-xs">
                      <div className="flex items-center gap-2">
                        <span className="p-1.5 rounded bg-slate-800 text-slate-300 text-[10px] font-mono uppercase">
                          {file.name.split('.').pop() || 'file'}
                        </span>
                        <span>{file.name}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2">
                        <div className="w-6 h-6 rounded-full bg-brand-purple/20 text-brand-purple flex items-center justify-center font-bold text-[10px] border border-brand-purple/40 shrink-0">
                          {(file.display_name || file.username || 'U')[0].toUpperCase()}
                        </div>
                        <div className="flex flex-col">
                          <span className="text-white text-xs font-semibold">{file.display_name || file.username}</span>
                          <span className="text-[10px] text-slate-400 font-mono">@{file.username}</span>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3.5">
                      <span className="px-2.5 py-1 rounded-md text-[10px] font-semibold bg-slate-800 text-slate-300 border border-slate-700">
                        {file.category}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-slate-400 text-xs font-mono">
                      {file.size_formatted}
                    </td>
                    <td className="px-4 py-3.5 text-slate-400 text-xs font-mono">
                      {file.device_id}
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      <div className="inline-flex items-center gap-2">
                        <button
                          onClick={() => triggerDownload(file.download_url, file.name)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-brand-purple/20 hover:bg-brand-purple text-brand-purple hover:text-white border border-brand-purple/40 rounded-lg text-xs font-medium transition-all cursor-pointer shadow-sm"
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
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 bg-red-500/10 hover:bg-red-600 text-red-400 hover:text-white border border-red-500/30 rounded-lg text-xs font-medium transition-all cursor-pointer"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
