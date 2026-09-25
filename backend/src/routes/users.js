const express = require('express');
const usersController = require('../controllers/usersController');
const progressController = require('../controllers/progressController');

const router = express.Router();

router.post('/users', usersController.upsertUser);
router.get('/users/:id', usersController.getUser);
router.put('/users/:id', usersController.updateUser);
router.delete('/users/:id', usersController.deleteUser);
router.get('/users/:userId/progress', progressController.getProgress);
router.post('/users/:userId/progress', progressController.upsertProgress);

module.exports = router;
