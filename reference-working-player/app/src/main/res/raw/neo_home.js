(function () {
  if (location.pathname !== '/' || document.getElementById('neo-home-v2')) return;

  var root = document.getElementById('wrapper') || document.body;
  var source = root.querySelector(':scope > .container') || document.querySelector('#wrapper .container');
  if (!source) return;

  var app = document.createElement('main');
  app.id = 'neo-home-v2';
  var coverCache = Object.create(null);
  var pending = Object.create(null);

  function basePath(href) {
    try {
      var path = new URL(href, location.href).pathname;
      var match = path.match(/^(\/serie\/[^\/]+)/);
      return match ? match[1] : '';
    } catch (e) { return ''; }
  }

    document.querySelectorAll('a[href^="/serie/"],a[href*="186.2.175.5/serie/"]').forEach(function (a) {
    var base = basePath(a.href);
    var image = a.querySelector('.homeContentPromotionBoxPicture>img,.seriesListHorizontalCover>img,img[alt*="Cover"]');
    if (base && image) {
      var src = image.getAttribute('data-src') || image.currentSrc || image.src;
      if (src && src.indexOf('data:image') !== 0) coverCache[base] = src;
    }
  });

  function loadCover(base, image) {
    if (!base || !image) return;
    if (coverCache[base]) { image.src = coverCache[base]; return; }
    if (!pending[base]) {
      pending[base] = fetch(base, { credentials: 'include' }).then(function (r) { return r.text(); }).then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var found = doc.querySelector('img[src*="/media/images/channel/"],img[itemprop="image"],.seriesCoverBox img,.seriesCover img');
        var src = found && (found.getAttribute('data-src') || found.getAttribute('src'));
        if (src) coverCache[base] = new URL(src, location.href).href;
        return coverCache[base] || '';
      }).catch(function () { return ''; });
    }
    pending[base].then(function (src) { if (src && image.isConnected) image.src = src; });
  }

  function addSpotlight() {
    var links = Array.prototype.slice.call(document.querySelectorAll('a[href^="/serie/"]')).filter(function(a){return /^\/serie\/[^\/]+\/?$/.test(new URL(a.href,location.href).pathname) && a.querySelector('img');});
    links = links.filter(function (x, i, a) { return a.indexOf(x) === i; }).slice(0, 10);
    if (!links.length) return;
    var hero = document.createElement('section');
    hero.id = 'neo-spotlight';
    hero.innerHTML = '<img class="neoSpotlightImage"><div class="neoSpotlightVideo"></div><div class="neoSpotlightShade"></div><div class="neoSpotlightCopy"><div class="neoSpotlightRank"></div><h1></h1><div class="neoSpotlightMeta">Anime · Aktuell</div><p></p><div class="neoSpotlightActions"><a class="neoWatch">▶ Jetzt ansehen</a><a class="neoDetails">Details ›</a></div></div><div class="neoSpotlightNav"><button class="neoPrev">‹</button><div class="neoDots"></div><button class="neoNext">›</button></div>';
    app.appendChild(hero);
    var current = 0, timer = 0, token = 0;
    links.forEach(function (_, i) { var dot = document.createElement('button'); dot.onclick = function () { show(i); }; hero.querySelector('.neoDots').appendChild(dot); });
    function youtubeId(doc) {
      var nodes = Array.prototype.slice.call(doc.querySelectorAll('iframe[src],a[href]'));
      for (var i=0;i<nodes.length;i++) { var raw = nodes[i].getAttribute('src') || nodes[i].getAttribute('href') || ''; if (!/youtu/i.test(raw)) continue; try { var u = new URL(raw, location.href); var id = u.searchParams.get('v') || (/youtu\.be/i.test(u.hostname) ? u.pathname.slice(1) : ((u.pathname.match(/\/(?:embed|shorts)\/([^/?]+)/)||[])[1])); if (id) return id; } catch(e){} }
      return '';
    }
    function installTrailer(doc, myToken) {
      var id = youtubeId(doc), trailer = doc.querySelector('.trailerButton');
      if (!id && trailer && trailer.href) return fetch(trailer.href, { credentials:'include' }).then(function(r){return r.text();}).then(function(h){ if (myToken !== token) return; var td = new DOMParser().parseFromString(h,'text/html'); installTrailer(td,myToken); }).catch(function(){});
      if (!id || myToken !== token) return;
      var frame = document.createElement('iframe'); frame.allow = 'autoplay; encrypted-media; picture-in-picture';
      frame.src = 'https://www.youtube-nocookie.com/embed/' + encodeURIComponent(id) + '?autoplay=1&mute=1&controls=0&loop=1&playlist=' + encodeURIComponent(id) + '&playsinline=1&rel=0';
      hero.querySelector('.neoSpotlightVideo').appendChild(frame);
    }
    function show(index) {
      current = (index + links.length) % links.length; token++; var myToken = token, link = links[current];
      clearTimeout(timer); hero.querySelector('.neoSpotlightVideo').innerHTML = '';
      var originalImage = link.querySelector('img');
      hero.querySelector('.neoSpotlightImage').src = originalImage && (originalImage.getAttribute('data-src') || originalImage.currentSrc || originalImage.src) || coverCache[basePath(link.href)] || 'https://neo.local/banner.png';
      hero.querySelector('.neoSpotlightRank').textContent = '#' + (current + 1) + ' SPOTLIGHT';
      hero.querySelector('h1').textContent = (link.querySelector('h1,h2,h3,h4,h5,.title') || link).textContent.trim();
      hero.querySelector('p').textContent = ''; hero.querySelector('.neoWatch').href = link.href; hero.querySelector('.neoDetails').href = link.href;
      hero.querySelectorAll('.neoDots button').forEach(function(d,i){d.classList.toggle('active',i===current);if(i===current)d.scrollIntoView({block:'nearest',inline:'center'});});
      fetch(link.href, { credentials: 'include' }).then(function (response) { return response.text(); }).then(function (html) {
        if (myToken !== token) return;
      var doc = new DOMParser().parseFromString(html, 'text/html');
      var description = doc.querySelector('[itemprop="description"],.description,.seri_des,p.lead');
      if (description) hero.querySelector('p').textContent = description.getAttribute('data-full-description') || description.textContent.trim();
        installTrailer(doc,myToken);
      }).catch(function () { });
      timer = setTimeout(function(){show(current+1);},12000);
    }
    hero.querySelector('.neoPrev').onclick=function(){show(current-1);}; hero.querySelector('.neoNext').onclick=function(){show(current+1);};
    var sx=0; hero.addEventListener('touchstart',function(e){sx=e.touches[0].clientX;},{passive:true}); hero.addEventListener('touchend',function(e){var dx=e.changedTouches[0].clientX-sx;if(Math.abs(dx)>55)show(current+(dx<0?1:-1));},{passive:true});
    show(0);
  }

  function heading(text) {
    return Array.prototype.find.call(document.querySelectorAll('.pageTitle15,h1,h2'), function (h) {
      return (h.textContent || '').trim() === text;
    });
  }

  function scopeFor(h) {
    if (!h) return null;
    var carousel = h.closest('.carousel');
    if (carousel) return carousel;
    var row = h.closest('.row');
      if (row && row.querySelectorAll('a[href^="/serie/"]').length) return row;
    var node = h.parentElement;
    for (var i = 0; node && i < 4; i++, node = node.parentElement) {
      if (node.querySelectorAll('a[href^="/serie/"]').length) return node;
    }
    return h.parentElement;
  }

  function cleanTitle(a, image) {
    var titleNode = a.querySelector('h3,.seriesListTitle,.seriesTitle,[itemprop="name"]');
    var title = titleNode && titleNode.textContent;
    if (!title) title = (a.getAttribute('title') || '').replace(/ als Stream anschauen.*$/i, '');
    if (!title && image) title = (image.getAttribute('alt') || '').replace(/[, ]*(Anime )?Cover.*$/i, '');
    if (!title) {
      var lines = (a.innerText || '').split(/\n+/).map(function (x) { return x.trim(); }).filter(Boolean);
      title = lines.find(function (x) { return !/^(S\d+|St\.|Staffel|Episode|~|Neu!|Deutsch|English)/i.test(x); }) || '';
    }
    return title.trim();
  }

  function makeCard(a, episodeMode) {
    var base = basePath(a.href);
    if (!base) return null;
    var originalImage = a.querySelector('.homeContentPromotionBoxPicture>img,.seriesListHorizontalCover>img,img[alt*="Cover"]');
    var title = cleanTitle(a, originalImage);
    if (!title) return null;
    var card = document.createElement('a');
    card.className = 'neoRailCard';
    card.href = a.href;
    var image = document.createElement('img');
    image.alt = title;
    image.loading = 'lazy';
    image.src = originalImage && (originalImage.getAttribute('data-src') || originalImage.currentSrc || originalImage.src) || coverCache[base] || 'https://neo.local/banner.png';
    if (!originalImage || image.src.indexOf('data:image') === 0 || /flag|language/i.test(originalImage.className || '')) loadCover(base, image);
    card.appendChild(image);
    var name = document.createElement('div');
    name.className = 'neoRailTitle';
    name.textContent = title;
    card.appendChild(name);
    if (episodeMode) {
      var text = (a.innerText || '').replace(title, '').trim();
      var episode = text.match(/(?:S\d+\s*E\d+|St\.\s*\d+\s*Ep\.\s*\d+|Staffel\s*\d+.*?Episode\s*\d+)/i);
      var date = text.match(/(?:Mo|Di|Mi|Do|Fr|Sa|So)[,.]?\s*\d{1,2}\.\d{1,2}\.\d{4}[^\n]*/i);
      if (episode || date) {
        var meta = document.createElement('div');
        meta.className = 'neoRailMeta';
        meta.textContent = [episode && episode[0], date && date[0]].filter(Boolean).join(' · ');
        card.appendChild(meta);
      }
    }
    return card;
  }

  function addRail(label, sourceLabel, episodeMode, limit) {
    var h = heading(sourceLabel || label);
    var scope = scopeFor(h);
    if (!scope) return;
    var links = Array.prototype.slice.call(scope.querySelectorAll('a[href^="/serie/"]'));
    var seen = Object.create(null);
    var cards = [];
    links.forEach(function (a) {
      var key = episodeMode ? a.href + '|' + (a.innerText || '').trim() : basePath(a.href);
      if (!key || seen[key]) return;
      seen[key] = true;
      var card = makeCard(a, episodeMode);
      if (card) cards.push(card);
    });
    if (!cards.length) return;
    var section = document.createElement('section');
    section.className = 'neoRailSection';
    section.innerHTML = '<h2></h2><div class="neoRail"></div>';
    section.querySelector('h2').textContent = label;
    var rail = section.querySelector('.neoRail');
    cards.slice(0, limit || 50).forEach(function (card) { rail.appendChild(card); });
    app.appendChild(section);
  }

  function addGenres() {
    var h = heading('Genres');
    var scope = scopeFor(h);
    if (!scope) return;
    var links = Array.prototype.slice.call(scope.querySelectorAll('a[href*="genre"],a[href*="/genres/"]'));
    if (!links.length) return;
    var section = document.createElement('section');
    section.className = 'neoRailSection neoGenres';
    section.innerHTML = '<h2>Genres</h2><div class="neoGenreRail"></div>';
    var rail = section.querySelector('.neoGenreRail');
    links.forEach(function (old) {
      var a = document.createElement('a');
      a.href = old.href;
      a.textContent = old.textContent.trim();
      if (a.textContent) rail.appendChild(a);
    });
    app.appendChild(section);
  }

  addSpotlight();
  var logged = !!document.querySelector('.main-header a[href*="logout"]');
  if (logged) addRail('Jetzt weiterschauen', 'Jetzt weiterschauen', true, 30);
  addRail('Beliebte Serien', 'Beliebte Serien', false, 30);
  addRail('Die 50 neuesten Episoden', 'Die 50 neuesten Episoden', true, 50);
  addRail('Neue Serien', 'Neue Serien', false, 30);
  addRail('Serienkalender', 'Serienkalender', true, 40);
  if (logged) addRail('Deine Watchlist', 'Deine Watchlist', false, 50);
  addRail('Derzeit beliebt', 'Derzeit beliebt', false, 30);
  addRail('Das sehen andere SerienStreams-Nutzer', 'Das sehen andere SerienStreams Nutzer', false, 30);
  addGenres();

  if (!app.children.length) return;
  var style = document.createElement('style');
  style.id = 'neo-home-v2-style';
  style.textContent = 'body.neoHomeV2 #wrapper>.container{display:none!important}body.neoHomeV2 #wrapper>#neo-spotlight{display:none!important}body.neoHomeV2 .animeNews,body.neoHomeV2 .shoutbox{display:none!important}#neo-home-v2{max-width:1500px;margin:0 auto;padding:0 0 90px;background:#050505;color:#fff}#neo-home-v2>#neo-spotlight{display:flex!important;position:relative;min-height:570px;align-items:flex-end;overflow:hidden}.neoSpotlightImage,.neoSpotlightVideo,.neoSpotlightVideo iframe{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;border:0;pointer-events:none}.neoSpotlightShade{position:absolute;inset:0;background:linear-gradient(90deg,rgba(0,0,0,.94),rgba(0,0,0,.18)),linear-gradient(0deg,#050505,transparent 55%)}.neoSpotlightCopy{position:relative;z-index:2;padding:48px 5vw;max-width:780px}.neoSpotlightCopy h1{font-size:clamp(36px,7vw,72px);margin:8px 0 12px}.neoSpotlightCopy p{font-size:16px;line-height:1.5;color:#ddd;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}.neoSpotlightActions{display:flex;gap:10px;margin-top:16px}.neoSpotlightActions a{padding:13px 17px;border-radius:11px;color:#fff!important;text-decoration:none;font-weight:700}.neoWatch{background:#e50914}.neoDetails{background:#333}.neoRailSection{padding:22px 5vw 26px;border-top:1px solid #242424}.neoRailSection h2{font-size:24px;margin:0 0 17px;padding-left:12px;border-left:4px solid #e50914;color:#fff}.neoRail{display:flex;gap:14px;overflow-x:auto;overflow-y:hidden;padding:4px 2px 18px;scroll-snap-type:x mandatory;overscroll-behavior-inline:contain;-webkit-overflow-scrolling:touch}.neoRail::-webkit-scrollbar{height:4px}.neoRail::-webkit-scrollbar-thumb{background:#82101a}.neoRailCard{flex:0 0 145px;width:145px;color:#fff!important;text-decoration:none!important;scroll-snap-align:start}.neoRailCard img{display:block;width:145px;height:215px;object-fit:cover;border-radius:13px;background:#172027;box-shadow:0 8px 22px #000}.neoRailTitle{font-size:14px;font-weight:700;line-height:1.25;margin-top:9px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.neoRailMeta{font-size:11px;color:#aaa;margin-top:5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.neoGenreRail{display:flex;gap:9px;overflow-x:auto;padding-bottom:12px}.neoGenreRail a{flex:0 0 auto;background:#271015;border:1px solid #e50914;color:#fff!important;text-decoration:none;padding:10px 14px;border-radius:999px;font-weight:700}@media(max-width:600px){#neo-home-v2>#neo-spotlight{min-height:520px}.neoSpotlightCopy{padding:32px 5vw}.neoSpotlightCopy p{font-size:14px}}@media(min-width:700px){.neoRailCard,.neoRailCard img{width:185px}.neoRailCard{flex-basis:185px}.neoRailCard img{height:275px}.neoRailTitle{font-size:17px}}@media(min-width:1000px){.neoRailCard,.neoRailCard img{width:230px}.neoRailCard{flex-basis:230px}.neoRailCard img{height:345px}.neoRailSection h2{font-size:34px}}';
  style.textContent += '.neoSpotlightNav{position:absolute;z-index:5;right:3vw;bottom:34px;display:flex;align-items:center;gap:10px}.neoSpotlightNav>button{width:46px;height:46px;border:0;border-radius:50%;background:rgba(25,25,25,.9);color:#fff;font-size:30px}.neoDots{display:flex;gap:6px;max-width:78px;overflow:hidden;padding:4px}.neoDots button{flex:0 0 8px;width:8px;height:8px;padding:0;border:0;border-radius:50%;background:#641017}.neoDots button.active{background:#e50914;transform:scale(1.5)}@media(max-width:600px){.neoSpotlightNav{right:5vw;bottom:14px}.neoSpotlightNav>button{width:38px;height:38px;font-size:25px}.neoSpotlightCopy{padding-bottom:70px}.neoSpotlightCopy h1{font-size:clamp(29px,9vw,44px);line-height:1.05;display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}}';
  document.head.appendChild(style);
  document.body.classList.add('neoHomeV2');
  root.appendChild(app);
  app.querySelectorAll('.neoRail,.neoGenreRail').forEach(function (rail) {
    var startX = 0, startY = 0, startScroll = 0, horizontal = false;
    rail.style.touchAction = 'pan-y';
    rail.addEventListener('touchstart', function (event) {
      var touch = event.touches[0];
      startX = touch.clientX; startY = touch.clientY; startScroll = rail.scrollLeft; horizontal = false;
    }, { passive: true });
    rail.addEventListener('touchmove', function (event) {
      var touch = event.touches[0], dx = touch.clientX - startX, dy = touch.clientY - startY;
      if (!horizontal && Math.abs(dx) > Math.abs(dy) + 5) horizontal = true;
      if (horizontal) { event.preventDefault(); rail.scrollLeft = startScroll - dx; }
    }, { passive: false });
    rail.querySelectorAll('a').forEach(function (card) {
      card.addEventListener('focus', function () { card.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' }); });
      var image = card.querySelector('img'); if (image) image.setAttribute('draggable', 'false');
    });
  });
})();
