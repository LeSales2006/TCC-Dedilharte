function errorHandler(error, req, res, next) {
  if (res.headersSent) return next(error);
  // Do not log SQL parameters, passwords, connection URLs or request bodies.
  console.error('Erro na API', { method: req.method, path: req.path, code: error.code });
  if (error.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'JSON invalido.' });
  }
  if (error.type === 'entity.too.large') {
    return res.status(413).json({ error: 'Conteudo excede o limite permitido.' });
  }
  if (['ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'EAI_AGAIN',
    '53300', '57P01', '57P02', '57P03', '57014'].includes(error.code)
      || String(error.code || '').startsWith('08')
      || /timeout|connection terminated/i.test(error.message || '')) {
    res.set('Retry-After', '10');
    return res.status(503).json({ error: 'Servico temporariamente indisponivel. Tente novamente.' });
  }
  return res.status(500).json({ error: 'Erro interno do servidor.' });
}

module.exports = errorHandler;
