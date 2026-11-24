# Bank app notifications on Android 17: Revolut, Trade Republic, ImaginBank

Research for GitHub issue #9 (PabloJimenezJimenez/financeTracker). Researched 2026-10-20.
Target: one sideloaded APK on a Google Pixel 8 running Android 17. All data stays on the device.

Evidence labels used below:
- **[P]** primary source: Android/AOSP docs or code, the bank's own help centre, app store listing or press release.
- **[S]** secondary source: forum posts or open-source projects that parse these notifications. Treat it as an indication, not a fact.
- **[?]** not confirmed by any source found.

## Answer

### Revolut (`com.revolut.revolut`)

| Movement | Notified? | Evidence |
|---|---|---|
| Card payments (physical, virtual, Google Pay) | Yes, "instant spending notifications to keep track of every payment" | [P] Play listing |
| Refunds, cashback | Yes, appear as their own notifications ("refund" / "cashback" in the title) | [S] ynab-notif-import, Finances_Android |
| Incoming transfers | Yes, "we aim to notify you as soon as the transfer arrives" | [P] help centre (search excerpt only) |
| Outgoing transfers / P2P | Probably ("You sent ... to X") | [S] Finances_Android parser |
| Upcoming payments / subscriptions | Notified *beforehand* (a reminder, not the debit itself) | [P] Play listing |
| Direct debits (the actual debit) | Not confirmed | [?] |
| Savings interest | Not confirmed. The help centre describes interest accrual and payout, not a notification | [?] |
| Trades (stock orders filled) | Not confirmed. Only price alerts ("Trading volatility alerts") are documented | [P] help centre / [?] |

**What the user must enable:** transaction and transfer notifications cannot be turned off inside the Revolut app; they are controlled only in Android Settings > Apps > Revolut > Notifications. The in-app "Notification settings" only covers marketing and promotions [P]. So: allow Revolut's notification permission and leave its transaction channels on.

**What the text contains** [S, captured by third-party projects]:
- Title = merchant name; body = amount and currency, sometimes with a balance line. Example (Hungarian account, English UI): title `Coop`, body `You spent HUF804` / `HUF balance: HUF42,285.97`.
- Spanish UI (inferred from a regex, not a captured sample): `Has pagado 4,50 € en <merchant>` followed by a category emoji.
- Polish UI (2021): title `Backblaze`, body `💼 Zapłacono 1,11 USD (11,11 zł) w: X`. A foreign-currency payment shows both the original amount and the converted amount, with a non-breaking space before the currency.
- Wording depends on the app language and has changed over time (one project had to rewrite its Hungarian regex after Revolut changed the verb). No card suffix or account ID appears in the samples. The balance line names the currency pocket, which is the only hint of which account was used.

**Confidence:** medium. Card payments are well supported; transfers likely; direct debits, interest and trades unknown.

### Trade Republic (`de.traderepublic.app`)

| Movement | Notified? | Evidence |
|---|---|---|
| Card payments | Yes: "a push notification for every card transaction, including declined transactions" | [P] support 1668 |
| Declined ATM withdrawals | Yes | [P] support 1665 |
| Interest payout (monthly) | Yes: a push notification on the payout day, plus an entry in Cash > Transactions | [P] support 1534 |
| Incoming transfer / deposit | Reported ("the money has arrived, you can invest now") | [S] traderepublic.community forum |
| Savings plan executions | Reported (a push, then details in the profile) | [S] traderepublic.community forum |
| Trades / order executions | Contractually sent to the in-app mailbox (timeline); whether a push accompanies it is not documented | [P] Customer Agreement / [?] |
| Outgoing transfers, direct debits, Saveback, round-up | Not confirmed | [?] |
| Price alerts | Yes (not a transaction) | [P] support 746 |

**What the user must enable:** Android notification permission for the app. No official page describes Trade Republic's in-app notification toggles. One forum user reports that advertising pushes have a separate toggle from transactional pushes [S].

