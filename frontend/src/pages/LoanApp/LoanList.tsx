import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

type Row = { id: string; name: string; email: string; loanAmount: string };

export function LoanList() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await fetch("/api/loan/list");
        if (!res.ok) throw new Error(String(res.status));
        const data = (await res.json()) as { applications?: Row[] };
        if (!cancelled) setRows(data.applications ?? []);
      } catch {
        if (!cancelled) setError("Could not load applications.");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="mx-auto max-w-3xl rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h1 className="text-lg font-semibold text-slate-900 dark:text-white">Submitted applications</h1>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Listed below are applications received from this portal. For questions about an entry, use the reference
        details on your confirmation.
      </p>
      <p className="mt-3 text-sm">
        <Link to="/loan-form" className="text-emerald-700 hover:underline dark:text-emerald-400">
          ← New application
        </Link>
      </p>

      {error ? <p className="mt-3 text-sm text-red-700">{error}</p> : null}

      {rows.length === 0 && !error ? <p className="mt-4 text-slate-600">No applications yet.</p> : null}

      {rows.length > 0 ? (
        <table
          data-testid="loan-table"
          className="mt-4 w-full border-collapse overflow-hidden rounded-lg border border-slate-200 text-left dark:border-slate-700"
        >
          <thead>
            <tr className="bg-slate-100 dark:bg-slate-800">
              <th className="border-b border-slate-200 px-3 py-2 text-sm font-medium dark:border-slate-600">Name</th>
              <th className="border-b border-slate-200 px-3 py-2 text-sm font-medium dark:border-slate-600">Email</th>
              <th className="border-b border-slate-200 px-3 py-2 text-sm font-medium dark:border-slate-600">
                Loan amount
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} data-testid="loan-row" className="border-b border-slate-100 dark:border-slate-800">
                <td className="px-3 py-2 text-sm" data-testid="cell-name">
                  {r.name}
                </td>
                <td className="px-3 py-2 text-sm" data-testid="cell-email">
                  {r.email}
                </td>
                <td className="px-3 py-2 text-sm" data-testid="cell-amount">
                  {r.loanAmount}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </div>
  );
}
