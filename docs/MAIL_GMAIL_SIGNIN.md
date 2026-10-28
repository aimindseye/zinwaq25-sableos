# Sable Mail: Sign in with Google (Gmail OAuth)

Sable Mail ships without the Thunderbird OAuth clients (they belong to other
package names and signing keys). Without a Google client of your own, Gmail
works only with an **app password** (Google Account > Security > 2-Step
Verification > App passwords; IMAP `imap.gmail.com` 993 SSL/TLS, SMTP
`smtp.gmail.com` 465 SSL/TLS). With your own client, account setup offers
"Sign in with Google" for Gmail addresses, the same flow as Thunderbird.

The client id is not secret, but it is yours, so it comes from the build host
(`SABLE_MAIL_GOOGLE_CLIENT_ID`) and is never committed.

## 1. Find the signing certificate of the installed Sable Mail

The Android client is bound to the package name and the SHA-1 of the key that
signs the APK on the phone (the platform build signs it). Read it from the
installed APK:

```bash
adb -s 36022310232425 shell pm path org.sableos.mail          # package:/system/.../SableMail.apk
adb -s 36022310232425 pull "$(adb -s 36022310232425 shell pm path org.sableos.mail | sed 's/^package://' | tr -d '\r')" /tmp/SableMail.apk
keytool -printcert -jarfile /tmp/SableMail.apk | grep SHA1
```

If you later sign builds with different keys (for example your own release
keys), add that SHA-1 as well, or create a second Android client.

## 2. Create the Google Cloud OAuth client

1. https://console.cloud.google.com/ > create a project (e.g. "Sable Mail").
2. APIs & Services > Library > enable **Gmail API**.
3. Google Auth Platform (OAuth consent screen) > **External**; app name
   "Sable Mail", your email as support and developer contact.
4. Data access (Scopes) > add `https://mail.google.com/`.
5. Audience: add your Gmail address as a test user, then **Publish app**
   (In production). In "Testing" Google expires refresh tokens after 7 days, so
   you would have to sign in again every week. An unverified app in
   production works for you; Google shows an "unverified app" warning once,
   choose Advanced > Go to Sable Mail.
6. Clients > Create client > **Android**: package name `org.sableos.mail`,
   SHA-1 from step 1. Open the client > Advanced settings > **Enable custom
   URI scheme** (Sable Mail redirects to `org.sableos.mail:/oauth2redirect`).
7. Copy the client id (`NNNN-xxxx.apps.googleusercontent.com`). There is no
   client secret for an Android client.

## 3. Build with it

On the build host, before the app build (the Sable Mail flavor reads it):

```bash
export SABLE_MAIL_GOOGLE_CLIENT_ID=NNNN-xxxx.apps.googleusercontent.com
```

Put the line in `~/.bashrc` (or the shell profile the build runs from) so every
build keeps it. The flavor prints `SABLE_MAIL_OAUTH=GMAIL_OWNER_CLIENT` when it
is used and `SABLE_MAIL_OAUTH=DISABLED_UNTIL_SABLE_CLIENT_REGISTRATION` when it
is not set. It refuses the upstream Thunderbird/K-9 Google projects. Then
rebuild and flash as usual.

## 4. Use it

Sable Mail > add account > your Gmail address > **Sign in with Google**. A
browser opens Google's sign-in, then returns to Sable Mail. An existing Gmail
account set up with a password can be switched in its server settings
(Authentication: OAuth 2.0), or removed and added again.

Troubleshooting: `redirect_uri_mismatch` or a browser that does not return
means custom URI scheme is off (step 6). `Error 400: invalid_request` with a
package/SHA-1 message means the SHA-1 does not match the installed APK (step 1).
