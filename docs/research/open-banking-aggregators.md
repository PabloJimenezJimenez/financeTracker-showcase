# Open-banking aggregators: free access to Revolut, Trade Republic and ImaginBank

Research for issue #8. All sources checked on **2026-10-20**. Every claim cites its source; where a page could not be read or a fact could not be confirmed, that is said.

## Answer

| Provider | Revolut (ES) | Trade Republic (ES) | ImaginBank | Free forever for a private person? | New sign-ups | Company / licence needed | Consent renewal | Freshness | Phone-only, no server? |
|---|---|---|---|---|---|---|---|---|---|
| **Enable Banking** (restricted production) | Yes, not beta [1] | Yes, **beta** [1] | Yes ("imagin", **beta**, personal only) [1] | **Yes, as written today.** "free of charge", production limited to Linked Accounts "for the personal use of private individuals" [2]. Terms can change and access is revocable [2] | Open (self-serve Control Panel) [2][4] | None for restricted mode; unrestricted is organisations only [3] | Revolut and CaixaBank/imagin: up to 180 days. Trade Republic: 90 days [1] | 4 background fetches a day per bank is common; no daily cap when the user is present (PSU headers) [5] | Technically yes. Puts an **RSA private key** on the phone, which the docs say not to do [6] |
| **GoCardless Bank Account Data** (ex-Nordigen) | Yes [7] | **No** [7] | **No** (CaixaBank only) [7] | Had a free tier (API still returns "402 Free usage limit exceeded" [8]) | **Closed:** "New signups for Bank Account Data are currently disabled" [9] | n/a (no sign-up possible) | Up to 180 days in the EEA [10] | 4 requests a day per account per scope [11] | GoCardless says to call it only from a back-end [12][13]. Would put `secret_id`/`secret_key` on the phone |
| **Tink** (Visa) | Yes [14] | **No** (not in ES, DE or FR lists) [14] | Yes [14] | **No.** No public free tier, and prices only for "existing customers who have an active business relation" [15] | Console sign-up is open, but sandbox only [16] | Production (even testing with real accounts) needs contact with sales plus KYB [16] | Up to 180 days, or 90 days depending on market and product [17] | 4 a day unattended (PSD2) [17] | `client_secret` would be on the phone. Not designed for this |
| **Salt Edge** | Yes [18] | **No** [18] | Yes [18] | **No.** "Test" status allows 100 live connections for 90 days, then needs "Live" [19]. Pricing page could not be read | Open for Pending/Test | Live status is commercial (not confirmed: pricing page returned 404) | Not confirmed | Not confirmed | `App-id` + `Secret` in every request. Signing key "must be securely stored on the Client's server" [19] |
| Yapily, TrueLayer and other B2B aggregators | not checked | not checked | not checked | No public free tier. Yapily pricing is aimed at businesses [20]. TrueLayer pricing page returned 404 | — | Business | — | — | — |

**Bottom line.** Only **Enable Banking** meets the "free forever, private individual, no company" bar today, and it is also the only provider whose public bank list covers **all three** banks in Spain: Revolut, Trade Republic (beta, 90-day consent) and imagin (beta, 180-day consent). Its Terms of Service (last updated 2026-01-09) say that restricted production use "is free of charge" and allowed "for the personal use of private individuals" on accounts you link yourself. The same terms say the right is "limited, revocable", that functionality may be discontinued "at any time", and that the terms may change. So "forever" is the current policy, not a guarantee. GoCardless is closed to new sign-ups and lacks Trade Republic and imagin. Tink and Salt Edge are commercial products that need KYB or a contract and also lack Trade Republic. Compared with bank-app notifications, Enable Banking gives you a complete, booked transaction ledger with IBANs, balances and history (usually 90 days or more after the first hour, often longer). The costs:
- It is not real time: about 4 background pulls a day per bank, though more are allowed while the app is open.
- You must re-authenticate through each bank's SCA every 180 days (every 90 days for Trade Republic).
- Your transaction data goes through a third party (Enable Banking Oy, a Finnish licensed AISP).
- A phone-only build has to hold the app's RSA signing key on the device, against Enable Banking's own advice. The risk can be reduced by generating the key in Android Keystore (non-exportable) and uploading only the public certificate. That approach is an inference and was not tested.

