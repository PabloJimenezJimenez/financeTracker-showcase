# Finance Tracker

A personal finance app that runs entirely on the owner's phone: it records money coming in and
going out, and turns it into indicators the owner defines.

## Money movements

**Transaction**:
One movement of money into or out of an Account, entered by hand or captured from a bank.
_Avoid_: Movement, operation, entry

**Account**:
A place money is held and moves through: a bank account, a card, cash, or an investment account.
An investment Account holds cash like any other and also Holdings; its value is its cash plus
the market value of its Holdings.
_Avoid_: Wallet, bank

**Institution**:
The bank or broker where one or more of the owner's Accounts are held (e.g. Revolut, Trade
Republic). Anything that comes from the bank attaches to it. Cash has no Institution.
_Avoid_: Bank (for the Account), provider, entity

**Archived Account**:
An Account the owner has put away for good: it no longer shows as an Account or as a choice when
recording money, but its money still counts in the total and its past Transactions still show and
count. Only an Account with Transactions is archived; one without them is deleted instead.
_Avoid_: Closed account, hidden account, deleted account

**Transfer**:
Money moved between two of the owner's own Accounts, recorded as two linked Transactions
(out of one, into the other) that never count as spending or income.
_Avoid_: Internal movement, top-up

**Refund**:
Money returned for an earlier purchase. It belongs to the purchase's category and reduces
that category's spending; it is never income.
_Avoid_: Return, reimbursement

**Merchant**:
Where money was spent or came from, as named by the bank or the owner (e.g. Mercadona). A
Merchant has a default Category that its Transactions get unless the owner picks another. Two
names that differ only in case, accents or spacing are the same Merchant.
_Avoid_: Shop, payee, vendor

**Merchant rule**:
A keyword that recognises a Merchant in a bank's text, matched as a whole word regardless of
case and accents. Some come with the app; the owner's own rules are learnt from their choices.
_Avoid_: Pattern, filter, mapping

**Category**:
What money was spent on or came from (e.g. Groceries > Food). Categories form a tree two levels
deep; each top-level Category is either spending or income, and its children share that kind. A
Transaction without a Split sits in one Category, which may be a parent. A Transfer has none.
_Avoid_: Tag, type, label

**Uncategorised**:
The state of a Transaction (or Split line) with no Category yet. It is not a Category; it waits
for the owner's review and Metrics show it on its own.
_Avoid_: Other, unknown, misc

**Split**:
The breakdown of one Transaction into lines, each with its own amount and Category, that add
up to the Transaction's amount. When a Transaction has a Split, Metrics use its lines.
_Avoid_: Itemisation, breakdown

## Investments

**Asset**:
Something the owner can buy and hold for its value: a stock, an ETF or fund, a crypto coin, a
commodity such as gold, or a bond. It may have no price source, in which case the owner gives its
price by hand.
_Avoid_: Security, instrument, ticker

**Trade**:
A change to what an investment Account holds of one Asset: a purchase or sale (how much, at what
price, with what fee, on which date), an opening position whose history is unknown, or an
adjustment such as a stock split that changes the quantity but not the cost. The cash a purchase or sale
moves comes from or goes to an Account at the same Institution and is neither spending nor income.
An Asset received for free (a reward, staking, a gift) is income invested at once: income at its
market value that day, then a purchase of it, unless the owner turns that off and takes it in at
no cost.
_Avoid_: Order, operation, investment (for one purchase)

**Holding**:
How much of one Asset an investment Account owns, as the sum of its Trades, and what it cost. Its
cost follows the first-bought, first-sold rule; fees add to the cost of a purchase and reduce the
proceeds of a sale.
_Avoid_: Position, investment, stock

**Net worth**:
Everything the owner has: the cash in all Accounts plus the market value of all Holdings.
_Avoid_: Total balance, wealth, patrimony

## Indicators

**Metric**:
A value computed from Transactions over a period. A simple Metric starts from spending or
income, keeps the Transactions that match its Categories, Merchants and Accounts, and measures
them as a sum, a count or an average (e.g. grocery spending this month). A combined Metric is
built from saved Metrics: their ratio, their difference, or one Metric's change against the
previous period, with both sides measured over the combined Metric's own period.
_Avoid_: Stat, figure, measure

**KPI**:
A Metric the owner pins and tracks over time, optionally with a target in the Metric's own unit
and a direction (lower or higher is better). While its period is unfinished, a KPI that adds up
money or Transactions is judged on where it is heading by the end of the period, not on what it
shows so far.
_Avoid_: Goal, indicator, metric (when the tracked one is meant)

**Verdict**:
How a KPI is doing right now. With a target it is on track or off track; with only a direction it
is better or worse than the previous period; with neither there is no verdict, only the value.
_Avoid_: Status, score, health

## Security

**App lock**:
The lock that keeps the app's screens hidden until the owner unlocks it with the phone's own screen
lock (fingerprint, face, PIN, pattern or password). It locks on every cold start and every time the
app returns from the background, except from a system screen the app itself opened (Settings, the
unlock prompt, a file picker). It is always on and the app has no PIN or secret of its own.
_Avoid_: Passcode, PIN lock, app password
