const express = require('express');
const router = express.Router();

const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'datasaver2024';

// Auth middleware
function auth(req, res, next) {
  const pw = req.headers['x-admin-password'] || req.query.pw;
  if (pw !== ADMIN_PASSWORD) return res.status(401).json({ error: 'Unauthorized' });
  next();
}

module.exports = function(supabase) {

  // ============ DASHBOARD ============
  router.get('/api/dashboard', auth, async (req, res) => {
    try {
      const now = new Date();
      const todayStr = now.toISOString().split('T')[0];
      const weekAgo = new Date(now - 7*24*60*60*1000).toISOString();

      // Total users
      const { count: totalUsers } = await supabase.from('users').select('*', { count: 'exact', head: true });

      // Signups today
      const { count: signupsToday } = await supabase.from('users').select('*', { count: 'exact', head: true }).gte('created_at', todayStr);

      // Active subscriptions (not basic, not expired)
      const { data: activeSubs } = await supabase.from('users').select('subscription_plan').neq('subscription_plan', 'basic').not('subscription_plan', 'is', null).gte('subscription_expires_at', now.toISOString());

      // Revenue today (wallet debits = purchases + subscriptions)
      const { data: todayTxns } = await supabase.from('wallet_transactions').select('amount, type').eq('type', 'debit').gte('created_at', todayStr);
      let revenueToday = 0;
      if (todayTxns) todayTxns.forEach(t => revenueToday += parseFloat(t.amount || 0));

      // Revenue this week
      const { data: weekTxns } = await supabase.from('wallet_transactions').select('amount, type').eq('type', 'debit').gte('created_at', weekAgo);
      let revenueWeek = 0;
      if (weekTxns) weekTxns.forEach(t => revenueWeek += parseFloat(t.amount || 0));

      // Total revenue all time
      const { data: allDebits } = await supabase.from('wallet_transactions').select('amount').eq('type', 'debit');
      let revenueTotal = 0;
      if (allDebits) allDebits.forEach(t => revenueTotal += parseFloat(t.amount || 0));

      // Total deposits (money in)
      const { data: allCredits } = await supabase.from('wallet_transactions').select('amount').eq('type', 'credit').eq('status', 'success');
      let depositsTotal = 0;
      if (allCredits) allCredits.forEach(t => depositsTotal += parseFloat(t.amount || 0));

      // Recent transactions (last 10)
      const { data: recentTxns } = await supabase.from('transactions').select('*').order('created_at', { ascending: false }).limit(10);

      // Recently onboarded (last 10 users)
      const { data: recentUsers } = await supabase.from('users').select('id, name, phone, email, subscription_plan, created_at').order('created_at', { ascending: false }).limit(10);

      // Pending task submissions
      const { count: pendingTasks } = await supabase.from('task_submissions').select('*', { count: 'exact', head: true }).eq('status', 'pending');

      res.json({
        totalUsers: totalUsers || 0,
        signupsToday: signupsToday || 0,
        activeSubscriptions: activeSubs ? activeSubs.length : 0,
        revenueToday,
        revenueWeek,
        revenueTotal,
        depositsTotal,
        profit: depositsTotal - revenueTotal,
        pendingTasks: pendingTasks || 0,
        recentTransactions: recentTxns || [],
        recentUsers: recentUsers || []
      });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ USERS ============
  router.get('/api/users', auth, async (req, res) => {
    try {
      const { search, page } = req.query;
      const limit = 50;
      const offset = ((parseInt(page) || 1) - 1) * limit;
      let query = supabase.from('users').select('id, name, phone, email, wallet_balance, subscription_plan, subscription_expires_at, created_at, total_saved_bytes, total_blocked_requests', { count: 'exact' });
      if (search) {
        query = query.or(`name.ilike.%${search}%,phone.ilike.%${search}%,email.ilike.%${search}%`);
      }
      query = query.order('created_at', { ascending: false }).range(offset, offset + limit - 1);
      const { data, count, error } = await query;
      if (error) return res.status(500).json({ error: error.message });
      res.json({ users: data || [], total: count || 0 });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.get('/api/users/:id', auth, async (req, res) => {
    try {
      const { data: user } = await supabase.from('users').select('*').eq('id', req.params.id).single();
      if (!user) return res.status(404).json({ error: 'User not found' });
      // Get user transactions
      const { data: txns } = await supabase.from('transactions').select('*').eq('user_id', user.id).order('created_at', { ascending: false }).limit(20);
      // Get wallet transactions
      const { data: walletTxns } = await supabase.from('wallet_transactions').select('*').eq('user_id', user.id).order('created_at', { ascending: false }).limit(20);
      // Get task submissions
      const { data: taskSubs } = await supabase.from('task_submissions').select('*').eq('user_id', user.id).order('created_at', { ascending: false });
      res.json({ user, transactions: txns || [], walletTransactions: walletTxns || [], taskSubmissions: taskSubs || [] });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/users/:id/update', auth, async (req, res) => {
    try {
      const updates = req.body;
      delete updates.id;
      const { error } = await supabase.from('users').update(updates).eq('id', req.params.id);
      if (error) return res.status(500).json({ error: error.message });
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/users/:id/credit', auth, async (req, res) => {
    try {
      const { amount, description } = req.body;
      const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('id', req.params.id).single();
      if (!user) return res.status(404).json({ error: 'User not found' });
      const newBal = parseFloat(user.wallet_balance || 0) + parseFloat(amount);
      await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
      await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'credit', amount: parseFloat(amount), status: 'success', description: description || 'Admin credit' });
      res.json({ success: true, balance: newBal });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/users/:id/debit', auth, async (req, res) => {
    try {
      const { amount, description } = req.body;
      const { data: user } = await supabase.from('users').select('id, wallet_balance').eq('id', req.params.id).single();
      if (!user) return res.status(404).json({ error: 'User not found' });
      const newBal = parseFloat(user.wallet_balance || 0) - parseFloat(amount);
      await supabase.from('users').update({ wallet_balance: newBal }).eq('id', user.id);
      await supabase.from('wallet_transactions').insert({ user_id: user.id, type: 'debit', amount: parseFloat(amount), description: description || 'Admin debit' });
      res.json({ success: true, balance: newBal });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/users/:id/subscription', auth, async (req, res) => {
    try {
      const { plan, days } = req.body;
      const expires = days ? new Date(Date.now() + days * 24*60*60*1000).toISOString() : null;
      await supabase.from('users').update({ subscription_plan: plan || 'basic', subscription_expires_at: expires }).eq('id', req.params.id);
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.delete('/api/users/:id', auth, async (req, res) => {
    try {
      await supabase.from('task_submissions').delete().eq('user_id', req.params.id);
      await supabase.from('wallet_transactions').delete().eq('user_id', req.params.id);
      await supabase.from('transactions').delete().eq('user_id', req.params.id);
      await supabase.from('savings_history').delete().eq('user_id', req.params.id);
      await supabase.from('subscriptions').delete().eq('user_id', req.params.id);
      await supabase.from('users').delete().eq('id', req.params.id);
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ TRANSACTIONS ============
  router.get('/api/transactions', auth, async (req, res) => {
    try {
      const { status, type, page } = req.query;
      const limit = 50;
      const offset = ((parseInt(page) || 1) - 1) * limit;
      let query = supabase.from('transactions').select('*', { count: 'exact' });
      if (status) query = query.eq('status', status);
      if (type) query = query.eq('type', type);
      query = query.order('created_at', { ascending: false }).range(offset, offset + limit - 1);
      const { data, count, error } = await query;
      if (error) return res.status(500).json({ error: error.message });
      res.json({ transactions: data || [], total: count || 0 });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ SUBSCRIPTIONS ============
  router.get('/api/subscriptions', auth, async (req, res) => {
    try {
      const { data } = await supabase.from('users').select('id, name, phone, email, subscription_plan, subscription_expires_at, wallet_balance').neq('subscription_plan', 'basic').not('subscription_plan', 'is', null).order('subscription_expires_at', { ascending: false });
      // Revenue by plan
      const { data: subHistory } = await supabase.from('subscriptions').select('plan, amount, created_at').order('created_at', { ascending: false }).limit(100);
      res.json({ activeUsers: data || [], history: subHistory || [] });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ TASKS ============
  router.get('/api/tasks/all', auth, async (req, res) => {
    try {
      const { data } = await supabase.from('tasks').select('*').order('created_at', { ascending: false });
      res.json(data || []);
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/tasks/create', auth, async (req, res) => {
    try {
      const { title, description, instructions, link, type, reward, reward_type, active, max_participants } = req.body;
      const { data, error } = await supabase.from('tasks').insert({ title, description, instructions, link, type: type || 'general', reward: reward || 0, reward_type: reward_type || 'airtime', active: active !== false, max_participants: max_participants || 0 }).select().single();
      if (error) return res.status(500).json({ error: error.message });
      res.json({ success: true, task: data });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/tasks/:id/update', auth, async (req, res) => {
    try {
      const updates = req.body;
      delete updates.id;
      const { error } = await supabase.from('tasks').update(updates).eq('id', req.params.id);
      if (error) return res.status(500).json({ error: error.message });
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.delete('/api/tasks/:id', auth, async (req, res) => {
    try {
      await supabase.from('task_submissions').delete().eq('task_id', req.params.id);
      await supabase.from('tasks').delete().eq('id', req.params.id);
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ TASK SUBMISSIONS ============
  router.get('/api/submissions', auth, async (req, res) => {
    try {
      const { status } = req.query;
      let query = supabase.from('task_submissions').select('*, users(name, phone, email), tasks(title, reward, reward_type)');
      if (status) query = query.eq('status', status);
      query = query.order('created_at', { ascending: false }).limit(100);
      const { data, error } = await query;
      if (error) return res.status(500).json({ error: error.message });
      res.json(data || []);
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/submissions/:id/approve', auth, async (req, res) => {
    try {
      await supabase.from('task_submissions').update({ status: 'approved' }).eq('id', req.params.id);
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/submissions/:id/reject', auth, async (req, res) => {
    try {
      await supabase.from('task_submissions').update({ status: 'rejected' }).eq('id', req.params.id);
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ WALLET TRANSACTIONS ============
  router.get('/api/wallet-transactions', auth, async (req, res) => {
    try {
      const { type, page } = req.query;
      const limit = 50;
      const offset = ((parseInt(page) || 1) - 1) * limit;
      let query = supabase.from('wallet_transactions').select('*, users(name, phone)', { count: 'exact' });
      if (type) query = query.eq('type', type);
      query = query.order('created_at', { ascending: false }).range(offset, offset + limit - 1);
      const { data, count, error } = await query;
      if (error) return res.status(500).json({ error: error.message });
      res.json({ transactions: data || [], total: count || 0 });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ DATA PLANS (PRICING) ============
  router.get('/api/data-plans', auth, async (req, res) => {
    try {
      let query = supabase.from('data_plans').select('*').order('network').order('amount', { ascending: true });
      if (req.query.network) query = query.eq('network', req.query.network);
      const { data, error } = await query;
      if (error) return res.status(500).json({ error: error.message });
      res.json(data || []);
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/data-plans', auth, async (req, res) => {
    try {
      const { network, size, amount, selling_price, data_id, active } = req.body;
      const { error } = await supabase.from('data_plans').insert({ network, size, amount, selling_price: selling_price || 0, data_id, active: active !== false });
      if (error) return res.status(500).json({ error: error.message });
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/data-plans/:id', auth, async (req, res) => {
    try {
      const updates = req.body;
      delete updates.id;
      delete updates.duration;
      const { error } = await supabase.from('data_plans').update(updates).eq('id', req.params.id);
      if (error) return res.status(500).json({ error: error.message });
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  router.post('/api/data-plans/:id/delete', auth, async (req, res) => {
    try {
      const { error } = await supabase.from('data_plans').delete().eq('id', req.params.id);
      if (error) return res.status(500).json({ error: error.message });
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  // ============ SETTINGS ============
  router.get('/api/settings', auth, async (req, res) => {
    try {
      const { data } = await supabase.from('app_settings').select('*');
      const settings = {};
      if (data) data.forEach(s => settings[s.key] = s.value);
      res.json(settings);
    } catch (e) {
      // Table might not exist yet
      res.json({ premium_price: '500', premium_duration: '7', professional_price: '1500', professional_duration: '30', enterprise_price: '5000', enterprise_duration: '30', announcement: '' });
    }
  });

  router.post('/api/settings', auth, async (req, res) => {
    try {
      const settings = req.body;
      for (const [key, value] of Object.entries(settings)) {
        await supabase.from('app_settings').upsert({ key, value: String(value) }, { onConflict: 'key' });
      }
      res.json({ success: true });
    } catch (e) {
      res.status(500).json({ error: e.message });
    }
  });

  return router;
};