A sensible design is to keep notifications for instant capture and add Enable Banking as an optional reconciliation source.

## Details per provider

### Enable Banking (Enable Banking Oy, Finland)

**Free tier and licensing.** The Terms of Service at https://enablebanking.com/terms ("Last updated: January 9, 2026") include these clauses. The page is a JavaScript app, so the text was read from its bundle, https://enablebanking.com/js/app.957d223a.js [2].
- "Use of the Control Panel and the API under these Terms is free of charge." It adds: "No fees will be applied retroactively without prior notice."
- Restriction of Use: "Use of the API in the Production Environment is limited to Linked Accounts and is available solely for evaluation purposes or for the personal use of private individuals. The number of Linked Accounts per Application may be limited." No number is given, so the limit is unconfirmed.
- The terms grant no right to use the API for business purposes, for "accessing account information that does not belong to the Control Panel user who associated the Linked Accounts", or for "any commercial purpose".
- They grant only a "limited, revocable right". They also say "Enable Banking may modify or discontinue any functionality at any time" and "Continued use ... after changes constitutes acceptance of the updated Terms."
- Enable Banking acts as the authorised AISP, so you need no licence of your own. The terms link the EBA register entry https://euclid.eba.europa.eu/register/pir/view/PSD_AISP/FI_FIN_FSA!29884997 (not opened).

The Control Panel UI text (same bundle) says: "unrestricted access in the production environment is available only to organisations. Individuals can activate applications by linking their accounts and may continue using them in restricted mode, adding more linked accounts if needed." [3]

The linked-accounts doc describes activation as a way to evaluate "before a contract for full production usage ... has been signed, as well as individual non-commercial use". It says the app stays restricted "until an agreement has been signed" [4]. In restricted mode the API returns only linked accounts and strips any others. Every account you want must therefore be linked in the Control Panel first [5].

The FAQ says commercial pricing is volume-based with a monthly minimum and "When getting started you can use sandbox environment or link own accounts to your production application" [5].

Registering a production app requires a description, a GDPR contact email, and privacy-policy and terms URLs [6]. The FAQ says these are needed for the end-user consent screen [5]. A personal app needs some placeholder pages for these.

**Coverage** comes from the public ASPSP list at https://enablebanking.com/api/aspsps, filtered to country ES [1]:

| Name | BIC | `maximum_consent_validity` | beta | PSU types |
|---|---|---|---|---|
| Revolut | REVOLT21 | 15,552,000 s = 180 days | false | business, personal |
| Trade Republic | TREBDED1XXX | 7,776,000 s = 90 days | **true** | business, personal |
| imagin | CAIXESBBXXX | 180 days | **true** | personal |
| CaixaBank | CAIXESBBXXX | 180 days | false | business, personal |

The FAQ explains the beta flag: "all new integrations have the beta flag ... the flag does not mean that the connection would be unstable" [5]. The Spain market page notes that CaixaBank uses the CaixaBankNow app for SCA, "supporting automatic app switch on mobile devices" [21]. The page does not say whether imagin works the same way.

**Consent.** The `valid_until` value is capped at now plus `maximum_consent_validity` for each bank. The FAQ says the consent "only needs to be renewed once the valid_until date-time is reached", and token refresh is handled internally [5][6].

**Freshness and history.**
- "many ASPSPs have the limit of 4 times a day for data fetches when PSU ... is not online". When PSU headers are sent (the user is present), limits on background fetching "do not apply" [5].
- Full history is often available only for about one hour after the first authorisation. After that, many banks restrict transaction data to the past 90 days. "Most ASPSPs provide access to at least one year." [5] So import the full history right after linking.
- The API marks transactions as booked or pending (`transaction_status`) and provides `entry_reference` for de-duplication [6].
- Real-time push is not offered for AIS. Webhooks are documented only for payment status [6].

**Phone-only use.** Every call is authorised with a JWT signed with RS256 using the application's private RSA key. The JWT carries `kid` (the app ID), has `iss`/`aud` set to `enablebanking.com`/`api.enablebanking.com`, and lives for at most 24 hours [6].

The docs say: "keep the private key in secret (e.g. don't expose it to client, share with anyone nor embed into mobile or other apps intalled to user devices)" [6]. The FAQ adds that authorisation tokens "can't be shared with end users" [5]. That advice is about multi-user apps. Here the only user is the account owner, and restricted mode limits the key to the owner's own linked accounts [2][5].

