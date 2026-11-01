const FEATURES = [
  { title: 'Budget plan', text: 'A 50/30/20 split of your salary after EMIs, with overspend alerts.' },
  { title: 'AI recommendations', text: 'Investment ideas matched to your risk profile and current market trends.' },
  { title: 'Portfolio & goals', text: 'Track SIPs, FDs and stocks, and progress towards your savings goals.' },
  { title: 'Notifications', text: 'EMI due-date reminders and budget alerts.' },
];

export default function ComingSoon() {
  return (
    <>
      <div className="page-head"><h1>Insights</h1></div>
      <p className="muted">These features are on the way. They will appear here as each service goes live.</p>
      <div className="feature-grid">
        {FEATURES.map((f) => (
          <div className="card feature" key={f.title}>
            <span className="badge">Coming soon</span>
            <h3>{f.title}</h3>
            <p className="muted">{f.text}</p>
          </div>
        ))}
      </div>
    </>
  );
}
