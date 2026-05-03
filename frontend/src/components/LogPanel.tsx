import { useEffect, useRef } from "react";

export function LogPanel({ lines }: { lines: string[] }) {
  const bottom = useRef<HTMLDivElement>(null);
  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [lines]);

  return (
    <div className="flex min-h-0 flex-1 flex-col rounded-lg border border-slate-200 bg-slate-950 dark:border-slate-700">
      <div className="border-b border-slate-800 px-3 py-2">
        <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
          Live logs
        </span>
      </div>
      <pre className="log-scroll max-h-[320px] flex-1 overflow-auto p-3 font-mono text-xs leading-relaxed text-emerald-400/95">
        {lines.length === 0 ? (
          <span className="text-slate-600">Waiting for orchestration output…</span>
        ) : (
          lines.map((line, i) => (
            <div key={`${i}-${line.slice(0, 24)}`} className="whitespace-pre-wrap break-all">
              {line}
            </div>
          ))
        )}
        <div ref={bottom} />
      </pre>
    </div>
  );
}
