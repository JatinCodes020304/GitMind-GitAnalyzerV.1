import { useEffect, useState } from "react";
import { Server } from "lucide-react";

/**
 * Rendered only while a request is loading. If it takes longer than a few
 * seconds, the backend is most likely waking up from sleep (free hosting),
 * so tell the visitor instead of leaving them on a silent skeleton.
 */
export default function ColdStartNotice({ delayMs = 6000 }: { delayMs?: number }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setVisible(true), delayMs);
    return () => window.clearTimeout(timer);
  }, [delayMs]);

  if (!visible) return null;

  return (
    <div
      role="status"
      className="mb-6 flex items-start gap-3 rounded-xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-200"
    >
      <Server className="mt-0.5 h-4 w-4 flex-shrink-0" />
      <p>
        The server is waking up after being idle (free hosting). The first request can take up to a
        minute. Later searches will be fast.
      </p>
    </div>
  );
}
