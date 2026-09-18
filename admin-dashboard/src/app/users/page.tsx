'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';

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
          <h1 className="text-2xl font-bold text-white">User Registry & Directory</h1>
          <p className="text-slate-400 mt-1 text-sm">
            Manage, filter, search, and perform CRUD actions on all registered VibeSync profiles.
          </p>
        </div>
        <button
          onClick={() => {
            setFormData({ username: '', display_name: '', bio: '', is_banned: false });
            setShowCreateModal(true);
          }}
          className="flex items-center justify-center gap-2 px-4 py-2.5 bg-brand-purple hover:bg-brand-purple/90 text-white rounded-xl font-medium text-xs shadow-lg shadow-brand-purple/20 transition-all cursor-pointer"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Add New User
        </button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="text-3xl font-bold text-brand-teal">{users.length}</div>
          <div className="text-xs text-slate-500 mt-1">Total Users</div>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="text-3xl font-bold text-brand-purple">
            {users.filter((u) => Date.now() - u.created_at < 86400000).length}
          </div>
          <div className="text-xs text-slate-500 mt-1">Active Today</div>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="text-3xl font-bold text-sky-400">
            {users.filter((u) => u.device_id).length}
          </div>
          <div className="text-xs text-slate-500 mt-1">Linked Devices</div>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <div className="text-3xl font-bold text-red-400">
            {users.filter((u) => u.is_banned).length}
          </div>
          <div className="text-xs text-slate-500 mt-1">Banned Accounts</div>
        </div>
      </div>

      {error && (
        <div className="mb-6 p-4 bg-red-900/50 border border-red-700 text-red-200 rounded-xl text-xs">
          {error}
        </div>
      )}

      {/* UNIFIED FILTER TOOLBAR: Search Bar + Status Dropdown + Sort Dropdown */}
      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl mb-6 shadow-md">
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
              className="w-full pl-10 pr-4 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white placeholder-slate-500 focus:outline-none focus:border-brand-purple transition-all"
            />
          </div>

          {/* Unified Filter Dropdowns Group */}
          <div className="flex items-center gap-3">
            {/* Status Filter Dropdown */}
            <div className="flex items-center gap-2">
              <span className="text-xs text-slate-400 font-medium hidden sm:inline">Status:</span>
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as FilterStatus)}
                className="px-3.5 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-brand-purple transition-all cursor-pointer font-medium"
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
              <span className="text-xs text-slate-400 font-medium hidden sm:inline">Sort:</span>
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortOption)}
                className="px-3.5 py-2.5 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-200 focus:outline-none focus:border-brand-purple transition-all cursor-pointer font-medium"
              >
                <option value="NEWEST">Joined (Newest)</option>
                <option value="OLDEST">Joined (Oldest)</option>
                <option value="USERNAME">Username (A-Z)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Filter Badges Summary */}
        <div className="flex items-center justify-between text-xs text-slate-500 mt-3 pt-3 border-t border-slate-800/80">
          <div>
            Showing <span className="text-white font-semibold">{filteredAndSorted.length}</span> of {users.length} users
          </div>
          {(search || statusFilter !== 'ALL' || sortBy !== 'NEWEST') && (
            <button
              onClick={() => {
                setSearch('');
                setStatusFilter('ALL');
                setSortBy('NEWEST');
              }}
              className="text-brand-purple hover:underline text-xs"
            >
              Reset Filters
            </button>
          )}
        </div>
      </div>

      {/* Users Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-xl">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-800 bg-slate-800/50">
              <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-400 uppercase tracking-wider">
                User Profile
              </th>
              <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Status / Bio
              </th>
              <th className="text-left px-4 py-3.5 text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Joined
              </th>
              <th className="text-right px-4 py-3.5 text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800">
            {loading ? (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-slate-500">
                  <div className="flex items-center justify-center gap-2">
                    <svg className="animate-spin w-4 h-4 text-brand-purple" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Loading registered users…
                  </div>
                </td>
              </tr>
            ) : filteredAndSorted.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-12 text-center text-slate-500">
                  No users match the selected search & filter criteria.
                </td>
              </tr>
            ) : (
              filteredAndSorted.map((user) => (
                <tr key={user.id} className="hover:bg-slate-800/40 transition-colors">
                  <td className="px-4 py-3.5">
                    <div className="flex items-center gap-3">
                      <div className="w-9 h-9 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center font-bold text-brand-teal text-xs">
                        {user.username.charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-brand-teal font-mono font-semibold">@{user.username}</span>
                          {user.is_banned ? (
                            <span className="px-2 py-0.5 rounded text-[10px] bg-red-950 text-red-400 border border-red-800 font-bold">
                              BANNED
                            </span>
                          ) : null}
                        </div>
                        <div className="text-white text-xs font-medium">{user.display_name}</div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 text-slate-400 text-xs max-w-xs truncate">
                    {user.bio ?? 'Available | Powered by VibeSync'}
                  </td>
                  <td className="px-4 py-3.5 text-slate-500 text-xs">{timeAgo(user.created_at)}</td>
                  <td className="px-4 py-3.5 text-right">
                    <div className="flex items-center justify-end gap-2">
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
                        className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium transition-colors"
                      >
                        Edit
                      </button>

                      {/* Ban / Unban Toggle */}
                      <button
                        onClick={() => handleToggleBan(user)}
                        className={`px-2.5 py-1 rounded-lg text-xs font-medium transition-colors ${
                          user.is_banned
                            ? 'bg-emerald-950 hover:bg-emerald-900 text-emerald-300 border border-emerald-800'
                            : 'bg-amber-950 hover:bg-amber-900 text-amber-300 border border-amber-800'
                        }`}
                      >
                        {user.is_banned ? 'Unban' : 'Ban'}
                      </button>

                      {/* Delete Button */}
                      <button
                        onClick={() => setDeletingUser(user)}
                        className="px-2.5 py-1 bg-red-950/80 hover:bg-red-900 text-red-300 border border-red-800 rounded-lg text-xs font-medium transition-colors"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-xl font-bold text-white mb-4">Create New User Profile</h2>
            <form onSubmit={handleCreateUser} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">
                  Username (@)
                </label>
                <input
                  type="text"
                  required
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  placeholder="e.g. john_doe"
                  className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-brand-purple"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">
                  Display Name
                </label>
                <input
                  type="text"
                  value={formData.display_name}
                  onChange={(e) => setFormData({ ...formData, display_name: e.target.value })}
                  placeholder="John Doe"
                  className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-brand-purple"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Bio</label>
                <input
                  type="text"
                  value={formData.bio}
                  onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
                  placeholder="Available | Powered by VibeSync"
                  className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-brand-purple"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="create_banned"
                  checked={formData.is_banned}
                  onChange={(e) => setFormData({ ...formData, is_banned: e.target.checked })}
                  className="rounded bg-slate-950 border-slate-800 text-brand-purple"
                />
                <label htmlFor="create_banned" className="text-xs text-slate-300">
                  Mark as Banned Account
                </label>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="px-4 py-2 bg-slate-800 text-slate-300 rounded-xl text-xs font-medium hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-brand-purple text-white rounded-xl text-xs font-medium hover:bg-brand-purple/90"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-xl font-bold text-white mb-4">Edit @{editingUser.username}</h2>
            <form onSubmit={handleUpdateUser} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">
                  Display Name
                </label>
                <input
                  type="text"
                  value={formData.display_name}
                  onChange={(e) => setFormData({ ...formData, display_name: e.target.value })}
                  className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-brand-purple"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Bio</label>
                <input
                  type="text"
                  value={formData.bio}
                  onChange={(e) => setFormData({ ...formData, bio: e.target.value })}
                  className="w-full px-3.5 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-white focus:outline-none focus:border-brand-purple"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="edit_banned"
                  checked={formData.is_banned}
                  onChange={(e) => setFormData({ ...formData, is_banned: e.target.checked })}
                  className="rounded bg-slate-950 border-slate-800 text-brand-purple"
                />
                <label htmlFor="edit_banned" className="text-xs text-slate-300">
                  Account Banned Status
                </label>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setEditingUser(null)}
                  className="px-4 py-2 bg-slate-800 text-slate-300 rounded-xl text-xs font-medium hover:bg-slate-700"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-brand-purple text-white rounded-xl text-xs font-medium hover:bg-brand-purple/90"
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-xl font-bold text-white mb-2">Delete User Account?</h2>
            <p className="text-xs text-slate-400 mb-6">
              Are you sure you want to delete <span className="text-brand-teal font-semibold">@{deletingUser.username}</span>? This will unlink associated devices.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeletingUser(null)}
                className="px-4 py-2 bg-slate-800 text-slate-300 rounded-xl text-xs font-medium hover:bg-slate-700"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteUser}
                className="px-4 py-2 bg-red-600 text-white rounded-xl text-xs font-medium hover:bg-red-700"
              >
                Yes, Delete User
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
