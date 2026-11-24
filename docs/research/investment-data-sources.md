# Investment data sources for Trade Republic and Revolut holdings

Research for issue #10. Researched 2026-10-20. Every claim cites a numbered source in the list at the end. Where only a third-party page or a search-index snippet was available, the text says so. Revolut's help centre, its legal pages, and EUR-Lex blocked direct fetching ("security check" or 403) during this research, so some Revolut facts come from search-index text of Revolut's own pages and are marked **[snippet]**.

## Answer

### Holdings: Trade Republic (TR)

| Option | Cost | Effort | Risk |
|---|---|---|---|
| **A. In-app CSV "Transaction export"** (shared into our app or picked from Files) | Free | Medium: one parser for a documented, stable-looking CSV. Holdings = sum of BUY/SELL/split rows per ISIN or crypto symbol. | Low. Official feature (since about April 2026), but there is no official TR page for it, so the column names come from an open-source importer's test files [3][4][5]. The user re-exports by hand to refresh. |
| B. PDF statements and settlement documents | Free | High: PDF text parsing | Low legal risk, brittle to parse [1][2] |
| C. Unofficial private API (pytr and similar) | Free | High: port the WebSocket protocol and login (AWS WAF token, 2FA) to Kotlin | **High.** TR's Customer Agreement clause 2.3 prohibits access "using access paths, programs and/or other interfaces not provided by Trade Republic", and breach allows extraordinary termination [6]. It also breaks whenever TR changes pinned values [7]. |
| D. PSD2 aggregator | Free (Enable Banking restricted mode) or n/a (GoCardless signups closed) | Medium | Covers only the **cash/payment account**, never the securities or crypto holdings [11][12][13][14] |

### Holdings: Revolut

| Option | Cost | Effort | Risk |
|---|---|---|---|
| **A. Stocks: "Account statement"** from Invest → More → Documents → Stocks (CSV/Excel/PDF; the PDF has a "Portfolio breakdown" with Symbol, ISIN, Quantity and Price) | Free | Medium. The PDF is the only format whose layout was confirmed (from a 2026 sample) [18]. The CSV column names could not be confirmed [16][17]. | Low; the user refreshes by hand |
| **B. Crypto: Revolut X API with a read-only key** (`GET /1.0/balances`). Revolut is unifying crypto balances between the main app and Revolut X [21][22][23]. | Free | Medium: Ed25519 request signing, key made in the Revolut X web app | Low to medium. It is an official API. Whether the unified balance is live for this user, and so whether main-app crypto shows up, is **unconfirmed**. |
| C. Crypto statement from Crypto → More → Documents | Free | Medium | Low; file format not confirmed [19] |
| D. Commodities (gold, silver and other metals in the app) | Free | Manual entry or per-transaction statements | No documented statement or export for commodities was found [20] |
| E. Open Banking / PSD2 | n/a | n/a | Revolut's Open Banking API is only for regulated TPPs holding eIDAS/OBIE certificates [24], and PSD2 covers payment accounts only [13][14]. Not usable for holdings. |
| F. Unofficial Revolut app API | n/a | n/a | No maintained project found. Revolut's Personal Terms forbid reverse-engineering its products [25]. |

### Current prices

| Asset | Recommended free source | Key? | Limits | Identifier |
|---|---|---|---|---|
| Crypto | **Revolut X public tickers** `GET https://revx.revolut.com/api/1.0/public/tickers` (EEA EUR pairs, same venue as Revolut). Fallbacks: **Kraken** public Ticker, or the **CoinGecko** Demo plan. | Revolut X: no. Kraken: no. CoinGecko: Demo key. | Revolut X public: 1 request/s [22]. Kraken: no published number found [27]. CoinGecko Demo: 100 calls/min and 10,000/month, attribution required [28][29]. | Ticker (`BTC/EUR`, `XBTEUR`) or CoinGecko id |
| EU stocks/ETFs (what TR and Revolut EU hold) | **No clean free official source found.** Best available: **Alpha Vantage** `GLOBAL_QUOTE` (end-of-day) with exchange suffix such as `.DEX` (XETRA) or `.LON` | Free key | 25 requests/day [30][31] | Ticker plus suffix. ISIN is not accepted. Map ISIN → ticker once with **OpenFIGI** (free; 25 req/min without key, 250 with) [35]. |
| US stocks/ETFs | Finnhub free (60 calls/min, US only, personal use) [32]; FMP Basic (250/day, US only) [33]; Twelve Data Basic (8/min, 800/day, US equities/ETFs) [34] | Free key | as listed | Ticker |
| Gold/silver spot | **Alpha Vantage `GOLD_SILVER_SPOT`** (`XAU`/`XAG`) [31] | Free key | Shares the 25/day budget | `GOLD`/`XAU`, `SILVER`/`XAG` |
| Platinum/palladium, other commodities | No free spot source confirmed. Alpha Vantage has WTI, Brent, copper and others, but not platinum or palladium [31]. | — | — | — |
| FX (USD → EUR) | **ECB euro reference rates** `eurofxref-daily.xml` | No | Daily at about 16:00 CET, "for information purposes only" [36] | ISO currency code |
| Unofficial (Yahoo Finance via yfinance-style endpoints) | — | No | Undocumented. Yahoo's API terms bar "excessive or abusive usage"; yfinance says it is not affiliated with Yahoo and is for research and education [37][38]. | Ticker such as `VWCE.DE` |

