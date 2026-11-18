import { useContext } from "react";
import { Link } from "react-router-dom";
import { statuses, workspacePath } from "./types";
import { Auth } from "./auth";
import Brand from "./Brand";
export default function Home() {
  const { user } = useContext(Auth);
  return (
    <div className="home">
      <header className="public-nav">
        <Brand />
        <Link
          className="button secondary"
          to={user ? workspacePath(user) : "/login"}
        >
          {user ? "Open workspace" : "Sign in"} <span>↗</span>
        </Link>
      </header>
      <main className="hero">
        <span className="eyebrow">LESS BUSYWORK. MORE FORWARD.</span>
        <h1>
          Make room
          <br />
          for <em>progress.</em>
        </h1>
        <p>
          A clear place for everything you need to do.
          <br />
          Organize your work, find your focus, and move things forward.
        </p>
        <Link className="button" to={user ? workspacePath(user) : "/login"}>
          Enter your workspace <span>↗</span>
        </Link>
        <div className="hero-note">
          <span className="tiny-dot" /> A little structure. A lot of
          possibility.
        </div>
        <section className="flow-preview" aria-label="Available task statuses">
          {Object.entries(statuses).map(([key, label], i) => (
            <div key={key}>
              <span className={"status " + key}>
                <i />
                {label}
              </span>
              <div className="preview-lines">
                <b>
                  {
                    [
                      "Start with an idea",
                      "Find your momentum",
                      "Celebrate the small wins",
                    ][i]
                  }
                </b>
                <p>
                  {
                    [
                      "Make space for what matters.",
                      "One focused step at a time.",
                      "Look how far you’ve come.",
                    ][i]
                  }
                </p>
              </div>
            </div>
          ))}
        </section>
        <section className="home-details">
          <div>
            <span className="eyebrow">A SPACE FOR EVERY ROLE</span>
            <h2>Your work. The bigger picture.</h2>
            <p>
              <strong>USER</strong> · A private workspace for your own tasks.
              <br />
              <strong>MANAGER</strong> · Assign tasks and coordinate the team.
              <br />
              <strong>ADMIN</strong> · Manage and assign every team task.
            </p>
          </div>
          <div>
            <span className="eyebrow">BUILT WITH CARE</span>
            <h2>Simple by design.</h2>
            <p>
              Java 21 · Spring Boot · Spring Security
              <br />
              React · TypeScript · Vite · In-memory storage
            </p>
          </div>
        </section>
      </main>
      <footer>
        Task Tracker <span>Thoughtful tools for everyday progress.</span>
      </footer>
    </div>
  );
}
