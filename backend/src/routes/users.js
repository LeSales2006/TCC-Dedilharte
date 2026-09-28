const express = require('express');
const usersController = require('../controllers/usersController');
const progressController = require('../controllers/progressController');
const activityController = require('../controllers/activityController');
const songProgressController = require('../controllers/songProgressController');
const arpeggiosController = require('../controllers/arpeggiosController');
const modulesController = require('../controllers/modulesController');
const moduleProgressController = require('../controllers/moduleProgressController');
const multer = require('multer');
const {
  authenticateToken,
  requireSelfOrAdmin,
  requireAdmin,
  requireStaff,
  requireConfigurator,
} = require('../middleware/authMiddleware');

const router = express.Router();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 1024 * 1024 },
});

router.use(authenticateToken);

router.get('/profile', usersController.getProfile);
router.put('/profile', usersController.updateProfile);
router.get('/users/me', usersController.getProfile);
router.put('/users/me/photo', upload.single('photo'), usersController.uploadMyPhoto);
router.get('/users/me/photo', usersController.getMyPhoto);
router.delete('/users/me/photo', usersController.deleteMyPhoto);
router.post('/activity', activityController.recordAccess);
router.get('/activity/summary', activityController.getSummary);
router.get('/songs/progress', songProgressController.listSongProgress);
router.post('/songs/progress', songProgressController.upsertSongProgress);
router.get('/arpeggios', arpeggiosController.listPublic);
router.get('/arpeggios/:id', arpeggiosController.getPublic);
router.get('/modules', modulesController.listPublic);
router.get('/module-progress', moduleProgressController.listMyProgress);
router.post('/module-items/:id/progress', moduleProgressController.upsertMyProgress);
router.get('/admin/arpeggios', requireStaff, arpeggiosController.listAdmin);
router.post('/admin/arpeggios', requireStaff, arpeggiosController.createAdmin);
router.put('/admin/arpeggios/:id', requireStaff, arpeggiosController.updateAdmin);
router.delete('/admin/arpeggios/:id', requireStaff, arpeggiosController.deleteAdmin);
router.post('/configurator/modules', requireConfigurator, modulesController.createModule);
router.patch('/configurator/modules/:id', requireConfigurator, modulesController.updateModule);
router.get('/staff/modules', requireStaff, modulesController.listStaff);
router.post('/staff/modules/:id/items', requireStaff, modulesController.createItem);
router.patch('/staff/module-items/:id', requireStaff, modulesController.updateItem);
router.patch('/staff/module-items/:id/order', requireStaff, modulesController.updateItemOrder);
router.delete('/staff/module-items/:id', requireStaff, modulesController.deleteItem);
router.post('/configurator/users', requireConfigurator, usersController.createManagedUser);
router.patch('/configurator/users/:id/role', requireConfigurator, usersController.updateManagedRole);
router.patch('/configurator/users/:id/status', requireConfigurator, usersController.updateManagedStatus);
router.delete('/configurator/users/:id', requireConfigurator, usersController.deleteManagedUser);
router.post('/users', usersController.upsertUser);
router.get('/users/:id', requireSelfOrAdmin('id'), usersController.getUser);
router.put('/users/:id', requireSelfOrAdmin('id'), usersController.updateUser);
router.delete('/users/:id', requireSelfOrAdmin('id'), usersController.deleteUser);
router.get('/users/:userId/progress', requireSelfOrAdmin('userId'), progressController.getProgress);
router.post('/users/:userId/progress', requireSelfOrAdmin('userId'), progressController.upsertProgress);

module.exports = router;
