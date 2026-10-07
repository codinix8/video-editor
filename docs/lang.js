(function(){
  var saved = localStorage.getItem('babacut-lang');
  var nav = (navigator.language||'en').toLowerCase();
  var lang = saved || (nav.startsWith('de') ? 'de' : nav.startsWith('fa') ? 'fa' : 'en');
  apply(lang);
  window.setLang = function(l){ localStorage.setItem('babacut-lang', l); apply(l); };
  function apply(l){
    document.documentElement.lang = l;
    document.documentElement.dir = (l==='fa') ? 'rtl' : 'ltr';
    document.querySelectorAll('.flags button').forEach(function(b){ b.classList.toggle('active', b.dataset.l===l); });
  }
  document.addEventListener('DOMContentLoaded', function(){ apply(document.documentElement.lang); });
})();