### Bottom line

For **holdings**, the only free, allowed and reasonably sturdy route is **file import of the brokers' own exports**. For Trade Republic that is the CSV "Transaction export" (from about April 2026). It has an ISIN or crypto symbol in `symbol`, plus `shares`, `price`, `amount`, `fee` and `tax`, so holdings can be rebuilt from it [3][4][5]. For Revolut stocks it is the monthly or custom-range Account statement, whose PDF lists the end-of-period holdings by ISIN [18]. For Revolut crypto, the official Revolut X API with a read-only key may expose the same balances as the main app once unified balances are live [21][23]. Unofficial APIs are a bad fit: Trade Republic's contract explicitly prohibits non-TR interfaces and allows termination for it [6], and PSD2 aggregators cannot see securities or crypto at all [13][14]. For **prices**, crypto and FX are easy and free (Revolut X public tickers or Kraken without a key; ECB rates). Gold and silver work through Alpha Vantage's free key. **European ETFs/stocks by ISIN are the weak spot:** every free official tier found is US-only or capped at 20 to 25 calls/day. So plan on one ISIN → ticker mapping via OpenFIGI, one daily end-of-day refresh via Alpha Vantage (25/day), and manual price entry as fallback, or accept the legal and stability risk of Yahoo's unofficial endpoints.

## Details

### 1. Trade Republic

**Official API.** None for retail customers was found. TR's Customer Agreement (version 06.07.01, Estonian edition in English, the only English edition that downloaded) says services "can only be used via this Application", plus Web Trading [6]. Clause 2.3 reads: "Any use of the features and services provided by Trade Republic by using access paths, programs and/or other interfaces not provided by Trade Republic outside the Application is prohibited. In case of violation of this prohibition, Trade Republic reserves the right of extraordinary termination according to Clause 10.2." [6] The German and Spanish editions returned 403. That the same clause is in them is likely but **unconfirmed**.

**Unofficial API (evidence only, not recommended).** `pytr` (pytr-org/pytr, MIT, latest PyPI release 0.4.10 on 2026-08-06) is an "unofficial" Python client for TR's private WebSocket API. It can show the portfolio, fetch prices, download all timeline PDFs and export transactions to CSV [7][8]. Login needs a 4-digit app/SMS code plus an AWS WAF token, or an opt-in push-approval flow. TR can change pinned app version, User-Agent and platform values, which breaks login until users override them [7]. Risks: (1) contract breach under clause 2.3 [6]; (2) frequent breakage; (3) for us, a full Kotlin re-implementation, since pytr is Python; (4) the app would hold the user's TR session credentials.

