const express = require('express');
const authController = require('../controllers/authController');
const { authenticateToken } = require('../middleware/authMiddleware');
const passwords = require('../controllers/passwordController');
const { rateLimit, emailKey } = require('../middleware/rateLimit');

const router = express.Router();

router.post('/register', rateLimit('register-ip', 5, 900), authController.register);
router.post('/login', rateLimit('login-ip', 30, 900), rateLimit('login-email', 15, 900, emailKey), authController.login);
router.get('/me', authenticateToken, authController.me);
router.post('/forgot-password', rateLimit('forgot-ip', 5, 900), rateLimit('forgot-email', 3, 3600, emailKey), passwords.forgot);
router.post('/reset-password', rateLimit('reset-ip', 10, 900), passwords.reset);
router.post('/change-password', authenticateToken, rateLimit('change-user', 5, 900, req => req.user.id), passwords.change);
router.get('/reset-password', require('../resetPasswordPage'));

// An unknown public auth route must not fall through to /api's JWT middleware.
router.use((req, res) => res.status(404).json({ error: 'Rota de autenticação não encontrada.' }));

module.exports = router;
