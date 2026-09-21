import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-slate-50 p-6 text-center">
      <h2 className="text-2xl font-bold text-slate-900 mb-2">Page Not Found</h2>
      <p className="text-sm text-slate-500 mb-6">The requested resource could not be located.</p>
      <Link
        href="/"
        className="px-4 py-2 bg-brand-purple text-white rounded-xl text-xs font-semibold shadow-sm hover:bg-brand-purple/90 transition-all"
      >
        Return to Dashboard
      </Link>
    </div>
  );
}
