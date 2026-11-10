function configuration() {
  if (!process.env.RESEND_API_KEY || !process.env.MAIL_FROM) throw new Error('RESET_EMAIL_NOT_CONFIGURED');
  return { key: process.env.RESEND_API_KEY, from: process.env.MAIL_FROM };
}
async function send(address, code, requestId) {
  const config = configuration();
  if (!/^\d{6}$/.test(code)) throw new Error('INVALID_RECOVERY_CODE');
  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST', signal: AbortSignal.timeout(15000),
    headers: { Authorization: `Bearer ${config.key}`, 'Content-Type': 'application/json', 'Idempotency-Key': `recovery/${requestId}` },
    body: JSON.stringify({ from: config.from, to: [address], subject: 'Código de recuperação do Dedilharte',
      text: `Seu código de recuperação é: ${code}\n\nDigite os 6 dígitos no aplicativo em até 10 minutos. O código só pode ser usado uma vez. Não compartilhe este código. Se você não solicitou, ignore esta mensagem.` }),
  });
  if (!response.ok) throw new Error('RESET_EMAIL_SEND_FAILED');
  const receipt = await response.json().catch(() => null);
  if (!receipt || typeof receipt.id !== 'string' || !receipt.id.trim()) throw new Error('RESET_EMAIL_SEND_FAILED');
}
module.exports = { configuration, send };
