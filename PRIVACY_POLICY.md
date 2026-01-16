# Privacy Policy for Curtain Android

**Last Updated:** January 16, 2026

## 1. Introduction

Curtain is a proteomics data visualization application developed for the scientific research community with the aim to make data exploration session created from the main Curtain web application https://curtain.proteo.info available to mobile devices. This privacy policy explains how we handle information when you use our app.

## 2. Information We Collect

Curtain Android does **NOT** collect, store, or transmit any personal information to external servers. All data processing happens locally on your device.

## 3. Camera Permission

> **Why We Need Camera Access:**
>
> The app requests camera permission (`android.permission.CAMERA`) exclusively for scanning QR codes that contain links to proteomics datasets.

### Camera Usage Details:

- **Purpose:** Scan QR codes containing dataset URLs from curtain.proteo.info
- **Scope:** Camera access is only used when you explicitly open the QR scanner feature
- **Data Processing:** Camera feed is processed locally on your device using ML Kit Barcode Scanning
- **No Recording:** The app does NOT record, save, or transmit camera images or videos
- **No Storage:** Camera data is not stored in any form on your device or elsewhere
- **Immediate Processing:** QR codes are decoded in real-time and immediately discarded
- **Extracted Information:** Only the decoded URL text from the QR code is used to load datasets

## 4. Data Storage

The app stores the following data locally on your device:

- **Proteomics Datasets:** Downloaded from curtain.proteo.info and cached locally for offline access
- **User Preferences:** App theme settings (light/dark mode)
- **Analysis State:** Your selections, annotations, and plot customizations
- **Database Cache:** Local SQLite and DuckDB databases for performance optimization

**All data remains on your device and is never transmitted to external servers except when downloading datasets from curtain.proteo.info.**

## 5. Network Access

The app requires internet permission (`android.permission.INTERNET`) for:

- Downloading proteomics datasets from curtain.proteo.info

**No analytics, tracking, or user data is transmitted.**

## 6. Third-Party Services

The app connects to these external services:

- **curtain.proteo.info:** Source of proteomics datasets

These services have their own privacy policies governing data handling.

## 7. Google ML Kit

QR code scanning uses Google ML Kit Barcode Scanning API. According to Google's documentation, ML Kit processes barcode data entirely on-device without sending data to Google servers.

## 8. Data Security

- All data is stored locally using Android's secure storage mechanisms
- No user accounts or authentication required
- No cloud backup of sensitive research data
- Data is only accessible within the app's sandbox

## 9. Children's Privacy

Curtain is design for exploring publicly available Scientific datasets on their owned locally on the device. Thus we do not send or communicate any information that would allow collect information from children.

## 10. Your Rights

You have full control over your data:

- **Access:** All data is stored locally and accessible to you
- **Deletion:** Uninstalling the app removes all locally stored data
- **Permission Control:** You can revoke camera permission at any time in Android Settings
- **Data Export:** You can force rebuild or clear cached datasets via the app menu

## 11. Changes to This Policy

We may update this privacy policy from time to time. Updates will be posted within the app and on this page. The "Last Updated" date at the top reflects the most recent changes.

## 12. Open Source

Curtain Android is open source software. You can review the complete source code to verify our privacy claims.

## 13. Contact Information

If you have questions or concerns about this privacy policy or how the app handles data, please contact:

- **Project Website:** [curtain.proteo.info](https://curtain.proteo.info)
- **GitHub:** Check the app's repository for issue reporting

## 14. Consent

By using Curtain Android, you consent to this privacy policy. If you do not agree with this policy, please do not use the app.

---

*This privacy policy complies with Google Play Store requirements for apps using camera permissions.*