**What the text contains:** no official or captured example text was found [?]. One open-source Spanish budgeting app classifies Trade Republic notifications by the sign alone: `+` means income, no sign means expense. This suggests incoming money is shown as `+25,00 €` and card payments as an unsigned amount [S, the owner's own observation in Mi-Presupuesto T-048]. Merchant presence is unconfirmed.

**Confidence:** high for card payments and interest; low for everything else. Trade Republic offers a CSV export of all transactions (used by the same project), which can fill the gaps [S].

### imagin / ImaginBank (`com.imaginbank.app`, CaixaBank)

The Play listing is now titled "imagin: Mobile Bank, Finances" [P].

| Movement | Notified? | Evidence |
|---|---|---|
| Card purchases | Push alerts exist for card purchases. Threshold unknown for imagin; CaixaBank's own card alert service by default flags operations **over €500** | [P] 2016 launch press release; [P] CaixaBank card-security page |
| ATM withdrawals | CaixaBank default: withdrawals **over €1,000** | [P] CaixaBank (applies to CaixaBank; not confirmed for imagin) |
| First purchases abroad | CaixaBank default: notified regardless of amount | [P] CaixaBank |
| Account movements (incoming transfers, payroll) | "movimientos de la cuenta" alerts exist; payroll alert reported | [P] 2016 press release; [S] helpmycash |
| Direct debits (recibos) | "cargos de recibos" alerts exist; a "receipt above an amount you define" alert is reported | [P] 2016 press release; [S] helpmycash |
| Bizum, interest, investments | Not confirmed | [?] |

**What the user must enable:** alerts are opt-in. The app shows an alert-activation screen on first login; afterwards go to Menu > Settings > Manage alerts. Users coming from CaixaBank or Bankia must set them up again [P imagin FAQ]. Forum users report that notifications reach only one device: the last one where "Recibir notificaciones" was enabled [S]. **Turn on every movement alert and set any amount threshold to the minimum; otherwise small card payments will not be notified.**

**What the text contains:** no published or captured example text was found [?]. Expect Spanish text with amount in `EUR`/`€`, probably a merchant, possibly a card suffix (other Spanish banks such as Openbank use `pago con tu tarjeta **1234 el 12/03 10:15 por 4,50 EUR en <merchant>`, [S]). Capture real samples before writing the parser.

**Confidence:** medium that alerts exist and can cover cards, account movements and direct debits; low on thresholds and wording. The most specific official sources are CaixaBank's (not imagin's) and the imagin launch note from 2016.

### Bottom line

Yes, but only as a best-effort feed, not a complete ledger. Android 17 still lets a sideloaded app read every bank notification. The cost is one extra "Allow restricted settings" step before granting Notification access, and the system rebinds the listener after reboot. Coverage differs by bank:
- **Trade Republic:** the only bank that officially promises a notification for every card transaction, plus monthly interest.
- **Revolut:** promises card-spend notifications and arrival of incoming transfers. Its text gives amount, currency and merchant.
- **imagin:** alerts are opt-in and CaixaBank's documented defaults are amount thresholds (over €500 card, over €1,000 ATM), so the owner must enable each alert at the lowest amount.

No source documents notifications for direct debits at Revolut or Trade Republic, for trades at any of the three, or for Saveback or round-ups. No bank publishes its notification wording, so the parser must be built from samples captured on the owner's phone.

No source quantifies how many movements go un-notified. The documented gaps above, plus push delivery not being guaranteed, mean a periodic reconciliation (CSV export or balance check) is needed.

## Android platform (Android 13 to 17, Pixel 8)

### 1. Declaring and granting the listener
- Declare a `<service>` protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` with an intent filter for `android.service.notification.NotificationListenerService`. Optional meta-data `default_filter_types` / `disabled_filter_types` set which types (conversations, alerting, silent, ongoing) the listener receives by default [P NotificationListenerService reference].
- No runtime permission dialog exists. The user grants "Notification access" in Settings. The app can open the per-app page with `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (API 30+) or the list with `ACTION_NOTIFICATION_LISTENER_SETTINGS`. It checks the result with `NotificationManager.isNotificationListenerAccessGranted(ComponentName)` (API 27+) [P Settings, NotificationManager references].
- Device implementations are "STRONGLY RECOMMENDED" to let the user choose, per listener, which notification types (conversations, alerting, silent, important ongoing) are bridged, and to exclude specific apps [P CDD 17 §3.8.3 C-SR-1/2]. If the owner unticks "silent" or excludes a bank app on that page, the listener never sees it.

