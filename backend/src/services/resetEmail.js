function configuration() {
  const base = new URL(process.env.PASSWORD_RESET_BASE_URL || '');
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash
      || !process.env.RESEND_API_KEY || !process.env.MAIL_FROM) throw new Error('RESET_EMAIL_NOT_CONFIGURED');
  return { base, key: process.env.RESEND_API_KEY, from: process.env.MAIL_FROM };
}
async function send(email, token) {
  const config = configuration();
  if (token) config.base.hash = new URLSearchParams({ token }).toString();
  const text = token
    ? `Para escolher uma nova senha, abra este link em até 30 minutos:\n${config.base}\n\nO link só pode ser usado uma vez. Se você não solicitou, ignore esta mensagem.`
    : 'Recebemos uma solicitação de recuperação de senha do Dedilharte para este endereço. Confira o e-mail usado no cadastro e solicite novamente pelo aplicativo. Se você não solicitou, ignore esta mensagem.';
  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST', signal: AbortSignal.timeout(15000),
    headers: { Authorization: `Bearer ${config.key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ from: config.from, to: [email], subject: 'Redefinir senha do Dedilharte',
      text }),
  });
  if (!response.ok) throw new Error('RESET_EMAIL_SEND_FAILED');
  const receipt = await response.json().catch(() => null);
  if (!receipt || typeof receipt.id !== 'string' || !receipt.id.trim()) throw new Error('RESET_EMAIL_SEND_FAILED');
}
module.exports = { configuration, send };
