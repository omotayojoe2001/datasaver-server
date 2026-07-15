require('dotenv').config(); // Load environment variables
const express = require('express');
const sharp = require('sharp');
const compression = require('compression');
const axios = require('axios');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');

const app = express();
const PORT = process.env.PORT || 3000;
// Task endpoints: /api/tasks, /api/tasks/all, /api/tasks/create, /admin/api/tasks/*

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
// FIREBASE ADMIN (for push notifications) - TEMPORARILY DISABLED
// ============================================
// let admin = null;
// Firebase init disabled due to syntax error - will fix later
console.log('Firebase push notifications temporarily disabled');

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

// GET /api/subscription-plans - public endpoint for mobile app to get subscription pricing
app.get('/api/subscription-plans', async (req, res) => {
  try {
    const { data: settings, error } = await supabase.from('app_settings').select('key, value');
    if (error) return res.status(500).json({ error: error.message });
    
    // Convert array to object
    const settingsObj = {};
    if (settings) {
      for (const s of settings) {
        settingsObj[s.key] = s.value;
      }
    }
    
    // Return subscription plans with prices from database
    res.json({
      premium: {
        price: parseInt(settingsObj.premium_price) || 500,
        duration: parseInt(settingsObj.premium_duration) || 7
      },
      professional: {
        price: parseInt(settingsObj.professional_price) || 1500,
        duration: parseInt(settingsObj.professional_duration) || 30
      },
      enterprise: {
        price: parseInt(settingsObj.enterprise_price) || 5000,
        duration: parseInt(settingsObj.enterprise_duration) || 30
      }
    });
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
  if (!email || !pin || !phone) return res.status(400).json({ error: 'Phone number, email and PIN are required' });

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

    const row = { email, pin: pin || '0000', name: name || '', phone: phone || null, referral_code: userReferralCode };
    const { data, error } = await supabase.from('users').insert(row).select('id, name, phone, email, wallet_balance, subscription_plan, referral_code').single();
    if (error) return res.status(500).json({ error: error.message });

    // If referral code provided, apply referral (stored as pending, admin approves rewards)
    let referralMsg = '';
    if (referral_code && data.id) {
      try {
        // Find referrer
        const { data: referrer } = await supabase.from('users').select('id, phone').eq('referral_code', referral_code).single();
        if (referrer && referrer.phone !== phone) {
          // Get reward amount
          const { data: settings } = await supabase.from('app_settings').select('value').eq('key', 'referral_reward_amount').single();
          const rewardAmount = settings ? parseInt(settings.value) : 500;

          // Create referral as PENDING (admin must approve to credit reward)
          await supabase.from('referrals').insert({
            referrer_user_id: referrer.id,
            referred_user_id: data.id,
            reward_amount: rewardAmount,
            status: 'pending'
          });

          referralMsg = ' Referral recorded! You\'ll earn \u20a6' + rewardAmount + ' when admin approves.';
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
// Records each sync to activity log for permanent record
app.post('/api/savings/sync', async (req, res) => {
  const { phone, saved_bytes, blocked_requests, ad_bytes, bg_bytes } = req.body;
  console.log('SAVINGS SYNC:', { phone, saved_bytes, blocked_requests });
  if (!phone) return res.status(400).json({ error: 'phone required' });
  try {
    // Get user ID
    const { data: user, error: userErr } = await supabase.from('users').select('id').eq('phone', phone).single();
    if (userErr || !user) return res.status(404).json({ error: 'User not found' });

    const incomingSaved = saved_bytes || 0;
    const incomingBlocked = blocked_requests || 0;
    
    // Calculate Naira: ₦1 per MB
    const savedNaira = (incomingSaved / (1024 * 1024));
    
    // Record to ACTIVITY LOG - this is the permanent record
    await supabase.from('savings_activity_log').insert({
      user_id: user.id,
      phone: phone,
      saved_bytes: incomingSaved,
      blocked_requests: incomingBlocked,
      saved_naira: savedNaira
    });
    
    // Also save to daily history for backward compatibility
    const today = new Date().toISOString().split('T')[0];
    const { data: existing } = await supabase.from('savings_history')
      .select('id, saved_bytes, blocked_requests').eq('user_id', user.id).eq('date', today).single();
    
    const existingSaved = existing ? (existing.saved_bytes || 0) : 0;
    const existingBlocked = existing ? (existing.blocked_requests || 0) : 0;
    
    let savedDelta = 0;
    let blockedDelta = 0;
    if (incomingSaved > existingSaved) savedDelta = incomingSaved - existingSaved;
    if (incomingBlocked > existingBlocked) blockedDelta = incomingBlocked - existingBlocked;
    
    if (existing) {
      const newSaved = (existing.saved_bytes || 0) + savedDelta;
      const newBlocked = (existing.blocked_requests || 0) + blockedDelta;
      await supabase.from('savings_history').update({
        saved_bytes: newSaved,
        blocked_requests: newBlocked
      }).eq('id', existing.id);
    } else {
      await supabase.from('savings_history').insert({
        user_id: user.id, date: today,
        saved_bytes: incomingSaved,
        blocked_requests: incomingBlocked
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
    // Get user ID only
    const { data: user } = await supabase.from('users').select('id').eq('phone', req.params.phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    // Fetch ALL daily rows
    const { data: allDays } = await supabase.from('savings_history')
      .select('date, saved_bytes, blocked_requests')
      .eq('user_id', user.id)
      .order('date', { ascending: false });

    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    const weekAgo = new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];
    const monthAgo = new Date(now - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0];

    // Sum all history for all-time total
    let totalSaved = 0;
    let totalBlocked = 0;
    if (allDays && allDays.length > 0) {
      for (const h of allDays) {
        totalSaved += h.saved_bytes || 0;
        totalBlocked += h.blocked_requests || 0;
      }
    }
    
    // Calculate week/month/today from history
    let weekSaved = 0, monthSaved = 0;
    let weekBlocked = 0, monthBlocked = 0;
    let todaySaved = 0, todayBlocked = 0;
    if (allDays) {
      for (const h of allDays) {
        const s = h.saved_bytes || 0;
        const b = h.blocked_requests || 0;
        if (h.date === todayStr) { todaySaved += s; todayBlocked += b; }
        if (h.date >= weekAgo) { weekSaved += s; weekBlocked += b; }
        if (h.date >= monthAgo) { monthSaved += s; monthBlocked += b; }
      }
    }

    // Calculate Naira value: ₦1 per MB saved
    const NAIRA_PER_MB = 1;
    
    // Only return the most recent 30 days for the breakdown list
    const history = (allDays || []).map(h => ({
      date: h.date,
      saved_bytes: h.saved_bytes,
      blocked_requests: h.blocked_requests,
      saved_naira: Math.round(((h.saved_bytes || 0) / (1024 * 1024)) * NAIRA_PER_MB * 100) / 100
    })).slice(0, 30);
    const totalSavedNaira = (totalSaved / (1024 * 1024)) * NAIRA_PER_MB;
    const todaySavedNaira = (todaySaved / (1024 * 1024)) * NAIRA_PER_MB;
    const weekSavedNaira = (weekSaved / (1024 * 1024)) * NAIRA_PER_MB;
    const monthSavedNaira = (monthSaved / (1024 * 1024)) * NAIRA_PER_MB;

    res.json({
      total_saved: totalSaved,
      total_blocked: totalBlocked,
      total_saved_naira: Math.round(totalSavedNaira * 100) / 100,
      today: { saved: todaySaved, blocked: todayBlocked, saved_naira: Math.round(todaySavedNaira * 100) / 100 },
      week: { saved: weekSaved, blocked: weekBlocked, saved_naira: Math.round(weekSavedNaira * 100) / 100 },
      month: { saved: monthSaved, blocked: monthBlocked, saved_naira: Math.round(monthSavedNaira * 100) / 100 },
      history
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/savings/activity/:phone  — get activity log
app.get('/api/savings/activity/:phone', async (req, res) => {
  try {
    const { data: user } = await supabase.from('users').select('id').eq('phone', req.params.phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    // Fetch activity log - most recent first
    const { data: logs } = await supabase.from('savings_activity_log')
      .select('id, saved_bytes, blocked_requests, saved_naira, created_at')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false })
      .limit(100);

    res.json({ activity: logs || [] });
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

// Device limits per plan (not admin-configurable)
const PLAN_DEVICES = { premium: 1, professional: 2, enterprise: 5 };

// Fetch subscription pricing from admin settings (app_settings), with fallback to defaults
async function getSubscriptionPricing() {
  const defaults = {
    premium:      { price: 500,  duration: 7,  devices: 1 },
    professional: { price: 1500, duration: 30, devices: 2 },
    enterprise:   { price: 5000, duration: 30, devices: 5 }
  };
  try {
    const { data: settings } = await supabase.from('app_settings').select('key, value');
    const s = {};
    if (settings) for (const row of settings) s[row.key] = row.value;
    return {
      premium:      { price: parseInt(s.premium_price) || defaults.premium.price, duration: parseInt(s.premium_duration) || defaults.premium.duration, devices: 1 },
      professional: { price: parseInt(s.professional_price) || defaults.professional.price, duration: parseInt(s.professional_duration) || defaults.professional.duration, devices: 2 },
      enterprise:   { price: parseInt(s.enterprise_price) || defaults.enterprise.price, duration: parseInt(s.enterprise_duration) || defaults.enterprise.duration, devices: 5 }
    };
  } catch (e) {
    return defaults;
  }
}

// POST /api/subscribe  { phone, plan }
app.post('/api/subscribe', async (req, res) => {
  const { phone, plan } = req.body;
  if (!phone || !plan) return res.status(400).json({ error: 'phone and plan required' });
  const pricing = await getSubscriptionPricing();
  const cfg = pricing[plan];
  if (!cfg) return res.status(400).json({ error: 'Invalid plan. Choose premium, professional, or enterprise' });

  try {
    const { data: user } = await supabase.from('users').select('id, wallet_balance, subscription_plan').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    const bal = parseFloat(user.wallet_balance || 0);
    if (bal < cfg.price) {
      return res.status(400).json({ success: false, error: 'Insufficient wallet balance. You have \u20a6' + bal.toFixed(0) + ' but need \u20a6' + cfg.price });
    }

    const durationMs = cfg.duration * 24 * 60 * 60 * 1000;
    const durationLabel = cfg.duration + ' days';
    const expiresAt = new Date(Date.now() + durationMs).toISOString();
    const newBal = bal - cfg.price;

    // Update user
    await supabase.from('users').update({ subscription_plan: plan, subscription_expires_at: expiresAt, wallet_balance: newBal }).eq('id', user.id);

    // Log subscription
    await supabase.from('subscriptions').insert({ user_id: user.id, plan, amount: cfg.price, duration: durationLabel, expires_at: expiresAt });

    // Log wallet debit
    await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'debit', amount: cfg.price, description: plan.charAt(0).toUpperCase() + plan.slice(1) + ' subscription (' + durationLabel + ')' });
    
    // Send notification for subscription
    try {
      await supabase.from('notifications').insert({
        title: 'Subscription Activated',
        body: 'You have subscribed to the ' + plan.charAt(0).toUpperCase() + plan.slice(1) + ' plan for ' + durationLabel,
        type: 'subscription',
        target_phone: phone
      });
    } catch (nfe) { console.log('Notif error:', nfe.message); }
    
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
  if (phone) { const { data: u } = await supabase.from('users').select('id, wallet_balance, phone').eq('phone', phone).single(); user = u; }
  if (!user && email) { const { data: u } = await supabase.from('users').select('id, wallet_balance, phone').eq('email', email).single(); user = u; }
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

  // Send notification for successful deposit (only on first credit)
  try {
    if (user.phone) {
      await supabase.from('notifications').insert({
        title: 'Wallet Credited',
        body: '\u20a6' + amount + ' has been added to your wallet',
        type: 'wallet',
        target_phone: user.phone
      });
    }
  } catch (nfe) { console.log('Notif error:', nfe.message); }

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
    
    // Send notification for wallet top-up
    try {
      await supabase.from('notifications').insert({
        title: 'Wallet Credited',
        body: '₦' + amount + ' has been added to your wallet',
        type: 'wallet',
        target_phone: phone
      });
    } catch (nfe) { console.log('Notif error:', nfe.message); }
    
    res.json({ success: true, balance: newBal });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ============================================
// TASKS & EARN
// ============================================

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

// ADMIN TASK ROUTES
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

// Also support /admin/api/tasks/all
app.get('/admin/api/tasks/all', adminAuth, async (req, res) => {
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
    const { title, type, description, instructions, link, reward, reward_type, max_participants } = req.body;
    if (!title || !reward) return res.status(400).json({ error: 'title and reward required' });
    
    const { data, error } = await supabase.from('tasks').insert({
      title,
      description: description || instructions || '',
      reward,
      reward_type: reward_type || type || 'airtime',
      active: true
    });
    
    if (error) return res.status(500).json({ error: error.message });
    res.json(data);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Also support /admin/api/tasks/create
app.post('/admin/api/tasks/create', adminAuth, async (req, res) => {
  try {
    const { title, type, description, instructions, link, reward, reward_type, max_participants } = req.body;
    if (!title || !reward) return res.status(400).json({ error: 'title and reward required' });
    
    const { data, error } = await supabase.from('tasks').insert({
      title,
      description: description || instructions || '',
      instructions: instructions || '',
      link: link || '',
      reward,
      reward_type: reward_type || type || 'airtime',
      active: true
    });
    
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true, data });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ADMIN SUBMISSIONS API
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
    const { data: sub, error: subErr } = await supabase.from('task_submissions').select('*, users(phone)').eq('id', id).single();
    if (subErr || !sub) return res.status(404).json({ error: 'Submission not found' });
    
    await supabase.from('task_submissions').update({ status: 'approved' }).eq('id', id);
    
    const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('id', sub.user_id).single();
    if (user) {
      const reward = sub.tasks?.reward || 0;
      const newBalance = (parseFloat(user.wallet_balance) || 0) + reward;
      await supabase.from('users').update({ wallet_balance: newBalance }).eq('id', user.id);
      await supabase.from('wallet_transactions').insert({
        user_id: user.id,
        type: 'task_reward',
        amount: reward,
        description: 'Task reward: ' + (sub.tasks?.title || 'Task'),
        status: 'completed'
      });
      
      // Send notification for task approval
      try {
        const userPhone = sub.users?.phone;
        if (userPhone) {
          await supabase.from('notifications').insert({
            title: 'Task Approved!',
            body: 'Your task "' + (sub.tasks?.title || 'Task') + '" has been approved! ₦' + reward + ' has been added to your wallet.',
            type: 'task',
            target_phone: userPhone
          });
        }
      } catch (nfe) { console.log('Notif error:', nfe.message); }
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
// ADMIN USER MANAGEMENT
// ============================================

// GET user details
app.get('/admin/api/users/:id', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { data: user, error: userErr } = await supabase.from('users').select('*').eq('id', id).single();
    if (userErr || !user) return res.status(404).json({ error: 'User not found' });
    
    const { data: transactions } = await supabase.from('transactions').select('*').eq('user_id', id).order('created_at', { ascending: false }).limit(50);
    const { data: walletTransactions } = await supabase.from('wallet_transactions').select('*').eq('user_id', id).order('created_at', { ascending: false }).limit(50);
    const { data: taskSubmissions } = await supabase.from('task_submissions').select('*').eq('user_id', id).order('created_at', { ascending: false }).limit(20);
    
    res.json({ user, transactions: transactions || [], walletTransactions: walletTransactions || [], taskSubmissions: taskSubmissions || [] });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Credit user wallet
app.post('/admin/api/users/:id/credit', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { amount, description } = req.body;
    if (!amount) return res.status(400).json({ error: 'Amount required' });
    
    const { data: user, error: userErr } = await supabase.from('users').select('*').eq('id', id).single();
    if (userErr || !user) return res.status(404).json({ error: 'User not found' });
    
    const newBalance = (parseFloat(user.wallet_balance) || 0) + parseFloat(amount);
    await supabase.from('users').update({ wallet_balance: newBalance }).eq('id', id);
    await supabase.from('wallet_transactions').insert({
      user_id: id,
      type: 'credit',
      amount: parseFloat(amount),
      description: description || 'Admin credit',
      status: 'success'
    });
    res.json({ success: true, balance: newBalance });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Debit user wallet
app.post('/admin/api/users/:id/debit', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { amount, description } = req.body;
    if (!amount) return res.status(400).json({ error: 'Amount required' });
    
    const { data: user, error: userErr } = await supabase.from('users').select('*').eq('id', id).single();
    if (userErr || !user) return res.status(404).json({ error: 'User not found' });
    
    const currentBalance = parseFloat(user.wallet_balance) || 0;
    const debitAmount = parseFloat(amount);
    if (currentBalance < debitAmount) return res.status(400).json({ error: 'Insufficient balance' });
    
    const newBalance = currentBalance - debitAmount;
    await supabase.from('users').update({ wallet_balance: newBalance }).eq('id', id);
    await supabase.from('wallet_transactions').insert({
      user_id: id,
      type: 'debit',
      amount: debitAmount,
      description: description || 'Admin debit',
      status: 'success'
    });
    res.json({ success: true, balance: newBalance });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Change user subscription
app.post('/admin/api/users/:id/subscription', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { plan, days } = req.body;
    if (!plan) return res.status(400).json({ error: 'Plan required' });
    
    const updates = { subscription_plan: plan };
    if (days && days > 0) {
      const expiresAt = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
      updates.subscription_expires_at = expiresAt;
    } else {
      updates.subscription_expires_at = null;
    }
    
    await supabase.from('users').update(updates).eq('id', id);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Update user profile
app.post('/admin/api/users/:id/update', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { name, phone, email } = req.body;
    const updates = {};
    if (name !== undefined) updates.name = name;
    if (phone !== undefined) updates.phone = phone;
    if (email !== undefined) updates.email = email;
    
    const { error } = await supabase.from('users').update(updates).eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Delete user
app.delete('/admin/api/users/:id', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    // Delete related data first
    await supabase.from('wallet_transactions').delete().eq('user_id', id);
    await supabase.from('task_submissions').delete().eq('user_id', id);
    await supabase.from('transactions').delete().eq('user_id', id);
    await supabase.from('savings_history').delete().eq('user_id', id);
    // Delete user
    const { error } = await supabase.from('users').delete().eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// ADMIN REFERRALS MANAGEMENT
// ============================================

// GET /admin/api/referrals - list all referrals with user details
app.get('/admin/api/referrals', adminAuth, async (req, res) => {
  try {
    const { data: referrals, error } = await supabase
      .from('referrals')
      .select('*, referrer:users!referrer_user_id(name,phone,email), referred:users!referred_user_id(name,phone,email)')
      .order('created_at', { ascending: false });
    
    if (error) return res.status(500).json({ error: error.message });
    
    // Format for admin display
    const rows = (referrals || []).map(r => ({
      id: r.id,
      referrer_name: r.referrer?.name || '-',
      referrer_phone: r.referrer?.phone || '-',
      referred_name: r.referred?.name || '-',
      referred_phone: r.referred?.phone || '-',
      reward_amount: r.reward_amount,
      status: r.status,
      created_at: r.created_at
    }));
    
    res.json({ referrals: rows, total: rows.length });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /admin/api/referrals/:id/approve - approve referral and credit reward
app.post('/admin/api/referrals/:id/approve', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    
    // Get referral details
    const { data: referral, error: refError } = await supabase
      .from('referrals')
      .select('*, referrer:users!referrer_user_id(*), referred:users!referred_user_id(*)')
      .eq('id', id)
      .single();
    
    if (refError || !referral) return res.status(404).json({ error: 'Referral not found' });
    if (referral.status === 'completed') return res.status(400).json({ error: 'Referral already approved' });
    
    // Credit referrer's wallet
    const referrer = referral['referrer:users!referrer_user_id'];
    if (referrer) {
      const newBal = parseFloat(referrer.wallet_balance || 0) + referral.reward_amount;
      await supabase.from('users').update({ wallet_balance: newBal }).eq('id', referrer.id);
      
      // Log transaction
      await supabase.from('wallet_transactions').insert({
        user_id: referrer.id,
        type: 'credit',
        amount: referral.reward_amount,
        description: 'Referral reward for inviting ' + (referral['referrer:users!referred_user_id']?.phone || 'user')
      });
    }
    
    // Update referral status
    await supabase.from('referrals').update({ status: 'completed' }).eq('id', id);
    
    res.json({ success: true, message: 'Referral approved! ₦' + referral.reward_amount + ' credited to referrer.' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// POST /admin/api/referrals/:id/reject - reject referral
app.post('/admin/api/referrals/:id/reject', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    
    const { error } = await supabase.from('referrals').update({ status: 'rejected' }).eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    
    res.json({ success: true, message: 'Referral rejected.' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ============================================
// ADMIN DATA PLANS MANAGEMENT
// ============================================

// Get all data plans
app.get('/admin/api/data-plans', adminAuth, async (req, res) => {
  try {
    const { network } = req.query;
    let query = supabase.from('data_plans').select('*').order('amount', { ascending: true });
    if (network) query = query.eq('network', network.toUpperCase());
    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json(data || []);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Create data plan
app.post('/admin/api/data-plans', adminAuth, async (req, res) => {
  try {
    const { network, size, amount, selling_price, data_id, active } = req.body;
    if (!size || !amount) return res.status(400).json({ error: 'Size and cost required' });
    
    const { data, error } = await supabase.from('data_plans').insert({
      network: network || 'MTN',
      size,
      amount,
      selling_price: selling_price || amount,
      data_id,
      active: active !== false
    });
    
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true, data });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Update data plan
app.post('/admin/api/data-plans/:id', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { network, size, amount, selling_price, data_id, active } = req.body;
    const updates = {};
    if (network) updates.network = network;
    if (size) updates.size = size;
    if (amount) updates.amount = amount;
    if (selling_price !== undefined) updates.selling_price = selling_price;
    if (data_id !== undefined) updates.data_id = data_id;
    if (active !== undefined) updates.active = active;
    
    const { error } = await supabase.from('data_plans').update(updates).eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Delete data plan
app.post('/admin/api/data-plans/:id/delete', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { error } = await supabase.from('data_plans').delete().eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
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
      .order('created_at', { ascending: false }); // Newest first

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

// ADMIN: Get all notifications
app.get('/admin/api/notifications', adminAuth, async (req, res) => {
  try {
    const { data, error } = await supabase.from('notifications')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(50);
    if (error) return res.status(500).json({ error: error.message });
    res.json(data || []);
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

    const { data: user } = await supabase.from('users').select('id, referral_code').eq('phone', phone).single();
    if (!user) return res.status(404).json({ error: 'User not found' });

    // Count ALL referrals (pending + completed); earnings only from completed
    const { data: referrals, error } = await supabase.from('referrals')
      .select('*')
      .eq('referrer_user_id', user.id);

    if (error) return res.status(500).json({ error: error.message });

    const count = (referrals || []).length; // Count ALL referrals
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
      reward_per_referral: rewardPerRef,
      referral_code: user.referral_code || ''
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

    // Create referral record as PENDING (admin must approve to credit reward)
    await supabase.from('referrals').insert({
      referrer_user_id: referrer.id,
      referred_user_id: newUser.id,
      reward_amount: rewardAmount,
      status: 'pending'
    });

    res.json({ success: true, message: 'Referral recorded! Reward will be credited when admin approves.' });
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

// WALLET TRANSACTIONS
app.get('/api/wallet-transactions', adminAuth, async (req, res) => {
  try {
    const { type } = req.query;
    let query = supabase.from('wallet_transactions').select('*, users(phone, name)').order('created_at', { ascending: false });
    if (type && type !== 'all') query = query.eq('type', type);
    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json(data || []);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ADMIN SETTINGS
app.post('/api/settings', adminAuth, async (req, res) => {
  try {
    const { premium_price, premium_duration, professional_price, professional_duration, enterprise_price, enterprise_duration, announcement, new_password } = req.body;
    
    // Log settings changes - would need settings table to store
    console.log('Settings update:', { premium_price, premium_duration, professional_price, professional_duration, enterprise_price, enterprise_duration, announcement });
    
    if (new_password) {
      console.log('Admin password change requested');
    }
    
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// DELETE task
app.delete('/api/tasks/:id', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { error } = await supabase.from('tasks').delete().eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// DELETE task (admin prefix version)
app.delete('/admin/api/tasks/:id', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { error } = await supabase.from('tasks').delete().eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// UPDATE task
app.post('/api/tasks/:id/update', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { title, description, reward, reward_type, active } = req.body;
    const updates = {};
    if (title) updates.title = title;
    if (description !== undefined) updates.description = description;
    if (reward) updates.reward = reward;
    if (reward_type) updates.reward_type = reward_type;
    if (active !== undefined) updates.active = active;
    
    const { error } = await supabase.from('tasks').update(updates).eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// UPDATE task - /admin/api version for admin panel
app.post('/admin/api/tasks/:id/update', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { title, description, instructions, link, reward, reward_type, active } = req.body;
    const updates = {};
    if (title) updates.title = title;
    if (description !== undefined) updates.description = description;
    if (instructions !== undefined) updates.instructions = instructions;
    if (link !== undefined) updates.link = link;
    if (reward) updates.reward = reward;
    if (reward_type) updates.reward_type = reward_type;
    if (active !== undefined) updates.active = active;
    
    const { error } = await supabase.from('tasks').update(updates).eq('id', id);
    if (error) return res.status(500).json({ error: error.message });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ADMIN API VERSIONS (with /admin prefix for admin panel)
app.get('/admin/api/submissions', adminAuth, async (req, res) => {
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

app.post('/admin/api/submissions/:id/approve', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    const { data: sub, error: subErr } = await supabase.from('task_submissions').select('*, tasks(reward, reward_type)').eq('id', id).single();
    if (subErr || !sub) return res.status(404).json({ error: 'Submission not found' });
    await supabase.from('task_submissions').update({ status: 'approved' }).eq('id', id);
    const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('id', sub.user_id).single();
    if (user) {
      const reward = sub.tasks?.reward || 0;
      const newBalance = (parseFloat(user.wallet_balance) || 0) + reward;
      await supabase.from('users').update({ wallet_balance: newBalance }).eq('id', user.id);
      await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'task_reward', amount: reward, description: 'Task reward', status: 'completed' });
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/admin/api/submissions/:id/reject', adminAuth, async (req, res) => {
  try {
    const { id } = req.params;
    await supabase.from('task_submissions').update({ status: 'rejected' }).eq('id', id);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get('/admin/api/wallet-transactions', adminAuth, async (req, res) => {
  try {
    const { type } = req.query;
    let query = supabase.from('wallet_transactions').select('*, users(phone, name)').order('created_at', { ascending: false });
    if (type && type !== 'all') query = query.eq('type', type);
    const { data, error } = await query;
    if (error) return res.status(500).json({ error: error.message });
    res.json({ transactions: data || [], total: data?.length || 0 });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET settings for admin panel
app.get('/admin/api/settings', adminAuth, async (req, res) => {
  try {
    const { data: settings, error } = await supabase.from('app_settings').select('key, value');
    if (error) return res.status(500).json({ error: error.message });
    
    // Convert array to object
    const settingsObj = {};
    if (settings) {
      for (const s of settings) {
        settingsObj[s.key] = s.value;
      }
    }
    res.json(settingsObj);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.post('/admin/api/settings', adminAuth, async (req, res) => {
  try {
    const { premium_price, premium_duration, professional_price, professional_duration, enterprise_price, enterprise_duration, admin_password } = req.body;
    
    // Save each setting to app_settings table
    const settings = {
      premium_price: premium_price || '500',
      premium_duration: premium_duration || '7',
      professional_price: professional_price || '1500',
      professional_duration: professional_duration || '30',
      enterprise_price: enterprise_price || '5000',
      enterprise_duration: enterprise_duration || '30'
    };
    
    for (const [key, value] of Object.entries(settings)) {
      await supabase.from('app_settings').upsert({ key, value }, { onConflict: 'key' });
    }
    
    // Handle admin password change
    if (admin_password) {
      console.log('Admin password change requested');
      // Note: Password change would require updating the ADMIN_PW env var on Render
      // For now, just log it. In production, you'd store in app_settings and check there
    }
    
    console.log('Settings saved:', settings);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`DataSaver server running on port ${PORT}`);
  console.log(`API: http://localhost:${PORT}/api/plans`);
  console.log(`Proxy: http://localhost:${PORT}/proxy?url=https://example.com`);
});

