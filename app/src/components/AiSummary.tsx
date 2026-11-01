import { useQuery } from "@tanstack/react-query";
import { fetchAiSummary } from "@/api/githubApi";
import type { AiInsight } from "@/api/githubApi";
import { useState } from "react";
import { Sparkles, Layers, Code2, Target, Star, ArrowUpRight, LoaderCircle } from "lucide-react";

function Evidence({ username, repositories }: { username: string; repositories: string[] }) {
  return <div className="mt-3 flex flex-wrap gap-2">{repositories.map(repo => <a key={repo} href={`https://github.com/${encodeURIComponent(username)}/${encodeURIComponent(repo)}`} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 rounded-md border border-indigo-400/20 bg-indigo-500/5 px-2 py-1 text-xs text-indigo-300 hover:text-white">{repo}<ArrowUpRight className="h-3 w-3" /></a>)}</div>;
}
function Insights({ title, icon: Icon, items, username }: { title: string; icon: typeof Layers; items: AiInsight[]; username: string }) {
  return <section className="rounded-xl border border-slate-800 bg-slate-950/40 p-5"><h3 className="mb-4 flex items-center gap-2 font-semibold text-white"><Icon className="h-4 w-4 text-indigo-400" />{title}</h3>
    {items.length ? <div className="space-y-5">{items.map((item, index) => <div key={`${item.title}-${index}`}><h4 className="text-sm font-medium text-slate-200">{item.title}</h4><p className="mt-1 text-sm leading-6 text-slate-400">{item.detail}</p><Evidence username={username} repositories={item.repositories} /></div>)}</div> : <p className="text-sm text-slate-500">Not enough public evidence to identify a pattern.</p>}
  </section>;
}
export default function AiSummary({ username }: { username: string }) {
  const [requested, setRequested] = useState(false);
  const query = useQuery({ queryKey: ["ai-insights-v2", username.toLowerCase()], queryFn: () => fetchAiSummary(username), enabled: requested, retry: false, staleTime: 10 * 60 * 1000 });
  const report = query.data;
  const unavailable = query.error || report?.degraded;
  return <section className="overflow-hidden rounded-2xl border border-indigo-400/20 bg-slate-900/40">
    <div className="border-b border-slate-800/70 bg-gradient-to-r from-indigo-500/10 to-transparent p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="flex items-center gap-2 text-lg font-semibold text-white"><Sparkles className="h-5 w-5 text-indigo-400" />AI developer profile</h2>{report?.provider && !report.degraded && <span className="rounded-full border border-slate-700 px-3 py-1 text-xs capitalize text-slate-400">Generated with {report.provider}</span>}</div>
      <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-400">What they build, where their public work points, and what to explore next. Every interpretation is based on repository metadata, with links to the evidence.</p>
    </div>
    <div className="p-5 sm:p-6">
      {!requested && <div><div className="mb-5 grid gap-3 sm:grid-cols-3">{["Developer focus", "Project patterns", "Useful next steps"].map(label => <div key={label} className="rounded-xl border border-slate-800 bg-slate-950/40 px-4 py-3 text-sm text-slate-300">{label}</div>)}</div><button className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-indigo-500" onClick={() => setRequested(true)}><Sparkles className="h-4 w-4" />Analyze developer</button><p className="mt-3 text-xs text-slate-500">Sends aggregate metrics and up to 12 original repositories to the configured AI provider. Results may be cached for 24 hours.</p></div>}
      {query.isFetching && <div role="status" className="py-6"><p className="flex items-center gap-2 text-sm text-indigo-300"><LoaderCircle className="h-4 w-4 animate-spin motion-reduce:animate-none" />Connecting the dots across their projects...</p><div aria-hidden="true" className="mt-5 grid gap-4 sm:grid-cols-2">{[0, 1, 2, 3].map(index => <div key={index} className="h-24 animate-pulse rounded-xl bg-slate-800/50 motion-reduce:animate-none" />)}</div></div>}
      {unavailable && !query.isFetching && <div role="status" className="rounded-xl border border-amber-400/20 bg-amber-500/5 p-4"><p className="text-sm leading-6 text-amber-200">{report?.message || query.error?.message || "AI analysis could not be loaded."}</p><button className="mt-3 text-sm font-medium text-indigo-300 hover:text-white" onClick={() => void query.refetch()}>Try again</button></div>}
      {report?.summary && !report.degraded && !query.isFetching && <div className="space-y-5">
        <p className="text-base leading-7 text-slate-200">{report.summary}</p>
        {report.developerType && <section className="rounded-xl border border-indigo-400/20 bg-indigo-500/5 p-5"><p className="mb-2 text-xs uppercase tracking-wider text-indigo-300">Likely developer focus</p><div className="flex flex-wrap items-center gap-3"><h3 className="text-xl font-semibold text-white">{report.developerType.label}</h3><span className="rounded-full bg-slate-800 px-2.5 py-1 text-xs text-slate-300">{report.developerType.confidence} evidence confidence</span></div><p className="mt-3 text-sm leading-6 text-slate-400">{report.developerType.explanation}</p><Evidence username={username} repositories={report.developerType.repositories} /></section>}
        <div className="grid gap-5 lg:grid-cols-2"><Insights title="What they build" icon={Layers} items={report.projectTypes || []} username={username} /><Insights title="Strengths suggested by their work" icon={Code2} items={report.strengths || []} username={username} /></div>
        {!!report.notableProjects?.length && <section className="rounded-xl border border-slate-800 bg-slate-950/40 p-5"><h3 className="mb-4 flex items-center gap-2 font-semibold text-white"><Star className="h-4 w-4 text-amber-400" />Projects that tell their story</h3><div className="grid gap-4 md:grid-cols-3">{report.notableProjects.map(project => <div key={project.repository} className="rounded-lg bg-slate-900/70 p-4"><Evidence username={username} repositories={[project.repository]} /><p className="mt-3 text-sm leading-6 text-slate-400">{project.reason}</p></div>)}</div></section>}
        <Insights title="Worth exploring next" icon={Target} items={report.nextSteps || []} username={username} />
        <details className="rounded-xl border border-slate-800 p-4 text-sm text-slate-400"><summary className="cursor-pointer text-slate-300">How to read this analysis</summary><p className="mt-3 leading-6">Based on {report.sampleSize ?? 12} selected original repositories, prioritizing stars. This is an AI interpretation of a sample, not a skill rating. Source code and private work were not inspected.</p><ul className="mt-3 list-disc space-y-2 pl-5">{report.limitations?.map((item, index) => <li key={index}>{item}</li>)}</ul>{report.generatedAt && <p className="mt-3 text-xs">Generated {new Date(report.generatedAt).toLocaleString()}. Cached for up to 24 hours.</p>}</details>
      </div>}
    </div>
  </section>;
}
