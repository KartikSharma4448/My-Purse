# Security

My Purse stores sensitive user-provided information. Its database and imported documents are encrypted locally; Android Keystore protects the key material. Release builds request screenshot protection. These measures do not guarantee protection on a compromised or unlocked device.

## Reporting a Vulnerability

Use this repository's GitHub **Security > Report a vulnerability** feature when available. If private reporting is unavailable, open an issue asking for a private reporting channel without posting exploit details, real vault contents, credentials, or personal documents.

Include the affected version, Android version, reproduction steps using synthetic data, and the security impact. Do not test against other people's devices or data.

## Credential Handling

Signing keys and `keystore.properties` are local-only and excluded from Git. Forks must create their own keys and use their own app identity. Never upload a keystore or password to an issue, pull request, CI artifact, or release.

Root detection and malware scanning are not implemented. A user-selected copy or share operation makes data available to the chosen destination outside the vault.
