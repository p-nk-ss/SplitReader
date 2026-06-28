# Privacy Policy — Mirrolit

_Last updated: 2026-06-28_

Mirrolit ("the app", "we", "us") is a multi-language e-book reader with on-device and
optional online translation. This policy explains what data the app handles and why.

> **Before publishing:** host this page at a public URL and paste that URL into Google
> Play Console → App content → Privacy policy. Replace the contact email below with the
> address you want users to reach you at.

## Summary

- Reading happens **on your device**. Your books, reading progress, bookmarks, notes,
  saved words, and statistics are stored **locally** and are not uploaded to us.
- The default translator (Google ML Kit) runs **fully on-device** — no text leaves your
  device to translate.
- We collect an **email address (and optional name)** only if you create an account, to
  provide sign-in. Authentication is handled by Google Firebase.
- We collect **crash diagnostics** to fix bugs.
- If you choose to enable an **online translation provider**, the text you translate is
  sent to that third-party service. This is optional and off by default.

## Data we collect

### Account data (only if you sign in)
- **Email address** and **optional display name**, processed by **Google Firebase
  Authentication** to create and secure your account.
- Used solely for authentication and syncing your account state. Not sold, not shared
  with advertisers.

### Diagnostics
- **Crash reports and basic device/diagnostic information** via **Google Firebase
  Crashlytics**, used only to detect and fix crashes and improve stability.

### On-device data (not collected by us)
- Imported books, reading position, bookmarks, notes, saved vocabulary, and reading
  statistics are stored **locally on your device** and are **not** transmitted to us.

## Optional features that send data to third parties

These are **off by default** and only operate when you turn them on:

- **Online translation providers** (DeepL, Google Cloud Translation, Microsoft Azure
  Translator, LibreTranslate): if you configure one with your own API key, the text you
  ask to translate is sent over HTTPS to that provider. Their handling of that text is
  governed by **their** privacy policies. The default **on-device ML Kit** translator
  sends nothing.
- **Free book catalog** (Project Gutenberg, Standard Ebooks): when you browse or download
  public-domain books, the app contacts those services over HTTPS to fetch listings and
  files.
- **Google Drive import**: if you choose to import a book from Google Drive, the app uses
  read-only access to the file you select. We do not browse or store your wider Drive.

API keys you enter for online translators are stored **encrypted on your device** (Android
Keystore) and are never transmitted to us.

## Purchases

Premium ("unlimited library") is sold as a one-time in-app purchase processed by **Google
Play Billing**. We do not receive or store your payment details; Google handles payment.

## How data is protected

- All network communication uses **HTTPS (encrypted in transit)**.
- Account credentials are managed by Firebase Authentication.
- Locally stored secrets (translator API keys) are encrypted with a device-bound key.

## Data retention and deletion

- Local data is removed when you uninstall the app or delete books in-app.
- **Account deletion:** you can permanently delete your account from within the app
  (Profile → Delete account), which removes your Firebase Authentication record. You may
  also request deletion by emailing us at the address below.

## Children

Mirrolit is not directed at children under 13. We do not knowingly collect personal data
from children.

## Changes to this policy

We may update this policy; the "Last updated" date will change accordingly. Material
changes will be reflected in the app's store listing.

## Contact

For privacy questions or deletion requests, contact: **pankaz6jha@gmail.com**
