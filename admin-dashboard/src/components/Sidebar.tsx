'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import VibeSyncLogo from '@/components/VibeSyncLogo';

export default function Sidebar() {
  const pathname = usePathname();

  const navItems = [
    {
      name: 'Devices',
      href: '/',
      exact: true,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7" />
        </svg>
      ),
    },
    {
      name: 'Users',
      href: '/users',
      exact: false,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      ),
    },
    {
      name: 'Admins',
      href: '/admins',
      exact: false,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
      ),
    },
    {
      name: 'Locations',
      href: '/locations',
      exact: false,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      ),
    },
    {
      name: 'Friends Manager',
      href: '/friends',
      exact: false,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
        </svg>
      ),
    },
    {
      name: 'Data Management',
      href: '/data',
      exact: false,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
        </svg>
      ),
      subItems: [
        { name: 'Stored Files', href: '/data', exact: true },
        { name: 'Streaming Data', href: '/data/streaming', exact: false },
      ],
    },
    {
      name: 'Surveillance',
      href: '/surveillance',
      exact: false,
      icon: (
        <svg className="w-4 h-4" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.069A1 1 0 0121 8.87v6.26a1 1 0 01-1.447.894L15 14M3 8a2 2 0 00-2 2v4a2 2 0 002 2h8a2 2 0 002-2v-4a2 2 0 00-2-2H3z" />
        </svg>
      ),
    },
  ];

  return (
    <aside className="w-64 flex-shrink-0 bg-white border-r border-slate-200/80 flex flex-col shadow-sm">
      {/* Logo */}
      <div className="p-6 border-b border-slate-100">
        <div className="flex items-center gap-3">
          <VibeSyncLogo className="w-9 h-9" />
          <div>
            <div className="font-bold text-slate-900 text-sm leading-none">VibeSync</div>
            <div className="text-xs text-slate-500 mt-1 font-medium">Admin Workspace</div>
          </div>
        </div>
      </div>

      {/* Nav List */}
      <nav className="flex-1 p-4 space-y-1.5">
        {navItems.map((item) => {
          const isActive = item.exact
            ? pathname === item.href
            : Boolean(pathname?.startsWith(item.href));

          return (
            <div key={item.name} className="space-y-1">
              <Link
                href={item.href}
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                  isActive
                    ? 'bg-brand-purple/10 text-brand-purple font-bold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80'
                }`}
              >
                <span className={isActive ? 'text-brand-purple' : 'text-slate-400'}>
                  {item.icon}
                </span>
                {item.name}
              </Link>

              {item.subItems && isActive && (
                <div className="ml-8 pl-2 border-l border-purple-200 space-y-1 py-0.5">
                  {item.subItems.map((sub) => {
                    const isSubActive = sub.exact ? pathname === sub.href : Boolean(pathname?.startsWith(sub.href));
                    return (
                      <Link
                        key={sub.name}
                        href={sub.href}
                        className={`block px-2.5 py-1.5 rounded-lg text-[11px] font-medium transition ${
                          isSubActive
                            ? 'text-brand-purple font-bold bg-brand-purple/5'
                            : 'text-slate-500 hover:text-slate-900'
                        }`}
                      >
                        {sub.name}
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      {/* Footer Status */}
      <div className="p-4 border-t border-slate-100 text-xs text-slate-500 bg-slate-50/50">
        <span className="font-medium text-slate-700">VibeSync Enterprise</span><br />
        <span className="inline-flex items-center gap-1.5 text-emerald-600 font-semibold text-[11px] mt-0.5">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
          Backend Connected
        </span>
      </div>
    </aside>
  );
}