### 2. Restricted settings for a sideloaded APK
- The Android 17 CDD (§9.8, unchanged from 15 and 16) requires "Restricted Settings" for apps installed from a downloaded file (`PACKAGE_SOURCE_DOWNLOADED_FILE`) or a local file (`PACKAGE_SOURCE_LOCAL_FILE`). Notification listener access (`OPSTR_ACCESS_NOTIFICATIONS`) is on the enforced list. Restricted Settings must be on by default, and user confirmation may only be obtained from the app's App info page through `EnhancedConfirmationManager` [P CDD 17].
- AOSP's `EnhancedConfirmationService` (main branch) treats an app as guarded in three cases: (1) its package source is local or downloaded file; (2) no installer is recorded, as with a plain `adb install`; (3) its installer is neither preinstalled nor on the device's trusted-installer allowlist. The exception: if the device configures no trusted installers, everything is trusted [P AOSP code]. Whether the Pixel 8 build ships a trusted-installer list, and so whether an `adb install` is guarded, could not be confirmed. **Plan for the restricted-settings step either way.**
- What the user does: open Notification access and tap the app. A "Restricted setting" dialog appears. Then go to Settings > Apps > the app > ⋮ (More) > **Allow restricted settings**, confirm (PIN or biometric), and return to Notification access to enable it [P Google Android Help]. This is a one-time step per install. Reinstalling (not updating) resets it.
- Related Android 17 items:
  - Advanced Protection Mode, when the owner turns it on, blocks installs from unknown sources [P Android 17 features].
  - Google's developer verification requirement begins on 2026-09-30 in Indonesia, Singapore and Thailand and goes global in 2027. Even then, ADB installs of one's own builds remain allowed, and a free "limited distribution" account covers up to 20 devices [P Android Developer Console help]. Spain is not affected in 2026.

### 3. What a listener receives
- **`StatusBarNotification`** [P reference]:
  - `getPackageName()` (use this to route by bank) and `getOpPkg()`
  - `getPostTime()` (when it was posted; may differ from `Notification.when`)
  - `getKey()` (unique per record; good for de-duplication), `getId()`, `getTag()`
  - `getUser()` / `getUid()`
  - `getGroupKey()`, `getOverrideGroupKey()`, `isGroup()`, `isAppGroup()`
  - `isOngoing()`, `isClearable()`
  - `getNotification()`
- **`Notification`**: text lives in `extras`: `android.title`, `android.text`, `android.bigText`, `android.subText`, `android.textLines`. Also available: `getChannelId()`, `category`, `when` and `actions` [P Notification reference]. Community parsers read title, text and big text, and skip group-summary notifications, which otherwise produce empty "transactions" [S Finances_Android].
- **Updates:** callbacks are `onNotificationPosted` / `onNotificationRemoved` / ranking changes. An update re-delivers the same key, so de-duplicate by key and content.
- **Custom views:** Android 17 adds stricter memory checks for notifications that use custom views [P Android 17 summary]. A bank notification drawn with custom `RemoteViews` may have little or no text in `extras` [?, not observed for these three apps].

### 4. Things that hide or redact content
- **OTP redaction (Android 15+):** untrusted listeners receive a redacted copy when the system detects an OTP. The title becomes the app label, the text a generic "sensitive" string, and subtext and action titles are blanked [P Android 15 behaviour changes; AOSP `NotificationManagerService.redactStatusBarNotification`]. The CDD makes this mandatory for every listener except system or uid<10000 apps, System UI, Shell, companion-device apps and holders of the automotive-projection, notification-intelligence or HOME roles [P CDD 17 §3.8.3.4]. A sideloaded app is untrusted. Transaction notices should not be flagged, but a misdetected digit string would arrive redacted [?]. Treat a redacted payload as "unparseable", never as zero.
- **Blocked notifications are never delivered.** If the bank app's notifications or the relevant channel are turned off, `NotificationManagerService` drops the record before any listener sees it [P AOSP code]. The bank app also needs the Android 13+ `POST_NOTIFICATIONS` permission. Silenced notifications are still delivered if the listener accepts the "silent" type.
- **Lock-screen privacy settings:** `VISIBILITY_PRIVATE` is documented as concealing content "on secure lockscreens" [P Notification reference]. The docs do not say it affects listeners, and the CDD requires listeners to get notifications "in their entirety" [P CDD 17 §3.8.3.2]. So it should not reduce what the app sees (inference).

