# Preloaded Merchants are seeded once by stable key, and deletes clear references

The app ships a list of preloaded Merchants, and app updates may add more. Each one has a stable key that never changes, and the database records every key it was ever seeded with. On each open, only keys it has never seen are added. A preloaded Merchant the owner renamed keeps the new name, and one the owner deleted never comes back, because its key is still recorded. Matching by name instead would bring back deleted Merchants and duplicate renamed ones.

A Transaction refers to its Merchant, and a Merchant to its default Category. Both are foreign keys that become empty when the target is deleted: deleting a Merchant leaves its Transactions without a Merchant, and deleting a Category leaves Merchants without a default Category. They never delete the Transaction, which is the owner's financial record.

## Consequences

- The seed-key table must never be cleared or rebuilt by a migration, or deleted preloaded Merchants would return.
- Changing a preloaded Merchant's name or keywords in a later release has no effect on databases that already have it. To fix a preloaded Merchant, ship a new key and let the old one stay.
- Deleting a Category with Transactions still requires moving them first (Categories brief). The empty-reference rule covers Merchant defaults, not Transactions.
