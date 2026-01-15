# Security Policy

## 🔒 Commitment to Security

OSPdfReader takes security seriously. As an open-source PDF reader that handles user documents, we are committed to protecting user privacy and ensuring the application is secure.

---

## 📋 Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| Latest  | ✅ Yes             |
| Older   | ❌ No              |

We recommend always using the latest version of OSPdfReader for the best security.

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability in OSPdfReader, we appreciate your help in disclosing it responsibly.

### Do NOT

- ❌ Open a public GitHub issue for security vulnerabilities
- ❌ Disclose the vulnerability publicly before it's fixed
- ❌ Exploit the vulnerability beyond what's necessary to demonstrate it

### Do

1. **Report privately** via GitHub's private vulnerability reporting:
   - Go to the repository's **Security** tab
   - Click **"Report a vulnerability"**
   - Provide detailed information about the issue

2. Alternatively, email the maintainers directly (check repository for contact info)

### What to Include

Please include as much of the following information as possible:

- **Type of vulnerability** (e.g., data exposure, code injection, permission bypass)
- **Affected component** (e.g., PDF rendering, OCR, Google Drive sync)
- **Steps to reproduce** the vulnerability
- **Proof of concept** (code or screenshots)
- **Potential impact** and severity assessment
- **Suggested fix** (if you have one)

---

## ⏱️ Response Timeline

| Stage | Timeline |
|-------|----------|
| Acknowledgment | Within 48 hours |
| Initial assessment | Within 1 week |
| Fix development | Depends on severity |
| Public disclosure | After fix is released |

We aim to:
- Acknowledge reports within **48 hours**
- Provide an initial assessment within **1 week**
- Develop and release fixes based on severity:
  - **Critical**: As soon as possible
  - **High**: Within 2 weeks
  - **Medium**: Within 1 month
  - **Low**: Next regular release

---

## 🛡️ Security Best Practices

### For Users

- **Download from trusted sources**: Only install from official releases or build from source
- **Keep updated**: Always use the latest version
- **Review permissions**: Understand what permissions the app requests
- **Backup documents**: Keep separate backups of important PDFs

### Application Security

OSPdfReader implements these security measures:

- **Local processing**: PDF rendering and OCR happen entirely on-device
- **Minimal permissions**: Only necessary Android permissions are requested
- **No analytics**: No tracking or data collection
- **Open source**: All code is publicly auditable
- **Secure storage**: Sensitive data uses Android's secure storage mechanisms

---

## 📜 Scope

The following are **in scope** for security reports:

- 🔓 Unauthorized access to user data
- 🔓 Data leakage or exposure
- 🔓 Permission bypass
- 🔓 Code injection vulnerabilities
- 🔓 Insecure data storage
- 🔓 Google Drive authentication issues

The following are **out of scope**:

- ❌ Issues requiring physical device access
- ❌ Social engineering attacks
- ❌ Issues in third-party dependencies (report to the respective project)
- ❌ Theoretical vulnerabilities without proof of concept

---

## 🏆 Recognition

We appreciate security researchers who help keep OSPdfReader safe. With your permission, we will:

- Credit you in our release notes
- Add your name to a security acknowledgments section
- Provide a letter of appreciation if requested

---

## 📞 Contact

For security-related inquiries:

- **Email**: [durjoymajumdar02@gmail.com](mailto:durjoymajumdar02@gmail.com)
- **Website**: [asokakrsna.github.io](https://asokakrsna.github.io)

You can also use GitHub's private vulnerability reporting feature.

---

**Thank you for helping keep OSPdfReader secure! 🙏**
