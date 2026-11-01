import { Skeleton } from "@/components/ui/skeleton";
import { useEffect, useState } from "react";

const jokes = [
  "Counting stars. Still no telescope required.",
  "Untangling branches. The squirrels are helping.",
  "Asking the commits who broke the build.",
  "Checking if ‘final-final’ really was final.",
  "Looking for the README. It’s always the last place we look.",
];

export default function DashboardSkeleton() {
  const [beat, setBeat] = useState(0);
  useEffect(() => {
    const timer = window.setInterval(() => setBeat((value) => value + 1), 4000);
    return () => window.clearInterval(timer);
  }, []);
  return (
    <div className="space-y-6" aria-busy="true">
      <section className="relative overflow-hidden rounded-2xl border border-indigo-400/20 bg-slate-900/50 px-6 py-10 text-center">
        <div aria-hidden="true" className="pointer-events-none absolute left-1/2 top-0 h-64 w-64 -translate-x-1/2 rounded-full bg-indigo-500/10 blur-3xl" />
        <div className="relative mx-auto max-w-lg">
          <div aria-hidden="true" className="relative mx-auto mb-6 flex h-24 w-24 items-center justify-center rounded-3xl border border-indigo-400/20 bg-indigo-500/10">
            <svg width="70" height="70" viewBox="0 0 76 76" fill="none">
              <path d="M23 57V19M23 43C23 27 53 45 53 20" stroke="#818cf8" strokeWidth="4" strokeLinecap="round" />
              <circle className="gitmind-node" cx="23" cy="57" r="7" fill="#a5b4fc" />
              <circle className="gitmind-node" style={{ animationDelay: '.3s' }} cx="23" cy="19" r="7" fill="#a5b4fc" />
              <circle className="gitmind-node" style={{ animationDelay: '.6s' }} cx="53" cy="20" r="7" fill="#34d399" />
              <path d="M20 56h.1M26 56h.1" stroke="#0f172a" strokeWidth="3" strokeLinecap="round" />
              <path d="M20 60q3 3 6 0" stroke="#0f172a" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
            <span className="absolute -right-4 -top-3 rotate-12 rounded-lg border border-slate-700 bg-slate-950 px-2 py-1 font-mono text-xs text-emerald-300">git fetch ☕</span>
          </div>
          <p className="mb-3 text-xs uppercase tracking-[0.2em] text-indigo-300">A little Git detective work</p>
          <h2 className="mb-3 text-2xl font-semibold text-white">Good things take a few commits.</h2>
          <p role="status" aria-live="polite" aria-atomic="true" className="min-h-[3rem] text-sm leading-6 text-slate-300">{jokes[beat % jokes.length]}</p>
          <div aria-hidden="true" className="mt-2 flex justify-center gap-2">
            {[0, 1, 2].map((index) => <span key={index} className="gitmind-dot h-1.5 w-1.5 rounded-full bg-indigo-400" style={{ animationDelay: `${index * 180}ms` }} />)}
          </div>
          <p className="mt-5 text-xs leading-5 text-slate-500">{beat >= 4 ? "Still waiting on GitHub. Large profiles can take longer to load." : "Fetching public GitHub data. Profiles with lots of repositories can take a little longer."}</p>
        </div>
      </section>
      <style>{`
        @keyframes gitmind-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .35; } }
        @keyframes gitmind-hop { 0%, 70%, 100% { transform: translateY(0); opacity: .4; } 35% { transform: translateY(-5px); opacity: 1; } }
        .gitmind-node { animation: gitmind-pulse 1.8s ease-in-out infinite; }
        .gitmind-dot { animation: gitmind-hop 1.2s ease-in-out infinite; }
        @media (prefers-reduced-motion: reduce) { .gitmind-node, .gitmind-dot { animation: none; } }
      `}</style>
      {/* Profile Card Skeleton */}
      <div className="rounded-2xl border border-slate-800/60 bg-slate-900/40 p-6">
        <div className="flex flex-col sm:flex-row items-start gap-6">
          <Skeleton className="h-24 w-24 rounded-full bg-slate-800" />
          <div className="flex-1 space-y-3 w-full">
            <Skeleton className="h-7 w-48 bg-slate-800" />
            <Skeleton className="h-4 w-32 bg-slate-800" />
            <Skeleton className="h-4 w-full max-w-md bg-slate-800" />
            <div className="flex gap-4 pt-2">
              <Skeleton className="h-4 w-20 bg-slate-800" />
              <Skeleton className="h-4 w-20 bg-slate-800" />
              <Skeleton className="h-4 w-20 bg-slate-800" />
            </div>
          </div>
        </div>
      </div>

      {/* Stats Grid Skeleton */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className="rounded-xl border border-slate-800/60 bg-slate-900/40 p-4"
          >
            <Skeleton className="h-4 w-20 bg-slate-800 mb-2" />
            <Skeleton className="h-8 w-16 bg-slate-800" />
          </div>
        ))}
      </div>

      {/* Charts and Repos Skeleton */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="rounded-2xl border border-slate-800/60 bg-slate-900/40 p-6">
          <Skeleton className="h-6 w-40 bg-slate-800 mb-4" />
          <Skeleton className="h-64 w-full bg-slate-800 rounded-xl" />
        </div>
        <div className="rounded-2xl border border-slate-800/60 bg-slate-900/40 p-6">
          <Skeleton className="h-6 w-40 bg-slate-800 mb-4" />
          <div className="space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-20 w-full bg-slate-800 rounded-xl" />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
