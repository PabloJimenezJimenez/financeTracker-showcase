# Metric filters are stored as lists of ids, without foreign keys

A saved Metric keeps its Category and Account filters as comma-separated id lists in its own row, not in join tables with foreign keys. With cascading join tables, deleting the only Account in a Metric's filter would remove the filter row and silently widen the Metric to "all Accounts", so a Metric about one card would start counting every Account. With plain id lists, an id that no longer exists simply matches nothing, so the Metric shows what is really left. Only the definition is stored; values are always computed from Transactions.

## Consequences

- The database does not keep these ids consistent. Anything that deletes, merges or moves a Category or an Account must update the Metric filters itself, in the same transaction. For example, deleting a Category and moving its Transactions to another Category also replaces the deleted id in every Metric filter.
- Filters can't be queried with SQL joins. That is fine, because Metrics are evaluated in Kotlin over the Transactions.
