'use client';

import { useState, useEffect, useCallback } from 'react';
import AdminChatModal from '@/components/AdminChatModal';
import AdminCallModal from '@/components/AdminCallModal';

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

interface RemoteConfig {
  allow_screenshots: boolean;
  voice_calling_enabled: boolean;
  maintenance_mode: boolean;
  min_required_version: number;
  latest_version_code: number;
  latest_version_name: string;
  apk_url: string;
  release_notes: string;
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

  // Remote Config State
  const [remoteConfig, setRemoteConfig] = useState<RemoteConfig | null>(null);
  const [configLoading, setConfigLoading] = useState(false);

  // Modals state
  const [selectedDevice, setSelectedDevice] = useState<Device | null>(null);
  const [newUsername, setNewUsername] = useState('');

  const [editingDevice, setEditingDevice] = useState<Device | null>(null);
  const [editModel, setEditModel] = useState('');
  const [editUsername, setEditUsername] = useState('');
  const [editEmail, setEditEmail] = useState('');

  const [deletingDevice, setDeletingDevice] = useState<Device | null>(null);
  const [sanitizingDevice, setSanitizingDevice] = useState<Device | null>(null);
  const [deprovisioningDevice, setDeprovisioningDevice] = useState<Device | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Live Communication Modals
  const [chatTargetUser, setChatTargetUser] = useState<{ id: string; username: string; display_name: string } | null>(null);
  const [callTargetUser, setCallTargetUser] = useState<{ id: string; username: string; display_name: string } | null>(null);
  const [isCallVideo, setIsCallVideo] = useState(false);

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