### 5. Battery, background and reliability
- **No foreground service or job is needed.** The system binds enabled listeners itself with `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE | BIND_NOT_PERCEPTIBLE | BIND_ALLOW_WHITELIST_MANAGEMENT`, which starts the app process on demand [P AOSP code]. `BIND_NOT_PERCEPTIBLE` was added "because too many 3P apps could be kept in memory as notification listeners", so the process can still be killed under memory pressure.
- **After a crash or kill:** when the binding dies, the system unbinds and schedules one rebind after 10 s (`ON_BINDING_DIED_REBIND_DELAY_MS = 10000`). A second death before that rebind is not retried. The app can call `requestRebind(ComponentName)`, which fails if access is not granted. After `onListenerDisconnected()` the app gets no events until `onListenerConnected()` [P reference; AOSP code].
- **After reboot:** `ManagedServices.onUserUnlocked` → `rebindServices`, so the listener is rebound once the user unlocks after boot. No `BOOT_COMPLETED` receiver is needed; nothing arrives before first unlock [P AOSP code].
- **Force stop:** on Android 15+ the app stays in the stopped state until the user opens it again [P Android 15 behaviour changes]. Whether the system rebinds a force-stopped listener was not confirmed [?]. Tell the owner not to force-stop the app.
- **Missed while disconnected:** a listener sees only what is posted while it is connected. On (re)connect, `getActiveNotifications()` returns notifications still in the shade [P reference]. Anything the owner dismissed meanwhile is lost; there is no third-party API to Android's notification history.
- **Job quotas:** JobScheduler and WorkManager runtime quotas tightened in Android 16 [P Android 16 behaviour changes]. Persist directly in the listener callback (a quick Room insert) instead of scheduling work.
- **Upstream delivery:** bank pushes arrive via FCM. Normal-priority messages may be held until the device leaves Doze; only high-priority messages are delivered immediately. FCM notes that slow processing can lead to "delayed or missing notifications" [P Firebase docs]. The bank, not the listener, controls this.
- **Battery usage "Restricted":** its effect on a system-bound listener was not confirmed for Android 17 [?]. Leave the app on "Optimized" or "Unrestricted".

### 6. Extra source worth listening to
Google Wallet posts its own "purchase notification" after each tap-to-pay transaction [P Google Wallet help]. One project captured `75. SZ. ABC ÁRUHÁZ` / `HUF804.00 with Revolut Mastercard ••1413` [S]. This gives a second, card-suffixed signal for Revolut and Trade Republic card taps. De-duplicate it against the bank's own notification.

## Sources

Android / AOSP (primary)
- NotificationListenerService reference: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- StatusBarNotification reference: https://developer.android.com/reference/android/service/notification/StatusBarNotification
- Notification reference (extras, visibility, channel): https://developer.android.com/reference/android/app/Notification
- Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS: https://developer.android.com/reference/android/provider/Settings
- NotificationManager.isNotificationListenerAccessGranted: https://developer.android.com/reference/android/app/NotificationManager
- Android 17 CDD (§3.8.3.1, §3.8.3.2, §3.8.3.4 sensitive notification protection, §9.8 restricted settings): https://source.android.com/docs/compatibility/17/android-17-cdd
- Android 15 and 16 CDD (same §9.8 text): https://source.android.com/docs/compatibility/15/android-15-cdd , https://source.android.com/docs/compatibility/16/android-16-cdd
- Android 15 behaviour changes (OTP redaction, stopped state): https://developer.android.com/about/versions/15/behavior-changes-all
- Android 16 behaviour changes (job quotas): https://developer.android.com/about/versions/16/behavior-changes-all
- Android 17 summary, behaviour changes and features (custom-view memory limits, SMS OTP, AAPM): https://developer.android.com/about/versions/17/summary , https://developer.android.com/about/versions/17/behavior-changes-all , https://developer.android.com/about/versions/17/behavior-changes-17 , https://developer.android.com/about/versions/17/features
- AOSP EnhancedConfirmationService (main): https://android.googlesource.com/platform/packages/modules/Permission/+/refs/heads/main/service/java/com/android/ecm/EnhancedConfirmationService.java
- AOSP ManagedServices (main; rebind on unlock, 10 s rebind after binding death): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/ManagedServices.java
- AOSP NotificationManagerService (main; listener bind flags, redaction, blocked-app suppression): https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationManagerService.java
- Google Android Help, allow restricted settings: https://support.google.com/android/answer/12623953
- Android developer verification: https://support.google.com/android-developer-console/answer/16561738
- FCM message priority: https://firebase.google.com/docs/cloud-messaging/android/message-priority
- Google Wallet purchase notifications: https://support.google.com/wallet/answer/12059878

