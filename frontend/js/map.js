/* map.js —— 无地图 SDK 的经纬度画布渲染（与 Android MapView 同一套投影逻辑）
   能力：站点着色 / 最快路线绘制（可选动画）/ 站点点击命中检测 / 悬停光标 / 暗色主题适配 */
const MapPainter = {
  /**
   * @param canvas  HTMLCanvasElement
   * @param view    { user:{lat,lng}, stations:[...], route:[{lat,lng}], animate?:boolean }
   */
  draw(canvas, view) {
    const ctx = canvas.getContext('2d');
    const W = canvas.clientWidth, H = canvas.clientHeight;
    if (!W || !H) return;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);
    canvas._animToken = (canvas._animToken || 0) + 1;   // 使旧动画失效

    const dark = document.documentElement.dataset.theme === 'dark';

    // 底图网格
    ctx.strokeStyle = 'rgba(120,150,190,.18)';
    ctx.lineWidth = 1;
    for (let i = 40; i < W; i += 40) {
      ctx.beginPath(); ctx.moveTo(i, 0); ctx.lineTo(i, H); ctx.stroke();
    }
    for (let i = 40; i < H; i += 40) {
      ctx.beginPath(); ctx.moveTo(0, i); ctx.lineTo(W, i); ctx.stroke();
    }

    const pad = 56;
    const w = W - pad * 2, h = H - pad * 2;
    if (w <= 0 || h <= 0) return;

    // 聚合所有点求包围盒
    const pts = [{ lat: view.user.lat, lng: view.user.lng }];
    (view.stations || []).forEach(s => pts.push(s));
    (view.route || []).forEach(r => pts.push(r));
    let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
    pts.forEach(p => {
      minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat);
      minLng = Math.min(minLng, p.lng); maxLng = Math.max(maxLng, p.lng);
    });
    const spanLat = (maxLat - minLat) || 1e-6;
    const spanLng = (maxLng - minLng) || 1e-6;

    const project = (lat, lng) => ({
      x: pad + (lng - minLng) / spanLng * w,
      y: pad + (maxLat - lat) / spanLat * h
    });

    // 最快路线（橙色折线，带阴影；可选生长动画）
    const route = view.route || [];
    if (route.length >= 2) {
      const pts2 = route.map(p => project(p.lat, p.lng));
      if (view.animate) this._animateRoute(canvas, ctx, pts2, 800);
      else this._strokeRoute(ctx, pts2, 1);
      // 起终点小圆
      ctx.fillStyle = '#FF6D00';
      ctx.beginPath(); ctx.arc(pts2[0].x, pts2[0].y, 5, 0, Math.PI * 2); ctx.fill();
      ctx.beginPath(); ctx.arc(pts2[pts2.length - 1].x, pts2[pts2.length - 1].y, 5, 0, Math.PI * 2); ctx.fill();
    }

    // 站点（注册命中区域供点击/悬停）
    const hits = [];
    (view.stations || []).forEach(s => {
      const p = project(s.lat, s.lng);
      const filled = s.available > 0;
      ctx.beginPath(); ctx.arc(p.x, p.y, 13, 0, Math.PI * 2);
      ctx.fillStyle = filled ? '#34A853' : '#EA4335';
      ctx.fill();
      ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; ctx.stroke();
      // 名称标签
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillStyle = dark ? '#CBD5E1' : '#374151';
      ctx.fillText(s.name && s.name.length > 8 ? s.name.slice(0, 8) + '…' : (s.name || ''), p.x, p.y - 18);
      hits.push({ id: s.id, x: p.x, y: p.y, r: 16 });
    });
    canvas._hits = hits;

    // 用户
    const u = project(view.user.lat, view.user.lng);
    ctx.beginPath(); ctx.arc(u.x, u.y, 15, 0, Math.PI * 2);
    ctx.fillStyle = '#1A73E8'; ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; ctx.stroke();
    ctx.font = 'bold 13px sans-serif';
    ctx.fillStyle = dark ? '#7FB3FF' : '#1A73E8';
    ctx.fillText('我', u.x - 7, u.y - 24);
  },

  /** 绘制折线的前 t 比例部分（0~1），用于生长动画 */
  _strokeRoute(ctx, pts2, t) {
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.shadowColor = 'rgba(255,109,0,.5)';
    ctx.shadowBlur = 8;
    ctx.strokeStyle = '#FF6D00';
    ctx.lineWidth = 7;
    ctx.beginPath();
    ctx.moveTo(pts2[0].x, pts2[0].y);
    if (t >= 1) {
      for (let i = 1; i < pts2.length; i++) ctx.lineTo(pts2[i].x, pts2[i].y);
      ctx.stroke();
      ctx.shadowBlur = 0;
      return;
    }
    let total = 0;
    const segs = [];
    for (let i = 1; i < pts2.length; i++) {
      const len = Math.hypot(pts2[i].x - pts2[i - 1].x, pts2[i].y - pts2[i - 1].y);
      segs.push({ a: pts2[i - 1], b: pts2[i], len });
      total += len;
    }
    const target = total * t;
    let acc = 0;
    for (const s of segs) {
      if (acc + s.len <= target) {
        ctx.lineTo(s.b.x, s.b.y);
        acc += s.len;
      } else {
        const k = (target - acc) / (s.len || 1);
        ctx.lineTo(s.a.x + (s.b.x - s.a.x) * k, s.a.y + (s.b.y - s.a.y) * k);
        break;
      }
    }
    ctx.stroke();
    ctx.shadowBlur = 0;
  },

  /** 路线从起点"生长"到终点（约 dur 毫秒）；画布重绘时旧动画自动停止 */
  _animateRoute(canvas, ctx, pts2, dur) {
    const token = canvas._animToken;
    const t0 = performance.now();
    const step = now => {
      if (canvas._animToken !== token) return;   // 已被新一轮绘制取代
      const t = Math.min(1, (now - t0) / dur);
      this._strokeRoute(ctx, pts2, t);
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  },

  /** 命中检测：返回站点 id 或 null（坐标单位：CSS 像素，相对画布左上角） */
  hitTest(canvas, x, y) {
    const hits = canvas._hits || [];
    for (const h of hits) {
      const dx = x - h.x, dy = y - h.y;
      if (dx * dx + dy * dy <= h.r * h.r) return h.id;
    }
    return null;
  }
};
