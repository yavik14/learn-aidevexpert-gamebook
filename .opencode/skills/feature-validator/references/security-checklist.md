# Feature Security Checklist

Use this checklist during feature validation. It is intentionally portable: do not assume any specific security plugin is available.

This is not a full repository security audit. It is a feature-scoped security review anchored to:

- the selected feature spec,
- the implementation diff,
- files changed by the implementation,
- directly supporting files needed to understand the changed security behavior.

## Security Review Principles

- Stay diff-focused: review changed behavior and directly supporting code, not the whole repo.
- Think in source -> control -> sink paths: where input/data comes from, what checks transform or restrict it, and where it is used.
- Preserve evidence: cite file paths, relevant code behavior, commands, or missing controls.
- Prefer concrete findings over generic advice.
- If a risk is theoretical but not exploitable in this feature slice, record it as a note, not a blocking finding.
- If a security issue blocks acceptance, produce a repair brief that an implementer can execute.

## Core Checks

### 1. Secrets And Configuration

Check for:

- committed API keys, tokens, private URLs, passwords, cookies, service credentials, or `.env` contents,
- secrets embedded in tests, fixtures, screenshots, logs, or docs,
- unsafe default credentials,
- config values that should be environment variables.

Pass signal:

- no secrets in the diff,
- `.env*` files are ignored or represented by safe examples,
- docs use placeholders for sensitive values.

### 2. Authentication And Authorization

When the feature touches identity, sessions, roles, API keys, admin, or service flows, check:

- unauthenticated access is intentionally public,
- authenticated-only operations enforce auth at the correct boundary,
- role checks cannot be bypassed by client/UI changes,
- service/API keys are checked before side effects,
- revoked or expired access is respected,
- public or shared surfaces do not expose private user data.

Pass signal:

- access checks are explicit at the correct boundary,
- negative cases are tested or manually verified.

### 3. Input Validation And Injection

When the feature handles user/API input, search, transcript text, file names, URLs, SQL, templates, or external payloads, check:

- input is parsed/validated at the boundary,
- SQL uses parameterized queries or ORM-safe APIs,
- search queries cannot break query syntax or access unintended records,
- rendered or generated content avoids injection (XSS, template, or format injection),
- URLs are validated before redirects or fetches,
- file paths cannot traverse outside allowed directories.

Pass signal:

- boundary validation is visible,
- dangerous sinks have nearby controls,
- tests cover at least one invalid or malicious input where practical.

### 4. Data Exposure And Privacy

When the feature returns or renders user, note, attachment, transcript, or progress data, check:

- responses include only required fields,
- private data does not appear in shared screens, logs, screenshots, or error messages,
- one user cannot access another user's private state,
- public or shared surfaces expose only intentionally public fields.

Pass signal:

- data selection is explicit,
- public/private boundaries are clear,
- logs avoid sensitive payloads.

### 5. External Services And SSRF-Like Risks

When the feature calls external services, webhooks, user-provided URLs, or other third parties, check:

- outbound URLs are allowlisted or constructed from trusted config,
- webhooks verify signatures or secrets,
- retries/timeouts are bounded,
- failures do not leak secrets,
- service clients isolate credentials from UI/client code.

Pass signal:

- external calls go through server-side adapters/providers,
- credentials stay server-side,
- failure modes are handled deliberately.

### 6. Dependency And Tooling Risk

When dependencies or tooling change, check:

- new packages are necessary for the feature,
- packages are current and plausibly maintained,
- install/build scripts do not introduce suspicious behavior,
- lockfile changes match package changes,
- generated build artifacts are ignored unless intentionally tracked.

Pass signal:

- dependency additions are justified,
- no obvious abandoned or unrelated package is introduced,
- generated build artifacts such as build output directories, coverage, and caches are ignored.

### 7. Client, Server, And Device Boundary

Check the boundary between trusted backend logic and untrusted client or on-device code:

- secrets, API keys, and service credentials are not embedded in client or on-device code,
- server endpoints validate input server-side,
- client/UI checks are not treated as authorization,
- public configuration contains only public values,
- sensitive operations run in the trusted boundary (backend service or secured native layer), not in client code.

Pass signal:

- the client/server or client/device split is obvious,
- sensitive operations and credentials stay in the trusted boundary.

### 8. File Uploads And Media

When the feature touches uploads, recordings, transcripts, generated PDFs, or local files, check:

- file type and size limits exist,
- paths and names are sanitized,
- untrusted files are not executed or served with unsafe content types,
- generated files do not include private data unintentionally.

Pass signal:

- upload/media constraints are explicit,
- storage and serving paths are controlled.

### 9. Auditability And Abuse Controls

When the feature changes access, sharing, admin, review, or service APIs, check:

- important state changes are auditable,
- destructive/revocation actions are explicit,
- rate limits or abuse controls are considered for public endpoints,
- errors are actionable without leaking internals.

Pass signal:

- audit fields/logging hooks are planned or implemented at the appropriate maturity level,
- public endpoints have basic abuse considerations.

## Finding Format

Use the validator's required finding format. Security findings should add:

- Attack path: how an attacker or unauthorized actor reaches the issue.
- Affected asset: user data, private records, service credential, admin capability, etc.
- Closest missing or weak control: auth check, validation, allowlist, server/client boundary, escaping, parameterization, etc.

Example:

```md
### Finding: Sync API accepts unauthenticated writes

- Severity: High
- Evidence: `sync/api/notes` creates records without checking the service API key described in the spec.
- Attack path: Anyone who can reach the endpoint can create records.
- Affected asset: User records and private notes.
- Closest missing control: Service API key verification before side effects.
- Why it matters: Unauthorized writes corrupt or expose private user data.
- Required change: Reject requests without the configured service API key before parsing or writing data.
- Suggested implementation:
  1. Add a service API key guard for this endpoint.
  2. Return 401/403 before any database write when the key is absent or invalid.
  3. Add tests for missing, invalid, and valid keys.
- Verification after fix:
  - the repo's test command
  - API call without key returns 401/403 and creates no record.
  - API call with valid key succeeds.
```

## Acceptance Guidance

- Critical/High exploitable security findings should produce `revise` or `block`.
- Missing security tests for a security-sensitive feature usually produces `revise`.
- Purely future-facing risks outside the feature scope can be recorded as notes, not blockers.
- If a full security audit is needed, recommend a dedicated security scan rather than overloading feature validation.
