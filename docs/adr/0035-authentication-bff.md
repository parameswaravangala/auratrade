# ADR 0035: Authentication via a backend-for-frontend session; Keycloak only before external users

- Status: accepted (founder approved Phase 0 on 2026-11-27)
- Date: 2026-11-27

## Context
Today there is no authentication, and paper decisions record `decided_by` as free text (B-031). The Security review found:
- The app binds 0.0.0.0:8080 and the dev endpoints change state with no Host or Origin check.
- Any web page the founder opens can POST to them, and DNS rebinding defeats CORS.
- Postgres (5433) and Ollama (11434) are published on all interfaces.

Reviewers disagreed on the identity provider:
- **Draft:** Keycloak plus SPA tokens in R1.
- **Security:** a BFF with an HttpOnly cookie, since in-memory tokens are still exposed to XSS from news or model text.
- **Enterprise and Product:** Keycloak costs 1 to 2 GB of RAM on the laptop and is not needed for one user. The real need is a trustworthy principal and an audit trail.

## Decision
1. **Immediately (exempt from the freeze if the founder agrees):**
   - Bind to `127.0.0.1` in the dev profile.
   - Add a filter that rejects any non-local `Host` and any foreign `Origin`.
   - Stop accepting `text/plain` on `/dev/llm/ask`.
   - Publish the compose ports on `127.0.0.1` only.
   - Fail startup if `dev` and `prod` profiles are both active, or if `prod` runs with the default DB password.
2. **R1:** a BFF in aura-core. Spring Security form login for a single local user, adding a passkey once Spring Security's WebAuthn support is verified for Boot 4.2. The session is an HttpOnly, Secure, SameSite=Strict cookie, with CSRF on. The SPA holds no tokens. The decider comes from the principal, and the `decidedBy` request field is removed.
3. **Before gate G3 (external users):** switch the BFF to `oauth2Login` against Keycloak 26.8 (or a managed IdP if approved). The SPA stays as it is.
4. **Before any live order (G4):** decision-bound step-up. A WebAuthn assertion over the order id and quantity is verified on the server. A session-wide "step-up within 10 minutes" window is not enough.

## Consequences
The smallest attack surface for a laptop app, and no token handling in JavaScript. One more migration step when Keycloak arrives, but the SPA and the API do not change.
