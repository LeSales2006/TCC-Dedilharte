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
      `WITH current_user AS (
           SELECT weekly_goal, created_at::date AS created_date
           FROM users
           WHERE id = $1
       ),
       current_week AS (
           SELECT COUNT(DISTINCT access_date)::int AS days
           FROM user_activity
           WHERE user_id = $1
             AND access_date >= date_trunc('week', CURRENT_DATE)::date
             AND access_date < (date_trunc('week', CURRENT_DATE)::date + INTERVAL '7 days')
       ),
       recent AS (
           SELECT COUNT(DISTINCT access_date)::int AS days
           FROM user_activity
           WHERE user_id = $1
             AND access_date >= (date_trunc('week', CURRENT_DATE)::date - INTERVAL '21 days')
             AND access_date < (date_trunc('week', CURRENT_DATE)::date + INTERVAL '7 days')
       )
       SELECT
           COALESCE((SELECT weekly_goal FROM current_user), 3)::int AS weekly_goal,
           COALESCE((SELECT days FROM current_week), 0)::int AS access_days_this_week,
           COALESCE((SELECT days FROM recent), 0)::int AS access_days_last_four_weeks,
           GREATEST(
               1,
               LEAST(
                   4,
                   FLOOR((CURRENT_DATE - COALESCE((SELECT created_date FROM current_user), CURRENT_DATE)) / 7)::int + 1
               )
           ) AS available_weeks`,
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
    });
  } catch (error) {
    return next(error);
  }
}

module.exports = {
  recordAccess,
  getSummary,
};
