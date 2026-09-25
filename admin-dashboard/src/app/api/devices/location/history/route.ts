import { NextRequest, NextResponse } from 'next/server';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const userId = searchParams.get('userId') || '';
    const deviceId = searchParams.get('deviceId') || '';
    const timeframe = searchParams.get('timeframe') || '1h';

    if (!userId && !deviceId) {
      return NextResponse.json(
        { error: 'Either userId or deviceId parameter is required' },
        { status: 400 }
      );
    }

    const query = new URLSearchParams();
    if (userId) query.set('userId', userId);
    if (deviceId) query.set('deviceId', deviceId);
    query.set('timeframe', timeframe);

    const backendUrl = `${API_BASE}/api/devices/location/history?${query.toString()}`;
    const res = await fetch(backendUrl, {
      cache: 'no-store',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!res.ok) {
      const errText = await res.text();
      return NextResponse.json(
        { error: `Backend returned ${res.status}: ${errText}` },
        { status: res.status }
      );
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (error: unknown) {
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Internal Server Error' },
      { status: 500 }
    );
  }
}
