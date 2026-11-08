const pool = require('../db');

function clampWeeklyGoal(value) {
  const parsed = Number(value);
  if (!Number.isInteger(parsed)) {
    return 3;
  }
  return Math.max(1, Math.min(7, parsed));
}

function toNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

async function recordAccess(req, res, next) {
  try {
    const result = await pool.query(
      `INSERT INTO user_activity (user_id, access_date)
       VALUES ($1, CURRENT_DATE)
       ON CONFLICT (user_id, access_date) DO NOTHING
       RETURNING id, user_id, access_date, created_at`,
      [req.user.id]
    );

    return res.status(result.rowCount === 0 ? 200 : 201).json({
      recorded: result.rowCount > 0,
    });
  } catch (error) {
    return next(error);
  }
}

async function getSummary(req, res, next) {
  try {
    const result = await pool.query(
      `WITH account AS (
           SELECT weekly_goal, created_at::date AS created_date FROM users WHERE id = $1
       ), weeks AS (
           SELECT date_trunc('week', CURRENT_DATE)::date - i * 7 AS start_date
           FROM generate_series(3, 0, -1) AS i
       ), totals AS (
           SELECT w.start_date, COUNT(a.access_date)::int AS days
           FROM weeks w LEFT JOIN user_activity a
             ON a.user_id = $1 AND a.access_date >= w.start_date
             AND a.access_date < w.start_date + 7 AND a.access_date <= CURRENT_DATE
           GROUP BY w.start_date
       )
       SELECT COALESCE((SELECT weekly_goal FROM account), 3)::int AS weekly_goal,
           COALESCE((SELECT days FROM totals WHERE start_date = date_trunc('week', CURRENT_DATE)::date), 0) AS access_days_this_week,
           (SELECT SUM(days)::int FROM totals) AS access_days_last_four_weeks,
           (SELECT ARRAY_AGG(days ORDER BY start_date) FROM totals) AS weekly_accesses,
           GREATEST(1, LEAST(4, (CURRENT_DATE - date_trunc('week',
               COALESCE((SELECT created_date FROM account), CURRENT_DATE))::date) / 7 + 1)) AS available_weeks`,
      [req.user.id]
    );

    const row = result.rows[0] || {};
    const weeklyGoal = clampWeeklyGoal(row.weekly_goal);
    const accessDaysThisWeek = toNumber(row.access_days_this_week);
    const accessDaysLastFourWeeks = toNumber(row.access_days_last_four_weeks);
    const availableWeeks = Math.max(1, Math.min(4, toNumber(row.available_weeks) || 1));

    return res.json({
      weeklyGoal,
      accessDaysThisWeek,
      accessDaysLastFourWeeks,
      availableWeeks,
      averageAccessesPerWeek: accessDaysLastFourWeeks / availableWeeks,
      weeklyAccesses: (row.weekly_accesses || []).slice(-availableWeeks).map(toNumber),
    });
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  recordAccess,
  getSummary,
};
