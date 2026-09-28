export default function Privacy() {
  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-16 sm:py-20">
      <h1 className="text-4xl font-bold text-ink tracking-tight mb-3">Privacy Policy</h1>
      <p className="text-muted mb-10">Last updated: September 2026</p>

      <div className="space-y-8 text-muted leading-relaxed">
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Data We Collect</h2>
          <p>
            NexaIoT collects account information you provide (name, email) and device data you connect to
            the platform, such as sensor readings and device metadata.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Security</h2>
          <p>
            We use token-based authentication and encourage strong passwords. In a production deployment,
            all traffic should be served over HTTPS.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Your Rights</h2>
          <p>
            You can request access to, correction of, or deletion of your account data at any time by
            contacting us.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Contact</h2>
          <p>Questions about this policy can be sent to privacy@nexaiot.dev.</p>
        </section>
      </div>
    </div>
  );
}
