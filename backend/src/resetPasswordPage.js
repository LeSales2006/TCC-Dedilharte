const crypto = require('crypto');
module.exports = (req, res) => {
  const nonce = crypto.randomBytes(18).toString('base64');
  res.set({ 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff',
    'Content-Security-Policy': `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'` });
  res.type('html').send(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1"><title>Nova senha — Dedilharte</title>
  <style nonce="${nonce}">*{box-sizing:border-box}body{margin:0;padding:24px;background:#1b4f4c;font:17px system-ui;color:#1b4f4c}main{max-width:460px;margin:6vh auto;padding:24px;border-radius:20px;background:white}label,input,button{display:block;width:100%;margin:12px 0}input,button{font:inherit;padding:14px;border:1px solid #329691;border-radius:12px}button{background:#54d6dd;color:#1b4f4c;font-weight:bold}button:disabled{opacity:.5}</style></head>
  <body><main><h1>Nova senha</h1><p>Use pelo menos 8 caracteres, incluindo uma letra e um número.</p>
  <form id="reset"><label for="password">Nova senha</label><input id="password" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
  <label for="confirm">Confirmar nova senha</label><input id="confirm" type="password" autocomplete="new-password" minlength="8" maxlength="72" required>
  <button id="submit">Salvar nova senha</button></form><p id="message" role="status" aria-live="polite"></p></main>
  <script nonce="${nonce}">const token = new URLSearchParams(location.hash.slice(1)).get('token');history.replaceState(null,'',location.pathname);
  const form=document.getElementById('reset'), message=document.getElementById('message'), button=document.getElementById('submit');
  if(!token || !/^[a-f0-9]{64}$/.test(token)){form.hidden=true;message.textContent='Link inválido. Solicite outro no aplicativo.';}
  form.addEventListener('submit',async e=>{e.preventDefault();const password=document.getElementById('password').value, confirm=document.getElementById('confirm').value;
    if(password!==confirm){message.textContent='As senhas devem ser iguais.';return;}button.disabled=true;
    try{const response=await fetch(location.pathname,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token,newPassword:password,confirmPassword:confirm})});
      const result=await response.json();message.textContent=result.message||result.error||'Não foi possível alterar a senha.';if(response.ok){form.reset();form.hidden=true;}}
    catch(_){message.textContent='Sem conexão. Tente novamente.';}finally{button.disabled=false;}});</script></body></html>`);
};
