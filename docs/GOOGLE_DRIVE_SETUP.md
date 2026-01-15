# Google Drive Sync Setup Guide

This guide helps you set up Google Drive sync for OSPdfReader. Since this is an open-source app, you'll need to configure your own Google Cloud project to use the sync feature.

---

## 🤔 Why Do I Need My Own Setup?

OSPdfReader is free and open source. To keep it that way without requiring authentication servers or backend infrastructure, each user who wants Google Drive sync needs to set up their own OAuth credentials. This ensures:

- ✅ **Privacy**: Your data stays between you and Google
- ✅ **No servers**: No third-party servers handling your auth
- ✅ **Full control**: You control your own API access
- ✅ **Free forever**: No backend costs to pass on to users

---

## 📋 Prerequisites

- A Google account
- ~15 minutes of time
- Internet connection

---

## 🚀 Step-by-Step Setup

### Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click the project dropdown at the top → **New Project**
3. Enter a name like `OSPdfReader-Personal`
4. Click **Create**
5. Wait for the project to be created, then select it

### Step 2: Enable Google Drive API

1. In the Cloud Console, go to **APIs & Services → Library**
2. Search for "Google Drive API"
3. Click on it and press **Enable**

### Step 3: Configure OAuth Consent Screen

1. Go to **APIs & Services → OAuth consent screen**
2. Select **External** (unless you have Google Workspace)
3. Click **Create**
4. Fill in the required fields:
   - **App name**: `OSPdfReader`
   - **User support email**: Your email
   - **Developer contact email**: Your email
5. Click **Save and Continue**
6. On the **Scopes** page:
   - Click **Add or Remove Scopes**
   - Search for and add: `https://www.googleapis.com/auth/drive.file`
   - Click **Update** then **Save and Continue**
7. On the **Test users** page:
   - Click **Add Users**
   - Add your own email address
   - Click **Save and Continue**
8. Review and click **Back to Dashboard**

> 📝 **Note**: With "External" type, your app will be in "Testing" mode. This is fine for personal use and allows up to 100 test users.

### Step 4: Create OAuth Credentials

1. Go to **APIs & Services → Credentials**
2. Click **+ CREATE CREDENTIALS → OAuth client ID**
3. Select **Android** as the application type
4. Fill in the details:
   - **Name**: `OSPdfReader Android`
   - **Package name**: `com.ospdf.reader` (check your app's actual package name)
   - **SHA-1 certificate fingerprint**: See [Getting Your SHA-1](#getting-your-sha-1) below
5. Click **Create**
6. Note down the **Client ID** (you'll need this)

### Step 5: Get Your SHA-1 Fingerprint {#getting-your-sha-1}

#### If Using the Pre-built APK

If you're using a pre-built release APK, you'll need the SHA-1 from the release keystore. Check the GitHub release notes or contact the maintainer.

#### If Building from Source

**Option A: Using Android Studio**

1. Open OSPdfReader in Android Studio
2. Go to **View → Tool Windows → Gradle**
3. Navigate to **:app → Tasks → android → signingReport**
4. Double-click to run
5. Find the SHA-1 in the output:
   ```
   Variant: debug
   SHA1: XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX:XX
   ```

**Option B: Using Command Line**

For debug keystore (default location):

```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Mac/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Copy the SHA1 fingerprint (format: `XX:XX:XX:XX:...`)

### Step 6: Configure the App

After creating your OAuth client:

1. Open OSPdfReader
2. Go to **Settings → Google Drive Sync**
3. Tap **Configure OAuth**
4. Enter your **Client ID** from Step 4
5. Tap **Save**
6. Tap **Sign In** and authenticate with your Google account
7. Grant the requested permissions

---

## ✅ Verification

To verify your setup is working:

1. Open a PDF in OSPdfReader
2. Make some annotations
3. Go to **Settings → Google Drive Sync → Sync Now**
4. Check your Google Drive for the synced file

---

## 🔧 Troubleshooting

### "Sign-in failed" or "Access Denied"

- Verify your SHA-1 fingerprint is correct
- Make sure your email is added as a test user
- Check that the package name matches exactly

### "API not enabled"

- Go to Cloud Console → APIs & Services → Library
- Ensure Google Drive API is enabled

### "Quota exceeded"

- Free tier has generous limits for personal use
- If you hit limits, check for sync loops or excessive API calls

### Can't find OAuth configuration in app

- Make sure you're using a version that supports custom OAuth
- Check Settings → Google Drive Sync

---

## 🔐 Security Notes

- Your OAuth Client ID is **not secret** - it's safe to have in the app
- The OAuth flow uses PKCE for security
- Your credentials are stored securely on your device
- You can revoke access anytime at [myaccount.google.com/permissions](https://myaccount.google.com/permissions)

---

## 📚 Additional Resources

- [Google Cloud Console](https://console.cloud.google.com/)
- [Google Drive API Documentation](https://developers.google.com/drive/api/guides/about-sdk)
- [OAuth 2.0 for Mobile Apps](https://developers.google.com/identity/protocols/oauth2/native-app)

---

## 💬 Need Help?

If you're stuck:
- Open an issue on GitHub with the `help wanted` label
- **Email**: [durjoymajumdar02@gmail.com](mailto:durjoymajumdar02@gmail.com)
- **Website**: [asokakrsna.github.io](https://asokakrsna.github.io)

---

**Made with ❤️ for privacy-conscious users**
