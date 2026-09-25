'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface AdminUser {
  id: string;
  email: string;
  username: string;
  created_at: number;
}

function timeAgo(timestamp: number): string {
  if (!timestamp) return 'Just now';
  const diffSec = Math.floor((Date.now() - timestamp) / 1000);
  if (diffSec < 60) return `${Math.max(1, diffSec)}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

type SortOption = 'NEWEST' | 'OLDEST' | 'USERNAME';

export default function AdminsPage() {
  const [admins, setAdmins] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter State
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('NEWEST');

  // Modals state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [deletingAdmin, setDeletingAdmin] = useState<AdminUser | null>(null);

  // Form State for Create
  const [formData, setFormData] = useState({
    email: '',
    username: '',
    password: '',
  });

  const fetchAdmins = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/admin/admins`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: AdminUser[] = await res.json();
      setAdmins(data);
      setError(null);
    } catch (e: unknown) {
      setError(`Failed to load admins: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAdmins();
  }, [fetchAdmins]);

  // Create Admin Handler
  const handleCreateAdmin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.email.trim() || !formData.password.trim()) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/admins`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.detail || 'Failed to create admin');
      }
      setShowCreateModal(false);
      setFormData({ email: '', username: '', password: '' });
      fetchAdmins();
    } catch (err: unknown) {
      alert(`Error: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Delete Admin Handler
  const handleDeleteAdmin = async () => {
    if (!deletingAdmin) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/admins/${deletingAdmin.id}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.detail || 'Failed to delete admin');
      }
      setDeletingAdmin(null);
      fetchAdmins();
    } catch (err: unknown) {
      alert(`Error: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Filter & Sort Logic
  const filteredAndSorted = useMemo(() => {
    return admins
      .filter((admin) => {
        const query = search.toLowerCase().trim();
        const matchesQuery =
          !query ||
          admin.username.toLowerCase().includes(query) ||
          admin.email.toLowerCase().includes(query);
        return matchesQuery;
      })
      .sort((a, b) => {
        if (sortBy === 'NEWEST') return b.created_at - a.created_at;
        if (sortBy === 'OLDEST') return a.created_at - b.created_at;
        if (sortBy === 'USERNAME') return a.username.localeCompare(b.username);
        return 0;
      });
  }, [admins, search, sortBy]);

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Administrators</h1>
          <p className="text-slate-500 text-xs mt-1 font-medium">
            Manage system administrators, credentials, and supervisory access roles.
          </p>
        </div>
        <button
          onClick={() => {
            setFormData({ email: '', username: '', password: '' });
            setShowCreateModal(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-semibold text-xs shadow-sm transition-all cursor-pointer"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add New Admin
        </button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-purple-600">{admins.length}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Total Admins</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-teal-600">{admins.length}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Active Credentials</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-sky-600">
            {admins[0]?.username ? `@${admins[0].username}` : 'None'}
          </div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Latest Admin</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-emerald-600">100%</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">System Protection</div>
        </div>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-rose-50 border border-rose-200 text-rose-800 rounded-xl text-xs font-medium shadow-sm">
          {error}
        </div>
      )}

      {/* Unified Filter Toolbar */}
      <div className="bg-white border border-slate-200/80 p-4 rounded-2xl mb-6 shadow-sm">
        <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
          
          {/* Search Input */}
          <div className="relative flex-1">
            <svg
              className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
              />
            </svg>
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by @username, email address…"
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-purple-600 transition-all"
            />
          </div>

          {/* Sort Dropdown */}
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500 font-medium hidden sm:inline">Sort:</span>
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortOption)}
                className="px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 focus:outline-none focus:border-purple-600 transition-all cursor-pointer font-medium"
              >
                <option value="NEWEST">Joined (Newest)</option>
                <option value="OLDEST">Joined (Oldest)</option>
                <option value="USERNAME">Username (A-Z)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Filter Badges Summary */}
        <div className="flex items-center justify-between text-xs text-slate-500 mt-3 pt-3 border-t border-slate-100">
          <div>
            Showing <span className="text-slate-800 font-semibold">{filteredAndSorted.length}</span> of {admins.length} administrators
          </div>
          {search && (
            <button
              onClick={() => setSearch('')}
              className="text-purple-600 hover:text-purple-700 font-semibold hover:underline text-xs"
            >
              Reset Search
            </button>
          )}
        </div>
      </div>

      {/* Admins Table */}
      <div className="bg-white border border-slate-200/80 rounded-2xl overflow-hidden shadow-sm">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-200/80 bg-slate-50/80 text-slate-500 font-semibold uppercase tracking-wider">
              <th className="px-4 py-3.5">Admin Profile</th>
              <th className="px-4 py-3.5">Email Address</th>
              <th className="px-4 py-3.5">Role & Access</th>
              <th className="px-4 py-3.5">Created</th>
              <th className="px-4 py-3.5 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-slate-400">
                  <div className="flex items-center justify-center gap-2 font-medium">
                    <svg className="animate-spin w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Loading administrators…
                  </div>
                </td>
              </tr>
            ) : filteredAndSorted.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-slate-400 font-medium">
                  No administrators match the search criteria.
                </td>
              </tr>
            ) : (
              filteredAndSorted.map((admin) => (
                <tr key={admin.id} className="hover:bg-slate-50/60 transition-colors">
                  <td className="px-4 py-3.5">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-purple-600 to-indigo-600 flex items-center justify-center font-bold text-white text-xs shadow-sm">
                        {admin.username.charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-slate-900 font-mono font-semibold">@{admin.username}</span>
                          <span className="px-2 py-0.5 rounded text-[10px] bg-purple-50 text-purple-700 border border-purple-200 font-bold">
                            ADMIN
                          </span>
                        </div>
                        <div className="text-slate-500 text-[11px] font-medium">VibeSync Control Panel</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-slate-700 font-mono text-xs font-medium">
                    {admin.email}
                  </td>
                  <td className="px-4 py-3.5">
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                      Full Access
                    </span>
                  </td>
                  <td className="px-4 py-3.5 text-slate-500 font-medium text-xs">
                    {timeAgo(admin.created_at)}
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    <button
                      onClick={() => setDeletingAdmin(admin)}
                      className="px-2.5 py-1 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-lg text-xs font-semibold transition-colors cursor-pointer"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Create Admin Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-100">
            <h2 className="text-lg font-bold text-slate-900 mb-1">Create Administrator Account</h2>
            <p className="text-xs text-slate-500 mb-5">
              Add a new administrator with email credentials and dashboard management permissions.
            </p>

            <form onSubmit={handleCreateAdmin} className="space-y-4 text-xs">
              <div>
                <label className="block text-slate-700 font-semibold mb-1">Email Address *</label>
                <input
                  type="email"
                  required
                  placeholder="admin@example.com"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 focus:outline-none focus:border-purple-600 font-medium"
                />
              </div>

              <div>
                <label className="block text-slate-700 font-semibold mb-1">Username (optional)</label>
                <input
                  type="text"
                  placeholder="e.g. John"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 focus:outline-none focus:border-purple-600 font-medium"
                />
              </div>

              <div>
                <label className="block text-slate-700 font-semibold mb-1">Password *</label>
                <input
                  type="password"
                  required
                  placeholder="Enter strong password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 focus:outline-none focus:border-purple-600 font-medium"
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50 font-semibold transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-semibold shadow-sm transition-colors cursor-pointer"
                >
                  Create Admin
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Admin Modal */}
      {deletingAdmin && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 shadow-xl border border-slate-100">
            <h2 className="text-lg font-bold text-slate-900 mb-1">Delete Administrator</h2>
            <p className="text-xs text-slate-500 mb-5">
              Are you sure you want to remove <span className="font-semibold text-slate-900">@{deletingAdmin.username}</span> ({deletingAdmin.email})? This action cannot be undone.
            </p>

            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setDeletingAdmin(null)}
                className="px-4 py-2 border border-slate-200 rounded-xl text-slate-600 hover:bg-slate-50 font-semibold text-xs transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteAdmin}
                className="px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-semibold text-xs shadow-sm transition-colors cursor-pointer"
              >
                Confirm Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
