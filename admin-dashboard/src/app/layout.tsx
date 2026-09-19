import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'VibeSync Admin Dashboard',
  description: 'Remote device management and user assignment panel for VibeSync',
};

import Sidebar from '@/components/Sidebar';

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="flex min-h-screen bg-slate-50 text-slate-900">
        <Sidebar />
        <main className="flex-1 overflow-auto">
          {children}
        </main>
      </body>
    </html>
  );
}
