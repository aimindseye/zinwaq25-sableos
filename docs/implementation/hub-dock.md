# Hub source dock, combined conversations and mail apps

> **2026-10-23:** the Sable Hub app no longer has a launcher entry; Sable Start's Hub page is the
> one Hub screen, with its own unread bar ([q4-review](q4-review.md#one-hub)). The dock below is
> part of the Hub app's screen, which users no longer reach from Start.

Status: **written and unit-tested (Hub unit tests, Lint, detekt, ktlint), not run on a device.** The
idea comes from Conduit (github.com/eqbirvin/Conduit). Conduit is GPL-3.0, and
this repository is Apache-2.0, so only the behaviour was reimplemented and no
code was copied.

## Source dock

A floating bar at the bottom of the Hub inbox shows one icon per source with its
unread count:

* SMS: unread SMS conversations.
* Calls: missed calls and voicemail.
* Each connected app, per profile: its unread conversations. A connected
  conversation counts as unread while its notification is still posted, and its
  count is the incoming messages since your last reply.

The dock total equals the inbox attention count, and a unit test checks this.
Sources with something unread come first, sorted by count and then name. The
chevron expands the bar into a grid that also shows sources with nothing unread.

Rows behind a locked work profile share one "Work profile" entry, which has no
app icon. The dock never shows more than the row list would.

Keys:
* **D** moves focus to the dock.
* **Enter** narrows the inbox to the focused source; pressing it again, or
  choosing a filter chip, clears the selection.
* A long press on an icon opens the app.

Code: `policy/HubSourceDock.kt`, and `ui/HubScreen.kt` for the UI.

## Combined conversations

Rows next to each other from the same person roll up under the newest one, which
shows a "+N" marker. A double-line divider closes a rolled-up group.

"The same person" means the whole display name matches, ignoring case and
spaces, or two SMS rows share an address. This works across sources: an SMS
thread and a WhatsApp chat with the same contact combine. Some rows never
combine:
* calls;
* rows whose name is redacted (app label, "SMS", "Work profile");
* a group chat with a one-to-one chat. Titles are compared whole, so these
  don't match.

Keys:
* **Right** or **Space** expands a rolled-up row.
* **Left** collapses it. On a child row, Left returns to the parent.
* **Enter** still opens the conversation.

The setting is **Combine conversations**, in Connected apps, and it is on by
default. With it off, the list is the same as before.

Code: `policy/HubConversationRollup.kt`.

## Mail apps

Hub has no INTERNET permission and stays offline, so it has no Gmail or Outlook
API logins. Instead it reads mail apps' notifications, as it does for chat apps.
A source counts as mail when either of these is true:
* it is a known mail app: Gmail, Outlook, Proton Mail, Yahoo Mail, Spark,
  FairEmail, K-9, Thunderbird, Samsung Email, BlueMail, Tuta, Fastmail, Sable
  Mail or AOSP Email;
* its notification has category `email`.

To support this, the history cache (schema v3) stores each notification's
category. Rows from v2 are kept, with no category.

Mail sources get an inbox **Mail** filter on **E** and appear in the dock. In
Connected apps, mail apps are listed near the top and marked "Mail". Each one
still has to be turned on by hand.

The screen also carries a note about duplicates, and warns when two mail apps
are on. The guidance is:
* Use one app per account.
* Sable Mail covers Gmail, Outlook, Yahoo, iCloud and IMAP accounts.
* Use another app only for accounts Sable Mail can't open, such as Proton, Tuta
  or managed work accounts.

An account signed in to two mail apps appears twice. Combining may put the two
copies next to each other, but it does not remove either.

Code: `policy/HubMailSources.kt`.

## What was checked

* Hub unit tests (119), Android Lint, detekt and ktlint.
* `tests/run.sh`.

Device behaviour is gate `Q4-HUB-DOCK` in [QUALIFICATION.md](../QUALIFICATION.md).
