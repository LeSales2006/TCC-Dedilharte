const express = require('express');
const authController = require('../controllers/authController');
const { authenticateToken } = require('../middleware/authMiddleware');
const passwords = require('../controllers/passwordController');
const { rateLimit, emailKey } = require('../middleware/rateLimit');

const router = express.Router();

router.post('/register', rateLimit('register-ip', 5, 900), authController.register);
router.post('/login', rateLimit('login-ip', 30, 900), rateLimit('login-email', 15, 900, emailKey), authController.login);
router.get('/me', authenticateToken, authController.me);
router.use('/password-recovery', (req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });
router.post('/password-recovery/request', rateLimit('recovery-ip', 5, 900),
  rateLimit('recovery-cooldown', 1, 60, emailKey), rateLimit('recovery-email', 3, 3600, emailKey), passwords.requestCode);
router.post('/password-recovery/verify', rateLimit('recovery-verify-ip', 30, 900), passwords.verifyCode);
router.post('/password-recovery/reset', rateLimit('recovery-reset-ip', 10, 900), passwords.reset);
router.post('/change-password', authenticateToken, rateLimit('change-user', 5, 900, req => req.user.id), passwords.change);

// An unknown public auth route must not fall through to /api's JWT middleware.
router.use((req, res) => res.status(404).json({ error: 'Rota de autenticação não encontrada.' }));

module.exports = router;
