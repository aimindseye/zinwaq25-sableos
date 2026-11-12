# Sable Mail: Sign in with Microsoft (Outlook, Hotmail, Microsoft 365)

Microsoft turned off password sign-in for IMAP, so Outlook.com, Hotmail, Live and Microsoft 365
mailboxes need OAuth. Sable Mail ships without the Thunderbird and K-9 Microsoft clients (they are
registered to other packages and signing keys). With your own registration, account setup offers
"Sign in with Microsoft" for those addresses, the same flow as Thunderbird.

Both values below come from the build host and are never committed.

## 1. The signing key's hash

Microsoft returns the sign-in only to `msauth://org.sableos.mail/<hash>`, where the hash is the
SHA-1 of the certificate that signs Sable Mail on the phone, in base64. The platform build signs
it, so use the certificate of the key your image is signed with (the release key for release
builds; the AOSP test key for a test-keys build).

From an installed Sable Mail:

```bash
adb -s <serial> pull "$(adb -s <serial> shell pm path org.sableos.mail | sed 's/^package://' | tr -d '\r')" /tmp/SableMail.apk
keytool -printcert -jarfile /tmp/SableMail.apk | sed -n 's/.*SHA1: //p' | tr -d ':' | xxd -r -p | base64
```

From the certificate file of the signing key:

```bash
openssl x509 -in releasekey.x509.pem -outform der | openssl sha1 -binary | base64
```

The result is 28 characters ending in `=`.

## 2. Register the app with Microsoft (free)

1. https://entra.microsoft.com/ with a Microsoft account (a free Azure sign-up creates the
   directory if you have none).
2. **App registrations > New registration**: name "Sable Mail"; supported account types
   **Accounts in any organizational directory and personal Microsoft accounts**.
3. **Authentication > Add a platform > Android**: package name `org.sableos.mail`, signature hash
   from step 1. Turn on **Allow public client flows**. No client secret.
4. **API permissions > Add a permission > Microsoft Graph > Delegated**: `IMAP.AccessAsUser.All`,
   `SMTP.Send`, `offline_access`, `openid`, `email`, `profile`.
5. Optional: **Branding & properties > Publisher verification** with a free Microsoft partner ID.
   Unverified apps show "unverified" at sign-in, and some work accounts need their admin to allow
   the app.
6. Copy the **Application (client) ID** from Overview.

## 3. Build

```bash
export SABLE_MAIL_MICROSOFT_CLIENT_ID=<application id>
export SABLE_MAIL_MICROSOFT_SIGNATURE_HASH=<hash from step 1>
bash build/sable.sh q25 full apps
```

The flavor prints `SABLE_MAIL_OAUTH=MICROSOFT_OWNER_CLIENT` (with `GMAIL_OWNER_CLIENT` too when
`SABLE_MAIL_GOOGLE_CLIENT_ID` is set, docs/MAIL_GMAIL_SIGNIN.md). It refuses the inherited
Thunderbird and K-9 client ids and a hash that is not 28 base64 characters.

## 4. Use

Sable Mail > add account > your Outlook, Hotmail or Microsoft 365 address > **Sign in with
Microsoft**. A browser opens Microsoft's sign-in and returns to Sable Mail.
