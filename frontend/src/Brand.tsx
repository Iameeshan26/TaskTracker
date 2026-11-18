import { Link } from "react-router-dom";
export default function Brand() {
  return (
    <Link className="brand" to="/" aria-label="Task Tracker home">
      <span className="brand-icon">✓</span>task tracker
      <span className="brand-period">.</span>
    </Link>
  );
}
