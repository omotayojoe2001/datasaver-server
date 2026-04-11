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

// POST /api/register  { phone, pin }
app.post('/api/register', async (req, res) => {
  const { phone, pin } = req.body;
  if (!phone) return res.status(400).json({ error: 'Phone required' });

  try {
    // Check if exists
    const { data: existing } = await supabase.from('users').select('id').eq('phone', phone).single();
    if (existing) return res.json({ user_id: existing.id, message: 'Welcome back' });

    const { data, error } = await supabase.from('users')
      .insert({ phone, pin: pin || '0000' })
      .select('id')
      .single();
    if (error) return res.status(500).json({ error: error.message });
    res.json({ user_id: data.id, message: 'Registered' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// GET /api/user/:phone
app.get('/api/user/:phone', async (req, res) => {
  try {
    const { data, error } = await supabase.from('users')
      .select('id, phone, wallet_balance, created_at')
      .eq('phone', req.params.phone)
      .single();
    if (error) return res.status(404).json({ error: 'User not found' });
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
// WALLET + PAYSTACK
// ============================================

// POST /api/wallet/initialize  { phone, amount, email }
app.post('/api/wallet/initialize', async (req, res) => {
  const { phone, amount, email } = req.body;
  if (!phone || !amount) return res.status(400).json({ error: 'phone and amount required' });
  try {
    const paystackRes = await axios.post('https://api.paystack.co/transaction/initialize', {
      email: email || phone + '@datasaver.app',
      amount: Math.round(parseFloat(amount) * 100),
      currency: 'NGN',
      metadata: { phone, type: 'wallet_topup' },
      callback_url: 'https://datasaver-server.onrender.com/api/wallet/callback'
    }, {
      headers: { 'Authorization': 'Bearer ' + PAYSTACK_SECRET, 'Content-Type': 'application/json' }
    });
    res.json({ success: true, authorization_url: paystackRes.data.data.authorization_url, reference: paystackRes.data.data.reference });
  } catch (e) {
    const msg = e.response ? JSON.stringify(e.response.data) : e.message;
    res.status(500).json({ error: 'Paystack init failed: ' + msg });
  }
});

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
      const phone = txn.metadata.phone;
      const amount = txn.amount / 100;
      const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single();
      if (user) {
        const newBal = parseFloat(user.wallet_balance || 0) + amount;
        await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
        await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount, description: 'Paystack top-up (ref: ' + ref + ')' });
      }
      res.send('<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{font-family:sans-serif;text-align:center;padding:40px;background:#f0f4f8}h1{color:#43A047}p{color:#333;font-size:18px}.btn{display:inline-block;margin-top:20px;padding:12px 32px;background:#1565C0;color:#fff;text-decoration:none;border-radius:8px;font-size:16px}</style></head><body><h1>Payment Successful!</h1><p>\u20a6' + amount + ' has been added to your wallet.</p><p>You can close this page and return to the app.</p></body></html>');
    } else {
      res.send('<h2>Payment not successful: ' + txn.status + '</h2>');
    }
  } catch (e) {
    res.send('<h2>Verification failed</h2><p>' + e.message + '</p>');
  }
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
      const phone = txn.metadata.phone;
      const amount = txn.amount / 100;
      const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('phone', phone).single();
      if (user) {
        // Check if already credited
        const { data: existing } = await supabase.from('wallet_transactions').select('id').ilike('description', '%' + reference + '%').single();
        if (!existing) {
          const newBal = parseFloat(user.wallet_balance || 0) + amount;
          await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
          await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount, description: 'Paystack top-up (ref: ' + reference + ')' });
          return res.json({ success: true, balance: newBal });
        }
        return res.json({ success: true, balance: parseFloat(user.wallet_balance || 0), message: 'Already credited' });
      }
      return res.status(404).json({ error: 'User not found' });
    }
    res.json({ success: false, status: txn.status });
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
