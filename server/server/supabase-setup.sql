-- ============================================
-- DataSaver Supabase Setup
-- Run this in: Supabase Dashboard > SQL Editor
-- ============================================

-- 1. USERS TABLE
CREATE TABLE IF NOT EXISTS users (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    phone VARCHAR(15) UNIQUE NOT NULL,
    pin VARCHAR(6),
    wallet_balance DECIMAL(12,2) DEFAULT 0.00,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. DATA PLANS TABLE
CREATE TABLE IF NOT EXISTS data_plans (
    id SERIAL PRIMARY KEY,
    data_id INTEGER NOT NULL,
    network VARCHAR(10) NOT NULL,
    plan_type VARCHAR(30) NOT NULL,
    amount DECIMAL(10,1) NOT NULL,
    size VARCHAR(20) NOT NULL,
    validity VARCHAR(30) NOT NULL,
    active BOOLEAN DEFAULT TRUE
);

-- 3. TRANSACTIONS TABLE
CREATE TABLE IF NOT EXISTS transactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('airtime', 'data')),
    network VARCHAR(10) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    amount DECIMAL(10,1) NOT NULL,
    data_plan_id INTEGER,
    plan_size VARCHAR(20),
    status VARCHAR(15) DEFAULT 'pending' CHECK (status IN ('pending','success','failed')),
    api_response TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. WALLET TRANSACTIONS (fund/debit history)
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    type VARCHAR(10) NOT NULL CHECK (type IN ('credit', 'debit')),
    amount DECIMAL(12,2) NOT NULL,
    description TEXT,
    balance_after DECIMAL(12,2),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================
-- SEED ALL DATA PLANS
-- ============================================

INSERT INTO data_plans (data_id, network, plan_type, amount, size, validity) VALUES
-- 9MOBILE
(221, '9MOBILE', 'CORPORATE GIFTING', 150.0, '500.0 MB', '30 days'),
(183, '9MOBILE', 'CORPORATE GIFTING', 300.0, '1.0 GB', '30 days'),
(184, '9MOBILE', 'CORPORATE GIFTING', 450.0, '1.5 GB', '30 days'),
(185, '9MOBILE', 'CORPORATE GIFTING', 600.0, '2.0 GB', '30 days'),
(186, '9MOBILE', 'CORPORATE GIFTING', 900.0, '3.0 GB', '30 days'),
(188, '9MOBILE', 'CORPORATE GIFTING', 1500.0, '5.0 GB', '30 days'),
(189, '9MOBILE', 'CORPORATE GIFTING', 3000.0, '10.0 GB', '30 days'),
(229, '9MOBILE', 'CORPORATE GIFTING', 6000.0, '20.0 GB', 'Monthly'),
(265, '9MOBILE', 'CORPORATE GIFTING', 1200.0, '4.0 GB', '30 days'),

-- AIRTEL
(310, 'AIRTEL', 'SME', 55.0, '150.0 MB', '1 day'),
(398, 'AIRTEL', 'SME', 114.0, '300.0 MB', '2 days'),
(397, 'AIRTEL', 'SME', 214.0, '600.0 MB', '2 days'),
(360, 'AIRTEL', 'SME', 314.0, '1.0 GB', '3 days'),
(428, 'AIRTEL', 'SME', 355.0, '500.0 MB', '1 day'),
(372, 'AIRTEL', 'SME', 514.0, '500.0 MB', '7 days'),
(386, 'AIRTEL', 'SME', 514.0, '1.5 GB', '2 days'),
(373, 'AIRTEL', 'SME', 814.0, '1.0 GB', '7 days'),
(419, 'AIRTEL', 'SME', 550.0, '2.0 GB', '1 day'),
(1014, 'AIRTEL', 'SME', 1014.0, '1.5 GB', '7 days'),
(375, 'AIRTEL', 'SME', 1514.0, '2.0 GB', '30 days'),
(388, 'AIRTEL', 'SME', 1514.0, '5.0 GB', '7 days'),
(387, 'AIRTEL', 'SME', 614.0, '2.0 GB', '2 days'),
(376, 'AIRTEL', 'SME', 765.0, '3.0 GB', '2 days'),
(424, 'AIRTEL', 'SME', 2000.0, '3.0 GB', '30 days'),
(377, 'AIRTEL', 'SME', 2514.0, '4.0 GB', '30 days'),
(378, 'AIRTEL', 'SME', 3014.0, '8.0 GB', '30 days'),
(379, 'AIRTEL', 'SME', 3014.0, '10.0 GB', '7 days'),
(283, 'AIRTEL', 'SME', 4014.0, '10.0 GB', 'Monthly'),
(380, 'AIRTEL', 'SME', 5014.0, '13.0 GB', '30 days'),
(381, 'AIRTEL', 'SME', 6014.0, '18.0 GB', '7 days'),
(382, 'AIRTEL', 'SME', 8014.0, '25.0 GB', '30 days'),
(383, 'AIRTEL', 'SME', 10014.0, '35.0 GB', '30 days'),
(384, 'AIRTEL', 'SME', 15014.0, '60.0 GB', '30 days'),
(385, 'AIRTEL', 'CORPORATE GIFTING', 20014.0, '100.0 GB', '30 days'),

