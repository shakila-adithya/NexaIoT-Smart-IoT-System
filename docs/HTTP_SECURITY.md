# HTTP and browser security

The Spring Boot application serves authenticated JSON APIs. The React/Vite
application is hosted separately, so API response headers do not replace the
security policy required by the frontend document host.

## CORS

Configure allowed browser origins with a comma-separated environment value:

```text
CORS_ALLOWED_ORIGINS=https://your-frontend.example
```

Local development defaults to `http://localhost:5173`. Blank and wildcard
entries are ignored. Credentials remain disabled because NexaIoT uses bearer
JWTs in the `Authorization` header rather than authentication cookies.

## CSRF and headers

CSRF remains disabled for the current bearer-token architecture. Re-evaluate
this if authentication moves to cookies. API responses use Spring Security's
`nosniff` and frame-deny protections plus an explicit
`strict-origin-when-cross-origin` referrer policy. HSTS is emitted by Spring
Security only for HTTPS requests; production deployments must use HTTPS.

## Frontend CSP

Because Vite serves the React HTML separately, configure CSP at that frontend
host. A deployment should start with a policy appropriate to its actual asset
and API origins, for example:

```text
default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self' https://api.example.com; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'
```

The example is a deployment proposal only; no hosting infrastructure is
invented or changed by R7.6.
