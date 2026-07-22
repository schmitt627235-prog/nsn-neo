(function () {
  if (!/^\/serie\/[^\/]+/.test(location.pathname) || document.getElementById('neo-detail-v2')) return;
  var wrapper = document.getElementById('wrapper') || document.body;
  var titleNode = document.querySelector('h1[itemprop="name"],h1');
  var coverNode = document.querySelector('img[src*="/media/images/channel/"],img[itemprop="image"]');
  if (!titleNode || !coverNode) return;

  var title = titleNode.textContent.trim();
  var cover = coverNode.getAttribute('data-src') || coverNode.currentSrc || coverNode.src;
  var backdropNode = document.querySelector('img[src*="/media/images/backdrop/"],img[alt="Backdrop"]');
  var backdrop = backdropNode && (backdropNode.getAttribute('data-src') || backdropNode.currentSrc || backdropNode.src) || cover;
  var descriptionNode = document.querySelector('[itemprop="description"],.description,.seri_des,p.lead');
  var description = descriptionNode && (descriptionNode.getAttribute('data-full-description') || descriptionNode.textContent.trim()) || '';
  var trailerNode = document.querySelector('.trailerButton');
  var playerFrame = document.querySelector('.player-wrap iframe,iframe[src*="/r?t="]');
  var hosters = Array.prototype.slice.call(document.querySelectorAll('button.link-box'));
  var languages = Array.prototype.slice.call(document.querySelectorAll('.changeLanguageBox img[data-lang-key]')).map(function (img) {
    return { key: img.getAttribute('data-lang-key'), title: img.getAttribute('title') || img.getAttribute('alt') || 'Sprache', src: img.getAttribute('src') };
  });

  var base = location.pathname.match(/^(\/serie\/[^\/]+)/)[1];
  var episodeLinks = [], episodeSeen = Object.create(null);
  document.querySelectorAll('a[href^="/serie/"]').forEach(function (a) {
    try {
      var url = new URL(a.href, location.href);
      var sm = url.pathname.match(/staffel-(\d+)/i), em = url.pathname.match(/episode-(\d+)/i);
      if (url.pathname.indexOf(base) !== 0 || !em) return;
      var episodeKey = (sm ? sm[1] : '1') + '|' + em[1];
      if (episodeSeen[episodeKey]) return;
      episodeSeen[episodeKey] = true;
      episodeLinks.push({ url: url.href, text: (a.innerText || '').trim(), season: sm ? +sm[1] : 1, episode: em ? +em[1] : 0 });
    } catch (e) { }
  });

  var recommendationLinks = [], recommendationSeen = Object.create(null);
  document.querySelectorAll('a[href^="/serie/"]').forEach(function (a) {
    var image = a.querySelector('img[src*="/media/images/channel/"],img[alt*="Poster"],.seriesListHorizontalCover img');
    if (!image) return;
    try {
      var url = new URL(a.href, location.href), match = url.pathname.match(/^(\/serie\/[^\/]+)/);
      if (!match || match[1] === base || recommendationSeen[match[1]]) return;
      recommendationSeen[match[1]] = true;
      var src = image.getAttribute('data-src') || image.currentSrc || image.src;
      var name = (a.getAttribute('title') || image.getAttribute('alt') || a.innerText || '').replace(/\s*(?:kostenlos online )?ansehen\s*\(Stream\).*$/i, '').replace(/ als Stream anschauen.*$/i, '').replace(/^(Poster,\s*)|([, ]*Anime Cover.*$)/gi, '').trim();
      if (src && name) recommendationLinks.push({ url: url.href, src: src, name: name });
    } catch (e) { }
  });

  var app = document.createElement('main');
  app.id = 'neo-detail-v2';
  var hero = document.createElement('section');
  hero.className = 'neoDetailHero';
  hero.innerHTML = '<img class="neoDetailBackdrop"><div class="neoDetailShade"></div><div class="neoDetailHeroInner"><img class="neoDetailCover"><div class="neoDetailCopy"><div class="neoDetailType">ANIME · STREAM</div><h1></h1><div class="neoDetailGenres"></div><p></p><div class="neoDetailActions"></div></div></div>';
  hero.querySelector('.neoDetailBackdrop').src = backdrop;
  hero.querySelector('.neoDetailCover').src = cover;
  hero.querySelector('h1').textContent = title;
  hero.querySelector('p').textContent = description;
  document.querySelectorAll('.genres a,.genre a').forEach(function (old) {
    var chip = document.createElement('span'); chip.textContent = old.textContent.trim();
    if (chip.textContent) hero.querySelector('.neoDetailGenres').appendChild(chip);
  });
  var watch = document.createElement('a'); watch.className = 'neoPrimary'; watch.href = '#neoStream'; watch.textContent = '▶ Jetzt ansehen';
  hero.querySelector('.neoDetailActions').appendChild(watch);
  if (trailerNode) {
    var trailer = document.createElement('a'); trailer.className = 'neoSecondary'; trailer.href = trailerNode.href; trailer.textContent = '▶ Trailer';
    hero.querySelector('.neoDetailActions').appendChild(trailer);
  }
  app.appendChild(hero);

  function section(label) {
    var element = document.createElement('section'); element.className = 'neoDetailSection';
    var heading = document.createElement('h2'); heading.textContent = label; element.appendChild(heading); app.appendChild(element); return element;
  }

  if (episodeLinks.length) {
    var episodes = section('Staffeln & Episoden');
    var seasonTabs = document.createElement('div'); seasonTabs.className = 'neoSeasonTabs';
    var rail = document.createElement('div'); rail.className = 'neoEpisodeRail';
    var seasons = episodeLinks.map(function (x) { return x.season; }).filter(function (x, i, a) { return a.indexOf(x) === i; }).sort(function (a,b) { return a-b; });
    function showSeason(season) {
      rail.innerHTML = '';
      seasonTabs.querySelectorAll('button').forEach(function (b) { b.classList.toggle('active', +b.dataset.season === season); });
      episodeLinks.filter(function (x) { return x.season === season; }).sort(function (a,b) { return a.episode-b.episode; }).forEach(function (item) {
        var a = document.createElement('a'); a.href = item.url; a.textContent = 'Episode ' + (item.episode || item.text); rail.appendChild(a);
      });
    }
    seasons.forEach(function (season) { var b = document.createElement('button'); b.type = 'button'; b.dataset.season = season; b.textContent = 'Staffel ' + season; b.onclick = function () { showSeason(season); }; seasonTabs.appendChild(b); });
    episodes.appendChild(seasonTabs); episodes.appendChild(rail); showSeason(seasons[0]);
  }

  var stream = section('Stream auswählen'); stream.id = 'neoStream';
  var languageTabs = document.createElement('div'); languageTabs.className = 'neoLanguageTabs';
  var hostRail = document.createElement('div'); hostRail.className = 'neoHostRail';
  function renderHosters(languageKey) {
    hostRail.innerHTML = '';
    languageTabs.querySelectorAll('button').forEach(function (b) { b.classList.toggle('active', b.dataset.lang === languageKey); });
    var hostSeen = Object.create(null);
    hosters.filter(function (a) { var li = a.closest('li[data-lang-key]'); return !languageKey || (li && li.getAttribute('data-lang-key') === languageKey); }).forEach(function (old) {
      var name = (old.querySelector('h4') || old).textContent.trim();
      if (!name || hostSeen[name]) return; hostSeen[name] = true;
      var a = document.createElement('button'); a.type = 'button'; a.textContent = name;
      a.onclick = function (event) {
        event.preventDefault();
        if (window.NeoRemote) NeoRemote.showCursor();
        old.click();
        setTimeout(function(){var frame=document.querySelector('.player-wrap iframe,iframe[src*="/r?t="]');if(frame){frame.tabIndex=0;if(frame.parentElement!==stream)stream.appendChild(frame);frame.focus();frame.classList.add('neoTvActive');frame.scrollIntoView({behavior:'smooth',block:'center'});}},250);
        return false;
      };
      hostRail.appendChild(a);
    });
  }
  languages.forEach(function (lang) { var b = document.createElement('button'); b.type = 'button'; b.dataset.lang = lang.key; b.title = lang.title; b.innerHTML = '<img><span></span>'; b.querySelector('img').src = lang.src; b.querySelector('span').textContent = lang.key === '1' ? 'Deutsch Dub' : (lang.key === '3' ? 'Deutsch Sub' : 'English Sub'); b.onclick = function () { renderHosters(lang.key); }; languageTabs.appendChild(b); });
  if (languages.length) stream.appendChild(languageTabs);
  stream.appendChild(hostRail); renderHosters(languages.length ? languages[0].key : '');
  if (playerFrame) { playerFrame.tabIndex = 0; stream.appendChild(playerFrame); }

  if (recommendationLinks.length) {
    var recommendations = section('Folgende Anime könnten dir auch gefallen'), recommendationRail = document.createElement('div'); recommendationRail.className = 'neoRecommendationRail';
    recommendationLinks.slice(0, 30).forEach(function (item) {
      var a = document.createElement('a'); a.href = item.url; a.innerHTML = '<img><span></span>'; a.querySelector('img').src = item.src; a.querySelector('img').alt = item.name; a.querySelector('span').textContent = item.name; recommendationRail.appendChild(a);
    });
    recommendations.appendChild(recommendationRail);
  }

  var style = document.createElement('style'); style.id = 'neo-detail-v2-style';
  style.textContent = 'body.neoDetailV2 #wrapper>.neoDetailSource{display:none!important}#neo-detail-v2{max-width:1500px;margin:auto;padding-bottom:90px;background:#050505;color:#fff}.neoDetailHero{position:relative;min-height:570px;display:flex;align-items:flex-end;overflow:hidden}.neoDetailBackdrop{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;filter:blur(10px);transform:scale(1.08);opacity:.5}.neoDetailShade{position:absolute;inset:0;background:linear-gradient(90deg,#050505 2%,rgba(5,5,5,.6) 60%,rgba(5,5,5,.2)),linear-gradient(0deg,#050505,transparent 60%)}.neoDetailHeroInner{position:relative;z-index:2;display:grid;grid-template-columns:180px 1fr;gap:24px;align-items:end;width:100%;padding:48px 5vw}.neoDetailCover{width:180px;height:270px;object-fit:cover;border-radius:16px;box-shadow:0 15px 40px #000}.neoDetailCopy h1{font-size:clamp(32px,6vw,64px);margin:7px 0 12px}.neoDetailCopy p{max-width:780px;line-height:1.5;color:#ddd}.neoDetailGenres,.neoDetailActions,.neoHostRail{display:flex;gap:9px;flex-wrap:wrap}.neoDetailGenres span{border:1px solid #a41420;border-radius:999px;padding:7px 11px}.neoDetailActions a{display:inline-block;margin-top:14px;padding:13px 17px;border-radius:11px;color:#fff!important;text-decoration:none;font-weight:700}.neoPrimary{background:#e50914}.neoSecondary{background:#333}.neoDetailSection{padding:25px 5vw;border-top:1px solid #242424}.neoDetailSection h2{font-size:24px;border-left:4px solid #e50914;padding-left:12px}.neoEpisodeRail,.neoRecommendationRail{display:flex;gap:12px;overflow-x:auto;padding:6px 3px 18px;scroll-snap-type:x mandatory}.neoEpisodeRail a,.neoHostRail a{flex:0 0 auto;background:#171717;border:1px solid #444;border-radius:10px;padding:12px 14px;color:#fff!important;text-decoration:none}.neoHostRail a{border:2px solid #e50914}.neoDetailSection iframe{display:block;width:100%!important;height:auto!important;aspect-ratio:16/9;border:0!important;position:static!important;margin-top:18px;background:#000;border-radius:15px}.neoRecommendationRail a{flex:0 0 145px;width:145px;color:#fff!important;text-decoration:none;scroll-snap-align:start}.neoRecommendationRail img{display:block;width:145px;height:215px;object-fit:cover;border-radius:13px;background:#172027}.neoRecommendationRail span{display:block;font-weight:700;font-size:14px;line-height:1.25;margin-top:8px}.neoEpisodeRail a:focus,.neoHostRail a:focus,.neoRecommendationRail a:focus{outline:5px solid #fff;outline-offset:5px}@media(max-width:600px){.neoDetailHero{min-height:620px}.neoDetailHeroInner{grid-template-columns:110px 1fr;gap:15px;padding:35px 5vw}.neoDetailCover{width:110px;height:165px}.neoDetailCopy h1{font-size:30px}.neoDetailCopy p{grid-column:1/-1;font-size:14px}.neoDetailType{font-size:12px}}@media(min-width:900px){.neoRecommendationRail a,.neoRecommendationRail img{width:220px}.neoRecommendationRail a{flex-basis:220px}.neoRecommendationRail img{height:330px}.neoDetailSection h2{font-size:34px}.neoEpisodeRail a,.neoHostRail a{font-size:21px;padding:16px 20px}}';
  style.textContent += '.neoDetailHeroInner{align-items:start!important}.neoSeasonTabs,.neoLanguageTabs{display:flex;gap:9px;flex-wrap:wrap;margin-bottom:14px}.neoSeasonTabs button,.neoLanguageTabs button,.neoFullscreen{background:#181818;color:#fff;border:1px solid #555;border-radius:10px;padding:12px 16px;font-weight:700}.neoFullscreen{display:block;margin:14px 0;border:2px solid #e50914}.neoSeasonTabs button.active,.neoLanguageTabs button.active{background:#e50914;border-color:#e50914}.neoLanguageTabs button{display:flex;align-items:center;gap:8px}.neoLanguageTabs img{width:34px;height:24px;object-fit:cover;border-radius:4px}@media(max-width:600px){.neoDetailHeroInner{align-items:start!important}.neoDetailCover{align-self:start!important}.neoDetailCopy p{margin-top:8px}.neoLanguageTabs button{padding:10px}.neoLanguageTabs span{font-size:13px}}';
  document.head.appendChild(style);
  Array.prototype.slice.call(wrapper.children).forEach(function (child) {
    if (child !== app && child.tagName !== 'SCRIPT' && child.tagName !== 'STYLE') child.classList.add('neoDetailSource');
  });
  document.body.classList.add('neoDetailV2');
  wrapper.insertBefore(app, wrapper.firstChild);

  app.querySelectorAll('.neoEpisodeRail,.neoRecommendationRail').forEach(function (rail) {
    var sx = 0, sy = 0, scroll = 0, horizontal = false; rail.style.touchAction = 'pan-y';
    rail.addEventListener('touchstart', function (e) { var t = e.touches[0]; sx = t.clientX; sy = t.clientY; scroll = rail.scrollLeft; horizontal = false; }, { passive: true });
    rail.addEventListener('touchmove', function (e) { var t = e.touches[0], dx = t.clientX - sx, dy = t.clientY - sy; if (!horizontal && Math.abs(dx) > Math.abs(dy) + 5) horizontal = true; if (horizontal) { e.preventDefault(); rail.scrollLeft = scroll - dx; } }, { passive: false });
    rail.querySelectorAll('a').forEach(function (a) { a.addEventListener('focus', function () { a.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' }); }); });
  });
})();
