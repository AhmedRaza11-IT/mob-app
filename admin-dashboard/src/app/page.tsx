'use client';

import { useState, useEffect, useCallback } from 'react';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface Device {
  device_id: string;
  device_model: string;
  username: string;
  email: string | null;
  last_sync_timestamp: number;
  is_blocked?: boolean;
  total_files?: number;
}

function timeAgo(timestamp: number): string {
  const now = Date.now();
  const diffSec = Math.floor((now - timestamp) / 1000);
  if (diffSec < 60) return `${diffSec}s ago`;
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  return `${Math.floor(diffSec / 86400)}d ago`;
}

export default function DevicesPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  // Modals state
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [newUsername, setNewUsername] = useState('');

  const [editingDevice, setEditingDevice] = useState<Device | null>(null);
  const [editModel, setEditModel] = useState('');
  const [editUsername, setEditUsername] = useState('');
  const [editEmail, setEditEmail] = useState('');

  const [deletingDevice, setDeletingDevice] = useState<Device | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchDevices = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data: Device[] = await res.json();
      setDevices(data);
      setError(null);
    } catch (e: unknown) {
      setError(`Failed to connect to backend: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDevices();
    const interval = setInterval(fetchDevices, 15000);
    return () => clearInterval(interval);
  }, [fetchDevices]);

  const showToast = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(null), 4000);
  };

  // 1. Assign Username
  const assignUsername = async () => {
    if (!selectedDevice || !newUsername.trim()) return;
    setActionLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/assign-username`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          target_device_id: selectedDevice.device_id,
          assigned_username: newUsername.trim(),
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast(`✓ Assigned @${newUsername.trim()} to ${selectedDevice.device_model}`);
      setSelectedDevice(null);
      setNewUsername('');
      await fetchDevices();
    } catch (e: unknown) {
      setError(`Assignment failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 2. Edit Device
  const handleSaveEdit = async () => {
    if (!editingDevice) return;
    setActionLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices/${encodeURIComponent(editingDevice.device_id)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_model: editModel,
          username: editUsername,
          email: editEmail,
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast(`✓ Updated device details for ${editModel}`);
      setEditingDevice(null);
      await fetchDevices();
    } catch (e: unknown) {
      setError(`Failed to update device: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 3. Block / Unblock Device
  const handleToggleBlock = async (device: Device) => {
    const nextBlockState = !device.is_blocked;
    setActionLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices/${encodeURIComponent(device.device_id)}/block`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ is_blocked: nextBlockState }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast(`✓ Device ${device.device_model} ${nextBlockState ? 'blocked' : 'unblocked'}`);
      await fetchDevices();
    } catch (e: unknown) {
      setError(`Failed to update block state: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 4. Delete Device
  const handleDeleteDevice = async () => {
    if (!deletingDevice) return;
    setActionLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices/${encodeURIComponent(deletingDevice.device_id)}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast(`✓ Device ${deletingDevice.device_model} deleted successfully`);
      setDeletingDevice(null);
      await fetchDevices();
    } catch (e: unknown) {
      setError(`Failed to delete device: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setActionLoading(false);
    }
  };

  const filtered = devices.filter(
    (d) =>
      d.device_id.toLowerCase().includes(search.toLowerCase()) ||
      d.device_model.toLowerCase().includes(search.toLowerCase()) ||
      d.username.toLowerCase().includes(search.toLowerCase()) ||
      (d.email ?? '').toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="p-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-white">Device Management</h1>
        <p className="text-slate-500 mt-1 text-sm">
          View, edit, block, and manage registered Android devices remotely.
        </p>
      </div>

      {/* Stats Row */}
      <div className="grid grid-cols-4 gap-4 mb-8">
        {[
          { label: 'Total Devices', value: devices.length, color: 'text-brand-purple' },
          {
            label: 'Active (≤1h)',
            value: devices.filter((d) => Date.now() - d.last_sync_timestamp < 3600000 && !d.is_blocked).length,
            color: 'text-brand-teal',
          },
          {
            label: 'Unassigned',
            value: devices.filter((d) => d.username === 'Current User').length,
            color: 'text-amber-400',
          },
          {
            label: 'Blocked Devices',
            value: devices.filter((d) => d.is_blocked).length,
            color: 'text-red-400',
          },
        ].map((stat) => (
          <div key={stat.label} className="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <div className={`text-3xl font-bold ${stat.color}`}>{stat.value}</div>
            <div className="text-sm text-slate-500 mt-1">{stat.label}</div>
          </div>
        ))}
      </div>

      {/* Success / Error toasts */}
      {successMsg && (
        <div className="mb-4 p-3 bg-emerald-900/50 border border-emerald-700 text-emerald-300 rounded-lg text-sm flex items-center gap-2">
          <span>{successMsg}</span>
        </div>
      )}
      {error && (
        <div className="mb-4 p-3 bg-red-900/50 border border-red-700 text-red-300 rounded-lg text-sm">
          {error}
        </div>
      )}

      {/* Search + Refresh Row */}
      <div className="flex items-center gap-3 mb-4">
        <div className="relative flex-1">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by ANDROID_ID, model, username or email…"
            className="w-full pl-9 pr-4 py-2.5 bg-slate-900 border border-slate-700 rounded-lg text-sm text-white placeholder-slate-500 focus:outline-none focus:border-brand-purple transition-colors"
          />
        </div>
        <button
          onClick={fetchDevices}
          className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg text-sm text-slate-300 transition-colors flex items-center gap-2"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Refresh
        </button>
      </div>

      {/* Table */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-800/50">
                <th className="text-left px-4 py-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">ANDROID_ID</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Device Model</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Username</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Status</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Last Sync</th>
                <th className="text-left px-4 py-3 text-xs font-semibold text-slate-400 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {loading ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-slate-500">
                    <div className="flex items-center justify-center gap-2">
                      <svg className="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Loading devices…
                    </div>
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-12 text-center text-slate-500">
                    No devices found. Launch the VibeSync app on a device to register it.
                  </td>
                </tr>
              ) : (
                filtered.map((device) => {
                  const isOnline = Date.now() - device.last_sync_timestamp < 3600000;
                  const isUnassigned = device.username === 'Current User';
                  return (
                    <tr key={device.device_id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="px-4 py-3">
                        <code className="text-xs text-slate-400 font-mono bg-slate-800 px-2 py-0.5 rounded">
                          {device.device_id.slice(0, 16)}…
                        </code>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className={`w-1.5 h-1.5 rounded-full ${device.is_blocked ? 'bg-red-500' : isOnline ? 'bg-emerald-400' : 'bg-slate-600'}`} />
                          <span className="text-white font-medium">{device.device_model}</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`font-mono text-sm ${
                            isUnassigned ? 'text-amber-400 italic' : 'text-brand-teal font-semibold'
                          }`}
                        >
                          {isUnassigned ? '⚠ Unassigned' : `@${device.username}`}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        {device.is_blocked ? (
                          <span className="px-2 py-0.5 text-xs font-semibold bg-red-950/80 text-red-400 border border-red-800/60 rounded-md">
                            🚫 Blocked
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 text-xs font-semibold bg-emerald-950/80 text-emerald-400 border border-emerald-800/60 rounded-md">
                            ✓ Active
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-slate-500 text-xs">{timeAgo(device.last_sync_timestamp)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {/* Edit Button */}
                          <button
                            onClick={() => {
                              setEditingDevice(device);
                              setEditModel(device.device_model);
                              setEditUsername(device.username);
                              setEditEmail(device.email || '');
                            }}
                            className="px-2.5 py-1 text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 rounded-lg hover:bg-slate-700 transition-colors flex items-center gap-1"
                            title="Edit Device Details"
                          >
                            ✏ Edit
                          </button>

                          {/* Block / Unblock Button */}
                          <button
                            onClick={() => handleToggleBlock(device)}
                            disabled={actionLoading}
                            className={`px-2.5 py-1 text-xs font-medium rounded-lg border transition-colors ${
                              device.is_blocked
                                ? 'bg-emerald-950/60 text-emerald-400 border-emerald-700 hover:bg-emerald-900/80'
                                : 'bg-amber-950/60 text-amber-400 border-amber-700 hover:bg-amber-900/80'
                            }`}
                            title={device.is_blocked ? 'Unblock Device' : 'Block Device'}
                          >
                            {device.is_blocked ? '🔓 Unblock' : '🚫 Block'}
                          </button>

                          {/* Delete Button */}
                          <button
                            onClick={() => setDeletingDevice(device)}
                            className="px-2.5 py-1 text-xs font-medium bg-red-950/60 text-red-400 border border-red-800/60 rounded-lg hover:bg-red-900/80 transition-colors"
                            title="Delete Device"
                          >
                            🗑 Delete
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

      {/* Edit Device Modal */}
      {editingDevice && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <h2 className="text-lg font-bold text-white mb-1">Edit Device</h2>
            <p className="text-sm text-slate-400 mb-5">
              Update parameters for <span className="text-white font-medium">{editingDevice.device_id.slice(0, 16)}…</span>
            </p>

            <div className="space-y-4 mb-6">
              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-1 uppercase tracking-wider">
                  Device Model
                </label>
                <input
                  type="text"
                  value={editModel}
                  onChange={(e) => setEditModel(e.target.value)}
                  className="w-full px-3 py-2.5 bg-slate-800 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:border-brand-purple"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-1 uppercase tracking-wider">
                  Assigned Username
                </label>
                <input
                  type="text"
                  value={editUsername}
                  onChange={(e) => setEditUsername(e.target.value)}
                  className="w-full px-3 py-2.5 bg-slate-800 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:border-brand-purple font-mono"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 mb-1 uppercase tracking-wider">
                  Email Address
                </label>
                <input
                  type="email"
                  value={editEmail}
                  onChange={(e) => setEditEmail(e.target.value)}
                  className="w-full px-3 py-2.5 bg-slate-800 border border-slate-600 rounded-lg text-white text-sm focus:outline-none focus:border-brand-purple"
                />
              </div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setEditingDevice(null)}
                className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-xl text-sm text-slate-300 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveEdit}
                disabled={actionLoading}
                className="flex-1 py-2.5 brand-gradient rounded-xl text-sm font-bold text-white disabled:opacity-50 transition-opacity"
              >
                {actionLoading ? 'Saving…' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deletingDevice && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <div className="w-12 h-12 rounded-full bg-red-950/80 border border-red-700/60 flex items-center justify-center mb-4 text-red-400 text-xl font-bold">
              ⚠
            </div>
            <h2 className="text-lg font-bold text-white mb-2">Delete Device?</h2>
            <p className="text-sm text-slate-400 mb-6">
              Are you sure you want to delete <span className="text-white font-semibold">{deletingDevice.device_model}</span> (<code className="text-xs bg-slate-800 px-1.5 py-0.5 rounded text-slate-300">{deletingDevice.device_id.slice(0, 14)}…</code>)? This action cannot be undone.
            </p>

            <div className="flex gap-3">
              <button
                onClick={() => setDeletingDevice(null)}
                className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-xl text-sm text-slate-300 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteDevice}
                disabled={actionLoading}
                className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 rounded-xl text-sm font-bold text-white disabled:opacity-50 transition-opacity"
              >
                {actionLoading ? 'Deleting…' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
