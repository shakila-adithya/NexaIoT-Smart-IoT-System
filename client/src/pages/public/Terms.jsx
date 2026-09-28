export default function Terms() {
  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-16 sm:py-20">
      <h1 className="text-4xl font-bold text-ink tracking-tight mb-3">Terms of Service</h1>
      <p className="text-muted mb-10">
        This is a frontend demonstration document and does not constitute a legally reviewed agreement.
      </p>

      <div className="space-y-8 text-muted leading-relaxed">
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Platform Usage</h2>
          <p>
            NexaIoT is provided for demonstration and educational purposes. Use of the platform implies
            acceptance of these terms.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">User Accounts</h2>
          <p>You are responsible for maintaining the confidentiality of your account credentials.</p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Device Responsibility</h2>
          <p>
            You are responsible for the devices you connect to NexaIoT and for ensuring they operate
            safely.
          </p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Data</h2>
          <p>Device and sensor data you upload remains yours. See our Privacy Policy for more detail.</p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Acceptable Use</h2>
          <p>You agree not to use NexaIoT for unlawful purposes or to disrupt the service for others.</p>
        </section>
        <section>
          <h2 className="text-xl font-semibold text-ink mb-2">Service Limitations</h2>
          <p>NexaIoT is provided "as is" for demonstration purposes without warranty of any kind.</p>
        </section>
      </div>
    </div>
  );
}