Mitigation (an inference, not tested): applications are registered with a "certificate or public key" [6]. Android Keystore can generate a non-exportable RSA key and sign RS256 (SHA256withRSA) on the device (https://developer.android.com/privacy-and-security/keystore, not re-read for this note). Only the public certificate would leave the phone, and the app would sign short-lived JWTs locally.

Other points for a phone-only build:
- No CORS issue applies because this is not a browser.
- The authorisation redirect must go to one of the app's registered `redirect_urls` [6]. Whether a custom scheme or an Android App Link is accepted was **not confirmed**.
- The FAQ warns that opening the bank auth URL in a WebView can break the switch to the bank app, so use Chrome Custom Tabs or the default browser [5].

### GoCardless Bank Account Data (formerly Nordigen)

**Sign-ups.** https://bankaccountdata.gocardless.com/new-signups-disabled states: "New signups for Bank Account Data are currently disabled." [9] This rules it out for a new user. No end date or reopening plan was found.

**Free tier.** The documented error code "402 Free usage limit exceeded" on agreement and requisition creation shows that a metered free tier existed [8]. The former pricing page (https://gocardless.com/bank-account-data/pricing) returns 404, and its limits could not be confirmed from a primary source. Secondary sources mention 50 bank connections a month, but this was not used.

**Coverage.** The official coverage spreadsheet is reached from https://gocardless.com/bank-account-data/coverage/, which redirects to a Google Sheet [7].
- Revolut: `REVOLUT_REVOLT21`, countries include ES, with 730 days of history.
- CaixaBank: `CAIXABANK_CAIXESBB`, 730 days. It "Requires two requisitions for gathering historical data and having continuous access".
- **No imagin row and no Trade Republic row.**

**Consent.** `access_valid_for_days` can be up to 180 days in the EEA since the RTS Art. 10a change of 25 July 2023 [10]. The overview page still says "up to 90 days" [12].

**Freshness.** "four requests per day per account for each access scope" (details, balances, transactions) [11]. The overview says "down to 4 API calls per day for every account" [12].

**Phone-only use.** `secret_id` + `secret_key` are exchanged for access and refresh JWTs [8]. GoCardless says to call the API "not from the front-end but from your own secure back-end, otherwise you might expose your credentials" [13]. It also says to "store all credentials and tokens server-side exclusively" [22].

### Tink (Visa)

**Coverage.** The public provider endpoint https://api.tink.com/api/v1/providers/ES lists "Revolut" (`es-revolut-ob`), "CaixaBank" (`es-redsys-caixabank-ob`) and "Imagin" (`es-redsys-imaginbank-ob`). All are OPEN_BANKING, PERSONAL, ENABLED [14]. **No Trade Republic** appears in the ES, DE or FR lists [14].

**Pricing and access.**
- The pricing page has no prices. It says prices are "applicable exclusively to our existing customers who have an active business relation with us" [15].
- The Console docs say all products can be tested in the sandbox. "When you're ready to create a production app, contact sales ... After you've gone through the Know Your Business process, we'll create and configure the app for you" [16].
- Even "pre-production" testing with your own real credentials uses a production app [16].
- The docs pages are a JavaScript app. Their text was read through the site's own public Contentful delivery endpoint, which the page itself uses.

**Not free for individuals.**

**Consent and freshness.** AIS consent is valid "for up to 180 days" and allows access "whenever the user actively requests that data and up to 4 times per" day otherwise [17]. Tink's continuous-access guide still says 90 days is the maximum [17].

**Phone-only use.** Uses `client_id`/`client_secret` [17]. A client secret would sit on the device. Not evaluated further because the provider is commercial-only.

### Salt Edge

**Coverage.** Public provider search on saltedge.com [18]:
- Revolut ES: `revolut_oauth_client_es`
- CaixaBank: `caixabank_oauth_client_es`
- imaginBank: `imagin_bank_oauth_client_es`
- Trade Republic: **none**

**Access tiers** (https://docs.saltedge.com/v6/) [19]:
- Pending: fake providers only, up to 10 connections.
- **Test**: live providers, "limited to 100 Connections", and lasts **90 days**.
- Live: no restrictions.

So Salt Edge has no permanent free tier for real banks. The pricing page could not be found (several saltedge.com pricing URLs return 404). A search snippet mentioned a free "Basic" plan for a "limited period", but this was not confirmed on a primary page.

**Phone-only use.** `App-id` and `Secret` headers go on every request. Request signing is required for Live clients, and the private key "must be securely stored on the Client's server" [19].

### Others

- **Yapily** pricing targets businesses ("Tailored pricing for every business", "Talk to an expert"). No free tier was seen [20].
- **TrueLayer**: https://truelayer.com/pricing/ returned 404 and was not confirmed.
- An independent overview, https://www.openbankingtracker.com/guides/free-open-banking-apis, returned HTTP 429 and was not read.
- No other EU aggregator with a published free plan for private individuals was found.

### Regulatory background

PSD2 RTS (Delegated Regulation (EU) 2018/389) Art. 36(5) limits unattended AIS access to 4 times per 24 hours. Art. 10a, added by Delegated Regulation (EU) 2022/2360, extends the SCA-exempt access period to 180 days from 25 July 2023. EUR-Lex (https://eur-lex.europa.eu/eli/reg_del/2022/2360/oj, https://eur-lex.europa.eu/eli/reg_del/2018/389/oj) returned an HTTP 202 bot challenge and **could not be read**. These facts are cited here through the providers' own docs [5][10][11][17].

## Sources

1. Enable Banking public ASPSP list, JSON: https://enablebanking.com/api/aspsps (also `?country=ES`)
2. Enable Banking Terms of Service, last updated 2026-01-09: https://enablebanking.com/terms. Text read from https://enablebanking.com/js/app.957d223a.js
3. Enable Banking Control Panel UI text, same bundle: https://enablebanking.com/js/app.957d223a.js
4. Enable Banking, "Whitelisting own accounts for restricted API usage in production": https://enablebanking.com/docs/api/linked-accounts/
5. Enable Banking FAQ: https://enablebanking.com/docs/faq/
6. Enable Banking API reference: https://enablebanking.com/docs/api/reference/
7. GoCardless Bank Account Data coverage sheet, linked from https://gocardless.com/bank-account-data/coverage/: https://docs.google.com/spreadsheets/d/1EZ5n7QDGaRIot5M86dwqd5UFSGEDTeTRzEq3D9uEDkM
8. GoCardless Bank Account Data API endpoints: https://docs.gocardless.com/bank-account-data/endpoints
9. GoCardless, "New signups disabled": https://bankaccountdata.gocardless.com/new-signups-disabled
10. GoCardless help centre, "EEA 180-day access": https://bankaccountdata.zendesk.com/hc/en-gb/articles/11528922309660-EEA-180-day-access
11. GoCardless help centre, "Bank API Rate Limits and Rate Limit Headers" (updated 2025-12-29): https://bankaccountdata.zendesk.com/hc/en-gb/articles/11529584398236-Bank-API-Rate-Limits-and-Rate-Limit-Headers
12. GoCardless Bank Account Data overview: https://docs.gocardless.com/bank-account-data/overview
13. GoCardless help centre, "How to fix CORS error?": https://bankaccountdata.zendesk.com/hc/en-gb/articles/11529110749724-How-to-fix-CORS-error
14. Tink public provider lists: https://api.tink.com/api/v1/providers/ES (also /DE, /FR)
15. Tink pricing: https://tink.com/pricing/
16. Tink Docs, "Set up your Tink Console account": https://docs.tink.com/resources/landing/set-up-your-tink-account (also https://tink.com/scale/)
17. Tink Docs, "Introduction to PSD2", "Added support for 180-day consent", "Continuous access to an account" and "Make your first API call" (docs.tink.com, read through the site's Contentful content)
18. Salt Edge provider search, used by https://www.saltedge.com/products/account_information/coverage/spain: https://www.saltedge.com/backend/v1/providers/search?query=revolut (also `caixabank`, `imagin`, `trade`)
19. Salt Edge API v6 docs: https://docs.saltedge.com/v6/
20. Yapily pricing: https://www.yapily.com/pricing
21. Enable Banking, Spain market notes: https://enablebanking.com/docs/markets/es/
22. GoCardless help centre, "Where should I store access tokens?": https://bankaccountdata.zendesk.com/hc/en-gb/articles/11529146050588-Where-should-I-store-access-tokens
