const express = require('express');
const usersController = require('../controllers/usersController');
const progressController = require('../controllers/progressController');
const activityController = require('../controllers/activityController');
const songProgressController = require('../controllers/songProgressController');
const songsController = require('../controllers/songsController');
const arpeggiosController = require('../controllers/arpeggiosController');
const modulesController = require('../controllers/modulesController');
const moduleProgressController = require('../controllers/moduleProgressController');
const multer = require('multer');
const { rateLimit } = require('../middleware/rateLimit');
const {
  authenticateToken,
  requireSelfOrAdmin,
  requireAdmin,
  requireStaff,
  requireConfigurator,
  requireStudentOrAdmin,
} = require('../middleware/authMiddleware');

const router = express.Router();
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 1024 * 1024 },
});

router.use(authenticateToken);

router.get('/profile', usersController.getProfile);
router.put('/profile', rateLimit('profile-user', 30, 900, req => req.user.id), usersController.updateProfile);
router.get('/users/me', usersController.getProfile);
router.delete('/users/me', rateLimit('delete-own-user', 5, 900, req => req.user.id), usersController.deleteOwnAccount);
router.put('/users/me/photo', upload.single('photo'), usersController.uploadMyPhoto);
router.get('/users/me/photo', usersController.getMyPhoto);
router.delete('/users/me/photo', usersController.deleteMyPhoto);
router.post('/activity', requireStudentOrAdmin, activityController.recordAccess);
router.get('/activity/summary', requireStudentOrAdmin, activityController.getSummary);
router.get('/songs/progress', requireStudentOrAdmin, songProgressController.listSongProgress);
router.post('/songs/progress', requireStudentOrAdmin, songProgressController.upsertSongProgress);
router.get('/songs', requireStudentOrAdmin, songsController.listPublic);
router.get('/arpeggios', requireStudentOrAdmin, arpeggiosController.listPublic);
router.get('/arpeggios/:id', requireStudentOrAdmin, arpeggiosController.getPublic);
router.get('/modules', requireStudentOrAdmin, modulesController.listPublic);
router.get('/module-progress', requireStudentOrAdmin, moduleProgressController.listMyProgress);
router.post('/module-items/:id/progress', requireStudentOrAdmin, moduleProgressController.upsertMyProgress);
router.get('/admin/arpeggios', requireStaff, arpeggiosController.listAdmin);
router.post('/admin/arpeggios', requireStaff, arpeggiosController.createAdmin);
router.put('/admin/arpeggios/:id', requireStaff, arpeggiosController.updateAdmin);
router.delete('/admin/arpeggios/:id', requireStaff, arpeggiosController.deleteAdmin);
router.get('/admin/songs', requireStaff, songsController.listAdmin);
router.post('/admin/songs', requireStaff, songsController.createAdmin);
router.put('/admin/songs/:id', requireStaff, songsController.updateAdmin);
router.delete('/admin/songs/:id', requireStaff, songsController.deleteAdmin);
router.post('/configurator/modules', requireAdmin, modulesController.createModule);
router.patch('/configurator/modules/:id', requireAdmin, modulesController.updateModule);
router.get('/staff/modules', requireStaff, modulesController.listStaff);
router.post('/staff/modules/:id/items', requireStaff, modulesController.createItem);
router.patch('/staff/module-items/:id', requireStaff, modulesController.updateItem);
router.patch('/staff/module-items/:id/order', requireStaff, modulesController.updateItemOrder);
router.delete('/staff/module-items/:id', requireStaff, modulesController.deleteItem);
router.get('/configurator/users', requireConfigurator, usersController.listManagedUsers);
router.post('/configurator/users', requireConfigurator, usersController.createManagedUser);
router.patch('/configurator/users/:id/role', requireConfigurator, usersController.updateManagedRole);
router.patch('/configurator/users/:id/status', requireAdmin, usersController.updateManagedStatus);
router.delete('/configurator/users/:id', requireAdmin, usersController.deleteManagedUser);
router.post('/users', usersController.upsertUser);
router.get('/users/:id', requireSelfOrAdmin('id'), usersController.getUser);
router.put('/users/:id', requireSelfOrAdmin('id'), usersController.updateUser);
router.delete('/users/:id', requireSelfOrAdmin('id'), usersController.deleteUser);
router.get('/users/:userId/progress', requireStudentOrAdmin, requireSelfOrAdmin('userId'), progressController.getProgress);
router.post('/users/:userId/progress', requireStudentOrAdmin, requireSelfOrAdmin('userId'), progressController.upsertProgress);

module.exports = router;
