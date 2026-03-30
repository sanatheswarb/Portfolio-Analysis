-- Clear holdings-related tables to allow fresh import
-- Order matters due to foreign keys: child tables first, then parents

-- Clear user holdings and related metrics first (they reference instruments)
TRUNCATE TABLE user_stock_metrics CASCADE;
TRUNCATE TABLE user_holdings CASCADE;

-- Clear fundamentals (references instruments)
TRUNCATE TABLE stock_fundamentals CASCADE;

-- Clear instruments as part of a complete reset (requires CASCADE due to FK from both tables above; this is destructive)
TRUNCATE TABLE instruments CASCADE;