**Exports the app offers.**
- *Transaction export (CSV).* Several independent sources report a CSV export added around 15 to 17 April 2026 [3][9][10]. Path (from third parties; TR's own help article for it was not found): Profile/User settings → Statements → Transactions Export → pick start and end dates → Create → save or share [9]. A Portfolio Performance forum user saw only a PDF option on app 4.2616.1 and got the CSV after updating to 4.2616.2 [5]. getquin says it is also available from the web app from 2026-04-15 [10]; **unconfirmed** by TR. On Android the user can share the file straight into our app (share intent) or save it and pick it with the system file picker [39][40].
- *CSV format* (from Portfolio Performance's importer test fixtures, updated 2026-10-19 [4]). Header:
  `datetime,date,account_type,category,type,asset_class,name,symbol,shares,price,amount,fee,tax,currency,original_amount,original_currency,fx_rate,description,transaction_id,value_date,counterparty_name,counterparty_iban,payment_reference`
  Delimiter is `,` or `;` and fields may be quoted (fixtures have both). The importer detects the file by the presence of `datetime`, `category`, `type`, `amount` and `currency` [4].
  - `category` is one of `TRADING`, `CASH` or `CORPORATE_ACTION`. `type` includes `BUY`, `SELL`, `DIVIDEND`, `INTEREST_PAYMENT`, `CARD_TRANSACTION`, `TRANSFER_*`, `SPLIT`, `FINAL_MATURITY` and others. `asset_class` is one of `STOCK`, `FUND`, `CRYPTO`, `BOND`, `DERIVATIVE` or `PRIVATE_FUND` [4].
  - For securities, `symbol` holds the **ISIN** (e.g. `DE0005557508`). For crypto it holds the **ticker** (e.g. `BTC`, `ETH`). `shares` and `price` are given per trade, so current holdings are the net of buys, sells and corporate actions [4]. The file covers the securities and cash accounts and includes FX, taxes and fees [5].
  - The file has no current market price and no explicit "positions" snapshot. Holdings must be rebuilt from history, so the user should export "all time".
- *PDFs.* Annual tax report under Profile → Tax [1]. Per-trade settlement documents under Cash → Transactions → trade → Documents [2]. The crypto-trades overview is sent yearly to the profile [2b].

**PSD2.** As a bank, TR holds a cash account with an IBAN. Whether it is reachable through Enable Banking was **not confirmed** (the ASPSP list needs auth; open-banking trackers list no integration [15]). Even if it is, PSD2 access-to-account covers *payment accounts*, defined as accounts "used for the execution of payment transactions" (PSD2 Art. 4(12), as restated by the EBA [13][14]). Securities custody and crypto holdings are outside it.

**Commodities at TR.** No TR source about how commodities appear in the CSV was found. The `asset_class` values seen in the fixtures do not include a commodity class, so ETCs probably appear as `FUND`/`STOCK` or `DERIVATIVE` with an ISIN. That is **unconfirmed**.

### 2. Revolut

**Official APIs.**
- *Open Banking API* (accounts, balances, transactions): "if you are a regulated third party provider". Production needs an eIDAS or OBIE certificate [24]. This is not available to a personal app, and it covers payment accounts only.
- *Business API*: business accounts only, not relevant.
- *Revolut X Crypto Exchange REST API*: base `https://revx.revolut.com/api/`. Each key maps to a Business or **Retail** user. The key is created by pasting an Ed25519 public key on the API keys page of the Revolut X web app. Requests are signed with headers `X-Revx-API-Key`, `X-Revx-Timestamp` and `X-Revx-Signature` [22]. `GET /1.0/balances` returns available, reserved, staked and total per currency. It allows 100 req/s and 1,000 req/min [21][22]. Keys can be read-only [23-snippet]. Public endpoints (`/1.0/public/tickers`, candles, order book) need no key and allow 1 req/s [22]. A live check on 2026-10-20 returned 770 pairs across `EEA` and `UK` regions, including `BTC/EUR` and `ETH/EUR`, and no XAU/XAG pairs [22-checked].
- *Unified crypto balance*: Revolut's help centre says crypto holdings are merged so the main app and Revolut X show one balance, "automatic" once launched. Tokens X does not support are visible but not tradable there [23-snippet]. The launch date and regional availability are **unconfirmed**. If it is live for the owner, a read-only Revolut X key gives the main-app crypto holdings through an official API.

**Unofficial API.** No maintained retail Revolut client was found. The Personal Terms (Revolut Bank UAB) say the customer "must not reverse-engineer any of our products" [25], and the Singapore terms also ban robots and spiders [26-snippet]. Treat this as not viable.

**Exports.**
- *Stocks*: Invest → More → Documents → Stocks → Account statement, Profit & Loss, or Costs and Charges (annual) [16-snippet]. The account statement contains "transaction history, starting and ending balances, and a portfolio snapshot at the end of the reporting period" [16-snippet]. EU (RSEUAB) accounts get statements at least quarterly [16-snippet]. Third parties report CSV and Excel choices, and Navexa says to pick "All time" and CSV [17]. The CSV header could **not** be confirmed from a primary source. A 2026 PDF sample (in Portfolio Performance's test data) has an "EUR Portfolio breakdown" table with `Symbol Company ISIN Quantity Price Value % of Portfolio` and a transactions table with `Date Symbol Type Quantity Price Side Value Fees Commission` [18]. Positions with ISIN and quantity can therefore be read directly from the statement, without rebuilding history.
- *Crypto*: Crypto → More → Documents → pick type and timeframe → generate [19-snippet]. The format (PDF/Excel/CSV) is **not stated** on the help page. Statements include both main-app and Revolut X activity by default [23-snippet].
- *Commodities*: buys and sells show in the app with "proof of your transaction" [20-snippet]. No commodities statement or export was documented.
- *Currency (cash) accounts*: PDF or Excel statements [19b-snippet].

On Android, all these are generated in the Revolut app and saved or shared as files. Our app takes them through the share sheet or the system file picker [39][40].

### 3. PSD2 aggregators (free, no own server)

- **GoCardless Bank Account Data** (ex-Nordigen, the classic free option): "New signups for Bank Account Data are currently disabled." [11]
- **Enable Banking**: restricted mode lets you activate a production app "by linking your own accounts", and "only linked accounts would be accessible" [12]. Its FAQ describes the service as "payment account information" and says nothing about investment or crypto accounts. Auth tokens are JWTs signed with the app's RSA private key. Consent lasts about 180 days for most banks [12]. The FAQ says "Our pricing is volume based" [12]. Free status for restricted or personal use is from the Terms per the Firefly III guide [12b] and **could not be re-read directly** (the page did not render).
- **Scope limit**: PSD2 AIS covers only payment accounts (Art. 4(12) via EBA Q&A) [13][14]. The EU's broader **FIDA** (open finance) proposal would cover investments, but as of September 2026 trilogues had stalled with no scheduled dates [41]. So no aggregator route exists for securities or crypto holdings in 2026.

### 4. Market prices from a phone

- **Alpha Vantage**: free key, "25 API requests per day"; realtime and 15-min-delayed US data is premium [30]. `GLOBAL_QUOTE` is end-of-day by default, with non-US symbols via suffix (`MBG.DEX` XETRA, `TSCO.LON`, `SHOP.TRT`). `SYMBOL_SEARCH` does keyword lookup. ISIN input is not documented [31]. `GOLD_SILVER_SPOT` accepts `GOLD`/`XAU` and `SILVER`/`XAG`. There are also WTI, Brent, natural gas, copper, aluminium and agricultural series [31]. Which European ETFs (e.g. `.DEX`, `.AMS`, `.PAR` listings) are covered was **not verified**; test with the real holdings before relying on it. Twenty-five calls a day roughly fits one daily refresh of up to about 20 positions plus gold.
- **Finnhub**: free tier with 60 calls/min, market data coverage "US", licence "Personal Use" [32-snippet].
- **Financial Modeling Prep**: Basic $0, 250 calls/day, end-of-day, US exchanges only [33-snippet].
- **Twelve Data**: Basic $0, 8 credits/min and 800/day. It includes US equities/ETFs, forex and crypto. EU markets are Pro only, and ISIN is a paid add-on [34].
- **EODHD**: free plan with 20 calls/day, EOD with 1 year of history, and stocks, forex, crypto and commodities. ISIN support on the free plan is not stated [42].
- **OpenFIGI**: "free and open to the public". `POST /v3/mapping` with `ID_ISIN` returns tickers and exchange codes. 25 req/min and 10 jobs/request without a key; 250 req/min and 100 jobs/request with a free key [35]. Use it once per new ISIN to get the exchange ticker for a price API.
- **CoinGecko**: Demo plan 100 calls/min, 10,000/month, attribution required, a Demo key needed via `x_cg_demo_api_key`. A keyless public API exists with an unpublished IP-based limit [28][29]. Commercial licence only on paid plans [28].
- **Kraken**: `GET https://api.kraken.com/0/public/Ticker?pair=XBTEUR` needs no authentication [27] (checked live 2026-10-20). Its rate limit is not stated on that page.
- **Revolut X public tickers**: see section 2. No key, 1 req/s, prices from the same venue as Revolut [22].
- **ECB reference rates**: 29 currencies against EUR, published at about 16:00 CET on working days, as XML/CSV/RSS. "Published for information purposes only" [36].
- **Stooq**: reports say an API key has been required for CSV downloads since March 2026, with a daily quota. There are no official terms to cite [43]. Not recommended.
- **Yahoo Finance (unofficial)**: no documented public finance API. The yfinance README says it is "not affiliated, endorsed, or vetted by Yahoo" and is meant for research and education [38]. Yahoo's developer API terms are revocable and prohibit "excessive or abusive usage" [37]. Endpoints change without notice. It is the only free source found that reliably covers EU-listed ETFs by exchange ticker, but this rests on wide community use and was **not verified** against Yahoo docs.

### 5. Open questions (not confirmable from sources)

1. TR has no official help article for the CSV export, and whether the web app offers it is unknown.
2. Exact Revolut stocks CSV/Excel header.
3. Format of the Revolut crypto statement, and whether the unified crypto balance is live for the owner's region.
4. How TR commodities and ETCs and Revolut commodities appear in exports.
5. Alpha Vantage coverage of the owner's specific EU ETF listings.

## Sources

1. Trade Republic Support, "Tax Report" (article 1078), fetched 2026-10-20 — https://support.traderepublic.com/en-de/1078-What-is-the-annual-tax-certificate
2. Trade Republic Support, "Where can I find my securities settlement statement?" (845) [search-index text] — https://support.traderepublic.com/en-nl/845-Where-can-I-find-my-securities-settlement-statement
   2b. Trade Republic Support, "How do I get an overview of the crypto trades I have made?" (1494) — https://support.traderepublic.com/en-de/1494
3. mobiflip.de, "Trade Republic bekommt neue Export-Funktion…", 2026-04-17 — https://www.mobiflip.de/trade-republic-bekommt-neue-export-funktion-und-outbank-zieht-sofort-nach/
4. Portfolio Performance source, `TradeRepublicCSVExtractor.java` and test fixtures `TransactionExport01–06.csv` (commit 416e6e6, 2026-10-19) — https://github.com/portfolio-performance/portfolio/tree/master/name.abuchen.portfolio.tests/src/name/abuchen/portfolio/datatransfer/traderepublic
5. Portfolio Performance forum, "Import Trade Republic CSV (Transaktionsexport)" — https://forum.portfolio-performance.info/t/import-trade-republic-csv-transaktionsexport/39126
6. Trade Republic Customer Agreement, version 06.07.01 (EE-en), clauses intro, 2.2, 2.3 — https://assets.traderepublic.com/assets/files/CA_EE-en.pdf
7. pytr README (unofficial; not affiliated with Trade Republic) — https://github.com/pytr-org/pytr
8. pytr on PyPI (0.4.10, 2026-08-06) — https://pypi.org/project/pytr/
9. Blockpit Help, "How to import data via Trade Republic CSV file" (third-party steps) — https://intercom.help/blockpit/en/articles/15452549-how-to-import-data-via-trade-republic-csv-file
10. getquin Help (third-party; web-app export from 2026-04-15) — https://help.getquin.com/en/articles/14633614
11. GoCardless Bank Account Data, "New signups disabled" — https://bankaccountdata.gocardless.com/new-signups-disabled
12. Enable Banking FAQ — https://enablebanking.com/docs/faq
    12b. Firefly III docs, Enable Banking guide (restricted mode, free) — https://docs.firefly-iii.org/tutorials/data-importer/eb/ ; Enable Banking Terms — https://enablebanking.com/terms
13. EBA Single Rulebook Q&A 2018_4272 (payment account definition, Art. 4(12) PSD2) — https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2018_4272
14. EBA Q&A 2021_5755 (account must be used for execution of payment transactions) — https://www.eba.europa.eu/single-rule-book-qa/qna/view/publicId/2021_5755 ; PSD2 text (could not be fetched in this session) — https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32015L2366
15. Open Banking Tracker, Trade Republic integrations (no listed integrations) — https://openbankingtracker.com/provider/trade-republic/integrations
16. Revolut Help, "Trading account information, statements and reports" [snippet] — https://help.revolut.com/help/wealth/stocks/getting-started-with-trading/managing-your-trading-account/trading-statements/accessing-my-trading-statements-and-reports/ ; EU page — https://help.revolut.com/en-IT/help/wealth/stocks/getting-started-with-trading/managing-your-trading-account/trading-statements/accessing-my-trading-statements-and-reports/
17. Navexa Help, "Import trades from Revolut" (2026-07-29, third-party) — https://help.navexa.com/en/articles/16116548-import-trades-from-revolut
18. Portfolio Performance test fixture of a Revolut account statement PDF (generated 2026-07-06), `AccountStatement03.txt` — https://github.com/portfolio-performance/portfolio/tree/master/name.abuchen.portfolio.tests/src/name/abuchen/portfolio/datatransfer/pdf/revolutltd
19. Revolut Help, "Download a cryptocurrency statement" [snippet] — https://help.revolut.com/help/profile-and-plan/managing-my-account/cryptocurrency-statement/
    19b. Revolut Help, "Download an account statement for a specific currency" [snippet] — https://help.revolut.com/help/profile-and-plan/managing-my-account/account-statement-per-chosen-currency/
20. Revolut, Commodities Services – Terms of Business [snippet] — https://www.revolut.com/legal/precious-metals/
21. Revolut Developer, Revolut X "Get balances" — https://developer.revolut.com/docs/api/revolut-x-crypto-exchange/operations/get-all-balances.md
22. Revolut Developer, Revolut X Crypto Exchange REST API (auth, base URL, rate limits, public endpoints) — https://developer.revolut.com/docs/x-api/revolut-x-crypto-exchange-rest-api ; live check of `https://revx.revolut.com/api/1.0/public/tickers` on 2026-10-20
23. Revolut Help, "About crypto unified balance" [snippet] — https://help.revolut.com/en-PT/help/wealth/cryptocurrencies/crypto-exchange/question-crypto-unified-balance/ ; "What API does Revolut X provide" [snippet] — https://help.revolut.com/help/wealth/cryptocurrencies/crypto-exchange/api-trading/question-what-api-does-revolut-x-provide/
24. Revolut Developer, Open Banking "Who can access the API" — https://developer.revolut.com/docs/guides/build-banking-apps/introduction-to-the-open-banking-api/who-can-access-the-api.md
25. Revolut, Terms & Conditions Change Overview (Revolut Bank UAB Personal Terms, reverse-engineering clause; open banking section 10) — https://assets.revolut.com/legal/terms/T&C_Change_Overview_EN.pdf
26. Revolut Personal Terms, Singapore [snippet] — https://www.revolut.com/en-SG/legal/terms/
27. Kraken API docs, Get Ticker Information — https://docs.kraken.com/api/docs/rest-api/get-ticker-information
28. CoinGecko API pricing — https://www.coingecko.com/en/api/pricing
29. CoinGecko docs, rate limits — https://docs.coingecko.com/docs/common-errors-rate-limit
30. Alpha Vantage support (free key, 25 requests/day) — https://www.alphavantage.co/support/
31. Alpha Vantage documentation (GLOBAL_QUOTE, SYMBOL_SEARCH, GOLD_SILVER_SPOT, commodities, CURRENCY_EXCHANGE_RATE) — https://www.alphavantage.co/documentation/
32. Finnhub pricing [snippet] — https://api.finnhub.io/pricing
33. Financial Modeling Prep pricing [snippet] — https://site.financialmodelingprep.com/pricing-plans
34. Twelve Data pricing — https://twelvedata.com/pricing
35. OpenFIGI API documentation — https://www.openfigi.com/api/documentation
36. ECB euro foreign exchange reference rates — https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html ; feed https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml
37. Yahoo Developer API Terms of Use — https://legal.yahoo.com/us/en/yahoo/terms/product-atos/apiforydn/index.html
38. yfinance README (unofficial) — https://github.com/ranaroussi/yfinance
39. Android Developers, Receive simple data from other apps — https://developer.android.com/training/sharing/receive
40. Android Developers, Access documents and other files from shared storage — https://developer.android.com/training/data-storage/shared/documents-files
41. WKO, "FIDA – Financial Data Access" (status, September 2026) — https://www.wko.at/information-consulting/finanzdienstleister/fida-financial-data-access
42. EODHD pricing — https://eodhd.com/pricing
43. pandas-datareader issue #1012 (Stooq API key requirement, 2026; third-party) — https://github.com/pydata/pandas-datareader/issues/1012
