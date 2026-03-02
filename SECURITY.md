# Security Practices

This document outlines the security controls in place for BudgetAI and the intended
approach for features not yet implemented.

---

## 1. Secrets & Environment Variables

All sensitive configuration values are resolved at runtime from environment variables.
Nothing is hardcoded in source code or committed to version control.

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HMAC signing key for JWT access and refresh tokens (min 32 bytes) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | PostgreSQL connection |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials |
| `CORS_ORIGINS` | Comma-separated list of allowed frontend origins |

On AWS Elastic Beanstalk these are set as **Environment Properties** in the EB console,
never inside `application-aws.yaml` itself (which only contains `${VAR}` placeholders).

**Local development:** The `application.yaml` fallback values (e.g.
`dev-secret-key-change-in-production-must-be-at-least-32-bytes-long`) are intentionally
inadequate for production and serve as an obvious reminder to supply real values.

---

## 2. HTTPS Enforced Everywhere

- The production API base URL is `https://api.aienvelopebudget.com` — no plain HTTP.
- The Angular frontend is deployed to S3 and served through **AWS CloudFront**, which is
  configured to redirect all `HTTP → HTTPS` requests at the distribution level.
- CORS is restricted to known origins via the `CORS_ORIGINS` environment variable;
  wildcard origins are not permitted in production.
- JWTs are short-lived (15-minute access tokens) and transmitted only in the
  `Authorization: Bearer` header, never in URLs or cookies.

---

## 3. AWS IAM — Least-Privilege Roles

Each IAM identity is scoped to only the AWS actions it actually needs.

### Elastic Beanstalk Application Role

The EC2 instance profile attached to the EB environment requires:

```
elasticbeanstalk:PutInstanceStatistics
logs:CreateLogGroup
logs:CreateLogStream
logs:PutLogEvents
```

No S3, RDS, or other service permissions unless explicitly required.

### Frontend Deploy User (CI/CD)

Used by the `npm run deploy:frontend` script (S3 sync + CloudFront invalidation):

```
s3:PutObject
s3:DeleteObject
s3:ListBucket        (on bucket: budget-ai-frontend-prod)
cloudfront:CreateInvalidation  (on distribution: E2E12XHUU72NVN)
```

No `s3:*` wildcard, no IAM management permissions, no EC2/RDS access.

### Key Rules

- Rotate access keys on a regular schedule (at minimum annually).
- Use IAM roles with instance profiles in preference to long-lived access keys wherever
  possible.
- MFA is required for any IAM user with console access.

---

## 4. Plaid Access Tokens — Encrypted at Rest *(Planned)*

> **Status:** Plaid integration is not yet implemented. This section documents the
> required approach for when it is added.

When a user links a bank account via Plaid, Plaid returns a permanent `access_token`
that must be stored in the database. This token is equivalent to a password and **must
not be stored as plaintext**.

**Required approach:**

1. Generate a 256-bit AES encryption key and store it in a new environment variable
   `PLAID_TOKEN_ENCRYPTION_KEY` (set in EB Environment Properties, never committed).
2. Before persisting a Plaid `access_token` to the database, encrypt it with
   `AES-256-GCM`. Store the ciphertext, IV, and authentication tag.
3. Decrypt just-in-time when the token is needed to make a Plaid API call.
4. The `plaid_access_token` column in the database must be typed `TEXT` and annotated
   with a custom `@Convert` (JPA `AttributeConverter`) that handles encrypt/decrypt
   transparently.
5. The raw plaintext token must never appear in logs, API responses, or serialized
   entity output.

---

## 5. No Logging of Sensitive Financial Data

The following controls prevent sensitive data from appearing in logs or error output:

| Control | Configuration |
|---|---|
| SQL logging disabled | `spring.jpa.show-sql: false` in all profiles |
| Actuator exposure minimal | Only `/actuator/health` is exposed; `show-details: never` |
| Passwords hashed with BCrypt | Plaintext passwords are never stored or logged |
| JWT claims do not include PII beyond email | `userId` (UUID) and `email` only |
| Spring Security stateless sessions | No `HttpSession` created; session IDs not logged |

**Additional guidelines for future development:**

- Never pass raw `amount`, `accountNumber`, or `accessToken` values into `log.info()` /
  `log.debug()` calls.
- Log correlation IDs and user IDs (UUIDs) for traceability, not email addresses or
  dollar amounts.
- If a logging aggregator (e.g. Datadog, CloudWatch) is configured, verify that no
  financial fields are included in structured log payloads before enabling verbose
  request logging.
