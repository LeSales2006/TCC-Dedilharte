function preferences(body, defaults = false) {
  const initialLevel = body.initialLevel === undefined ? (defaults ? 'beginner' : null) : body.initialLevel;
  const songGoal = body.songGoal === undefined ? (defaults ? 10 : null) : body.songGoal;
  const notificationsEnabled = body.notificationsEnabled === undefined ? (defaults ? false : null) : body.notificationsEnabled;
  const weeklyGoal = body.weeklyGoal === undefined ? (defaults ? 3 : null) : body.weeklyGoal;
  if (initialLevel !== null && !['beginner', 'intermediate'].includes(initialLevel)) return { error: 'Nível inicial inválido.' };
  if (songGoal !== null && (!Number.isInteger(songGoal) || songGoal < 1 || songGoal > 2147483647)) return { error: 'Meta de músicas deve ser um inteiro positivo.' };
  if (weeklyGoal !== null && (!Number.isInteger(weeklyGoal) || weeklyGoal < 1 || weeklyGoal > 7)) return { error: 'Meta semanal deve estar entre 1 e 7.' };
  if (notificationsEnabled !== null && typeof notificationsEnabled !== 'boolean') return { error: 'Preferência de notificações inválida.' };
  return { initialLevel, songGoal, notificationsEnabled, weeklyGoal };
}
function fields(row) {
  return { initialLevel: row.initial_level || 'beginner', songGoal: row.song_goal || 10,
    notificationsEnabled: Boolean(row.notifications_enabled) };
}
module.exports = { preferences, fields };
