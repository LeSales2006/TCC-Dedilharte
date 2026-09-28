const express = require('express');
const usersController = require('../controllers/usersController');
const progressController = require('../controllers/progressController');
const { authenticateToken, requireSelfOrAdmin } = require('../middleware/authMiddleware');

const router = express.Router();

router.use(authenticateToken);

router.post('/users', usersController.upsertUser);
router.get('/users/:id', requireSelfOrAdmin('id'), usersController.getUser);
router.put('/users/:id', requireSelfOrAdmin('id'), usersController.updateUser);
router.delete('/users/:id', requireSelfOrAdmin('id'), usersController.deleteUser);
router.get('/users/:userId/progress', requireSelfOrAdmin('userId'), progressController.getProgress);
router.post('/users/:userId/progress', requireSelfOrAdmin('userId'), progressController.upsertProgress);

module.exports = router;
