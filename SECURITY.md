# Security Policy

Gatehouse Gateway takes security and data privacy very seriously. We appreciate responsible disclosure of security vulnerabilities.

---

## Supported Versions

Only the latest release branch and `main` receive security updates and patches:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

If you discover a security vulnerability in Gatehouse Gateway, **please do NOT report it via public GitHub issues.**

Instead, please report vulnerabilities by:
1. Opening a **GitHub Security Advisory** directly on the repository: [New Security Advisory](https://github.com/crnjihia/gatehouse-gateway/security/advisories/new)
2. Or contacting the maintainer via email: `security@gatehouse-gateway.org`

### What to Include in Your Report
- A detailed description of the vulnerability.
- Steps to reproduce or proof-of-concept (PoC) code/requests.
- Potential impact and affected components (e.g., JWT validation, Redis Lua script, PII masking).
- Any proposed mitigations or fixes.

---

## Response Timeline

- **Initial Response:** Within 48 hours acknowledging receipt of the report.
- **Assessment & Triage:** Within 5 business days with vulnerability confirmation and severity assignment.
- **Fix & Disclosure:** Coordinated release and disclosure schedule with the finder.
