-- Default expense categories. Runs on every start; INSERT IGNORE keeps it idempotent.
INSERT IGNORE INTO expense_categories (category_id, name, description, essential) VALUES
  (1,  'Rent',          'Rent and maintenance',                   TRUE),
  (2,  'Groceries',     'Food and household supplies',            TRUE),
  (3,  'Utilities',     'Electricity, water, gas, internet',      TRUE),
  (4,  'Transport',     'Fuel, public transport, cabs',           TRUE),
  (5,  'Healthcare',    'Medicines, consultations',               TRUE),
  (6,  'Education',     'Fees, books, courses',                   TRUE),
  (7,  'Insurance',     'Premiums not tracked as investments',    TRUE),
  (8,  'Mobile',        'Mobile and DTH recharges',               TRUE),
  (9,  'Dining Out',    'Restaurants and food delivery',          FALSE),
  (10, 'Entertainment', 'Movies, streaming, events',              FALSE),
  (11, 'Shopping',      'Clothing, electronics, personal items',  FALSE),
  (12, 'Travel',        'Trips and holidays',                     FALSE),
  (13, 'Personal Care', 'Salon, grooming, fitness',               FALSE),
  (14, 'Gifts',         'Gifts and donations',                    FALSE),
  (15, 'Other',         'Anything else',                          FALSE);
