const ENDPOINT = 'https://api.brevo.com/v3/smtp/email';
const DEFAULT_SENDER_NAME = 'Dedilharte';
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// MAIL_FROM accepts either "no-reply@domain.com" or "Dedilharte <no-reply@domain.com>".
function sender(value) {
  const text = typeof value === 'string' ? value.trim() : '';
  const match = /^(.*?)\s*<\s*([^<>]+)\s*>$/.exec(text);
  const name = ((match && match[1]) || DEFAULT_SENDER_NAME).replace(/[\r\n]+/g, ' ').trim().slice(0, 70) || DEFAULT_SENDER_NAME;
  const email = ((match && match[2]) || text).trim();
  return { name, email };
}

function configuration() {
  const key = process.env.BREVO_API_KEY;
  const from = process.env.MAIL_FROM;
  if (!key || !from) throw new Error('RESET_EMAIL_NOT_CONFIGURED');
  const parsed = sender(from);
  if (!EMAIL.test(parsed.email) || parsed.email.length > 254) throw new Error('RESET_EMAIL_NOT_CONFIGURED');
  return { key, sender: parsed };
}

function message(address, code, config) {
  return {
    sender: config.sender,
    to: [{ email: address }],
    subject: 'Código de recuperação do Dedilharte',
    textContent: `Seu código de recuperação é: ${code}\n\nDigite os 6 dígitos no aplicativo em até 10 minutos. O código só pode ser usado uma vez. Não compartilhe este código. Se você não solicitou, ignore esta mensagem.`,
  };
}

async function send(address, code, requestId) {
  // requestId is kept in the signature for the controller contract; Brevo v3 has no API-level idempotency key.
  const config = configuration();
  if (!/^\d{6}$/.test(code)) throw new Error('INVALID_RECOVERY_CODE');
  const response = await fetch(ENDPOINT, {
    method: 'POST', signal: AbortSignal.timeout(15000),
    headers: { 'api-key': config.key, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(message(address, code, config)),
  });
  if (!response.ok) throw new Error('RESET_EMAIL_SEND_FAILED');
  const receipt = await response.json().catch(() => null);
  if (!receipt || typeof receipt.messageId !== 'string' || !receipt.messageId.trim()) throw new Error('RESET_EMAIL_SEND_FAILED');
}
module.exports = { configuration, send };
