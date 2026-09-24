'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface FriendInfo {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  bio: string | null;
  is_banned?: number;
}

interface UserOverview {
  id: string;
  username: string;
  display_name: string;
  avatar_url: string | null;
  bio: string | null;
  is_banned?: number;
  created_at: number;
  friends_count: number;
  friends: FriendInfo[];
}

export default function FriendsManagerPage() {
  const [users, setUsers] = useState<UserOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [userSearch, setUserSearch] = useState('');
  const [selectedFriendToAssign, setSelectedFriendToAssign] = useState<string>('');
  const [actionLoading, setActionLoading] = useState(false);
  const [notification, setNotification] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

  const showNotification = (message: string, type: 'success' | 'error' = 'success') => {
    setNotification({ message, type });
    setTimeout(() => setNotification(null), 4000);
  };

  const fetchOverview = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await fetch(`${API_BASE}/api/admin/friends-overview`);
      if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
      const data: UserOverview[] = await res.json();
      setUsers(data);
      if (!selectedUserId && data.length > 0) {
        setSelectedUserId(data[0].id);
      }
    } catch (e: any) {
      setError(e.message || 'Failed to fetch friends data');
    } finally {
      setLoading(false);
    }
  }, [selectedUserId]);

  useEffect(() => {
    fetchOverview();
  }, []);

  const selectedUser = useMemo(() => {
    return users.find((u) => u.id === selectedUserId) || null;
  }, [users, selectedUserId]);

  // Filtered user list for selection column
  const filteredUsers = useMemo(() => {
    const q = userSearch.toLowerCase().trim();
    if (!q) return users;
    return users.filter(
      (u) =>
        u.username.toLowerCase().includes(q) ||
        u.display_name.toLowerCase().includes(q)
    );
  }, [users, userSearch]);

  // Candidates who can be added as a friend to the selected user
  const availableFriendCandidates = useMemo(() => {
    if (!selectedUser) return [];
    const currentFriendIds = new Set(selectedUser.friends.map((f) => f.id));
    return users.filter(
      (u) => u.id !== selectedUser.id && !currentFriendIds.has(u.id)
    );
  }, [users, selectedUser]);

  // Handle Assign Friend
  const handleAssignFriend = async () => {
    if (!selectedUser || !selectedFriendToAssign) return;
    try {
      setActionLoading(true);
      const res = await fetch(`${API_BASE}/api/admin/users/${encodeURIComponent(selectedUser.username)}/friends`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ friend_id: selectedFriendToAssign }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || 'Failed to assign friend');

      showNotification(data.message || 'Friend assigned successfully!', 'success');
      setSelectedFriendToAssign('');
      await fetchOverview();
    } catch (err: any) {
      showNotification(err.message || 'Error assigning friend', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // Handle Remove Friend
  const handleRemoveFriend = async (friend: FriendInfo) => {
    if (!selectedUser) return;
    if (!confirm(`Are you sure you want to remove @${friend.username} from @${selectedUser.username}'s friends?`)) {
      return;
    }
    try {
      setActionLoading(true);
      const res = await fetch(
        `${API_BASE}/api/admin/users/${encodeURIComponent(selectedUser.username)}/friends/${encodeURIComponent(friend.username)}`,
        { method: 'DELETE' }
      );
      const data = await res.json();
      if (!res.ok) throw new Error(data.detail || 'Failed to remove friend');

      showNotification(data.message || 'Friend removed successfully!', 'success');
      await fetchOverview();
    } catch (err: any) {
      showNotification(err.message || 'Error removing friend', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  // Overall statistics
  const totalUsers = users.length;
  const totalFriendships = useMemo(() => {
    return Math.floor(users.reduce((acc, u) => acc + u.friends_count, 0) / 2);
  }, [users]);

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {notification && (
        <div
          className={`fixed top-4 right-4 z-50 flex items-center gap-2 px-4 py-3 rounded-xl shadow-xl border text-sm font-medium transition-all transform animate-bounce ${
            notification.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
              : 'bg-red-50 text-red-800 border-red-200'
          }`}
        >
          <span>{notification.type === 'success' ? '✅' : '❌'}</span>
          <span>{notification.message}</span>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-slate-800">🤝 Friends & Roster Management</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700">
              Live Sync
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Assign or remove friends for specific users. Changes reflect dynamically in the VibeSync mobile app in real-time.
          </p>
        </div>

        <button
          onClick={fetchOverview}
          disabled={loading || actionLoading}
          className="flex items-center gap-1.5 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs rounded-xl transition cursor-pointer border border-slate-200"
          title="Refresh Data"
        >
          <span className={loading ? 'animate-spin inline-block' : ''}>🔄</span>
          <span>Refresh</span>
        </button>
      </div>

      {/* Stats Summary Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center text-xl font-bold">
            👥
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Registered Users</div>
            <div className="text-2xl font-bold text-slate-800">{totalUsers}</div>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center text-xl font-bold">
            🤝
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Friendships</div>
            <div className="text-2xl font-bold text-emerald-600">{totalFriendships}</div>
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm flex items-center gap-4">
          <div className="w-12 h-12 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center text-xl font-bold">
            ⚡
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">WebSocket Gateway</div>
            <div className="text-sm font-semibold text-purple-700 flex items-center gap-1.5 mt-1">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
              Real-time push enabled
            </div>
          </div>
        </div>
      </div>

      {/* Main 2-Column Interface */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: User Selection (4 cols) */}
        <div className="lg:col-span-4 bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden flex flex-col max-h-[750px]">
          <div className="p-4 border-b border-slate-100 bg-slate-50/50">
            <h2 className="text-sm font-bold text-slate-800 uppercase tracking-wider mb-2">
              Select User ({filteredUsers.length})
            </h2>
            <div className="relative">
              <input
                type="text"
                value={userSearch}
                onChange={(e) => setUserSearch(e.target.value)}
                placeholder="Filter users by name or @"
                className="w-full pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
              <span className="absolute left-3 top-2.5 text-slate-400 text-xs">🔍</span>
            </div>
          </div>

          <div className="overflow-y-auto divide-y divide-slate-100 flex-1 p-2 space-y-1">
            {loading ? (
              <div className="p-8 text-center text-xs text-slate-400">Loading users...</div>
            ) : filteredUsers.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400">No users found</div>
            ) : (
              filteredUsers.map((u) => {
                const isSelected = u.id === selectedUserId;
                return (
                  <button
                    key={u.id}
                    onClick={() => {
                      setSelectedUserId(u.id);
                      setSelectedFriendToAssign('');
                    }}
                    className={`w-full text-left p-3 rounded-xl transition cursor-pointer flex items-center justify-between gap-3 ${
                      isSelected
                        ? 'bg-emerald-50 border border-emerald-300 text-emerald-950 shadow-sm'
                        : 'hover:bg-slate-50 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div
                        className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm text-white flex-shrink-0 ${
                          isSelected ? 'bg-emerald-600' : 'bg-slate-600'
                        }`}
                      >
                        {u.display_name.charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <div className="font-semibold text-xs truncate">{u.display_name}</div>
                        <div className="text-[11px] text-slate-400 truncate">@{u.username}</div>
                      </div>
                    </div>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full flex-shrink-0 ${
                        u.friends_count > 0
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-slate-100 text-slate-500'
                      }`}
                    >
                      {u.friends_count} {u.friends_count === 1 ? 'friend' : 'friends'}
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: Friends Manager for Selected User (8 cols) */}
        <div className="lg:col-span-8 space-y-6">
          {selectedUser ? (
            <>
              {/* Selected User Header Card */}
              <div className="bg-white rounded-2xl p-6 text-slate-800 shadow-sm border border-slate-200/80 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <div className="flex items-center gap-4">
                  <div className="w-14 h-14 rounded-2xl bg-emerald-600 flex items-center justify-center text-white text-2xl font-bold shadow-sm">
                    {selectedUser.display_name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h2 className="text-xl font-bold text-slate-800">{selectedUser.display_name}</h2>
                      <span className="px-2.5 py-0.5 rounded-md bg-slate-100 text-slate-600 text-xs font-mono border border-slate-200">
                        @{selectedUser.username}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1 max-w-md">
                      {selectedUser.bio || 'VibeSync User'}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2.5 bg-slate-50 px-4 py-2.5 rounded-xl border border-slate-200">
                  <span className="text-xl">🤝</span>
                  <div>
                    <div className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">Friends Count</div>
                    <div className="text-lg font-bold text-slate-800 leading-tight">{selectedUser.friends.length}</div>
                  </div>
                </div>
              </div>

              {/* Action Box: Assign New Friend */}
              <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm space-y-4">
                <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                  <span>➕</span>
                  <span>Assign New Friend to @{selectedUser.username}</span>
                </h3>

                {availableFriendCandidates.length === 0 ? (
                  <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-xs text-slate-500 text-center">
                    All other users in the directory are already friends with @{selectedUser.username}.
                  </div>
                ) : (
                  <div className="flex flex-col sm:flex-row gap-3">
                    <select
                      value={selectedFriendToAssign}
                      onChange={(e) => setSelectedFriendToAssign(e.target.value)}
                      className="flex-1 px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    >
                      <option value="">Select a user to assign as friend...</option>
                      {availableFriendCandidates.map((cand) => (
                        <option key={cand.id} value={cand.id}>
                          {cand.display_name} (@{cand.username})
                        </option>
                      ))}
                    </select>

                    <button
                      onClick={handleAssignFriend}
                      disabled={!selectedFriendToAssign || actionLoading}
                      className="px-6 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white font-semibold text-xs rounded-xl shadow-sm transition cursor-pointer flex items-center justify-center gap-2"
                    >
                      {actionLoading ? 'Assigning...' : '+ Assign Friend'}
                    </button>
                  </div>
                )}
              </div>

              {/* Current Friends List */}
              <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold text-slate-800 uppercase tracking-wider flex items-center gap-2">
                    <span>👥</span>
                    <span>Current Friends of @{selectedUser.username} ({selectedUser.friends.length})</span>
                  </h3>
                  <span className="text-xs text-slate-400">
                    Visible in @{selectedUser.username}&apos;s mobile app
                  </span>
                </div>

                {selectedUser.friends.length === 0 ? (
                  <div className="p-12 text-center space-y-3 bg-slate-50 rounded-2xl border border-dashed border-slate-200">
                    <div className="text-4xl text-slate-300">👤</div>
                    <div className="text-sm font-bold text-slate-700">No friends assigned yet</div>
                    <p className="text-xs text-slate-400 max-w-sm mx-auto">
                      Use the assign selector above to connect @{selectedUser.username} with other users.
                      Once assigned, they will immediately appear in each other&apos;s mobile Friends tab.
                    </p>
                  </div>
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {selectedUser.friends.map((friend) => (
                      <div
                        key={friend.id}
                        className="p-4 rounded-xl border border-slate-200 bg-white hover:border-slate-300 shadow-sm flex items-center justify-between gap-3 transition"
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <div className="w-10 h-10 rounded-full bg-emerald-600 text-white font-bold text-sm flex items-center justify-center flex-shrink-0">
                            {friend.display_name.charAt(0).toUpperCase()}
                          </div>
                          <div className="min-w-0">
                            <div className="font-semibold text-xs text-slate-800 truncate">
                              {friend.display_name}
                            </div>
                            <div className="text-[11px] text-slate-400 truncate">
                              @{friend.username}
                            </div>
                            <div className="text-[10px] text-slate-400 truncate mt-0.5">
                              {friend.bio || 'VibeSync Friend'}
                            </div>
                          </div>
                        </div>

                        <button
                          onClick={() => handleRemoveFriend(friend)}
                          disabled={actionLoading}
                          className="px-3 py-1.5 rounded-lg bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 text-xs font-semibold transition cursor-pointer flex-shrink-0"
                          title={`Remove @${friend.username} from friends`}
                        >
                          Delete
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="bg-white rounded-2xl p-12 text-center text-slate-400 border border-slate-200/80 shadow-sm">
              Please select a user from the left column to manage their friends.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
