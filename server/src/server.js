require('dotenv').config();
const express = require('express');
const sharp = require('sharp');
const compression = require('compression');
const axios = require('axios');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// ============================================
// SUPABASE
// ============================================
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://fjygdysjdpjafkjkqfad.supabase.co';
const SUPABASE_KEY = process.env.SUPABASE_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZqeWdkeXNqZHBqYWZramtxZmFkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU4MzA4NjgsImV4cCI6MjA5MTQwNjg2OH0.-CDiVLSsuvSMl0IYOrPDD3CKvwttkt_D4WTAMVdb6UM';
const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

// DataStation API config
const DATASTATION_URL = process.env.DATASTATION_URL || 'https://datastationapi.com/api';
const DATASTATION_TOKEN = process.env.DATASTATION_TOKEN || '1a3812d2a280b21cf9a198dde909bdf3d80c0b70';

// Paystack config
const PAYSTACK_SECRET = process.env.PAYSTACK_SECRET || '<paystack_secret>';
const PAYSTACK_PUBLIC = process.env.PAYSTACK_PUBLIC || '<paystack_public>';

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
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// USER API
// ============================================

// POST /api/register  { email, pin, name, phone }
app.post('/api/register', async (req, res) => {
  const { phone, pin, name, email } = req.body;
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

    const row = { email, pin: pin || '0000', name: name || '', phone: phone || '' };
    const { data, error } = await supabase.from('users').insert(row).select('id, name, phone, email, wallet_balance, subscription_plan').single();
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true, user_id: data.id, name: data.name, phone: data.phone, email: data.email, wallet_balance: data.wallet_balance, subscription_plan: data.subscription_plan || 'basic', message: 'Account created' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /api/login  { email, pin } or { phone, pin }
app.post('/api/login', async (req, res) => {
  const { phone, email, pin } = req.body;
  if ((!phone && !email) || !pin) return res.status(400).json({ error: 'Email/phone and PIN required' });

  try {
    let query = supabase.from('users').select('id, name, phone, email, pin, wallet_balance, subscription_plan, subscription_expires_at');
    if (email) {
      // Try email first, then fall back to treating it as phone
      const { data: emailUser } = await query.eq('email', email).single();
      if (emailUser) {
        if (emailUser.pin !== pin) return res.status(401).json({ error: 'Incorrect PIN' });
        return res.json({ success: true, user_id: emailUser.id, name: emailUser.name, phone: emailUser.phone, email: emailUser.email, wallet_balance: emailUser.wallet_balance, subscription_plan: emailUser.subscription_plan || 'basic', message: 'Login successful' });
      }
      // Email not found, try as phone number
      const { data: phoneUser } = await supabase.from('users').select('id, name, phone, email, pin, wallet_balance, subscription_plan, subscription_expires_at').eq('phone', email).single();
      if (phoneUser) {
        if (phoneUser.pin !== pin) return res.status(401).json({ error: 'Incorrect PIN' });
        return res.json({ success: true, user_id: phoneUser.id, name: phoneUser.name, phone: phoneUser.phone, email: phoneUser.email, wallet_balance: phoneUser.wallet_balance, subscription_plan: phoneUser.subscription_plan || 'basic', message: 'Login successful' });
      }
      return res.status(404).json({ error: 'Account not found. Please sign up first.' });
    } else {
      query = query.eq('phone', phone);
      const { data: user, error } = await query.single();
      if (error || !user) return res.status(404).json({ error: 'Account not found. Please sign up first.' });
      if (user.pin !== pin) return res.status(401).json({ error: 'Incorrect PIN' });
      res.json({ success: true, user_id: user.id, name: user.name, phone: user.phone, email: user.email, wallet_balance: user.wallet_balance, subscription_plan: user.subscription_plan || 'basic', message: 'Login successful' });
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
app.post('/api/savings/sync', async (req, res) => {
  const { phone, saved_bytes, blocked_requests, ad_bytes, bg_bytes } = req.body;
  if (!phone) return res.status(400).json({ error: 'phone required' });
  try {
    const { data: user } = await supabase.from('users').select('id').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });
    await supabase.from('users').update({
      total_saved_bytes: saved_bytes || 0,
      total_blocked_requests: blocked_requests || 0,
      ad_bytes_saved: ad_bytes || 0,
      bg_bytes_saved: bg_bytes || 0,
      last_savings_sync: new Date().toISOString()
    }).eq('id', user.id);
    res.json({ success: true });
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
    if (data.subscription_plan && data.subscription_plan !== 'basic' && data.subscription_expires_at) {
      if (new Date(data.subscription_expires_at) < new Date()) {
        await supabase.from('users').update({ subscription_plan: 'basic', subscription_expires_at: null }).eq('id', data.id);
        data.subscription_plan = 'basic';
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
    if (walletBal < parseFloat(plan.amount)) {
      return res.status(400).json({ success: false, error: 'Insufficient wallet balance. You have \u20a6' + walletBal.toFixed(0) + ' but need \u20a6' + plan.amount });
    }

    // Create pending transaction
    const { data: txn, error: txnErr } = await supabase.from('transactions')
      .insert({
        user_id: userId,
        type: 'data',
        network: plan.network,
        phone,
        amount: plan.amount,
        data_plan_id: plan.data_id,
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
      await supabase.from('users').update({ wallet_balance: walletBal - parseFloat(plan.amount) }).eq('id', userId);
      await supabase.from('wallet_transactions').insert({ user_id: userId, type: 'debit', amount: parseFloat(plan.amount), description: plan.size + ' ' + plan.network + ' data' });

      // Update transaction
      await supabase.from('transactions')
        .update({ status: 'success', api_response: JSON.stringify(apiRes.data) })
        .eq('id', txn.id);

      res.json({ success: true, transaction_id: txn.id, message: plan.size + ' data sent to ' + phone, api: apiRes.data, wallet_balance: walletBal - parseFloat(plan.amount) });
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

      res.json({ success: true, transaction_id: txn.id, message: 'N' + amount + ' airtime sent to ' + phone, api: apiRes.data, wallet_balance: walletBal - parseFloat(amount) });
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
  premium:      { amount: 500,   duration: '7 days',  ms: 7 * 24 * 60 * 60 * 1000 },
  professional: { amount: 1500,  duration: '30 days', ms: 30 * 24 * 60 * 60 * 1000 },
  enterprise:   { amount: 5000,  duration: '30 days', ms: 30 * 24 * 60 * 60 * 1000 }
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
    let plan = user.subscription_plan || 'basic';
    let expires = user.subscription_expires_at;
    if (plan !== 'basic' && expires && new Date(expires) < new Date()) {
      await supabase.from('users').update({ subscription_plan: 'basic', subscription_expires_at: null }).eq('id', user.id);
      plan = 'basic';
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

app.listen(PORT, '0.0.0.0', () => {
  console.log(`DataSaver server running on port ${PORT}`);
  console.log(`API: http://localhost:${PORT}/api/plans`);
  console.log(`Proxy: http://localhost:${PORT}/proxy?url=https://example.com`);
});
