import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

/**
 * Demo loan form — intentional bugs: no visible client validation on fully empty submit;
 * invalid non-empty values use nearly invisible error banner.
 */
export function LoanForm() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [loanAmount, setLoanAmount] = useState("");
  const [clientError, setClientError] = useState("");
  const [serverMsg, setServerMsg] = useState("");

  function validateNonEmpty(): boolean {
    if (!name.trim() || !email.trim() || !loanAmount.trim()) {
      setClientError("Please fill in all fields (name, email, loan amount).");
      return false;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setClientError("Enter a valid email address.");
      return false;
    }
    if (Number.isNaN(Number(loanAmount)) || Number(loanAmount) <= 0) {
      setClientError("Loan amount must be a positive number.");
      return false;
    }
    setClientError("");
    return true;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setServerMsg("");
    const allEmpty = !name.trim() && !email.trim() && !loanAmount.trim();
    let clientOk = true;
    if (allEmpty) {
      setClientError("");
      clientOk = false;
    } else {
      clientOk = validateNonEmpty();
    }

    const payload = {
      name: name.trim(),
      email: email.trim(),
      loanAmount: loanAmount.trim(),
    };

    const res = await fetch("/api/loan/apply", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = (await res.json()) as { success?: boolean; message?: string; id?: string };

    if (data.success) {
      setServerMsg(data.message || "Submitted.");
      if (clientOk) {
        setTimeout(() => navigate("/loan-list"), 400);
      }
    } else {
      setServerMsg("Something went wrong.");
    }
  }

  return (
    <div className="mx-auto max-w-lg rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h1 className="text-lg font-semibold text-slate-900 dark:text-white">Loan application</h1>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        Complete the form below to submit your request. Decisions are typically returned within a few business days.
      </p>

      <form onSubmit={onSubmit} className="mt-5 space-y-4">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">Full name</span>
          <input
            name="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 dark:border-slate-600 dark:bg-slate-950 dark:text-white"
            autoComplete="name"
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">Email</span>
          <input
            name="email"
            type="text"
            inputMode="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 dark:border-slate-600 dark:bg-slate-950 dark:text-white"
            autoComplete="email"
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">Loan amount (USD)</span>
          <input
            name="loanAmount"
            inputMode="decimal"
            value={loanAmount}
            onChange={(e) => setLoanAmount(e.target.value)}
            placeholder="e.g. 25000"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 dark:border-slate-600 dark:bg-slate-950 dark:text-white"
          />
        </label>

        {clientError ? <div className="error-banner--broken">{clientError}</div> : null}

        {serverMsg ? (
          <p className="font-semibold text-emerald-700 dark:text-emerald-400" data-testid="submit-toast">
            {serverMsg}
          </p>
        ) : null}

        <div className="submit-row--broken pt-2">
          <button
            type="submit"
            data-testid="submit-loan"
            className="rounded-lg bg-blue-600 px-7 py-3 text-sm font-semibold text-white hover:bg-blue-700"
          >
            Submit application
          </button>
        </div>
      </form>

      <p className="mt-6 text-sm">
        <Link to="/loan-list" className="text-emerald-700 hover:underline dark:text-emerald-400">
          View submitted applications →
        </Link>
      </p>
    </div>
  );
}
