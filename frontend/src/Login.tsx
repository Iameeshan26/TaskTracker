import { useState, useContext, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "./api";
import { type User, workspacePath } from "./types";
import { Auth, message } from "./auth";
import Brand from "./Brand";
export default function Login({
  expired,
  onLogin,
}: {
  expired: boolean;
  onLogin: () => void;
}) {
  const { setUser } = useContext(Auth);
  const navigate = useNavigate();
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setBusy(true);
    setError("");
    try {
      const user = await api<User>("/login", {
        method: "POST",
        body: JSON.stringify({
          username: data.get("username"),
          password: data.get("password"),
        }),
      });
      setUser(user);
      onLogin();
      navigate(workspacePath(user), {
        replace: true,
      });
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-page">
      <header className="public-nav">
        <Brand />
        <Link to="/">← Back to home</Link>
      </header>
      <main className="login-layout">
        <section className="login-intro">
          <span className="eyebrow">YOUR NEXT CHAPTER STARTS HERE</span>
          <h1>
            Good things
            <br />
            get <em>done.</em>
          </h1>
          <p>
            A fresh perspective on your everyday tasks.
            <br />
            Sign in and pick up where you left off.
          </p>
          <div className="login-art" aria-hidden="true">
            <span>✓</span>
            <span>✓</span>
            <span>↗</span>
          </div>
        </section>
        <section className="login-card">
          <span className="eyebrow">WELCOME BACK</span>
          <h2>Sign in to your workspace</h2>
          <p>Bring a little clarity to your day.</p>
          {expired && (
            <p role="status" className="notice">
              Your session has expired. Please sign in again.
            </p>
          )}
          <form onSubmit={submit}>
            <label htmlFor="username">Username</label>
            <input
              id="username"
              name="username"
              autoComplete="username"
              required
              maxLength={100}
              autoFocus
              placeholder="Enter your username"
            />
            <label htmlFor="password">Password</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              maxLength={200}
              placeholder="Enter your password"
            />
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button className="wide" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"} <span>↗</span>
            </button>
          </form>
          <p className="demo-hint">
            Local demo accounts are listed in the project README.
          </p>
        </section>
      </main>
    </div>
  );
}