Revolut
- Play listing (fetched 2026-10-20, updated Oct 20, 2026): https://play.google.com/store/apps/details?id=com.revolut.revolut
- Manage notifications (transaction alerts only via device settings). Direct fetch blocked by Cloudflare (HTTP 403); content seen only as search-engine excerpts: https://help.revolut.com/help/profile-and-plan/profile-plan/notifications/how-do-i-manage-my-notifications/ , https://help.revolut.com/en-ES/help/profile-and-plan/profile-plan/notifications/how-do-i-manage-my-notifications/
- Receiving a bank transfer ("we aim to notify you"), search excerpt only: https://help.revolut.com/help/transfers/inbound-transfers/receiving-a-bank-transfer/
- Trading watchlist and price alerts, search excerpt only: https://help.revolut.com/help/wealth/stocks/getting-started-with-trading/managing-your-trading-account/trading-alerts/managing-my-trading-watchlist-and-price-alerts/
- Savings interest, search excerpt only: https://help.revolut.com/en-US/help/app-features/savings-vaults/earning-interest-with-instant-access-savings/
- [S] Finances_Android, Revolut parser and docs with real samples: https://github.com/teppe21/Finances_Android/blob/HEAD/app/src/main/java/com/teppe21/finances/native/notification/parsing/SourceParsers.kt , https://github.com/teppe21/Finances_Android/blob/HEAD/docs/BANK_INTEGRATION.md
- [S] ynab-notif-import (title = payee, refund/cashback in title): https://github.com/jmoseley/ynab-notif-import/blob/HEAD/src/hooks/ynab.ts
- [S] mobile-apps-notifications-to-ynab (Polish Revolut sample, 2021): https://github.com/rogatty/mobile-apps-notifications-to-ynab/blob/HEAD/revolut.js
- [S] expense_toolkit (Spanish Revolut regex): https://github.com/ErmoGarcia/expense_toolkit/blob/HEAD/app/services/notification_parser.py
- [S] treasury-scribe PR #19 (Revolut wording change), seen only as a search excerpt and returned 404 when fetched: https://github.com/BenedekMolnarVito/treasury-scribe/pull/19

Trade Republic
- How can I keep my account secure? (every card transaction): https://support.traderepublic.com/en-de/1668
- When will I receive my interest payout?: https://support.traderepublic.com/en-de/1534
- Why was my withdrawal declined?: https://support.traderepublic.com/en-de/1665
- Can I use the app before I deposit money? (price alerts): https://support.traderepublic.com/en-de/746
- Customer Agreement (execution notices to mailbox): https://assets.traderepublic.com/assets/files/CA_NL-en.pdf
- Play listing: https://play.google.com/store/apps/details?id=de.traderepublic.app
- [S] Forum, push notifications (deposit arrival, savings plan): https://www.traderepublic.community/tags/push-benachrichtigung/ , https://www.traderepublic.community/tags/push-notifications/
- [S] Mi-Presupuesto T-048 (sign rule for TR notifications): https://github.com/JavierBerenguer/Mi-Presupuesto/blob/HEAD/docs/tasks/T-048.md

imagin / CaixaBank
- imagin FAQ, How can I register for alerts in imagin?: https://www.imagin.com/en/faqs/mobile-banking/enable-notifications
- imaginBank launch press release, 14 Jan 2016 (card purchases, account movements, direct debits): https://www.caixabank.com/docs/comunicacion/50412.pdf
- CaixaBank card security tips (CaixaBankAlert defaults: over €500, ATM over €1,000, first purchases abroad): https://www.caixabank.es/particular/tarjetas-bancarias/consejos-seguridad-tarjetas-bancarias-p.html
- CaixaBank, how to enable notifications in the CaixaBank app (account/card, security alerts): https://www.caixabank.es/particular/banca-digital/activate-notifications-caixabanknow-app.html
- Play listing: https://play.google.com/store/apps/details?id=com.imaginbank.app
- [S] HelpMyCash forum, notifications on one device only: https://www.helpmycash.com/preguntas/34855/notificaciones-aplicacion-imaginbank/
- [S] HelpMyCash CaixaBank page (payroll alert, receipt-above-amount alert): https://www.helpmycash.com/banco/caixa-bank/

### Could not be confirmed
- Exact notification wording for Trade Republic and imagin, and current Spanish wording for Revolut.
- Notifications for direct debits (Revolut, Trade Republic), trades (all three), savings interest (Revolut), Saveback and round-ups (Trade Republic), Bizum and interest (imagin).
- Whether imagin's card alerts default to CaixaBank's €500 threshold, and the minimum configurable amount.
- Whether a Pixel 8 on Android 17 treats an `adb install` as a restricted-settings "covered" app (it depends on the device's trusted-installer config).
- The effect of "Restricted" battery mode and of force-stop on a granted notification listener.
- Any figure for how many movements banks fail to notify.