-- GLO
(225, 'GLO', 'CORPORATE GIFTING', 90.0, '200.0 MB', '14 days'),
(203, 'GLO', 'CORPORATE GIFTING', 208.0, '500.0 MB', '30 days'),
(286, 'GLO', 'SME', 187.0, '750.0 MB', '1 day'),
(288, 'GLO', 'SME', 280.0, '1.5 GB', '1 day'),
(390, 'GLO', 'CORPORATE GIFTING', 255.0, '1.0 GB', '3 days'),
(289, 'GLO', 'SME', 468.0, '2.5 GB', '2 days'),
(194, 'GLO', 'CORPORATE GIFTING', 415.0, '1.0 GB', '30 days'),
(391, 'GLO', 'CORPORATE GIFTING', 765.0, '3.0 GB', '3 days'),
(293, 'GLO', 'CORPORATE GIFTING', 294.0, '1.0 GB', '7 days'),
(195, 'GLO', 'CORPORATE GIFTING', 830.0, '2.0 GB', '30 days'),
(394, 'GLO', 'CORPORATE GIFTING', 882.0, '3.0 GB', '7 days'),
(196, 'GLO', 'CORPORATE GIFTING', 1245.0, '3.0 GB', '30 days'),
(392, 'GLO', 'CORPORATE GIFTING', 1275.0, '5.0 GB', '3 days'),
(395, 'GLO', 'CORPORATE GIFTING', 1470.0, '5.0 GB', '7 days'),
(290, 'GLO', 'SME', 1875.0, '10.0 GB', '7 days'),
(197, 'GLO', 'CORPORATE GIFTING', 2075.0, '5.0 GB', '30 days'),
(200, 'GLO', 'CORPORATE GIFTING', 4150.0, '10.0 GB', '30 days'),

