(function(){
  var saved = localStorage.getItem('babacut-lang');
  var lang = saved || ((navigator.language||'en').toLowerCase().startsWith('de') ? 'de' : 'en');
  document.documentElement.lang = lang;
  window.setLang = function(l){ localStorage.setItem('babacut-lang', l); document.documentElement.lang = l; update(); };
  function update(){ var b=document.getElementById('langbtn'); if(b) b.textContent = document.documentElement.lang==='de' ? 'EN' : 'DE'; }
  document.addEventListener('DOMContentLoaded', update);
})();