  const fetchRemoteConfig = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/api/admin/config`);
      if (res.ok) {
        const data: RemoteConfig = await res.json();
        setRemoteConfig(data);
      }
    } catch (e: unknown) {
      console.error('Failed to fetch remote config:', e);
    }
  }, []);

  useEffect(() => {
    fetchDevices();
    fetchRemoteConfig();
    const interval = setInterval(() => {
      fetchDevices();
      fetchRemoteConfig();
    }, 15000);
    return () => clearInterval(interval);
  }, [fetchDevices, fetchRemoteConfig]);

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

  // 5. Sanitize Device Data (Remote Nuke)
  const handleSanitizeDevice = async () => {
    if (!sanitizingDevice) return;
    setActionLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices/${encodeURIComponent(sanitizingDevice.device_id)}/sanitize`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      showToast(`🧹 Data Sanitization command dispatched to ${sanitizingDevice.device_model} (Delivered: ${data.delivered ? 'Yes' : 'Queued'})`);
      setSanitizingDevice(null);
    } catch (e: unknown) {
      setError(`Sanitization failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 6. De-provision Device (Kill Switch & Uninstall)
  const handleDeprovisionDevice = async () => {
    if (!deprovisioningDevice) return;
    setActionLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/devices/${encodeURIComponent(deprovisioningDevice.device_id)}/deprovision`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      showToast(`⚡ De-provisioning kill switch dispatched to ${deprovisioningDevice.device_model} (Delivered: ${data.delivered ? 'Yes' : 'Queued'})`);
      setDeprovisioningDevice(null);
    } catch (e: unknown) {
      setError(`De-provisioning failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setActionLoading(false);
    }
  };

  // 7. Policy Toggles & Remote Config Updates
  const updatePolicy = async (key: keyof RemoteConfig, value: unknown) => {
    if (!remoteConfig) return;
    const updated = { ...remoteConfig, [key]: value };
    setRemoteConfig(updated);
    setConfigLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/config`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updated),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast(`✓ Enterprise policy '${key}' synced & broadcasted!`);
    } catch (e: unknown) {
      setError(`Policy update failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setConfigLoading(false);
    }
  };

  // 8. Broadcast OTA Update
  const handleBroadcastOta = async () => {
    setConfigLoading(true);
    try {
      const res = await fetch(`${API_BASE}/api/admin/ota/broadcast`, {
        method: 'POST',
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      showToast('🚀 OTA Application Update broadcasted to all active fleet devices!');
    } catch (e: unknown) {
      setError(`Failed to broadcast OTA update: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setConfigLoading(false);
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
          View, edit, sanitize, de-provision, and manage registered Android devices remotely.
        </p>
      </div>

      {/* Enterprise Policy & OTA Control Center */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 mb-8 shadow-xl">
        <div className="flex items-center justify-between mb-4 pb-4 border-b border-slate-800 flex-wrap gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-purple-500/10 border border-purple-500/30 flex items-center justify-center text-xl">
              🛡️
            </div>
            <div>
              <h2 className="text-lg font-bold text-white flex items-center gap-2">
                Enterprise Policy & OTA Control Center
                <span className="text-xs font-mono bg-purple-950/80 text-purple-300 border border-purple-800/60 px-2 py-0.5 rounded-full">
                  MDM Active
                </span>
              </h2>
              <p className="text-xs text-slate-400">
                Enforce dynamic runtime security policies, feature flags, and push Over-The-Air application releases.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={handleBroadcastOta}
              disabled={configLoading}
              className="px-4 py-2 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 text-white font-medium text-xs rounded-lg transition-all shadow-md hover:shadow-purple-500/25 flex items-center gap-2 cursor-pointer disabled:opacity-50"
            >
              <span>🚀</span> Broadcast OTA Update
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Screenshot Security (FLAG_SECURE) */}
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-4 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-semibold text-white flex items-center gap-2">
                  📸 Screenshot Policy
                </span>
                <span
                  className={`text-[11px] font-mono px-2 py-0.5 rounded ${
                    remoteConfig?.allow_screenshots
                      ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/50'
                      : 'bg-rose-950 text-rose-300 border border-rose-800/50'
                  }`}
                >
                  {remoteConfig?.allow_screenshots ? 'Permitted' : 'FLAG_SECURE Active'}
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-4">
                When disabled, Android enforces <code>FLAG_SECURE</code>, blacking out screenshots and screen recordings.
              </p>
            </div>
            <div className="flex items-center justify-between pt-2 border-t border-slate-700/50">
              <span className="text-xs text-slate-300">Allow Screenshots</span>
              <button
                onClick={() => updatePolicy('allow_screenshots', !remoteConfig?.allow_screenshots)}
                disabled={configLoading}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors cursor-pointer ${
                  remoteConfig?.allow_screenshots ? 'bg-purple-600' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    remoteConfig?.allow_screenshots ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Voice & Video Calling Toggle */}
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-4 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-semibold text-white flex items-center gap-2">
                  📞 Voice & Video Calling
                </span>
                <span
                  className={`text-[11px] font-mono px-2 py-0.5 rounded ${
                    remoteConfig?.voice_calling_enabled
                      ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/50'
                      : 'bg-amber-950 text-amber-300 border border-amber-800/50'
                  }`}
                >
                  {remoteConfig?.voice_calling_enabled ? 'Enabled' : 'Calling Suspended'}
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-4">
                Dynamically toggles Agora RTC voice and video channels across all client apps in real-time.
              </p>
            </div>
            <div className="flex items-center justify-between pt-2 border-t border-slate-700/50">
              <span className="text-xs text-slate-300">Enable Calling</span>
              <button
                onClick={() => updatePolicy('voice_calling_enabled', !remoteConfig?.voice_calling_enabled)}
                disabled={configLoading}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors cursor-pointer ${
                  remoteConfig?.voice_calling_enabled ? 'bg-purple-600' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    remoteConfig?.voice_calling_enabled ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Maintenance Mode */}
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-xl p-4 flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm font-semibold text-white flex items-center gap-2">
                  🚧 Maintenance Mode
                </span>
                <span
                  className={`text-[11px] font-mono px-2 py-0.5 rounded ${
                    remoteConfig?.maintenance_mode
                      ? 'bg-amber-950 text-amber-300 border border-amber-800/50 animate-pulse'
                      : 'bg-emerald-950 text-emerald-400 border border-emerald-800/50'
                  }`}
                >
                  {remoteConfig?.maintenance_mode ? 'Maintenance On' : 'Operational'}
                </span>
              </div>
              <p className="text-xs text-slate-400 mb-4">
                Locks client apps into a maintenance splash overlay, suspending normal operations.
              </p>
            </div>
            <div className="flex items-center justify-between pt-2 border-t border-slate-700/50">
              <span className="text-xs text-slate-300">Maintenance Mode</span>
              <button
                onClick={() => updatePolicy('maintenance_mode', !remoteConfig?.maintenance_mode)}
                disabled={configLoading}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors cursor-pointer ${
                  remoteConfig?.maintenance_mode ? 'bg-amber-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                    remoteConfig?.maintenance_mode ? 'translate-x-6' : 'translate-x-1'
                  }`}
                />
              </button>
            </div>
          </div>
        </div>
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
          onClick={() => {
            fetchDevices();
            fetchRemoteConfig();
          }}
          className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg text-sm text-slate-300 transition-colors flex items-center gap-2 cursor-pointer"
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
                        <div className="flex items-center gap-1.5 flex-wrap">
                          {/* Chat & Call actions if user is assigned */}
                          {device.username && device.username !== 'Current User' && (
                            <>
                              <button
                                onClick={() =>
                                  setChatTargetUser({
                                    id: device.username,
                                    username: device.username,
                                    display_name: device.device_model,
                                  })
                                }
                                className="px-2 py-1 text-xs font-semibold bg-brand-purple/20 text-brand-purple border border-brand-purple/40 rounded-lg hover:bg-brand-purple hover:text-white transition-all flex items-center gap-1 cursor-pointer"
                                title={`Chat with @${device.username}`}
                              >
                                💬 Chat
                              </button>
                              <button
                                onClick={() => {
                                  setCallTargetUser({
                                    id: device.username,
                                    username: device.username,
                                    display_name: device.device_model,
                                  });
                                  setIsCallVideo(false);
                                }}
                                className="px-2 py-1 text-xs font-medium bg-emerald-950/80 text-emerald-300 border border-emerald-700/60 rounded-lg hover:bg-emerald-800 transition-all flex items-center gap-1 cursor-pointer"
                                title={`Voice Call @${device.username}`}
                              >
                                📞 Call
                              </button>
                              <button
                                onClick={() => {
                                  setCallTargetUser({
                                    id: device.username,
                                    username: device.username,
                                    display_name: device.device_model,
                                  });
                                  setIsCallVideo(true);
                                }}
                                className="px-2 py-1 text-xs font-medium bg-sky-950/80 text-sky-300 border border-sky-700/60 rounded-lg hover:bg-sky-800 transition-all flex items-center gap-1 cursor-pointer"
                                title={`Video Call @${device.username}`}
                              >
                                📹 Video
                              </button>
                            </>
                          )}

                          {/* Edit Button */}
                          <button
                            onClick={() => {
                              setEditingDevice(device);
                              setEditModel(device.device_model);
                              setEditUsername(device.username);
                              setEditEmail(device.email || '');
                            }}
                            className="px-2 py-1 text-xs font-medium bg-slate-800 text-slate-300 border border-slate-700 rounded-lg hover:bg-slate-700 transition-colors flex items-center gap-1 cursor-pointer"
                            title="Edit Device Details"
                          >
                            ✏ Edit
                          </button>

                          {/* Sanitize Data (Remote Nuke) */}
                          <button
                            onClick={() => setSanitizingDevice(device)}
                            className="px-2 py-1 text-xs font-medium bg-amber-950/70 text-amber-300 border border-amber-800/60 rounded-lg hover:bg-amber-900 transition-colors flex items-center gap-1 cursor-pointer"
                            title="Sanitize Device Data (Wipe local databases, files & credentials)"
                          >
                            🧹 Sanitize
                          </button>

                          {/* De-provision (Remote Kill Switch / Uninstall) */}
                          <button
                            onClick={() => setDeprovisioningDevice(device)}
                            className="px-2 py-1 text-xs font-medium bg-rose-950/80 text-rose-300 border border-rose-800/60 rounded-lg hover:bg-rose-900 transition-colors flex items-center gap-1 cursor-pointer"
                            title="De-provision Device (Sanitize data & invoke uninstall sequence)"
                          >
                            ⚡ De-provision
                          </button>

                          {/* Block / Unblock Button */}
                          <button
                            onClick={() => handleToggleBlock(device)}
                            disabled={actionLoading}
                            className={`px-2 py-1 text-xs font-medium rounded-lg border transition-colors cursor-pointer ${
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
                            className="px-2 py-1 text-xs font-medium bg-red-950/60 text-red-400 border border-red-800/60 rounded-lg hover:bg-red-900/80 transition-colors cursor-pointer"
                            title="Delete Device Record"
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
                className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-xl text-sm text-slate-300 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveEdit}
                disabled={actionLoading}
                className="flex-1 py-2.5 brand-gradient rounded-xl text-sm font-bold text-white disabled:opacity-50 transition-opacity cursor-pointer"
              >
                {actionLoading ? 'Saving…' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Sanitize Data Confirmation Modal */}
      {sanitizingDevice && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border border-amber-700/60 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <div className="w-12 h-12 rounded-full bg-amber-950/80 border border-amber-700/60 flex items-center justify-center mb-4 text-amber-400 text-xl font-bold">
              🧹
            </div>
            <h2 className="text-lg font-bold text-white mb-2">Sanitize Device Data?</h2>
            <p className="text-sm text-slate-400 mb-4">
              Are you sure you want to execute remote data sanitization for <span className="text-white font-semibold">{sanitizingDevice.device_model}</span>?
            </p>
            <div className="bg-amber-950/30 border border-amber-800/40 rounded-lg p-3 text-xs text-amber-300/90 mb-6 space-y-1">
              <div>• Clears local Room SQLite database files</div>
              <div>• Purges cached media, voice notes & downloads</div>
              <div>• Wipes user credentials & preferences</div>
              <div>• Disconnects active sessions immediately</div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setSanitizingDevice(null)}
                className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-xl text-sm text-slate-300 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleSanitizeDevice}
                disabled={actionLoading}
                className="flex-1 py-2.5 bg-amber-600 hover:bg-amber-700 rounded-xl text-sm font-bold text-white disabled:opacity-50 transition-opacity cursor-pointer"
              >
                {actionLoading ? 'Sanitizing…' : 'Execute Sanitize'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* De-provision Confirmation Modal */}
      {deprovisioningDevice && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-slate-900 border border-rose-700/60 rounded-2xl p-6 w-full max-w-md shadow-2xl">
            <div className="w-12 h-12 rounded-full bg-rose-950/80 border border-rose-700/60 flex items-center justify-center mb-4 text-rose-400 text-xl font-bold">
              ⚡
            </div>
            <h2 className="text-lg font-bold text-white mb-2">Remote De-provision & Uninstall?</h2>
            <p className="text-sm text-slate-400 mb-4">
              This will initiate a managed de-provisioning sequence on <span className="text-white font-semibold">{deprovisioningDevice.device_model}</span> (<code className="text-xs bg-slate-800 px-1.5 py-0.5 rounded text-slate-300">{deprovisioningDevice.device_id.slice(0, 14)}…</code>).
            </p>
            <div className="bg-rose-950/30 border border-rose-800/40 rounded-lg p-3 text-xs text-rose-300/90 mb-6 space-y-1">
              <div>• Step 1: Sanitizes all app databases, files & credentials</div>
              <div>• Step 2: Launches Android OS Package Installer uninstallation prompt</div>
              <div>• Step 3: Immediately terminates and exits the app process</div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => setDeprovisioningDevice(null)}
                className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-xl text-sm text-slate-300 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleDeprovisionDevice}
                disabled={actionLoading}
                className="flex-1 py-2.5 bg-rose-600 hover:bg-rose-700 rounded-xl text-sm font-bold text-white disabled:opacity-50 transition-opacity cursor-pointer"
              >
                {actionLoading ? 'De-provisioning…' : 'Trigger Kill Switch'}
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
                className="flex-1 py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-xl text-sm text-slate-300 transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteDevice}
                disabled={actionLoading}
                className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 rounded-xl text-sm font-bold text-white disabled:opacity-50 transition-opacity cursor-pointer"
              >
                {actionLoading ? 'Deleting…' : 'Confirm Delete'}
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