-- MTN
(320, 'MTN', 'GIFTING', 98.0, '110.0 MB', '1 day'),
(321, 'MTN', 'GIFTING', 74.0, '75.0 MB', '1 day'),
(420, 'MTN', 'SME', 225.0, '1.0 GB', '1 day'),
(366, 'MTN', 'GIFTING', 340.0, '500.0 MB', '1 day'),
(344, 'MTN', 'GIFTING', 437.0, '750.0 MB', '3 days'),
(356, 'MTN', 'GIFTING', 485.0, '500.0 MB', '7 days'),
(215, 'MTN', 'GIFTING', 485.0, '1.0 GB', '1 day'),
(421, 'MTN', 'SME', 550.0, '2.5 GB', '1 day'),
(403, 'MTN', 'DATA COUPONS', 550.0, '1.0 GB', '30 days'),
(364, 'MTN', 'GIFTING', 582.0, '1.5 GB', '2 days'),
(367, 'MTN', 'GIFTING', 582.0, '1.4 GB', '2 days'),
(418, 'MTN', 'DATA COUPONS', 600.0, '1.0 GB', '30 days'),
(341, 'MTN', 'GIFTING', 728.0, '1.2 GB', '7 days'),
(316, 'MTN', 'GIFTING', 727.5, '2.5 GB', '1 day'),
(318, 'MTN', 'GIFTING', 727.5, '2.0 GB', '2 days'),
(342, 'MTN', 'GIFTING', 776.0, '1.0 GB', '7 days'),
(408, 'MTN', 'SME2', 780.0, '1.0 GB', '7 days'),
(417, 'MTN', 'SME2', 785.0, '1.0 GB', '30 days'),
(317, 'MTN', 'GIFTING', 882.0, '2.5 GB', '2 days'),
(365, 'MTN', 'GIFTING', 970.0, '1.5 GB', '7 days'),
(216, 'MTN', 'GIFTING', 980.0, '3.2 GB', '2 days'),
(404, 'MTN', 'DATA COUPONS', 1100.0, '2.0 GB', '30 days'),
(362, 'MTN', 'GIFTING', 1455.0, '1.12 GB', '30 days'),
(345, 'MTN', 'GIFTING', 1455.0, '2.0 GB', '30 days'),
(410, 'MTN', 'GIFTING', 1455.0, '2.0 GB', '30 days'),
(401, 'MTN', 'GIFTING', 1455.0, '3.5 GB', '7 days'),
(399, 'MTN', 'SME2', 1470.0, '2.0 GB', '30 days'),
(405, 'MTN', 'DATA COUPONS', 1650.0, '3.0 GB', '30 days'),
(346, 'MTN', 'GIFTING', 1940.0, '2.7 GB', '30 days'),
(400, 'MTN', 'SME2', 1980.0, '3.0 GB', '30 days'),
(217, 'MTN', 'GIFTING', 2425.0, '6.0 GB', '7 days'),
(353, 'MTN', 'GIFTING', 2425.0, '3.5 GB', '30 days'),
(406, 'MTN', 'DATA COUPONS', 2750.0, '5.0 GB', '30 days'),
(339, 'MTN', 'GIFTING', 2910.0, '6.75 GB', '30 days'),
(371, 'MTN', 'SME2', 3000.0, '5.0 GB', '30 days'),
(370, 'MTN', 'GIFTING', 3395.0, '7.0 GB', '30 days'),
(402, 'MTN', 'GIFTING', 3395.0, '11.0 GB', '7 days'),
(407, 'MTN', 'DATA COUPONS', 390.0, '500.0 MB', '30 days'),
(351, 'MTN', 'GIFTING', 4365.0, '10.0 GB', '30 days'),
(340, 'MTN', 'GIFTING', 4850.0, '14.5 GB', '30 days'),
(349, 'MTN', 'GIFTING', 5335.0, '12.5 GB', '30 days'),
(348, 'MTN', 'GIFTING', 6305.0, '16.5 GB', '30 days'),
(369, 'MTN', 'GIFTING', 7275.0, '20.0 GB', '30 days'),
(324, 'MTN', 'GIFTING', 8820.0, '40.0 GB', '2 months'),
(416, 'MTN', 'GIFTING', 8730.0, '25.0 GB', '30 days'),
(352, 'MTN', 'GIFTING', 10670.0, '36.0 GB', '30 days'),
(306, 'MTN', 'GIFTING', 17460.0, '75.0 GB', '30 days'),
(327, 'MTN', 'GIFTING', 24500.0, '90.0 GB', '2 months'),
(355, 'MTN', 'GIFTING', 33950.0, '165.0 GB', '30 days'),
(326, 'MTN', 'GIFTING', 38800.0, '150.0 GB', '60 days'),
(359, 'MTN', 'GIFTING', 48500.0, '200.0 GB', '30 days'),
(307, 'MTN', 'GIFTING', 49000.0, '200.0 GB', '60 days'),
(354, 'MTN', 'GIFTING', 53350.0, '250.0 GB', '30 days');

-- ============================================
-- ROW LEVEL SECURITY
-- ============================================

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE wallet_transactions ENABLE ROW LEVEL SECURITY;

-- data_plans is public read
ALTER TABLE data_plans ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can read active plans" ON data_plans
    FOR SELECT USING (active = true);

-- Users can only see their own data
CREATE POLICY "Users see own profile" ON users
    FOR SELECT USING (true);
CREATE POLICY "Users insert own profile" ON users
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Users see own transactions" ON transactions
    FOR SELECT USING (true);
CREATE POLICY "Users insert own transactions" ON transactions
    FOR INSERT WITH CHECK (true);
CREATE POLICY "Users update own transactions" ON transactions
    FOR UPDATE USING (true);

CREATE POLICY "Users see own wallet" ON wallet_transactions
    FOR SELECT USING (true);
CREATE POLICY "Users insert own wallet" ON wallet_transactions
    FOR INSERT WITH CHECK (true);

-- ============================================
-- INDEXES
-- ============================================
CREATE INDEX idx_plans_network ON data_plans(network) WHERE active = true;
CREATE INDEX idx_transactions_user ON transactions(user_id);
CREATE INDEX idx_wallet_user ON wallet_transactions(user_id);
