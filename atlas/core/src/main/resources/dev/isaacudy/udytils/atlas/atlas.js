(function() {
  var GRID = 32, NS = 'http://www.w3.org/2000/svg';
  var THUMB_W = 144, CURVE_GAP = 140, TN_GAP = 12, REF_GAP = 10;
  var viewport = document.getElementById('viewport');
  var world = document.getElementById('world');
  var searchInput = document.getElementById('search-input');
  var searchResults = document.getElementById('search-results');
  var scale = 1, panX = 0, panY = 0, isDrag = false, lastX = 0, lastY = 0;

  function applyTransform() {
    world.style.transform = 'translate('+panX+'px,'+panY+'px) scale('+scale+')';
    var gs = GRID*scale;
    viewport.style.backgroundSize = gs+'px '+gs+'px';
    viewport.style.backgroundPosition = panX+'px '+panY+'px';
  }
  viewport.addEventListener('mousedown', function(e) {
    if (e.target.closest('select,button,.ref,a,input,textarea')) return;
    isDrag=true; lastX=e.clientX; lastY=e.clientY;
    viewport.classList.add('dragging'); e.preventDefault();
  });
  window.addEventListener('mousemove', function(e) {
    if (!isDrag) return;
    panX+=e.clientX-lastX; panY+=e.clientY-lastY;
    lastX=e.clientX; lastY=e.clientY; applyTransform();
  });
  window.addEventListener('mouseup', function() { if(isDrag) savePanZoom(); isDrag=false; viewport.classList.remove('dragging'); });
  viewport.addEventListener('wheel', function(e) {
    if (e.ctrlKey||e.metaKey) {
      e.preventDefault();
      var r=viewport.getBoundingClientRect(), mx=e.clientX-r.left, my=e.clientY-r.top;
      var wx=(mx-panX)/scale, wy=(my-panY)/scale;
      scale=Math.max(0.1,Math.min(5,scale*Math.exp(-e.deltaY*0.004)));
      panX=mx-wx*scale; panY=my-wy*scale;
    } else { e.preventDefault(); panX-=e.deltaX; panY-=e.deltaY; }
    applyTransform(); debounceSave();
  }, {passive:false});

  var IMG_H = 600;
  function singleScreenScale() {
    var vh = viewport.clientHeight;
    return Math.min((vh * 0.75) / IMG_H, 1);
  }

  function zoomToFit() {
    var vw=viewport.clientWidth, vh=viewport.clientHeight;
    var ww=world.scrollWidth, wh=world.scrollHeight;
    if (!ww||!wh) return;
    scale=Math.min(vw/ww,vh/wh,1);
    panX=(vw-ww*scale)/2; panY=Math.max(0,(vh-wh*scale)/2);
    applyTransform(); savePanZoom();
  }

  function zoomSingleScreen() {
    var s = singleScreenScale();
    var vr = viewport.getBoundingClientRect();
    var cx = vr.width/2, cy = vr.height/2;
    var wx = (cx-panX)/scale, wy = (cy-panY)/scale;
    scale = s;
    panX = cx - wx*scale; panY = cy - wy*scale;
    applyTransform(); savePanZoom();
  }

  function zoomDefault() {
    scale = singleScreenScale();
    panX = 0; panY = 0;
    applyTransform(); savePanZoom();
  }

  var PZK = 'ui-atlas-panzoom';
  function savePanZoom() {
    try { localStorage.setItem(PZK, JSON.stringify({s:scale,x:panX,y:panY})); } catch(e) {}
  }
  function loadPanZoom() {
    try {
      var d = JSON.parse(localStorage.getItem(PZK));
      if (d && typeof d.s === 'number') { scale=d.s; panX=d.x; panY=d.y; return true; }
    } catch(e) {}
    return false;
  }

  var saveTimer = null;
  function debounceSave() { clearTimeout(saveTimer); saveTimer = setTimeout(savePanZoom, 300); }

  document.getElementById('zoom-fit-btn').addEventListener('click', zoomToFit);
  document.getElementById('zoom-single-btn').addEventListener('click', zoomSingleScreen);
  document.getElementById('zoom-in-btn').addEventListener('click', function() {
    var r=viewport.getBoundingClientRect(),cx=r.width/2,cy=r.height/2;
    var wx=(cx-panX)/scale,wy=(cy-panY)/scale;
    scale=Math.min(5,scale*1.3);panX=cx-wx*scale;panY=cy-wy*scale;applyTransform();savePanZoom();
  });
  document.getElementById('zoom-out-btn').addEventListener('click', function() {
    var r=viewport.getBoundingClientRect(),cx=r.width/2,cy=r.height/2;
    var wx=(cx-panX)/scale,wy=(cy-panY)/scale;
    scale=Math.max(0.1,scale/1.3);panX=cx-wx*scale;panY=cy-wy*scale;applyTransform();savePanZoom();
  });

  var CK='ui-atlas-collapsed';
  function ldC(){try{return JSON.parse(localStorage.getItem(CK)||'{}');}catch(e){return {};}}
  function svC(d){localStorage.setItem(CK,JSON.stringify(d));}
  var cs=ldC();
  document.querySelectorAll('.module-header,.feature-header').forEach(function(h){
    var k=h.dataset.collapseKey;
    if(cs[k]) h.classList.add('collapsed');
    h.addEventListener('click',function(){
      h.classList.toggle('collapsed');
      var s=ldC();if(h.classList.contains('collapsed'))s[k]=true;else delete s[k];svC(s);
      scheduleLayout();
    });
  });

  document.querySelectorAll('.variant-select').forEach(function(sel){
    sel.addEventListener('change',function(){
      var card=document.getElementById(sel.dataset.cardId);
      if(!card) return;
      var img=card.querySelector('.snapshot');
      if(!img) return;
      var vs=JSON.parse(img.dataset.variants);
      var v=vs[parseInt(sel.value)];
      if(v){img.src=v.imagePath; scheduleLayout();}
    });
  });

  var globalLeftW = 0, globalRightW = 0;
  var globalLeftNameW = 0, globalRightNameW = 0;
  var globalLeftContent = 0, globalRightContent = 0;

  function measureGlobals() {
    globalLeftNameW = 0; globalRightNameW = 0;
    document.querySelectorAll('.screen-row').forEach(function(row) {
      row.querySelectorAll('.gutter-in .ref .ref-label').forEach(function(l) {
        globalLeftNameW = Math.max(globalLeftNameW, l.scrollWidth);
      });
      row.querySelectorAll('.gutter-out .ref .ref-label').forEach(function(l) {
        globalRightNameW = Math.max(globalRightNameW, l.scrollWidth);
      });
    });
    globalLeftContent = THUMB_W + TN_GAP + globalLeftNameW;
    globalRightContent = THUMB_W + TN_GAP + globalRightNameW;
    globalLeftW = globalLeftContent > THUMB_W ? globalLeftContent + CURVE_GAP : 40;
    globalRightW = globalRightContent > THUMB_W ? globalRightContent + CURVE_GAP : 40;
  }

  function layoutAll() {
    measureGlobals();
    document.querySelectorAll('.screen-row').forEach(function(row) { try { layoutRow(row); } catch(e) {} });
  }

  function layoutRow(row) {
    var svg = row.querySelector('.connectors');
    if (svg) svg.innerHTML = '';

    var imgEl = row.querySelector('.screen-img');
    if (!imgEl) return;
    var imgH = imgEl.offsetHeight || 100;
    var imgCY = imgH / 2;

    var screen = row.closest('.screen');
    screen.style.paddingTop = '';

    var caption = screen.querySelector('.screen-caption');
    var toolbar = screen.querySelector('.screen-toolbar');

    function layoutGutter(gutter, side) {
      var isIn = side === 'in';
      var gutterW = isIn ? globalLeftW : globalRightW;
      var contentW = isIn ? globalLeftContent : globalRightContent;
      var nameW = isIn ? globalLeftNameW : globalRightNameW;
      gutter.style.width = gutterW + 'px';

      var refs = Array.from(gutter.querySelectorAll('.ref'));
      if (!refs.length) return 0;

      var refData = refs.map(function(ref) {
        var thumb = ref.querySelector('.ref-thumb, .ref-thumb-placeholder');
        var label = ref.querySelector('.ref-label');
        var th = thumb ? thumb.offsetHeight : 80;
        if (th < 20) th = 80;
        if (label) { label.style.width = nameW + 'px'; label.style.flexShrink = '0'; }
        return { ref: ref, h: th };
      });

      var totalH = 0;
      refData.forEach(function(d, i) { totalH += d.h; if (i > 0) totalH += REF_GAP; });
      var startY = imgCY - totalH / 2;

      var curY = startY;
      refData.forEach(function(d) {
        d.ref.style.top = curY + 'px';
        d.ref.style.height = d.h + 'px';
        d.ref.style.width = contentW + 'px';
        d.ref.style.flexDirection = 'row';
        if (isIn) {
          d.ref.style.right = CURVE_GAP + 'px';
          d.ref.style.left = 'auto';
        } else {
          d.ref.style.left = CURVE_GAP + 'px';
          d.ref.style.right = 'auto';
        }
        curY += d.h + REF_GAP;
      });

      gutter.style.height = Math.max(imgH, totalH) + 'px';
      return startY;
    }

    var gutterIn = row.querySelector('.gutter-in');
    var gutterOut = row.querySelector('.gutter-out');

    var startIn = gutterIn ? layoutGutter(gutterIn, 'in') : 0;
    var startOut = gutterOut ? layoutGutter(gutterOut, 'out') : 0;

    gutterIn.style.width = globalLeftW + 'px';
    gutterOut.style.width = globalRightW + 'px';

    var leftW = globalLeftW, imgW = imgEl.offsetWidth || 600;
    if (caption) { caption.style.paddingLeft = leftW + 'px'; caption.style.maxWidth = (leftW + imgW) + 'px'; }
    if (toolbar) { toolbar.style.paddingLeft = leftW + 'px'; toolbar.style.maxWidth = (leftW + imgW) + 'px'; }

    var worstOverflow = Math.max(0, -startIn, -startOut);
    if (worstOverflow > 0) {
      screen.style.paddingTop = Math.ceil(worstOverflow) + 'px';
    }

    if (!svg) return;
    var rw = row.scrollWidth, rh = row.scrollHeight;
    svg.setAttribute('width', rw); svg.setAttribute('height', rh);
    svg.style.width = rw+'px'; svg.style.height = rh+'px';

    var defs = document.createElementNS(NS,'defs');
    var mk = document.createElementNS(NS,'marker');
    mk.setAttribute('id','ah-'+svg.id); mk.setAttribute('markerWidth','7');
    mk.setAttribute('markerHeight','5'); mk.setAttribute('refX','7');
    mk.setAttribute('refY','2.5'); mk.setAttribute('orient','auto');
    var pg = document.createElementNS(NS,'polygon');
    pg.setAttribute('points','0 0, 7 2.5, 0 5');
    pg.setAttribute('fill','#a8a29e'); pg.setAttribute('opacity','0.5');
    mk.appendChild(pg); defs.appendChild(mk); svg.appendChild(defs);
    var mid = 'url(#ah-'+svg.id+')';

    function lo(el) {
      var x=0,y=0,c=el;
      while(c&&c!==row){x+=c.offsetLeft;y+=c.offsetTop;c=c.offsetParent;}
      return {x:x,y:y};
    }
    var ip = lo(imgEl);
    var imgL = ip.x, imgR = ip.x + imgEl.offsetWidth;
    var imgTop = ip.y, imgBot = ip.y + imgEl.offsetHeight;

    function drawCurve(sx,sy,tx,ty,kind) {
      var cpx=Math.abs(tx-sx)*0.45;
      var p=document.createElementNS(NS,'path');
      var c1x=sx<tx?sx+cpx:sx-cpx, c2x=sx<tx?tx-cpx:tx+cpx;
      p.setAttribute('d','M'+sx+','+sy+' C'+c1x+','+sy+' '+c2x+','+ty+' '+tx+','+ty);
      var cls='conn-edge';
      if(kind==='result')cls+=' kind-result'; else if(kind==='chrome')cls+=' kind-chrome';
      p.setAttribute('class',cls); p.setAttribute('marker-end',mid);
      svg.appendChild(p);
    }

    var imgMidY = (imgTop + imgBot) / 2;
    var ANCHOR_SPREAD = 12;

    var inRefs = Array.from(row.querySelectorAll('.gutter-in .ref'));
    inRefs.forEach(function(ref, i) {
      var label = ref.querySelector('.ref-label');
      if (!label) return;
      var lp = lo(label);
      var sx = lp.x + label.offsetWidth, sy = lp.y + label.offsetHeight/2;
      var ay = imgMidY + (i - (inRefs.length-1)/2) * ANCHOR_SPREAD;
      ay = Math.max(imgTop+4, Math.min(imgBot-4, ay));
      drawCurve(sx,sy,imgL,ay,ref.dataset.kind);
    });

    var outRefs = Array.from(row.querySelectorAll('.gutter-out .ref'));
    outRefs.forEach(function(ref, i) {
      var label = ref.querySelector('.ref-label');
      if (!label) return;
      var lp = lo(label);
      var tx = lp.x, ty = lp.y + label.offsetHeight/2;
      var ay = imgMidY + (i - (outRefs.length-1)/2) * ANCHOR_SPREAD;
      ay = Math.max(imgTop+4, Math.min(imgBot-4, ay));
      drawCurve(imgR,ay,tx,ty,ref.dataset.kind);
    });
  }

  function scheduleLayout() { requestAnimationFrame(layoutAll); }
  window.addEventListener('load', scheduleLayout);
  document.querySelectorAll('.snapshot').forEach(function(img) { img.addEventListener('load', scheduleLayout); });
  setTimeout(scheduleLayout, 300);
  setTimeout(scheduleLayout, 1500);

  function focusCard(dest) {
    var card = document.getElementById('card-'+dest);
    if (!card) return;
    var el = card.parentElement;
    while (el && el !== world) {
      if (el.classList.contains('collapsible-body')) {
        var hdr = el.previousElementSibling;
        if (hdr && hdr.classList.contains('collapsed')) {
          hdr.classList.remove('collapsed');
          var s=ldC(); delete s[hdr.dataset.collapseKey]; svC(s);
        }
      }
      el = el.parentElement;
    }
    requestAnimationFrame(function() {
      layoutAll();
      var oldScale = scale;
      var vr=viewport.getBoundingClientRect(), cr=card.getBoundingClientRect();
      var wx=(cr.left-vr.left-panX)/oldScale, wy=(cr.top-vr.top-panY)/oldScale;
      var ww=cr.width/oldScale, wh=cr.height/oldScale;
      scale = singleScreenScale();
      panX=vr.width/2-(wx+ww/2)*scale; panY=vr.height/2-(wy+wh/2)*scale;
      applyTransform(); savePanZoom();
      card.classList.add('focused');
      setTimeout(function(){card.classList.remove('focused');},1500);
    });
  }
  document.querySelectorAll('.ref').forEach(function(r){
    r.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();focusCard(r.dataset.dest);});
  });

  function toHtmlId(qn) { return qn.replace(/\./g, '_'); }

  searchInput.addEventListener('input',function(){
    var q=searchInput.value.toLowerCase().trim();
    if(q.length<2){searchResults.hidden=true;return;}
    var m=MANIFEST.nodes.filter(function(n){
      return n.displayName.toLowerCase().includes(q)||n.screenName.toLowerCase().includes(q)||n.destinationName.toLowerCase().includes(q);
    }).slice(0,15);
    if(!m.length){searchResults.hidden=true;return;}
    searchResults.innerHTML=''; searchResults.hidden=false;
    m.forEach(function(n){
      var d=document.createElement('div');d.className='search-item';
      d.innerHTML=n.displayName+'<span class="search-feature">'+n.featureGroup+'</span>';
      d.addEventListener('click',function(){searchResults.hidden=true;searchInput.value='';focusCard(toHtmlId(n.qualifiedName));});
      searchResults.appendChild(d);
    });
  });
  searchInput.addEventListener('blur',function(){setTimeout(function(){searchResults.hidden=true;},200);});

  var AK='ui-atlas-annotations';
  function ldA(){try{return JSON.parse(localStorage.getItem(AK)||'{}');}catch(e){return {};}}
  function svA(d){localStorage.setItem(AK,JSON.stringify(d));}
  function rfM(){
    var d=ldA();
    MANIFEST.nodes.forEach(function(n){
      var mk=document.getElementById('marker-'+toHtmlId(n.qualifiedName));
      if(mk) mk.hidden=!Object.keys(d).some(function(k){return k.startsWith(n.qualifiedName+'|')&&d[k].note;});
    });
  }
  rfM();
  var caDest=null,caVar=null;
  document.querySelectorAll('.annotate-btn').forEach(function(btn){
    btn.addEventListener('click',function(e){
      e.stopPropagation();
      var dest=btn.dataset.dest, scr=btn.closest('.screen');
      var sel=scr?scr.querySelector('.variant-select'):null;
      caVar=sel?sel.options[sel.selectedIndex].text:'Default';
      caDest=dest;
      document.getElementById('annotation-title').textContent=dest+' / '+caVar;
      var d=ldA();
      document.getElementById('annotation-text').value=(d[dest+'|'+caVar]&&d[dest+'|'+caVar].note)||'';
      document.getElementById('annotation-panel').hidden=false;
    });
  });
  document.getElementById('annotation-close').addEventListener('click',function(){document.getElementById('annotation-panel').hidden=true;});
  document.getElementById('annotation-save').addEventListener('click',function(){
    if(!caDest) return;
    var d=ldA(),k=caDest+'|'+caVar,note=document.getElementById('annotation-text').value.trim();
    if(note){
      var nd=MANIFEST.nodes.find(function(n){return toHtmlId(n.qualifiedName)===caDest;});
      d[k]={note:note,screen:caDest,feature:nd?nd.featureGroup:'',variant:caVar,updatedAt:new Date().toISOString()};
    } else delete d[k];
    svA(d);rfM();rfNB();document.getElementById('annotation-panel').hidden=true;
  });
  document.getElementById('annotation-delete').addEventListener('click',function(){
    if(!caDest) return;
    var d=ldA(); delete d[caDest+'|'+caVar]; svA(d);
    rfM();rfNB();document.getElementById('annotation-panel').hidden=true;
  });
  document.getElementById('annotation-clear-all').addEventListener('click',function(){
    if(!confirm('Clear ALL annotations?')) return;
    localStorage.removeItem(AK);rfM();rfNB();document.getElementById('annotation-panel').hidden=true;
  });
  document.getElementById('export-csv-btn').addEventListener('click',function(){
    var d=ldA(),ks=Object.keys(d);
    if(!ks.length){alert('No annotations to export.');return;}
    var csv='screen,feature,variant,note,updatedAt\n';
    ks.forEach(function(k){var r=d[k];csv+=[r.screen,r.feature,r.variant,r.note,r.updatedAt].map(ce).join(',')+'\n';});
    var b=new Blob([csv],{type:'text/csv'}),u=URL.createObjectURL(b);
    var a=document.createElement('a');a.href=u;a.download='ui-atlas-annotations.csv';
    document.body.appendChild(a);a.click();document.body.removeChild(a);URL.revokeObjectURL(u);
  });
  function ce(s){if(!s)return'';return(s.includes(',')||s.includes('"')||s.includes('\n'))?'"'+s.replace(/"/g,'""')+'"':s;}

  function rfNB(){if(!notesBrowser.hidden) buildNoteList();}
  var notesBrowser=document.getElementById('notes-browser');
  var notesList=document.getElementById('notes-list');
  var notesPos=document.getElementById('notes-pos');
  var notesCount=document.getElementById('notes-count');
  var noteIdx=-1, noteKeys=[];

  function outlineOrder() {
    var order=[];
    document.querySelectorAll('.screen[data-dest]').forEach(function(el){order.push(el.dataset.dest);});
    return order;
  }

  function buildNoteList() {
    var d=ldA(), order=outlineOrder();
    var entries=[];
    Object.keys(d).forEach(function(k){
      var r=d[k]; if(!r||!r.note) return;
      var parts=k.split('|'); var dest=parts[0]; var variant=parts.slice(1).join('|');
      var nd=MANIFEST.nodes.find(function(n){return toHtmlId(n.qualifiedName)===dest;});
      var idx=order.indexOf(dest);
      entries.push({key:k, dest:dest, variant:variant, display:nd?nd.displayName:dest, note:r.note, order:idx>=0?idx:9999});
    });
    entries.sort(function(a,b){return a.order-b.order||a.variant.localeCompare(b.variant);});
    noteKeys=entries;
    notesCount.textContent=entries.length;
    notesList.innerHTML='';
    entries.forEach(function(e,i){
      var stale=!document.getElementById('card-'+e.dest);
      var div=document.createElement('div'); div.className='note-entry'+(stale?' note-stale':''); div.dataset.idx=i;
      div.innerHTML='<div><span class="note-entry-screen">'+e.display+'</span><span class="note-entry-variant">'+e.variant+'</span></div><div class="note-entry-text">'+e.note.split('\n')[0]+'</div>';
      if(!stale) div.addEventListener('click',function(){jumpToNote(i);});
      notesList.appendChild(div);
    });
    if(noteIdx>=entries.length) noteIdx=entries.length-1;
    updateNotePos();
  }

  function updateNotePos() {
    if(!noteKeys.length){notesPos.textContent=''; return;}
    notesPos.textContent=(noteIdx+1)+' / '+noteKeys.length;
    notesList.querySelectorAll('.note-entry').forEach(function(el,i){el.classList.toggle('note-active',i===noteIdx);});
    var active=notesList.querySelector('.note-active');
    if(active) active.scrollIntoView({block:'nearest'});
  }

  function jumpToNote(i) {
    if(i<0||i>=noteKeys.length) return;
    noteIdx=i; updateNotePos();
    var e=noteKeys[i];
    var card=document.getElementById('card-'+e.dest);
    if(!card) return;
    var el=card.parentElement;
    while(el&&el!==world){
      if(el.classList.contains('collapsible-body')){
        var hdr=el.previousElementSibling;
        if(hdr&&hdr.classList.contains('collapsed')){hdr.classList.remove('collapsed');var s=ldC();delete s[hdr.dataset.collapseKey];svC(s);}
      }
      el=el.parentElement;
    }
    requestAnimationFrame(function(){requestAnimationFrame(function(){
      layoutAll();
      var sel=card.querySelector('.variant-select');
      if(sel){
        for(var oi=0;oi<sel.options.length;oi++){
          if(sel.options[oi].text===e.variant){sel.selectedIndex=oi;sel.dispatchEvent(new Event('change'));break;}
        }
      }
      var vr=viewport.getBoundingClientRect(),cr=card.getBoundingClientRect();
      var wx=(cr.left-vr.left-panX)/scale,wy=(cr.top-vr.top-panY)/scale;
      var ww=cr.width/scale,wh=cr.height/scale;
      panX=vr.width/2-(wx+ww/2)*scale;panY=vr.height/2-(wy+wh/2)*scale;
      applyTransform();savePanZoom();
      caDest=e.dest;caVar=e.variant;
      document.getElementById('annotation-title').textContent=e.display+' / '+e.variant;
      var d=ldA();
      document.getElementById('annotation-text').value=(d[e.key]&&d[e.key].note)||'';
      document.getElementById('annotation-panel').hidden=false;
    });});
  }

  function closeNotes(){notesBrowser.hidden=true;}
  document.getElementById('notes-btn').addEventListener('click',function(){
    notesBrowser.hidden=!notesBrowser.hidden;
    if(!notesBrowser.hidden) buildNoteList();
  });
  document.getElementById('notes-browser-close').addEventListener('click',closeNotes);
  document.getElementById('notes-prev').addEventListener('click',function(){if(noteKeys.length) jumpToNote(noteIdx<=0?noteKeys.length-1:noteIdx-1);});
  document.getElementById('notes-next').addEventListener('click',function(){if(noteKeys.length) jumpToNote(noteIdx>=noteKeys.length-1?0:noteIdx+1);});
  viewport.addEventListener('click',function(e){if(!notesBrowser.hidden&&!e.target.closest('.notes-browser')&&!e.target.closest('#notes-btn'))closeNotes();});
  document.addEventListener('keydown',function(e){
    if(e.key==='Escape'&&!notesBrowser.hidden){closeNotes();return;}
    if(notesBrowser.hidden) return;
    if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA'||e.target.tagName==='SELECT') return;
    if(e.key==='n'){e.preventDefault();document.getElementById('notes-next').click();}
    if(e.key==='p'){e.preventDefault();document.getElementById('notes-prev').click();}
  });

  (function migrateAnnotationKeys(){
    var d=ldA(), changed=false;
    var bySimple={};
    MANIFEST.nodes.forEach(function(n){
      var simple=n.destinationName;
      (bySimple[simple]=bySimple[simple]||[]).push(n);
    });
    var newD={};
    Object.keys(d).forEach(function(k){
      var parts=k.split('|'), dest=parts[0], variant=parts.slice(1).join('|');
      if(document.getElementById('card-'+dest)){newD[k]=d[k];return;}
      var candidates=bySimple[dest];
      if(candidates&&candidates.length===1){
        var newDest=toHtmlId(candidates[0].qualifiedName);
        var newKey=newDest+'|'+variant;
        var entry=d[k]; entry.screen=newDest;
        newD[newKey]=entry; changed=true;
      } else { newD[k]=d[k]; }
    });
    if(changed) svA(newD);
  })();
  buildNoteList();

  var ul=document.getElementById('unresolved-link');
  if(ul) ul.addEventListener('click',function(e){
    e.preventDefault();
    var p=document.getElementById('appendix-panel');
    if(p){p.open=true;p.scrollIntoView({behavior:'smooth',block:'end'});}
  });

  setTimeout(function() {
    if (loadPanZoom()) { applyTransform(); }
    else { zoomToFit(); }
  }, 100);
})();
