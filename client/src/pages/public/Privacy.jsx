import PageHero from "../../components/public/PageHero.jsx";

export default function Privacy() {
  return (
    <div>
      <PageHero compact ghost="Privacy" title="Privacy Policy" text="Last updated: September 2026" />
      <div className="mx-auto max-w-3xl px-6 py-20 sm:px-10">
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
    </div>
  );
}


