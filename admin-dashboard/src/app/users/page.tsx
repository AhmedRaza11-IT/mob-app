'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import AdminChatModal from '@/components/AdminChatModal';
import AdminCallModal from '@/components/AdminCallModal';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface User {
  id: string;
  username: string;
  display_name: string;
  bio: string | null;
  is_banned?: number;
  created_at: number;
  device_id?: string | null;
  device_model?: string | null;
}

function timeAgo(timestamp: number): string {
  const diffSec = Math.floor((Date.now() - timestamp) / 1000);
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

type FilterStatus = 'ALL' | 'ACTIVE' | 'HAS_DEVICE' | 'UNASSIGNED' | 'BANNED';
type SortOption = 'NEWEST' | 'OLDEST' | 'USERNAME';

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Unified Filter State
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<FilterStatus>('ALL');
  const [sortBy, setSortBy] = useState<SortOption>('NEWEST');

  // Modals state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [deletingUser, setDeletingUser] = useState<User | null>(null);

  // Live Communication Modals
  const [chatTargetUser, setChatTargetUser] = useState<{ id: string; username: string; display_name: string } | null>(null);
  const [callTargetUser, setCallTargetUser] = useState<{ id: string; username: string; display_name: string } | null>(null);
  const [isCallVideo, setIsCallVideo] = useState(false);

  // Form State for Create/Edit
  const [formData, setFormData] = useState({
    username: '',
    display_name: '',
    bio: '',
    is_banned: false,
  });

  const fetchUsers = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/users/all`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: User[] = await res.json();
      setUsers(data);
      setError(null);
    } catch (e: unknown) {
      setError(`Failed to load users: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // Create User Handler
  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.username.trim()) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/users`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });
      if (!res.ok) {
        const errData = await res.json();
        throw new Error(errData.detail || 'Failed to create user');
      }
      setShowCreateModal(false);
      setFormData({ username: '', display_name: '', bio: '', is_banned: false });
      fetchUsers();
    } catch (err: unknown) {
      alert(`Error: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Edit User Handler
  const handleUpdateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/users/${editingUser.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          display_name: formData.display_name,
          bio: formData.bio,
          is_banned: formData.is_banned,
        }),
      });
      if (!res.ok) throw new Error('Failed to update user');
      setEditingUser(null);
      fetchUsers();
    } catch (err: unknown) {
      alert(`Error: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Toggle Ban Status
  const handleToggleBan = async (user: User) => {
    try {
      const res = await fetch(`${API_BASE}/api/admin/users/${user.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          display_name: user.display_name,
          bio: user.bio,
          is_banned: user.is_banned ? 0 : 1,
        }),
      });
      if (!res.ok) throw new Error('Failed to update ban status');
      fetchUsers();
    } catch (err: unknown) {
      alert(`Error: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Delete User Handler
  const handleDeleteUser = async () => {
    if (!deletingUser) return;
    try {
      const res = await fetch(`${API_BASE}/api/admin/users/${deletingUser.id}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error('Failed to delete user');
      setDeletingUser(null);
      fetchUsers();
    } catch (err: unknown) {
      alert(`Error: ${err instanceof Error ? err.message : String(err)}`);
    }
  };

  // Unified Filtering & Sorting
  const filteredAndSorted = useMemo(() => {
    let result = users.filter((u) => {
      const q = search.toLowerCase().trim();
      const matchesSearch =
        !q ||
        u.username.toLowerCase().includes(q) ||
        u.display_name.toLowerCase().includes(q) ||
        (u.bio ?? '').toLowerCase().includes(q);

      if (!matchesSearch) return false;

      if (statusFilter === 'ACTIVE') {
        return Date.now() - u.created_at < 86400000;
      }
      if (statusFilter === 'HAS_DEVICE') {
        return Boolean(u.device_id);
      }
      if (statusFilter === 'UNASSIGNED') {
        return u.username.toLowerCase() === 'current user' || !u.device_id;
      }
      if (statusFilter === 'BANNED') {
        return Boolean(u.is_banned);
      }

      return true;
    });

    result.sort((a, b) => {
      if (sortBy === 'NEWEST') return b.created_at - a.created_at;
      if (sortBy === 'OLDEST') return a.created_at - b.created_at;
      if (sortBy === 'USERNAME') return a.username.localeCompare(b.username);
      return 0;
    });

    return result;
  }, [users, search, statusFilter, sortBy]);

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Page Header + Create User Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">User Registry & Directory</h1>
          <p className="text-slate-500 mt-1 text-sm font-medium">
            Manage, filter, search, and perform CRUD actions on all registered VibeSync profiles.
          </p>
        </div>
        <button
          onClick={() => {
            setFormData({ username: '', display_name: '', bio: '', is_banned: false });
            setShowCreateModal(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-semibold text-xs shadow-sm transition-all cursor-pointer"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add New User
        </button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-teal-600">{users.length}</div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Total Users</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-purple-600">
            {users.filter((u) => Date.now() - u.created_at < 86400000).length}
          </div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Active Today</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-sky-600">
            {users.filter((u) => u.device_id).length}
          </div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Linked Devices</div>
        </div>
        <div className="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-sm">
          <div className="text-3xl font-bold text-rose-600">
            {users.filter((u) => u.is_banned).length}
          </div>
          <div className="text-xs font-semibold text-slate-500 mt-1 uppercase tracking-wider">Banned Accounts</div>
        </div>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-rose-50 border border-rose-200 text-rose-800 rounded-xl text-xs font-medium shadow-sm">
          {error}
        </div>
      )}

      {/* UNIFIED FILTER TOOLBAR: Search Bar + Status Dropdown + Sort Dropdown */}
      <div className="bg-white border border-slate-200/80 p-4 rounded-2xl mb-6 shadow-sm">
        <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
          
          {/* Unified Search Input */}
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
              placeholder="Search by @username, display name, bio…"
              className="w-full pl-10 pr-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:border-purple-600 transition-all"
            />
          </div>

          {/* Unified Filter Dropdowns Group */}
          <div className="flex items-center gap-3">
            {/* Status Filter Dropdown */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-500 font-medium hidden sm:inline">Status:</span>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as FilterStatus)}
                className="px-3.5 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 focus:outline-none focus:border-purple-600 transition-all cursor-pointer font-medium"
              >
                <option value="ALL">All Statuses</option>
                <option value="ACTIVE">⚡ Active Today</option>
                <option value="HAS_DEVICE">📱 Has Linked Device</option>
                <option value="UNASSIGNED">⚠️ Unassigned Profiles</option>
                <option value="BANNED">🚫 Banned Accounts</option>
              </select>
            </div>

            {/* Sort Dropdown */}
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
            Showing <span className="text-slate-800 font-semibold">{filteredAndSorted.length}</span> of {users.length} users
          </div>
          {(search || statusFilter !== 'ALL' || sortBy !== 'NEWEST') && (
            <button
              onClick={() => {
                setSearch('');
                setStatusFilter('ALL');
                setSortBy('NEWEST');
              }}
              className="text-purple-600 hover:text-purple-700 font-semibold hover:underline text-xs"
            >
              Reset Filters
            </button>
          )}
        </div>
      </div>

      {/* Users Table */}
      <div className="bg-white border border-slate-200/80 rounded-2xl overflow-hidden shadow-sm">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-200/80 bg-slate-50/80 text-slate-500 font-semibold uppercase tracking-wider">
              <th className="px-4 py-3.5">
                User Profile
              </th>
              <th className="px-4 py-3.5">
                Status / Bio
              </th>
              <th className="px-4 py-3.5">
                Joined
              </th>
              <th className="px-4 py-3.5 text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-slate-400">
                  <div className="flex items-center justify-center gap-2 font-medium">
                    <svg className="animate-spin w-4 h-4 text-purple-600" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Loading registered users…
                  </div>
                </td>
              </tr>
            ) : filteredAndSorted.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-slate-400 font-medium">
                  No users match the selected search & filter criteria.
                </td>
              </tr>
            ) : (
              filteredAndSorted.map((user) => (
                <tr key={user.id} className="hover:bg-slate-50/60 transition-colors">
                  <td className="px-4 py-3.5">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-purple-50 border border-purple-200 flex items-center justify-center font-bold text-purple-700 text-xs">
                        {user.username.charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-slate-900 font-mono font-semibold">@{user.username}</span>
                          {user.is_banned ? (
                            <span className="px-2 py-0.5 rounded text-[10px] bg-rose-50 text-rose-700 border border-rose-200 font-bold">
                              BANNED
                            </span>
                          ) : null}
                        </div>
                        <div className="text-slate-500 text-xs font-medium">{user.display_name}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-slate-600 text-xs max-w-xs truncate font-medium">
                    {user.bio ?? 'Available | Powered by VibeSync'}
                  </td>
                  <td className="px-4 py-3.5 text-slate-500 font-medium text-xs">{timeAgo(user.created_at)}</td>
                  <td className="px-4 py-3.5 text-right">
                    <div className="flex items-center justify-end gap-1.5 flex-wrap">
                      {/* Message Button */}
                      <button
                        onClick={() => setChatTargetUser(user)}
                        className="px-2.5 py-1 bg-purple-50 hover:bg-purple-100 text-purple-700 border border-purple-200 rounded-lg text-xs font-semibold transition-all flex items-center gap-1 cursor-pointer"
                        title={`Message @${user.username}`}
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                        </svg>
                        <span>Chat</span>
                      </button>

                      {/* Voice Call Button */}
                      <button
                        onClick={() => {
                          setCallTargetUser(user);
                          setIsCallVideo(false);
                        }}
                        className="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border border-emerald-200 rounded-lg text-xs font-semibold transition-all flex items-center gap-1 cursor-pointer"
                        title={`Voice Call @${user.username}`}
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
                        </svg>
                        <span>Call</span>
                      </button>

                      {/* Video Call Button */}
                      <button
                        onClick={() => {
                          setCallTargetUser(user);
                          setIsCallVideo(true);
                        }}
                        className="px-2.5 py-1 bg-sky-50 hover:bg-sky-100 text-sky-700 border border-sky-200 rounded-lg text-xs font-semibold transition-all flex items-center gap-1 cursor-pointer"
                        title={`Video Call @${user.username}`}
                      >
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                        </svg>
                        <span>Video</span>
                      </button>

                      {/* Edit Button */}
                      <button
                        onClick={() => {
                          setEditingUser(user);
                          setFormData({
                            username: user.username,
                            display_name: user.display_name,
                            bio: user.bio ?? '',
                            is_banned: Boolean(user.is_banned),
                          });
                        }}
                        className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200/80 text-slate-700 border border-slate-200 rounded-lg text-xs font-semibold transition-colors cursor-pointer"
                      >
                        Edit
                      </button>

                      {/* Ban / Unban Toggle */}
                      <button
                        onClick={() => handleToggleBan(user)}
                        className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors cursor-pointer border ${
                          user.is_banned
                            ? 'bg-emerald-50 hover:bg-emerald-100 text-emerald-700 border-emerald-200'
                            : 'bg-amber-50 hover:bg-amber-100 text-amber-800 border-amber-200'
                        }`}
                      >
                        {user.is_banned ? 'Unban' : 'Ban'}
                      </button>

                      {/* Delete Button */}
                      <button
                        onClick={() => setDeletingUser(user)}
                        className="px-2.5 py-1 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 rounded-lg text-xs font-semibold transition-colors cursor-pointer"
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

      {/* CREATE USER MODAL */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
          <div className="bg-white border border-slate-200 rounded-2xl p-6 w-full max-w-md shadow-xl">
            <h2 className="text-base font-bold text-slate-900 mb-4">Create New User Profile</h2>
            <form onSubmit={handleCreateUser} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5 uppercase tracking-wider">
                  Username (@)
                </label>
                <input
                  type="text"
                  required
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  placeholder="e.g. john_doe"
                  className="w-full px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-purple-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5 uppercase tracking-wider">
                  Display Name
                </label>
                <input
                  type="text"
                  value={formData.display_name}
                  onChange={(e) => setFormData({ ...formData, display_name: e.target.value })}
                  placeholder="John Doe"
                  className="w-full px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-purple-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5 uppercase tracking-wider">Bio</label>
                <input
                  type="text"
                  value={formData.bio}
                  onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
                  placeholder="Available | Powered by VibeSync"
                  className="w-full px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-purple-600"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="create_banned"
                  checked={formData.is_banned}
                  onChange={(e) => setFormData({ ...formData, is_banned: e.target.checked })}
                  className="rounded border-slate-300 text-purple-600 focus:ring-purple-500"
                />
                <label htmlFor="create_banned" className="text-xs font-medium text-slate-700">
                  Mark as Banned Account
                </label>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-purple-600 text-white rounded-xl text-xs font-semibold hover:bg-purple-700 transition-colors shadow-sm"
                >
                  Create User
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EDIT USER MODAL */}
      {editingUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
          <div className="bg-white border border-slate-200 rounded-2xl p-6 w-full max-w-md shadow-xl">
            <h2 className="text-base font-bold text-slate-900 mb-4">Edit @{editingUser.username}</h2>
            <form onSubmit={handleUpdateUser} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5 uppercase tracking-wider">
                  Display Name
                </label>
                <input
                  type="text"
                  value={formData.display_name}
                  onChange={(e) => setFormData({ ...formData, display_name: e.target.value })}
                  className="w-full px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-purple-600"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1.5 uppercase tracking-wider">Bio</label>
                <input
                  type="text"
                  value={formData.bio}
                  onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
                  className="w-full px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-purple-600"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="edit_banned"
                  checked={formData.is_banned}
                  onChange={(e) => setFormData({ ...formData, is_banned: e.target.checked })}
                  className="rounded border-slate-300 text-purple-600 focus:ring-purple-500"
                />
                <label htmlFor="edit_banned" className="text-xs font-medium text-slate-700">
                  Account Banned Status
                </label>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setEditingUser(null)}
                  className="px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-purple-600 text-white rounded-xl text-xs font-semibold hover:bg-purple-700 transition-colors shadow-sm"
                >
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DELETE USER CONFIRMATION MODAL */}
      {deletingUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4">
          <div className="bg-white border border-rose-200 rounded-2xl p-6 w-full max-w-md shadow-xl">
            <h2 className="text-base font-bold text-slate-900 mb-2">Delete User Account?</h2>
            <p className="text-xs text-slate-500 mb-6 leading-relaxed">
              Are you sure you want to delete <span className="text-slate-900 font-semibold">@{deletingUser.username}</span>? This will unlink associated devices.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeletingUser(null)}
                className="px-4 py-2 bg-white border border-slate-200 text-slate-700 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteUser}
                className="px-4 py-2 bg-rose-600 text-white rounded-xl text-xs font-semibold hover:bg-rose-700 transition-colors shadow-sm"
              >
                Yes, Delete User
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ADMIN CHAT MODAL */}
      {chatTargetUser && (
        <AdminChatModal
          user={chatTargetUser}
          onClose={() => setChatTargetUser(null)}
          onStartCall={(target, isVideo) => {
            setCallTargetUser(target);
            setIsCallVideo(isVideo);
          }}
        />
      )}

      {/* ADMIN CALL MODAL */}
      {callTargetUser && (
        <AdminCallModal
          user={callTargetUser}
          isVideo={isCallVideo}
          onClose={() => setCallTargetUser(null)}
        />
      )}
    </div>
  );
}
