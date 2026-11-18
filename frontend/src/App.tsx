import {useState,useEffect} from "react";
import {Navigate,Route,Routes} from "react-router-dom";
import {api,ApiError} from "./api";
import type {User} from "./types";
import {Auth,message} from "./auth";
import Home from "./Home";
import Login from "./Login";
import Dashboard from "./Dashboard";
export default function App() {
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [expired, setExpired] = useState(false);
  const restore = () => {
    setLoading(true);
    setError("");
    api<User>("/me")
      .then(setUser)
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) setError(message(e));
      })
      .finally(() => setLoading(false));
  };
  useEffect(() => {
    restore();
    const expire = () => {
      setUser(null);
      setExpired(true);
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  if (loading)
    return (
      <div className="loading" role="status">
        Opening your workspace…
      </div>
    );
  if (error)
    return (
      <div className="loading">
        <p role="alert">{error}</p>
        <button onClick={restore}>Try again</button>
      </div>
    );
  return (
    <Auth.Provider value={{ user, setUser }}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route
          path="/login"
          element={
            user ? (
              <Navigate
                to={user.role === "ADMIN" ? "/admin" : "/dashboard"}
                replace
              />
            ) : (
              <Login expired={expired} onLogin={() => setExpired(false)} />
            )
          }
        />
        <Route
          path="/dashboard"
          element={
            user ? (
              <Dashboard key={user.username} />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route
          path="/admin"
          element={
            user ? (
              user.role === "ADMIN" ? (
                <Dashboard key={user.username} />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Auth.Provider>
  );
}
