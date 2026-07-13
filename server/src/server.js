require('dotenv').config(); // Load environment variables
const express = require('express');
const sharp = require('sharp');
const compression = require('compression');
const axios = require('axios');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');

const app = express();
const PORT = process.env.PORT || 3000;
// Task endpoints: /api/tasks, /api/tasks/all, /api/tasks/create

app.use(cors());
// Increase JSON body limit for proof uploads
app.use(express.json({ limit: '10mb' }));

// ============================================
// SUPABASE
// ============================================
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_KEY;
const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

// ============================================
// FIREBASE ADMIN (for push notifications)
// ============================================
let admin = null;
try {
    // Service account from env var or use hardcoded credentials
    const serviceAccount = {
        type: "service_account",
        project_id: process.env.FIREBASE_PROJECT_ID || "acorn-data-saver-app",
        private_key_id: process.env.FIREBASE_PRIVATE_KEY_ID || "f05df96b87cc198ec8334e0a6309c5dac14ec12a",
        private_key: process.env.FIREBASE_PRIVATE_KEY ? process.env.FIREBASE_PRIVATE_KEY.replace(/\n/g, '
') : "-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCoxJfk+PiBMr1g
DHwd2lMmDPEdN12ynJ+5QIGgoKQT13z2R25/V64gwUyHabblPikfBgpLTmtso4PW
V8bX1boW9WzINxPxnSLBmt186J7Eu1aZ5jwhcsVgXlrq1uPV9D29habUCWteAJfV
Ycb0itN+D1M3OJWAXX3hFqJfdQ9MO8LciD+w0AGV0sXbjYmKlqyvBM0h8SyYXDBS
/srSIHWQI1TapoyP3krp3GIVzqLbwEdRsXQeZvoAxF8B87CK9gkfLFIbOQrJB9ST
qspmJIxWZ6LiacPncRcmhaDM91cUrMtQrALKsXHwNrQ3fJZZQF/T31shykNMyfpj
30p+Yb/lAgMBAAECggEAICR3IG0ZmTOydSNTlTTyXp4a4ttbXUvusK/cMF0/+qZx
Ho+quBaZK5RdELI92unMl6PFHKQWGhYHYzCLBqrmOv6ppfU1d3AbwT9PPT2plWLv
oraCj8VF2a2Gx9C/Ck3u31Rf2mTj3b6jrEhnxcXV812UVgFRGeZUdeTjbmZDUFuG
/j0PxCNFYmPo+RGpONd2aM2qdZ23mBuiMI3v0dJLvFifAPbqyvd/WjZuDYhGsk+x
voF7GCqYP6S2tPN8HPPA+AYVHACL7mnGN6Ajy2edjwkix/pDIFcNRVL9rcRja2VM
rHKkSst9VtoFuRJfzbRSIukB9NQdRRzTxM7G2wep4QKBgQDgrYAreQ4bYMYIzd0I
sGLGIstlErC/qE0VEDUjIXiloXjkNXCac9kBt/Ij8JGJOLcYh5FGUfg4nQYe1rsy
XfgR/xPoqavSHx8Uxynf/FdjwUwb+t0DkaD/VvcUc4+iLcipnig4WWBX1Ki0x4hR
ffU3HtW5UhSmZ56oTzK7ptPWRQKBgQDAS7vuOrMoQuXvvTX4kE/XGSrDGkG6jlrv
l2N+EfErhYuE7D0AQjGnEy9xZ90VUVY07E0IN1jbkNCF4PXisO9s5zXA9n/At52o
3ZtO+P5RBRPcKXkXJDYGcP7YYnLYR+dgO3FnYYNjvdj7CofPUAC8oBLRcaY9F/2p
rQvvpdUtIQKBgB10oakRZdgRB+V/l8rb1RdE2IWXvbRizDhGt7CzYq3UTZUdrHWT
Wo/vHb+4elwTI24D1/fwJyrE61h/rmscBrnVRzbph600h06iDctfudVKMkA402D0
ZrcTH7F+tQX+GqCiK4O3s/nP145b2nNUoCFp2XtCV5K5YwON3ojbhkpBAoGBAIat
hDXZjtjH4dsCneY0zHZN/hEfNqG+Sho74UbOsiZVJd42xpKDydrGKRg4MjNYABSY
22rBuM4uopzhbdUTLt0LIi6/dcI314gJjVjGMvfzonEz6sc2aVAhm5tZeC3aTkar
20UYmrkkoe9Q9MVRtvJk+kkOW+u1/cb0l8OEVcWBAoGBAJ9duMgPs792CIzkjXuT
80nq4zbv7aCnrNwA2GIgNY0+VuLlHKQBEKxwy9p1N0PeKECHRaH9WOw5DEENJvfl
6G6rQqg0iwuQwerE+5fP806Q3pQU7VbfZIWJ38iqVsrXRUX7gaq5qVdXN2QMW31r
K5r1CLmq7qjQNMU9uQrRdQrH
-----END PRIVATE KEY-----",
        client_email: process.env.FIREBASE_CLIENT_EMAIL || "firebase-adminsdk-fbsvc@acorn-data-saver-app.iam.gserviceaccount.com",
        client_id: process.env.FIREBASE_CLIENT_ID || "105810181148629024912",
        auth_uri: "https://accounts.google.com/o/oauth2/auth",
        token_uri: "https://oauth2.googleapis.com/token",
        auth_provider_x509_cert_url: "https://www.googleapis.com/oauth2/v1/certs",
        client_x509_cert_url: "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40acorn-data-saver-app.iam.gserviceaccount.com"
    };
    if (serviceAccount.private_key && serviceAccount.client_email) {
        admin = require('firebase-admin');
        admin.initializeApp({
            credential: admin.credential.cert(serviceAccount)
        });
        console.log('Firebase Admin initialized');
    } else {
        console.log('Firebase credentials not configured - push notifications disabled');
    }
} catch (e) {
    console.log('Firebase init failed:', e.message);
}

// DataStation API config
const DATASTATION_URL = process.env.DATASTATION_URL || 'https://datastationapi.com/api';
const DATASTATION_TOKEN = process.env.DATASTATION_TOKEN;

// Paystack config
const PAYSTACK_SECRET = process.env.PAYSTACK_SECRET;
const PAYSTACK_PUBLIC = process.env.PAYSTACK_PUBLIC;

// DataStation uses numeric network IDs
const NETWORK_IDS = { 'MTN': 1, 'GLO': 2, '9MOBILE': 3, 'AIRTEL': 4 };

// ============================================
// DATA PLANS API
// ============================================

// GET /api/plans?network=MTN
app.get('/api/plans', async (req, res) => {
  try {
    let query = supabase.from('data_plans').select('*').eq('active', true).order('amount', { ascending: true });
    if (req.query.network) {
      query = query.eq('network', req.query.network.toUpperCase());
    }
    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    // Override amount with selling_price for user-facing display
    const plans = (data || []).map(p => ({ ...p, amount: p.selling_price || p.amount }));
    res.json(plans);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// USER API
// ============================================

// POST /api/register  { email, pin, name, phone, referral_code }
app.post('/api/register', async (req, res) => {
  const { phone, pin, name, email, referral_code } = req.body;
  if (!email || !pin) return res.status(400).json({ error: 'Email and PIN required' });

  try {
    // Check if email already exists
    const { data: emailExists } = await supabase.from('users').select('id').eq('email', email).single();
    if (emailExists) return res.status(409).json({ error: 'Email already registered. Please login instead.' });

    // Check if phone already exists
    if (phone) {
      const { data: existing } = await supabase.from('users').select('id').eq('phone', phone).single();
      if (existing) return res.status(409).json({ error: 'Phone number already registered. Please login instead.' });
    }

    // Generate referral code for this user
    const digits = (phone || '').replace(/[^0-9]/g, '');
    const shortDigits = digits.length > 6 ? digits.slice(-6) : digits;
    const userReferralCode = 'DS' + shortDigits + Math.random().toString(36).substring(2, 5).toUpperCase();

    const row = { email, pin: pin || '0000', name: name || '', phone: phone || '', referral_code: userReferralCode };
    const { data, error } = await supabase.from('users').insert(row).select('id, name, phone, email, wallet_balance, subscription_plan, referral_code').single();
    if (error) return res.status(500).json({ error: error.message });

    // If referral code provided, apply referral
    let referralMsg = '';
    if (referral_code && data.id) {
      try {
        // Find referrer
        const { data: referrer } = await supabase.from('users').select('id, phone').eq('referral_code', referral_code).single();
        if (referrer && referrer.phone !== phone) {
          // Get reward amount
          const { data: settings } = await supabase.from('app_settings').select('value').eq('key', 'referral_reward_amount').single();
          const rewardAmount = settings ? parseInt(settings.value) : 500;

          await supabase.from('referrals').insert({
            referrer_user_id: referrer.id,
            referred_user_id: data.id,
            reward_amount: rewardAmount,
            status: 'completed'
          });

          // Credit referrer's wallet
          const { data: refBal } = await supabase.from('users').select('wallet_balance').eq('id', referrer.id).single();
          if (refBal) {
            const newBal = parseFloat(refBal.wallet_balance || 0) + rewardAmount;
            await supabase.from('users').update({ wallet_balance: newBal }).eq('id', referrer.id);
          }

          await supabase.from('wallet_transactions').insert({
            user_id: referrer.id,
            type: 'credit',
            amount: rewardAmount,
            description: 'Referral reward for inviting ' + phone
          });

          referralMsg = ' Referral bonus \u20a6' + rewardAmount + ' sent to referrer!';
        }
      } catch (refErr) {
        // Referral failed — don't block registration
      }
    }

    res.json({ success: true, user_id: data.id, name: data.name, phone: data.phone, email: data.email, wallet_balance: data.wallet_balance, subscription_plan: data.subscription_plan || 'none', referral_code: data.referral_code, message: 'Account created' + referralMsg });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/fcm-token  { phone, fcm_token }
// Register FCM token for push notifications
app.post('/api/fcm-token', async (req, res) => {
  const { phone, fcm_token } = req.body;
  if (!phone || !fcm_token) return res.status(400).json({ error: 'Phone and fcm_token required' });

  try {
    const { error } = await supabase.from('users').update({ fcm_token }).eq('phone', phone);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/login  { email, pin } or { phone, pin }
app.post('/api/login', async (req, res) => {
  const { phone, email, pin } = req.body;
  if ((!phone && !email) || !pin) return res.status(400).json({ error: 'Email/phone and PIN required' });

  try {
    let query = supabase.from('users').select('id, name, phone, email, pin, wallet_balance, subscription_plan, subscription_expires_at, referral_code');
    if (email) {
      // Try email first, then fall back to treating it as phone
      const { data: emailUser } = await query.eq('email', email).single();
      if (emailUser) {
        if (emailUser.pin !== pin) return res.status(401).json({ error: 'Incorrect PIN' });
        return res.json({ success: true, user_id: emailUser.id, name: emailUser.name, phone: emailUser.phone, email: emailUser.email, wallet_balance: emailUser.wallet_balance, subscription_plan: emailUser.subscription_plan || 'none', referral_code: emailUser.referral_code || '', message: 'Login successful' });
      }
      // Email not found, try as phone number
      const { data: phoneUser } = await supabase.from('users').select('id, name, phone, email, pin, wallet_balance, subscription_plan, subscription_expires_at, referral_code').eq('phone', email).single();
      if (phoneUser) {
        if (phoneUser.pin !== pin) return res.status(401).json({ error: 'Incorrect PIN' });
        return res.json({ success: true, user_id: phoneUser.id, name: phoneUser.name, phone: phoneUser.phone, email: phoneUser.email, wallet_balance: phoneUser.wallet_balance, subscription_plan: phoneUser.subscription_plan || 'none', referral_code: phoneUser.referral_code || '', message: 'Login successful' });
      }
      return res.status(404).json({ error: 'Account not found. Please sign up first.' });
    } else {
      query = query.eq('phone', phone);
      const { data: user, error } = await query.single();
      if (error || !user) return res.status(404).json({ error: 'Account not found. Please sign up first.' });
      if (user.pin !== pin) return res.status(401).json({ error: 'Incorrect PIN' });
      res.json({ success: true, user_id: user.id, name: user.name, phone: user.phone, email: user.email, wallet_balance: user.wallet_balance, subscription_plan: user.subscription_plan || 'none', referral_code: user.referral_code || '', message: 'Login successful' });
    }
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/user/update  { phone, name, email, new_phone, photo_base64 }
app.post('/api/user/update', async (req, res) => {
  const { phone, name, email, new_phone, photo_base64 } = req.body;
  if (!phone) return res.status(400).json({ error: 'phone required' });
  try {
    const updates = {};
    if (name !== undefined && name !== null) updates.name = name;
    if (email !== undefined && email !== null) updates.email = email;
    if (new_phone) updates.phone = new_phone;
    if (photo_base64) updates.photo_base64 = photo_base64;
    if (Object.keys(updates).length === 0) return res.json({ success: true, message: 'Nothing to update' });
    const { data, error } = await supabase.from('users').update(updates).eq('phone', phone).select();
    if (error) return res.status(500).json({ error: error.message });
    if (!data || data.length === 0) return res.status(404).json({ error: 'User not found with phone: ' + phone });
    res.json({ success: true, message: 'Profile updated' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/savings/sync  { phone, saved_bytes, blocked_requests, ad_bytes, bg_bytes }
// Uses MAX - keeps highest value received (to handle duplicate syncs)
app.post('/api/savings/sync', async (req, res) => {
  const { phone, saved_bytes, blocked_requests, ad_bytes, bg_bytes } = req.body;
  if (!phone) return res.status(400).json({ error: 'phone required' });
  try {
    // Get current user data first
    const { data: user, error: userErr } = await supabase.from('users').select('id, total_saved_bytes, total_blocked_requests, ad_bytes_saved, bg_bytes_saved').eq('phone', phone).single();
    if (userErr || !user) return res.status(404).json({ error: 'User not found' });

    // Use MAX - keep highest value (handles duplicate syncs during session)
    const newTotalSaved = Math.max(user.total_saved_bytes || 0, saved_bytes || 0);
    const newTotalBlocked = Math.max(user.total_blocked_requests || 0, blocked_requests || 0);
    const newAdBytes = Math.max(user.ad_bytes_saved || 0, ad_bytes || 0);
    const newBgBytes = Math.max(user.bg_bytes_saved || 0, bg_bytes || 0);

    // Update user with max values
    await supabase.from('users').update({
      total_saved_bytes: newTotalSaved,
      total_blocked_requests: newTotalBlocked,
      ad_bytes_saved: newAdBytes,
      bg_bytes_saved: newBgBytes,
      last_savings_sync: new Date().toISOString()
    }).eq('id', user.id);

    // Save daily snapshot - store delta (today's savings only, not cumulative)
    const today = new Date().toISOString().split('T')[0];
    const { data: existing } = await supabase.from('savings_history')
      .select('id, saved_bytes, blocked_requests').eq('user_id', user.id).eq('date', today).single();
    if (existing) {
      // Only update if new values are higher (never go backwards)
      const newSaved = Math.max(existing.saved_bytes || 0, saved_bytes || 0);
      const newBlocked = Math.max(existing.blocked_requests || 0, blocked_requests || 0);
      await supabase.from('savings_history').update({
        saved_bytes: newSaved,
        blocked_requests: newBlocked,
        ad_bytes: ad_bytes || 0,
        bg_bytes: bg_bytes || 0
      }).eq('id', existing.id);
    } else {
      await supabase.from('savings_history').insert({
        user_id: user.id, date: today,
        saved_bytes: saved_bytes || 0,
        blocked_requests: blocked_requests || 0,
        ad_bytes: ad_bytes || 0,
        bg_bytes: bg_bytes || 0
      });
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/savings/:phone  — get savings history
app.get('/api/savings/:phone', async (req, res) => {
  try {
    const { data: user } = await supabase.from('users').select('id, total_saved_bytes, total_blocked_requests, ad_bytes_saved, bg_bytes_saved').eq('phone', req.params.phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    // Get daily history (last 30 days)
    const { data: history } = await supabase.from('savings_history')
      .select('*').eq('user_id', user.id)
      .order('date', { ascending: false }).limit(30);

    // Calculate today/week/month totals
    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    const weekAgo = new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const monthAgo = new Date(now - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

    let todaySaved = 0, weekSaved = 0, monthSaved = 0;
    let todayBlocked = 0, weekBlocked = 0, monthBlocked = 0;
    if (history) {
      for (const h of history) {
        if (h.date === todayStr) { todaySaved = h.saved_bytes; todayBlocked = h.blocked_requests; }
        if (h.date >= weekAgo) { weekSaved += h.saved_bytes; weekBlocked += h.blocked_requests; }
        if (h.date >= monthAgo) { monthSaved += h.saved_bytes; monthBlocked += h.blocked_requests; }
      }
    }

    res.json({
      total_saved: user.total_saved_bytes || 0,
      total_blocked: user.total_blocked_requests || 0,
      today: { saved: todaySaved, blocked: todayBlocked },
      week: { saved: weekSaved, blocked: weekBlocked },
      month: { saved: monthSaved, blocked: monthBlocked },
      history: history || []
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/user/:phone
app.get('/api/user/:phone', async (req, res) => {
  try {
    const { data, error } = await supabase.from('users')
      .select('id, phone, name, email, wallet_balance, subscription_plan, subscription_expires_at, created_at, photo_base64')
      .eq('phone', req.params.phone)
      .single();
    if (error) return res.status(404).json({ error: 'User not found' });
    // Check if subscription expired
    if (data.subscription_plan && data.subscription_plan !== 'none' && data.subscription_expires_at) {
      if (new Date(data.subscription_expires_at) < new Date()) {
        await supabase.from('users').update({ subscription_plan: 'none', subscription_expires_at: null }).eq('id', data.id);
        data.subscription_plan = 'none';
        data.subscription_expires_at = null;
      }
    }
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// BUY DATA
// ============================================

// POST /api/buy-data  { phone, network, data_plan_id, user_id }
app.post('/api/buy-data', async (req, res) => {
  const { phone, network, data_plan_id, user_id } = req.body;
  if (!phone || !data_plan_id) return res.status(400).json({ error: 'phone and data_plan_id required' });

  try {
    // Get plan details
    const { data: plan, error: planErr } = await supabase.from('data_plans')
      .select('*').eq('data_id', data_plan_id).single();
    if (planErr || !plan) return res.status(404).json({ error: 'Plan not found' });

    // Resolve user
    let userId = user_id || null;
    let walletBal = 0;
    if (!userId && phone) {
      const { data: u } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single();
      if (u) { userId = u.id; walletBal = parseFloat(u.wallet_balance || 0); }
    } else if (userId) {
      const { data: u } = await supabase.from('users').select('id, wallet_balance').eq('id', userId).single();
      if (u) walletBal = parseFloat(u.wallet_balance || 0);
    }

    // Check wallet balance
    if (walletBal < parseFloat(plan.selling_price || plan.amount)) {
      return res.status(400).json({ success: false, error: 'Insufficient wallet balance. You have \u20a6' + walletBal.toFixed(0) + ' but need \u20a6' + (plan.selling_price || plan.amount) });
    }

    // Create pending transaction
    const { data: txn, error: txnErr } = await supabase.from('transactions')
      .insert({
        user_id: userId,
        type: 'data',
        network: plan.network,
        phone,
        amount: plan.selling_price || plan.amount,
        plan_size: plan.size,
        status: 'pending'
      })
      .select('id')
      .single();
    if (txnErr) return res.status(500).json({ error: txnErr.message });

    // Call DataStation API
    try {
      const networkId = NETWORK_IDS[plan.network] || 1;
      const apiRes = await axios.post(DATASTATION_URL + '/data/', {
        network: networkId,
        mobile_number: phone,
        plan: plan.data_id,
        Ported_number: true
      }, {
        headers: {
          'Authorization': 'Token ' + DATASTATION_TOKEN,
          'Content-Type': 'application/json'
        }
      });

      // Debit wallet
      const chargeAmount = parseFloat(plan.selling_price || plan.amount);
      await supabase.from('users').update({ wallet_balance: walletBal - chargeAmount }).eq('id', userId);
      await supabase.from('wallet_transactions').insert({ user_id: userId, type: 'debit', amount: chargeAmount, description: plan.size + ' ' + plan.network + ' data' });

      // Update transaction
      await supabase.from('transactions')
        .update({ status: 'success', api_response: JSON.stringify(apiRes.data) })
        .eq('id', txn.id);

      // Send notification for successful purchase
      try {
        await supabase.from('notifications').insert({
          title: 'Data Purchased',
          body: plan.size + ' ' + plan.network + ' data sent to ' + phone,
          type: 'data',
          target_phone: phone
        });
      } catch (nfe) { console.log('Notif error:', nfe.message); }

      res.json({ success: true, transaction_id: txn.id, message: plan.size + ' data sent to ' + phone, api: apiRes.data, wallet_balance: walletBal - chargeAmount });
    } catch (apiErr) {
      const errMsg = apiErr.response ? JSON.stringify(apiErr.response.data) : apiErr.message;
      await supabase.from('transactions')
        .update({ status: 'failed', api_response: errMsg })
        .eq('id', txn.id);
      res.status(502).json({ success: false, transaction_id: txn.id, error: 'API call failed', detail: errMsg });
    }
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// BUY AIRTIME
// ============================================

// POST /api/buy-airtime  { phone, network, amount, user_id }
app.post('/api/buy-airtime', async (req, res) => {
  const { phone, network, amount, user_id } = req.body;
  if (!phone || !network || !amount) return res.status(400).json({ error: 'phone, network, amount required' });

  try {
    // Resolve user
    let userId = user_id || null;
    let walletBal = 0;
    if (!userId && phone) {
      const { data: u } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single();
      if (u) { userId = u.id; walletBal = parseFloat(u.wallet_balance || 0); }
    } else if (userId) {
      const { data: u } = await supabase.from('users').select('id, wallet_balance').eq('id', userId).single();
      if (u) walletBal = parseFloat(u.wallet_balance || 0);
    }

    // Check wallet balance
    if (walletBal < parseFloat(amount)) {
      return res.status(400).json({ success: false, error: 'Insufficient wallet balance. You have \u20a6' + walletBal.toFixed(0) + ' but need \u20a6' + amount });
    }

    // Create pending transaction
    const { data: txn, error: txnErr } = await supabase.from('transactions')
      .insert({
        user_id: userId,
        type: 'airtime',
        network,
        phone,
        amount: parseFloat(amount),
        status: 'pending'
      })
      .select('id')
      .single();
    if (txnErr) return res.status(500).json({ error: txnErr.message });

    // Call DataStation API
    try {
      const networkId = NETWORK_IDS[network] || 1;
      const apiRes = await axios.post(DATASTATION_URL + '/topup/', {
        network: networkId,
        mobile_number: phone,
        amount: parseInt(amount),
        Ported_number: true,
        airtime_type: 'VTU'
      }, {
        headers: {
          'Authorization': 'Token ' + DATASTATION_TOKEN,
          'Content-Type': 'application/json'
        }
      });

      // Debit wallet
      await supabase.from('users').update({ wallet_balance: walletBal - parseFloat(amount) }).eq('id', userId);
      await supabase.from('wallet_transactions').insert({ user_id: userId, type: 'debit', amount: parseFloat(amount), description: '\u20a6' + amount + ' ' + network + ' airtime' });

      await supabase.from('transactions')
        .update({ status: 'success', api_response: JSON.stringify(apiRes.data) })
        .eq('id', txn.id);

      // Send notification for successful purchase
      try {
        await supabase.from('notifications').insert({
          title: 'Airtime Purchased',
          body: '₦' + amount + ' ' + network + ' airtime sent to ' + phone,
          type: 'airtime',
          target_phone: phone
        });
      } catch (nfe) { console.log('Notif error:', nfe.message); }

      res.json({ success: true, transaction_id: txn.id, message: '₦' + amount + ' airtime sent to ' + phone, api: apiRes.data, wallet_balance: walletBal - parseFloat(amount) });
    } catch (apiErr) {
      const errMsg = apiErr.response ? JSON.stringify(apiErr.response.data) : apiErr.message;
      await supabase.from('transactions')
        .update({ status: 'failed', api_response: errMsg })
        .eq('id', txn.id);
      res.status(502).json({ success: false, transaction_id: txn.id, error: 'API call failed', detail: errMsg });
    }
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// TRANSACTION HISTORY
// ============================================

// GET /api/transactions/:phone
app.get('/api/transactions/:phone', async (req, res) => {
  try {
    const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('phone', req.params.phone).single();
    if (!user) return res.json([]);

    const { data, error } = await supabase.from('transactions')
      .select('*')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false })
      .limit(50);
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// SUBSCRIPTIONS
// ============================================

const PLAN_CONFIG = {
  premium:      { amount: 500,   duration: '90 days (promo)', ms: 90 * 24 * 60 * 60 * 1000, devices: 1, promo: true },
  professional: { amount: 1500,  duration: '30 days', ms: 30 * 24 * 60 * 60 * 1000, devices: 2 },
  enterprise:   { amount: 5000,  duration: '30 days', ms: 30 * 24 * 60 * 60 * 1000, devices: 5 }
};

// POST /api/subscribe  { phone, plan }
app.post('/api/subscribe', async (req, res) => {
  const { phone, plan } = req.body;
  if (!phone || !plan) return res.status(400).json({ error: 'phone and plan required' });
  const cfg = PLAN_CONFIG[plan];
  if (!cfg) return res.status(400).json({ error: 'Invalid plan. Choose premium, professional, or enterprise' });

  try {
    const { data: user } = await supabase.from('users').select('id, wallet_balance, subscription_plan').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    const bal = parseFloat(user.wallet_balance || 0);
    if (bal < cfg.amount) {
      return res.status(400).json({ success: false, error: 'Insufficient wallet balance. You have \u20a6' + bal.toFixed(0) + ' but need \u20a6' + cfg.amount });
    }

    const expiresAt = new Date(Date.now() + cfg.ms).toISOString();
    const newBal = bal - cfg.amount;

    // Update user
    await supabase.from('users').update({ subscription_plan: plan, subscription_expires_at: expiresAt, wallet_balance: newBal }).eq('id', user.id);

    // Log subscription
    await supabase.from('subscriptions').insert({ user_id: user.id, plan, amount: cfg.amount, duration: cfg.duration, expires_at: expiresAt });

    // Log wallet debit
    await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'debit', amount: cfg.amount, description: plan.charAt(0).toUpperCase() + plan.slice(1) + ' subscription (' + cfg.duration + ')' });

    res.json({ success: true, plan, expires_at: expiresAt, wallet_balance: newBal, message: 'Subscribed to ' + plan.charAt(0).toUpperCase() + plan.slice(1) + ' plan' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/subscription/:phone
app.get('/api/subscription/:phone', async (req, res) => {
  try {
    const { data: user } = await supabase.from('users').select('id, subscription_plan, subscription_expires_at').eq('phone', req.params.phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });
    // Check expiry
    let plan = user.subscription_plan || 'none';
    let expires = user.subscription_expires_at;
    if (plan !== 'none' && expires && new Date(expires) < new Date()) {
      await supabase.from('users').update({ subscription_plan: 'none', subscription_expires_at: null }).eq('id', user.id);
      plan = 'none';
      expires = null;
    }
    res.json({ plan, expires_at: expires });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// WALLET + PAYSTACK
// ============================================

// POST /api/wallet/initialize  { phone, amount, email }
app.post('/api/wallet/initialize', async (req, res) => {
  const { phone, amount, email } = req.body;
  if (!amount) return res.status(400).json({ error: 'amount required' });
  if (!email && !phone) return res.status(400).json({ error: 'email or phone required' });

  let payEmail = email;
  let payPhone = phone;
  if (!payEmail && payPhone) {
    const { data: u } = await supabase.from('users').select('email, phone').eq('phone', payPhone).single();
    if (u && u.email) payEmail = u.email;
  }
  if (!payEmail) return res.status(400).json({ error: 'Email is required for payment. Please update your profile.' });

  try {
    const paystackRes = await axios.post('https://api.paystack.co/transaction/initialize', {
      email: payEmail,
      amount: Math.round(parseFloat(amount) * 100),
      currency: 'NGN',
      metadata: { phone: payPhone || '', email: payEmail, type: 'wallet_topup' },
      callback_url: 'https://datasaver-server.onrender.com/api/wallet/callback'
    }, {
      headers: { 'Authorization': 'Bearer ' + PAYSTACK_SECRET, 'Content-Type': 'application/json' }
    });
    const ref = paystackRes.data.data.reference;

    // Save pending wallet transaction immediately
    let user = null;
    if (payPhone) { const { data: u } = await supabase.from('users').select('id').eq('phone', payPhone).single(); user = u; }
    if (!user && payEmail) { const { data: u } = await supabase.from('users').select('id').eq('email', payEmail).single(); user = u; }
    if (user) {
      await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount: parseFloat(amount), status: 'pending', description: 'Wallet top-up (ref: ' + ref + ')' });
    }

    res.json({ success: true, authorization_url: paystackRes.data.data.authorization_url, reference: ref });
  } catch (e) {
    const msg = e.response ? JSON.stringify(e.response.data) : e.message;
    res.status(500).json({ error: 'Paystack init failed: ' + msg });
  }
});

// Helper: credit wallet and update pending transaction to success
async function creditWallet(ref, amount, phone, email) {
  let user = null;
  if (phone) { const { data: u } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single(); user = u; }
  if (!user && email) { const { data: u } = await supabase.from('users').select('id, wallet_balance').eq('email', email).single(); user = u; }
  if (!user) return null;

  // Check if already credited (prevent double credit)
  const { data: existing } = await supabase.from('wallet_transactions').select('id, status').ilike('description', '%' + ref + '%').single();
  if (existing && existing.status === 'success') return user;

  // Update pending record to success, or insert if missing
  if (existing) {
    await supabase.from('wallet_transactions').update({ status: 'success' }).eq('id', existing.id);
  } else {
    await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount, status: 'success', description: 'Paystack top-up (ref: ' + ref + ')' });
  }

  const newBal = parseFloat(user.wallet_balance || 0) + amount;
  await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
  return { ...user, wallet_balance: newBal };
}

// Helper: mark pending transaction as failed
async function failWalletTxn(ref) {
  const { data: existing } = await supabase.from('wallet_transactions').select('id').ilike('description', '%' + ref + '%').single();
  if (existing) await supabase.from('wallet_transactions').update({ status: 'failed' }).eq('id', existing.id);
}

// GET /api/wallet/callback?reference=xxx (Paystack redirects here)
app.get('/api/wallet/callback', async (req, res) => {
  const ref = req.query.reference || req.query.trxref;
  if (!ref) return res.send('<h2>Missing reference</h2>');
  try {
    const verify = await axios.get('https://api.paystack.co/transaction/verify/' + ref, {
      headers: { 'Authorization': 'Bearer ' + PAYSTACK_SECRET }
    });
    const txn = verify.data.data;
    if (txn.status === 'success') {
      const amount = txn.amount / 100;
      await creditWallet(ref, amount, txn.metadata.phone, txn.metadata.email);
      res.send('<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{font-family:sans-serif;text-align:center;padding:40px;background:#f0f4f8}h1{color:#43A047}p{color:#333;font-size:18px}</style></head><body><h1>Payment Successful!</h1><p>\u20a6' + amount + ' has been added to your wallet.</p><p>You can close this page and return to the app.</p></body></html>');
    } else {
      await failWalletTxn(ref);
      res.send('<h2>Payment not successful: ' + txn.status + '</h2>');
    }
  } catch (e) {
    res.send('<h2>Verification failed</h2><p>' + e.message + '</p>');
  }
});

// POST /api/wallet/webhook (Paystack sends payment events here)
app.post('/api/wallet/webhook', async (req, res) => {
  const crypto = require('crypto');
  const hash = crypto.createHmac('sha512', PAYSTACK_SECRET).update(JSON.stringify(req.body)).digest('hex');
  if (hash !== req.headers['x-paystack-signature']) return res.sendStatus(400);

  const event = req.body;
  if (event.event === 'charge.success') {
    const txn = event.data;
    try {
      const result = await creditWallet(txn.reference, txn.amount / 100, txn.metadata && txn.metadata.phone, txn.metadata && txn.metadata.email);
      console.log('Webhook: credited ref', txn.reference, result ? 'OK' : 'user not found');
    } catch (e) {
      console.log('Webhook error:', e.message);
    }
  }
  res.sendStatus(200);
});

// POST /api/wallet/verify  { reference }
app.post('/api/wallet/verify', async (req, res) => {
  const { reference } = req.body;
  if (!reference) return res.status(400).json({ error: 'reference required' });
  try {
    const verify = await axios.get('https://api.paystack.co/transaction/verify/' + reference, {
      headers: { 'Authorization': 'Bearer ' + PAYSTACK_SECRET }
    });
    const txn = verify.data.data;
    if (txn.status === 'success') {
      const result = await creditWallet(reference, txn.amount / 100, txn.metadata.phone, txn.metadata.email);
      if (result) return res.json({ success: true, balance: result.wallet_balance });
      return res.status(404).json({ error: 'User not found' });
    }
    await failWalletTxn(reference);
    res.json({ success: false, status: txn.status });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/wallet/transactions/:phone — all wallet transactions
app.get('/api/wallet/transactions/:phone', async (req, res) => {
  try {
    const { data: user } = await supabase.from('users').select('id').eq('phone', req.params.phone).single();
    if (!user) return res.json([]);
    const { data, error } = await supabase.from('wallet_transactions')
      .select('*').eq('user_id', user.id).order('created_at', { ascending: false }).limit(50);
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/wallet/topup  { phone, amount } (manual/admin topup)
app.post('/api/wallet/topup', async (req, res) => {
  const { phone, amount } = req.body;
  if (!phone || !amount) return res.status(400).json({ error: 'phone and amount required' });
  try {
    const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });
    const newBal = parseFloat(user.wallet_balance || 0) + parseFloat(amount);
    await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
    await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount: parseFloat(amount), description: 'Wallet top-up' });
    res.json({ success: true, balance: newBal });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ============================================
// TASKS & EARN
// ============================================

// MORE SPECIFIC TASK ROUTES (must come BEFORE /api/tasks)
// GET /api/tasks/all — admin: get all tasks
app.get('/api/tasks/all', adminAuth, async (req, res) => {
  try {
    const { data, error } = await supabase.from('tasks').select('*').order('created_at', { ascending: false });
    if (error) return res.status(500).json({ error: error.message });
    res.json(data || []);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/tasks/create — admin: create task
app.post('/api/tasks/create', adminAuth, async (req, res) => {
  try {
    const { title, description, reward, reward_type, min_plan, daily_limit, proof_required, active } = req.body;
    if (!title || !reward) return res.status(400).json({ error: 'title and reward required' });
    
    const { data, error } = await supabase.from('tasks').insert({
      title, description, reward, reward_type: reward_type || 'airtime',
      min_plan: min_plan || 'none', daily_limit: daily_limit || 1,
      proof_required: proof_required || false, active: active !== false
    });
    
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// TASKS & EARN
app.post('/api/tasks/submit', async (req, res) => {
  const { phone, task_id, proof_base64 } = req.body;
  if (!phone || !task_id) return res.status(400).json({ error: 'phone and task_id required' });
  try {
    const { data: user } = await supabase.from('users').select('id').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });
    const { data: task } = await supabase.from('tasks').select('*').eq('id', task_id).single();
    if (!task) return res.status(404).json({ error: 'Task not found' });
    // Check if already submitted
    const { data: existing } = await supabase.from('task_submissions').select('id').eq('user_id', user.id).eq('task_id', task_id).single();
    if (existing) return res.status(409).json({ error: 'You already submitted this task' });

    // Upload proof to Supabase Storage or save as URL
    let proofUrl = null;
    if (proof_base64) {
      try {
        const fileName = `proofs/${user.id}/${task_id}_${Date.now()}.jpg`;
        const buffer = Buffer.from(proof_base64, 'base64');
        const { data: upload, error: uploadErr } = await supabase.storage
          .from('task-proofs')
          .upload(fileName, buffer, { contentType: 'image/jpeg', upsert: true });
        if (uploadErr) {
          console.log('Storage upload error:', uploadErr.message, '- saving as data URI fallback');
          // Fallback: save a truncated base64 as proof_url (first 200KB)
          const truncated = proof_base64.substring(0, 200000);
          proofUrl = 'data:image/jpeg;base64,' + truncated;
        } else {
          const { data: urlData } = supabase.storage.from('task-proofs').getPublicUrl(fileName);
          proofUrl = urlData.publicUrl;
        }
      } catch (storageErr) {
        console.log('Storage error:', storageErr.message);
        const truncated = proof_base64.substring(0, 200000);
        proofUrl = 'data:image/jpeg;base64,' + truncated;
      }
    }

    await supabase.from('task_submissions').insert({
      user_id: user.id, task_id, status: 'pending',
      reward: task.reward || 0, reward_type: task.reward_type || 'airtime',
      proof_url: proofUrl
    });
    res.json({ success: true, message: 'Proof submitted! Awaiting review.' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/tasks/claim  { phone } — claim all approved rewards
app.post('/api/tasks/claim', async (req, res) => {
  const { phone } = req.body;
  if (!phone) return res.status(400).json({ error: 'phone required' });
  try {
    const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });
    const { data: approved } = await supabase.from('task_submissions').select('id, reward, reward_type').eq('user_id', user.id).eq('status', 'approved');
    if (!approved || approved.length === 0) return res.status(400).json({ error: 'No rewards to claim' });
    let totalReward = 0;
    const ids = [];
    for (const s of approved) { totalReward += s.reward || 0; ids.push(s.id); }
    // Credit wallet with reward amount
    const newBal = parseFloat(user.wallet_balance || 0) + totalReward;
    await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
    await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount: totalReward, status: 'success', description: 'Task reward earned (' + approved.length + ' task' + (approved.length > 1 ? 's' : '') + ')' });
    // Mark as claimed
    for (const id of ids) { await supabase.from('task_submissions').update({ status: 'claimed' }).eq('id', id); }
    res.json({ success: true, message: '\u20a6' + totalReward + ' added to your wallet! Use it to buy airtime or data.', amount: totalReward, balance: newBal });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/tasks?phone=xxx — list tasks + user's status on each (general - must be last)
app.get('/api/tasks', async (req, res) => {
  const { phone } = req.query;
  const PLAN_LEVEL = { none: 0, premium: 1, professional: 2, enterprise: 3 };
  const DAILY_TASK_LIMITS = { none: 0, premium: 5, professional: 8, enterprise: 999 };
  try {
    const { data: allTasks } = await supabase.from('tasks').select('*').eq('active', true).order('created_at', { ascending: false });
    let pending_reward = 0, claimable_reward = 0;
    let userPlan = 'none';
    const subMap = {};

    if (phone) {
      const { data: user } = await supabase.from('users').select('id, subscription_plan').eq('phone', phone).single();
      if (user) {
        userPlan = user.subscription_plan || 'none';
        const { data: submissions } = await supabase.from('task_submissions').select('task_id, status, reward').eq('user_id', user.id);
        if (submissions) {
          for (const s of submissions) {
            subMap[s.task_id] = s.status;
            if (s.status === 'pending') pending_reward += s.reward || 0;
            if (s.status === 'approved') claimable_reward += s.reward || 0;
          }
        }
      }
    }

    const userLevel = PLAN_LEVEL[userPlan] ?? 0;
    const dailyLimit = DAILY_TASK_LIMITS[userPlan] ?? 2;
    const visibleTasks = [];
    const lockedTasks = [];

    for (const t of (allTasks || [])) {
      const taskMinPlan = t.min_plan || 'none';
      const taskLevel = PLAN_LEVEL[taskMinPlan] ?? 0;
      t.user_status = subMap[t.id] || 'available';
      if (taskLevel <= userLevel) {
        visibleTasks.push(t);
      } else {
        lockedTasks.push({ id: t.id, title: t.title, reward: t.reward, reward_type: t.reward_type, min_plan: taskMinPlan, locked: true });
      }
    }

    const limitedTasks = visibleTasks.slice(0, dailyLimit);
    const hiddenCount = Math.max(0, visibleTasks.length - dailyLimit);

    res.json({ tasks: limitedTasks, locked_tasks: lockedTasks, hidden_count: hiddenCount, daily_limit: dailyLimit, user_plan: userPlan, pending_reward, claimable_reward });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ADMIN SUBMISSIONS API (for admin panel)
// Get all task submissions
app.get('/api/submissions', adminAuth, async (req, res) => {
  try {
    const { status } = req.query;
    let query = supabase.from('task_submissions').select('*, tasks(title, reward, reward_type), users(phone, name)').order('created_at', { ascending: false });
    if (status && status !== 'all') query = query.eq('status', status);
    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json(data || []);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Approve submission
app.post('/api/submissions/:id/approve', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { data: sub, error: subErr } = await supabase.from('task_submissions').select('*, tasks(reward, reward_type)').eq('id', id).single();
    if (subErr || !sub) return res.status(404).json({ error: 'Submission not found' });
    
    await supabase.from('task_submissions').update({ status: 'approved' }).eq('id', id);
    
    // Credit user wallet
    const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('id', sub.user_id).single();
    if (user) {
      const reward = sub.tasks?.reward || 0;
      const newBalance = (parseFloat(user.wallet_balance) || 0) + reward;
      await supabase.from('users').update({ wallet_balance: newBalance }).eq('id', user.id);
      
      // Log transaction
      await supabase.from('wallet_transactions').insert({
        user_id: user.id,
        type: 'task_reward',
        amount: reward,
        description: 'Task reward: ' + (sub.tasks?.title || 'Task'),
        status: 'completed'
      });
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Reject submission
app.post('/api/submissions/:id/reject', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    await supabase.from('task_submissions').update({ status: 'rejected' }).eq('id', id);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ADMIN PANEL API (for admin-vercel)
// Password check middleware
const ADMIN_PW = process.env.ADMIN_PW || 'admin123';
const adminAuth = (req, res, next) => {
  const pw = req.headers['x-admin-password'];
  if (pw !== ADMIN_PW) {
    return res.status(403).json({ error: 'Invalid admin password' });
  }
  next();
};

// Dashboard
app.get('/admin/api/dashboard', adminAuth, async (req, res) => {
  try {
    const today = new Date().toISOString().split('T')[0];
    const weekAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString();
    
    const { data: users } = await supabase.from('users').select('id, created_at, name, phone, email, wallet_balance, subscription_plan, subscription_expires_at');
    const { data: txns } = await supabase.from('transactions').select('*').order('created_at', { ascending: false }).limit(100);
    const { data: savings } = await supabase.from('savings_history').select('*').order('created_at', { ascending: false });
    
    // Calculate stats
    const totalUsers = users?.length || 0;
    const signupsToday = users?.filter(u => u.created_at?.startsWith(today)).length || 0;
    const activeSubscriptions = users?.filter(u => u.subscription_plan && u.subscription_plan !== 'basic' && (!u.subscription_expires_at || new Date(u.subscription_expires_at) > new Date())).length || 0;
    
    // Revenue calculations
    const successTxns = txns?.filter(t => t.status === 'success') || [];
    const revenueToday = successTxns.filter(t => t.created_at?.startsWith(today)).reduce((sum, t) => sum + (parseFloat(t.amount) || 0), 0);
    const revenueWeek = successTxns.filter(t => t.created_at >= weekAgo).reduce((sum, t) => sum + (parseFloat(t.amount) || 0), 0);
    const revenueTotal = successTxns.reduce((sum, t) => sum + (parseFloat(t.amount) || 0), 0);
    
    // Wallet deposits
    const { data: walletTxns } = await supabase.from('wallet_transactions').select('amount, type').eq('type', 'credit') || { data: [] };
    const depositsTotal = walletTxns?.reduce((sum, t) => sum + (parseFloat(t.amount) || 0), 0) || 0;
    
    // Profit = deposits - spent
    const profit = depositsTotal - revenueTotal;
    
    // Pending tasks
    const { count: pendingTasks } = await supabase.from('task_submissions').select('*', { count: 'exact', head: true }).eq('status', 'pending');
    
    res.json({
      totalUsers,
      signupsToday,
      activeSubscriptions,
      revenueToday,
      revenueWeek,
      revenueTotal,
      depositsTotal,
      profit,
      pendingTasks: pendingTasks || 0,
      recentUsers: users?.slice(0, 5) || [],
      recentTransactions: txns || [],
      savingsHistory: savings || []
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Debug test route
app.get('/admin/api/test-new-route', adminAuth, async (req, res) => {
  res.json({ success: true, message: 'New route works!' });
});

// Users
app.get('/admin/api/all-users', adminAuth, async (req, res) => {
  try {
    const { data, error } = await supabase.from('users').select('id, created_at, name, phone, email, wallet_balance, subscription_plan').order('created_at', { ascending: false });
    if (error) return res.status(500).json({ error: error.message });
    res.json({ users: data || [], total: data?.length || 0 });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Transactions
app.get('/admin/api/transactions', adminAuth, async (req, res) => {
  try {
    const { status, type } = req.query;
    let query = supabase.from('transactions').select('*', { count: 'exact' }).order('created_at', { ascending: false });
    if (status) query = query.eq('status', status);
    if (type) query = query.eq('type', type);
    const { data, count, error } = await query.limit(200);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ transactions: data || [], total: count || 0 });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Subscriptions
app.get('/admin/api/subscriptions', adminAuth, async (req, res) => {
  try {
    const { data: users } = await supabase.from('users').select('id, name, phone, subscription_plan, subscription_expires_at, wallet_balance').order('created_at', { ascending: false });
    const { data: history } = await supabase.from('subscriptions').select('*').order('created_at', { ascending: false }).limit(100);
    
    const activeUsers = users?.filter(u => u.subscription_plan && u.subscription_plan !== 'basic' && (!u.subscription_expires_at || new Date(u.subscription_expires_at) > new Date())) || [];
    
    res.json({ activeUsers, history: history || [] });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Wallet transactions
app.get('/admin/api/wallet/:phone', adminAuth, async (req, res) => {
  try {
    const { data: user } = await supabase.from('users').select('id').eq('phone', req.params.phone).single();
    if (!user) return res.json({ transactions: [] });
    const { data, error } = await supabase.from('wallet_transactions').select('*').eq('user_id', user.id).order('created_at', { ascending: false });
    if (error) return res.status(500).json({ error: error.message });
    res.json({ transactions: data || [] });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Update user wallet
app.post('/admin/api/wallet/topup', adminAuth, async (req, res) => {
  try {
    const { phone, amount } = req.body;
    const { data: user } = await supabase.from('users').select('*').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });
    const newBalance = (parseFloat(user.wallet_balance) || 0) + parseFloat(amount);
    await supabase.from('users').update({ wallet_balance: newBalance }).eq('id', user.id);
    await supabase.from('wallet_transactions').insert({
      user_id: user.id,
      type: 'credit',
      amount: parseFloat(amount),
      description: 'Admin topup'
    });
    res.json({ success: true, new_balance: newBalance });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// ADMIN PANEL (deployed separately via admin-vercel)
// ============================================

// ============================================
// PRIVACY POLICY
// ============================================

app.get('/privacy', (req, res) => {
  res.send(`<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Privacy Policy - DataSaver</title>
<style>body{font-family:sans-serif;max-width:700px;margin:0 auto;padding:20px;color:#333}h1{color:#1565C0}h2{color:#555;margin-top:24px}</style></head><body>
<h1>DataSaver Privacy Policy</h1><p>Last updated: 2024</p>
<h2>Data We Collect</h2><p>We collect your phone number for account identification and transaction processing. We monitor app-level data usage locally on your device to show savings statistics.</p>
<h2>How We Use Data</h2><p>Your phone number is used to process airtime and data purchases. Usage statistics are stored locally on your device and never sent to our servers.</p>
<h2>Data Compression</h2><p>When compression is active, web requests are routed through our proxy server for optimization. We do not store, log, or inspect the content of your browsing.</p>
<h2>Third Parties</h2><p>We use DataStation API to fulfill airtime and data purchases. Your phone number is shared with them solely for transaction processing.</p>
<h2>Security</h2><p>All communications use HTTPS encryption. Your PIN is stored securely and never transmitted in plain text.</p>
<h2>Contact</h2><p>For questions about this policy, contact us through the app.</p>
</body></html>`);
});

// ============================================
// COMPRESSION PROXY (existing)
// ============================================

let totalOriginal = 0;
let totalCompressed = 0;

app.use(compression({ level: 9 }));

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    saved: {
      original: totalOriginal,
      compressed: totalCompressed,
      savedBytes: totalOriginal - totalCompressed,
      savedPercent: totalOriginal > 0
        ? ((1 - totalCompressed / totalOriginal) * 100).toFixed(1) : 0
    }
  });
});

app.get('/stats', (req, res) => {
  res.json({
    originalBytes: totalOriginal,
    compressedBytes: totalCompressed,
    savedBytes: totalOriginal - totalCompressed,
    savedPercent: totalOriginal > 0
      ? parseFloat(((1 - totalCompressed / totalOriginal) * 100).toFixed(1)) : 0
  });
});

app.get('/proxy', async (req, res) => {
  const targetUrl = req.query.url;
  if (!targetUrl) return res.status(400).json({ error: 'url param required' });

  try {
    const response = await axios.get(targetUrl, {
      responseType: 'arraybuffer',
      timeout: 15000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36',
        'Accept': req.headers.accept || '*/*'
      },
      maxRedirects: 10,
      decompress: true
    });

    const contentType = response.headers['content-type'] || '';
    const originalSize = response.data.length;
    totalOriginal += originalSize;

    if (contentType.includes('image/')) {
      try {
        const quality = parseInt(req.query.quality) || 40;
        let compressed;
        if (contentType.includes('png')) {
          compressed = await sharp(response.data).png({ quality: Math.min(quality, 50), compressionLevel: 9 }).toBuffer();
        } else if (contentType.includes('webp')) {
          compressed = await sharp(response.data).webp({ quality }).toBuffer();
        } else if (contentType.includes('gif')) {
          compressed = response.data;
        } else {
          compressed = await sharp(response.data).webp({ quality }).toBuffer();
        }
        totalCompressed += compressed.length;
        const saved = ((1 - compressed.length / originalSize) * 100).toFixed(1);
        res.set({
          'Content-Type': contentType.includes('png') ? 'image/png' : 'image/webp',
          'Content-Length': compressed.length,
          'X-Original-Size': originalSize,
          'X-Compressed-Size': compressed.length,
          'X-Data-Saved': `${saved}%`
        });
        return res.send(compressed);
      } catch (imgErr) {
        totalCompressed += originalSize;
        res.set('Content-Type', contentType);
        return res.send(response.data);
      }
    }

    totalCompressed += originalSize;
    res.set('Content-Type', contentType);
    res.send(response.data);
  } catch (err) {
    res.status(502).json({ error: 'Failed to fetch', message: err.message, url: targetUrl });
  }
});

app.get('/debug-env', (req, res) => {
  res.json({
    DATASTATION_URL: process.env.DATASTATION_URL || 'NOT SET',
    DATASTATION_TOKEN: process.env.DATASTATION_TOKEN ? 'SET' : 'NOT SET',
    NODE_ENV: process.env.NODE_ENV
  });
});

// ============================================
// NOTIFICATIONS API
// ============================================

// GET /api/notifications?phone=xxx&since_id=0&limit=30
app.get('/api/notifications', async (req, res) => {
  try {
    const { phone, since_id, limit } = req.query;
    if (!phone) return res.status(400).json({ error: 'Phone required' });

    // Get user ID from phone
    const { data: user } = await supabase.from('users').select('id').eq('phone', phone).single();
    if (!user) return res.json([]);

    let query = supabase.from('notifications')
      .select('*')
      .or(`target_phone.eq.${phone},target_phone.is.null`)
      .order('id', { ascending: true });

    if (since_id && since_id !== '0') {
      query = query.gt('id', parseInt(since_id));
    }

    const maxLimit = Math.min(parseInt(limit) || 30, 100);
    query = query.limit(maxLimit);

    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json(data || []);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/notifications  { title, body, type, target_phone?, admin_key }
// admin_key must match ADMIN_KEY env var
app.post('/api/notifications', async (req, res) => {
  try {
    const { title, body, type, target_phone, admin_key } = req.body;
    if (!title || !body) return res.status(400).json({ error: 'Title and body required' });
    if (admin_key !== (process.env.ADMIN_KEY || 'datasaver-admin-2024')) {
      return res.status(403).json({ error: 'Invalid admin key' });
    }

    // Store in database
    const row = {
      title,
      body,
      type: type || 'general',
      target_phone: target_phone || null
    };
    const { data, error } = await supabase.from('notifications').insert(row).select().single();
    if (error) return res.status(500).json({ error: error.message });

    // Send via FCM if Firebase is configured
    if (admin) {
      // Build FCM message
      const message = {
        notification: {
          title: title,
          body: body
        },
        data: {
          type: type || 'general',
          click_action: 'OPEN_APP'
        },
        android: {
          priority: 'high',
          notification: {
            channel_id: 'datasaver_push',
            sound: 'default',
            priority: 'high'
          }
        }
      };

      if (target_phone) {
        // Find user's FCM token
        const { data: user } = await supabase.from('users').select('fcm_token').eq('phone', target_phone).single();
        if (user && user.fcm_token) {
          message.token = user.fcm_token;
          await admin.messaging().send(message);
          console.log('FCM sent to', target_phone);
        }
      } else {
        // Broadcast to all users - send to first 500 tokens
        const { data: users } = await supabase.from('users').select('fcm_token').not('fcm_token', 'is', null).limit(500);
        if (users && users.length > 0) {
          // Send to multiple tokens
          const tokens = users.map(u => u.fcm_token).filter(t => t);
          if (tokens.length > 0) {
            message.tokens = tokens;
            try {
              const response = await admin.messaging().sendEachForMulticast(message);
              console.log('FCM broadcast:', response.successCount, 'sent');
            } catch (e) {
              console.log('FCM broadcast error:', e.message);
            }
          }
        }
      }
    }

    res.json({ success: true, notification: data });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// REFERRAL SYSTEM
// ============================================

// GET /api/referrals/stats?phone=xxx
app.get('/api/referrals/stats', async (req, res) => {
  try {
    const { phone } = req.query;
    if (!phone) return res.status(400).json({ error: 'Phone required' });

    const { data: user } = await supabase.from('users').select('id').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    // Count successful referrals
    const { data: referrals, error } = await supabase.from('referrals')
      .select('*')
      .eq('referrer_user_id', user.id);

    if (error) return res.status(500).json({ error: error.message });

    const count = (referrals || []).filter(r => r.status === 'completed').length;
    const totalEarnings = (referrals || []).filter(r => r.status === 'completed')
      .reduce((sum, r) => sum + (r.reward_amount || 0), 0);

    // Get admin-configurable reward per referral (default 500)
    const { data: settings } = await supabase.from('app_settings')
      .select('value')
      .eq('key', 'referral_reward_amount')
      .single();
    const rewardPerRef = settings ? parseInt(settings.value) : 500;

    res.json({
      referral_count: count,
      total_earnings: totalEarnings,
      reward_per_referral: rewardPerRef
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/referrals/apply  { phone, referral_code }
// Called when a new user signs up with a referral code
app.post('/api/referrals/apply', async (req, res) => {
  try {
    const { phone, referral_code } = req.body;
    if (!phone || !referral_code) return res.status(400).json({ error: 'Phone and referral_code required' });

    // Find referrer by their referral code
    const { data: referrer } = await supabase.from('users')
      .select('id, phone')
      .eq('referral_code', referral_code)
      .single();

    if (!referrer) return res.status(404).json({ error: 'Invalid referral code' });
    if (referrer.phone === phone) return res.status(400).json({ error: 'Cannot refer yourself' });

    // Find the new user
    const { data: newUser } = await supabase.from('users')
      .select('id')
      .eq('phone', phone)
      .single();
    if (!newUser) return res.status(404).json({ error: 'User not found' });

    // Check if referral already exists
    const { data: existing } = await supabase.from('referrals')
      .select('id')
      .eq('referred_user_id', newUser.id)
      .single();
    if (existing) return res.status(409).json({ error: 'Referral already applied' });

    // Get reward amount
    const { data: settings } = await supabase.from('app_settings')
      .select('value')
      .eq('key', 'referral_reward_amount')
      .single();
    const rewardAmount = settings ? parseInt(settings.value) : 500;

    // Create referral record
    await supabase.from('referrals').insert({
      referrer_user_id: referrer.id,
      referred_user_id: newUser.id,
      reward_amount: rewardAmount,
      status: 'completed'
    });

    // Credit reward to referrer's wallet
    await supabase.from('users')
      .update({ wallet_balance: supabase.rpc('increment_wallet', { user_id: referrer.id, amount: rewardAmount }) })
      .eq('id', referrer.id);

    // Also try direct update as fallback
    const { data: referrerData } = await supabase.from('users')
      .select('wallet_balance')
      .eq('id', referrer.id)
      .single();
    if (referrerData) {
      const newBal = parseFloat(referrerData.wallet_balance || 0) + rewardAmount;
      await supabase.from('users').update({ wallet_balance: newBal }).eq('id', referrer.id);
    }

    // Log wallet credit
    await supabase.from('wallet_transactions').insert({
      user_id: referrer.id,
      type: 'credit',
      amount: rewardAmount,
      description: 'Referral reward for inviting ' + phone
    });

    res.json({ success: true, message: 'Referral applied! \u20a6' + rewardAmount + ' credited to referrer.' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// PUT /api/referrals/settings  { admin_key, reward_amount }
// Admin endpoint to update referral reward amount
app.put('/api/referrals/settings', async (req, res) => {
  try {
    const { admin_key, reward_amount } = req.body;
    if (admin_key !== (process.env.ADMIN_KEY || 'datasaver-admin-2024')) {
      return res.status(403).json({ error: 'Invalid admin key' });
    }
    if (!reward_amount || reward_amount < 0) return res.status(400).json({ error: 'Invalid reward amount' });

    // Upsert the setting
    await supabase.from('app_settings').upsert(
      { key: 'referral_reward_amount', value: String(reward_amount) },
      { onConflict: 'key' }
    );

    res.json({ success: true, reward_amount });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`DataSaver server running on port ${PORT}`);
  console.log(`API: http://localhost:${PORT}/api/plans`);
  console.log(`Proxy: http://localhost:${PORT}/proxy?url=https://example.com`);
});

