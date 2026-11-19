# Sable Mail: which password each provider needs

Sable Mail receives over IMAP and sends over SMTP, always with TLS. Most providers need an
**app password** (a separate password that only works for mail and can be revoked) instead of the
account password. An app password needs no OAuth client ID, so it works with no Google or Microsoft
registration. Keep two-step verification on.

If setup does not find the servers by itself, choose the manual setup, set both authentication
types to **Password** and type the servers below. Ports 993 (incoming) and 465 (outgoing) use TLS
from the start; 143 and 587 use STARTTLS.

| Provider | What you need | Incoming | Outgoing |
|---|---|---|---|
| Gmail | An app password (two-step verification must be on); IMAP is always on. Or sign in with your own Google client ([MAIL_GMAIL_SIGNIN.md](MAIL_GMAIL_SIGNIN.md)). | imap.gmail.com 993 | smtp.gmail.com 465 |
| Google Workspace (company or school) | An app password; the admin must allow IMAP and app passwords | imap.gmail.com 993 | smtp.gmail.com 465 |
| Yahoo, AOL | An app password from the account security page | imap.mail.yahoo.com 993 (AOL: imap.aol.com) | smtp.mail.yahoo.com 465 (AOL: smtp.aol.com) |
| iCloud (also @me.com, @mac.com) | An app-specific password from appleid.apple.com | imap.mail.me.com 993 | smtp.mail.me.com 587 STARTTLS |
| Outlook.com, Hotmail, Live | Microsoft allows sign-in only with OAuth, so it needs your own Microsoft client ([MAIL_MICROSOFT_SIGNIN.md](MAIL_MICROSOFT_SIGNIN.md)) | not available without it | |
| Microsoft 365 (work or school) | Not supported | | |
| GMX, WEB.DE | Turn on POP3/IMAP in webmail; an app password if two-factor is on | imap.gmx.net / imap.web.de 993 | mail.gmx.net / smtp.web.de 587 STARTTLS |
| Zoho | Turn on IMAP access; an app password if two-factor is on | imap.zoho.com 993 | smtp.zoho.com 465 |
| Fastmail | An app password with mail access | imap.fastmail.com 993 | smtp.fastmail.com 465 |
| Proton Mail | Not possible on a phone (needs the desktop Bridge) | | |
| Any other domain | Address and password; setup tries the provider's published settings, then asks for servers | | |

Server names are the providers' published ones. Which sign-in each provider accepts changes over
time, so check the provider's help page if setup says the password is refused. Whether Sable Mail's
setup offers the **Password** option for Gmail and Yahoo (instead of only its own sign-in button) has
not been tried on the phone yet.

## What Sable Mail does after setup

- The Hub lists the 50 newest Inbox messages across accounts; opening one opens it in Sable Mail and
  marking it read syncs to the server.
- Delete an account in Sable Mail and its messages stay on the server. After changing a password,
  update it in the account's settings, or mail stops arriving until you do.
