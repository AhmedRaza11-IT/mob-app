import { NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface GeocodeCacheEntry {
  formattedAddress: string;
  city: string;
  country: string;
}

const geocodeCache = new Map<string, GeocodeCacheEntry>();

async function reverseGeocode(lat: number, lon: number): Promise<GeocodeCacheEntry> {
  const roundedLat = lat.toFixed(4);
  const roundedLon = lon.toFixed(4);
  const cacheKey = `${roundedLat},${roundedLon}`;

  if (geocodeCache.has(cacheKey)) {
    return geocodeCache.get(cacheKey)!;
  }

  try {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=18&addressdetails=1`;
    const res = await fetch(url, {
      headers: {
        'User-Agent': 'VibeSync-AdminDashboard/1.0 (telemetry@vibesync.io)',
        'Accept': 'application/json',
      },
      next: { revalidate: 3600 },
    });

    if (res.ok) {
      const data = await res.json();
      const addr = data.address || {};
      const formattedAddress = data.display_name || `${lat.toFixed(5)}, ${lon.toFixed(5)}`;
      const city =
        addr.city ||
        addr.town ||
        addr.village ||
        addr.suburb ||
        addr.county ||
        'Unknown City';
      const country = addr.country || 'Unknown Country';

      const entry: GeocodeCacheEntry = { formattedAddress, city, country };
      geocodeCache.set(cacheKey, entry);
      return entry;
    }
  } catch (err) {
    console.warn(`Reverse geocode failed for (${lat}, ${lon}):`, err);
  }

  return {
    formattedAddress: `${lat.toFixed(5)}, ${lon.toFixed(5)}`,
    city: 'Unknown City',
    country: 'Unknown Country',
  };
}

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const deviceId = body.deviceId || body.device_id;
    const userId = body.userId || body.user_id;
    const latitude = Number(body.latitude);
    const longitude = Number(body.longitude);
    const accuracy = Number(body.accuracy ?? 0);
    const timestamp = Number(body.timestamp ?? Date.now());

    if (!deviceId || isNaN(latitude) || isNaN(longitude)) {
      return NextResponse.json(
        { error: 'Missing required parameters: deviceId, latitude, longitude' },
        { status: 400 }
      );
    }

    // Try forwarding to main backend API
    try {
      const backendRes = await fetch(`${API_BASE}/api/devices/location`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          deviceId,
          userId,
          latitude,
          longitude,
          accuracy,
          timestamp,
        }),
      });

      if (backendRes.ok) {
        const data = await backendRes.json();
        return NextResponse.json(data);
      }
    } catch {
      // Fallback to local reverse geocode response if backend unreachable
    }

    const { formattedAddress, city, country } = await reverseGeocode(latitude, longitude);

    return NextResponse.json({
      id: `loc_${Date.now()}`,
      deviceId,
      userId: userId || deviceId,
      userName: userId || 'Unknown User',
      latitude,
      longitude,
      accuracy,
      formattedAddress,
      city,
      country,
      updatedAt: new Date(timestamp).toISOString(),
    });
  } catch (error) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Internal Server Error' },
      { status: 500 }
    );
  }
}

export async function GET() {
  try {
    const res = await fetch(`${API_BASE}/api/devices/location`, {
      cache: 'no-store',
    });

    if (res.ok) {
      const data = await res.json();
      return NextResponse.json(data);
    }

    return NextResponse.json([]);
  } catch (err) {
    console.error('Failed querying device locations:', err);
    return NextResponse.json([], { status: 200 });
  }
}
