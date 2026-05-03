import { Loader2, Send } from "lucide-react";
import { useEffect, useRef, useState } from "react";

type Role = "user" | "assistant";

interface ChatMessage {
  id: string;
  role: Role;
  content: string;
  viaGroq?: boolean;
}

async function askAgent(question: string): Promise<{ answer: string; groq: boolean }> {
  const r = await fetch("/api/ai/ask", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ question }),
  });
  if (!r.ok) {
    const t = await r.text();
    throw new Error(t || `Request failed (${r.status})`);
  }
  return r.json() as Promise<{ answer: string; groq: boolean }>;
}

function renderRichText(content: string) {
  return content.split("\n").map((line, i) => {
    const parts = line.split(/(\*\*[^*]+\*\*)/g);
    return (
      <p key={i} className={i > 0 ? "mt-2" : ""}>
        {parts.map((p, j) =>
          p.startsWith("**") && p.endsWith("**") ? (
            <strong key={j}>{p.slice(2, -2)}</strong>
          ) : (
            <span key={j}>{p}</span>
          ),
        )}
      </p>
    );
  });
}

export function AiAgentPage() {
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      content:
        "Ask about **Jira issues**, **test runs**, **bugs filed**, or **how Sentinel behaves**. Answers use live server data (recent runs in this JVM + Jira mock/API) and **Groq** when configured.",
    },
  ]);
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  async function send() {
    const text = input.trim();
    if (!text || loading) return;
    setInput("");
    const uid = `u-${Date.now()}`;
    setMessages((m) => [...m, { id: uid, role: "user", content: text }]);
    setLoading(true);
    try {
      const { answer, groq } = await askAgent(text);
      setMessages((m) => [
        ...m,
        {
          id: `a-${Date.now()}`,
          role: "assistant",
          content: answer,
          viaGroq: groq,
        },
      ]);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setMessages((m) => [
        ...m,
        {
          id: `e-${Date.now()}`,
          role: "assistant",
          content: `**Could not reach the agent.** ${msg}\n\nEnsure the API is running (e.g. port 9096) and try again.`,
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto flex h-[calc(100vh-8rem)] max-w-3xl flex-col">
      <div>
        <h1 className="text-xl font-semibold text-slate-900 dark:text-white">AI agent</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Groq-backed Q&amp;A over recent Jira context and QA run history on the server.
        </p>
      </div>

      <div className="mt-4 flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-slate-200 bg-white shadow-card dark:border-slate-800 dark:bg-slate-900/80 dark:shadow-card-dark">
        <div className="log-scroll flex-1 space-y-4 overflow-y-auto p-4">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
            >
              <div
                className={`max-w-[88%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm ${
                  msg.role === "user"
                    ? "rounded-br-md bg-blue-600 text-white dark:bg-blue-600"
                    : "rounded-bl-md border border-slate-200/90 bg-slate-50 text-slate-800 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
                }`}
              >
                {msg.role === "assistant" && msg.viaGroq != null ? (
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <span className="rounded-md bg-slate-200/80 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                      {msg.viaGroq ? "Groq" : "Offline"}
                    </span>
                  </div>
                ) : null}
                {renderRichText(msg.content)}
              </div>
            </div>
          ))}
          {loading ? (
            <div className="flex justify-start">
              <div className="flex items-center gap-2 rounded-2xl rounded-bl-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-400">
                <Loader2 className="h-4 w-4 shrink-0 animate-spin text-blue-600 dark:text-blue-400" />
                Thinking…
              </div>
            </div>
          ) : null}
          <div ref={bottom} />
        </div>
        <div className="border-t border-slate-200 p-3 dark:border-slate-800">
          <div className="flex gap-2">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  void send();
                }
              }}
              rows={2}
              disabled={loading}
              placeholder="Why did the last test fail?"
              className="min-h-[44px] flex-1 resize-none rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-inner outline-none ring-blue-500/30 focus:ring-2 disabled:opacity-60 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            />
            <button
              type="button"
              disabled={loading}
              onClick={() => void send()}
              className="self-end rounded-xl bg-blue-600 p-3 text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
              aria-label="Send"
            >
              {loading ? (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
              ) : (
                <Send className="h-4 w-4" />
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
