# MQTT production security

The NexaIoT application supports environment-driven MQTT broker configuration,
optional username/password authentication, TLS-required startup validation, and
strict validation of telemetry, state, and command topic structure. Command
topics are built from the trusted device record after owner-scoped lookup.

The application cannot prove the original broker publisher identity from a
normal inbound MQTT message. Production deployments must enforce that boundary
at the broker:

- the backend service account may subscribe to telemetry/state topics and
  publish command topics;
- each physical device may publish only to its own telemetry/state topics;
- each physical device may subscribe only to its own command topic;
- devices must not publish another device's topics, subscribe to broad `#`
  filters, or publish server command topics;
- broker authentication, TLS certificates, ACLs, and credential rotation are
  deployment responsibilities.

Use `MQTT_BROKER_URL=ssl://...:8883` with `MQTT_TLS_REQUIRED=true` for a
TLS-enforced deployment. Java's normal certificate and hostname validation is
used; no trust-all certificate configuration is provided.
