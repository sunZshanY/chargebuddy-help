/* app.js —— 充电宝救援 ChargeBuddy · 网页版主逻辑
 * 页面 = 首页(README 文档) + 演示(地图/低电量/路线) + 详情 + 下载
 * 技术 = HTML + CSS + JS（Vue3）+ Canvas 自绘地图，无任何三方地图 SDK */
const { createApp, ref, reactive, computed, onMounted, nextTick, watch } = Vue;

// 默认演示坐标：上海人民广场
const DEFAULT_POS = { lat: 31.2304, lng: 121.4737 };

createApp({
  setup() {
    // ---------- 页面状态 ----------
    const route = ref('home');                       // home | demo | detail | download
    const theme = ref(localStorage.getItem('cb-theme') || 'light');

    // ---------- 演示数据 ----------
    const current = ref({ ...DEFAULT_POS });
    const stations = ref([]);
    const selected = ref(null);
    const routeInfo = ref(null);
    const routeMode = ref('time');                   // time(最快) | distance(最短)
    const currentRoute = ref([]);
    const battery = ref(88);
    const batteryText = ref('电量充足');
    const loading = ref(false);
    const notice = ref('');
    const noticeErr = ref(false);
    const apkState = ref('checking');                // yes | no | checking
    const backendOk = ref(null);
    const backendLabel = ref('后端检测中…');
    const statStations = ref('-');
    const statEngine = ref('-');
    const routeText = ref('');
    const routeEngine = ref('-');
    const modal = reactive({ show: false, title: '', body: '' });

    const homeCanvas = ref(null);
    const detailCanvas = ref(null);
    let emergencyActive = false;
    const firedLevels = new Set();
    let drainTimer = null;

    // ---------- 文档页交互状态 ----------
    const searchQuery = ref('');
    const filteredStations = computed(() => {
      const q = searchQuery.value.trim().toLowerCase();
      if (!q) return stations.value;
      return stations.value.filter(s => (s.name || '').toLowerCase().includes(q));
    });
    const openSteps = reactive({ 1: true, 2: true, 3: false, 4: false, 5: false, 6: false });
    const allStepsOpen = ref(false);
    const showTop = ref(false);

    // ---------- 主题（亮/暗） ----------
    function applyTheme() { document.documentElement.dataset.theme = theme.value; }
    function toggleTheme() {
      theme.value = theme.value === 'dark' ? 'light' : 'dark';
      localStorage.setItem('cb-theme', theme.value);
      applyTheme();
      // 画布文字颜色随主题变化，需要重绘
      nextTick(() => {
        if (route.value === 'demo') drawHome();
        else if (route.value === 'detail') drawDetail();
      });
    }

    // ---------- 文档页辅助 ----------
    function copyCode(evt) {
      const btn = evt.currentTarget;
      const wrap = btn.closest('.code-block');
      const code = wrap ? wrap.querySelector('code') : null;
      if (!code) return;
      const text = code.innerText;
      const done = () => {
        btn.textContent = '✓ 已复制';
        btn.classList.add('copied');
        setTimeout(() => { btn.textContent = '复制'; btn.classList.remove('copied'); }, 1500);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done));
      } else {
        fallbackCopy(text, done);
      }
    }

    function fallbackCopy(text, done) {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); done(); } catch (e) { /* 忽略 */ }
      document.body.removeChild(ta);
    }

    function jumpTo(id) {
      if (route.value !== 'home') location.hash = '#/';
      nextTick(() => {
        const el = document.getElementById(id);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    }

    function goHome() {
      if (route.value === 'home') window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    // ---------- 折叠步骤 ----------
    function toggleStep(n) { openSteps[n] = !openSteps[n]; }
    function toggleAllSteps() {
      allStepsOpen.value = !allStepsOpen.value;
      for (let i = 1; i <= 6; i++) openSteps[i] = allStepsOpen.value;
    }

    // ---------- 回到顶部 ----------
    let scrollRaf = false;
    function onScroll() {
      if (scrollRaf) return;
      scrollRaf = true;
      requestAnimationFrame(() => {
        showTop.value = window.scrollY > 600;
        scrollRaf = false;
      });
    }
    function goTop() { window.scrollTo({ top: 0, behavior: 'smooth' }); }

    // ---------- 代码高亮（轻量：注释/字符串/数字/关键词） ----------
    function escHtml(s) {
      return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }
    function highlightLine(line) {
      const re = /(#.*$|\/\/.*$)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|(?<![\w.])(\d+(?:\.\d+)?)(?![\w.])|\b(cd|pip|install|python|uvicorn|char|void|int|return)\b/g;
      let html = '', last = 0, m;
      while ((m = re.exec(line))) {
        html += escHtml(line.slice(last, m.index));
        if (m[1]) html += '<span class="hl-c">' + escHtml(m[1]) + '</span>';
        else if (m[2]) html += '<span class="hl-s">' + escHtml(m[2]) + '</span>';
        else if (m[3]) html += '<span class="hl-n">' + escHtml(m[3]) + '</span>';
        else if (m[4]) html += '<span class="hl-k">' + escHtml(m[4]) + '</span>';
        last = m.index + m[0].length;
      }
      return html + escHtml(line.slice(last));
    }
    function highlightCode() {
      document.querySelectorAll('.code-block code').forEach(c => {
        if (c.dataset.hl) return;
        c.innerHTML = c.textContent.split('\n').map(highlightLine).join('\n');
        c.dataset.hl = '1';
      });
    }

    // ---------- 电量 ----------
    function updateBatteryText() {
      const v = battery.value;
      batteryText.value = v > 50 ? '电量充足' : v > 20 ? '电量健康' : v > 5 ? '电量偏低' : '电量告急！';
    }

    function flash(text, isErr = false) {
      notice.value = text;
      noticeErr.value = isErr;
    }

    // ---------- 定位 ----------
    function locate() {
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          pos => {
            current.value = { lat: pos.coords.latitude, lng: pos.coords.longitude };
            flash('已定位：' + current.value.lat.toFixed(4) + ', ' + current.value.lng.toFixed(4));
            refresh();
          },
          () => { flash('定位被拒绝，使用演示坐标（上海人民广场）', true); refresh(); },
          { timeout: 5000 }
        );
      } else {
        refresh();
      }
    }

    // ---------- 数据刷新 ----------
    async function refresh() {
      loading.value = true;
      try {
        stations.value = await API.stations(current.value.lat, current.value.lng);
        flash('已加载 ' + stations.value.length + ' 个充电宝站点');
        await nextTick();
        drawHome();
      } catch (e) {
        flash(e.message, true);
      } finally {
        loading.value = false;
      }
    }

    // ---------- 低电量自动定位（20%/10%/5% 或省电模式） ----------
    async function simulateLowBattery(level = 20) {
      if (emergencyActive) return;
      emergencyActive = true;
      battery.value = level;
      updateBatteryText();
      flash('电量仅剩 ' + level + '%！正在自动获取位置并寻找最近可借充电宝…');
      loading.value = true;
      try {
        const resp = await API.emergency(current.value.lat, current.value.lng);
        stations.value = [resp.station];
        currentRoute.value = resp.route.pathCoords;
        selected.value = resp.station;
        routeInfo.value = resp.route;
        flash(resp.hint);
        // 页面内弹窗（替代 alert）
        modal.show = true;
        modal.title = '低电量自动定位成功';
        modal.body =
          '电量即将耗尽（' + level + '%）！\n\n' +
          '最近可借充电宝：' + resp.station.name + '\n' +
          '可用 ' + resp.station.available + ' 个 · 步行约 ' + Math.round(resp.station.distance_m) + ' 米\n\n' +
          resp.hint;
        await nextTick();
        drawHome(true);                      // 路线生长动画
      } catch (e) {
        flash('低电量兜底失败：' + e.message, true);
      } finally {
        loading.value = false;
        emergencyActive = false;
      }
    }

    // ---------- 详情 / 路线 ----------
    function goDetail(s) {
      selected.value = s;
      location.hash = '#/detail/' + s.id;
    }

    function closeModal() { modal.show = false; }

    function goModalDetail() {
      modal.show = false;
      if (selected.value) goDetail(selected.value);
    }

    async function loadRoute(mode) {
      const s = selected.value;
      if (!s) return;
      if (mode) routeMode.value = mode;
      const label = routeMode.value === 'time' ? '最快' : '最短';
      flash('正在计算' + label + '路线（C++ 算法）…');
      try {
        routeInfo.value = await API.route(current.value.lat, current.value.lng, s.id, routeMode.value);
        currentRoute.value = routeInfo.value.pathCoords || [];
        flash('已生成到「' + s.name + '」的' + label + '路线');
      } catch (e) {
        routeInfo.value = null;
        flash(e.message, true);
      }
      await nextTick();
      drawDetail(true);                      // 路线生长动画
    }

    // ---------- 借用 / 归还 ----------
    async function rent() {
      const s = selected.value;
      if (!s || s.available <= 0) return;
      try {
        await API.rent(s.id);
        s.available--;
        battery.value = 100;                 // 借用成功，开始充电
        firedLevels.clear();
        updateBatteryText();
        flash('借用成功！正在充电，电量已恢复为 ' + battery.value + '%');
      } catch (e) {
        flash('借用失败：' + e.message, true);
      }
    }

    async function rentBack() {
      const s = selected.value;
      if (!s) return;
      try {
        const r = await API.returnBank(s.id);
        s.available = r.available;
        flash('归还成功！该点位现有 ' + s.available + ' 个可借充电宝');
      } catch (e) {
        flash('归还失败：' + e.message, true);
      }
    }

    // ---------- 地图绘制 ----------
    function drawHome(animate = false) {
      if (homeCanvas.value) {
        MapPainter.draw(homeCanvas.value, {
          user: current.value,
          stations: stations.value,
          route: currentRoute.value,
          animate
        });
      }
    }

    function drawDetail(animate = false) {
      if (detailCanvas.value && selected.value) {
        MapPainter.draw(detailCanvas.value, {
          user: current.value,
          stations: stations.value.length ? stations.value : [selected.value],
          route: routeInfo.value ? (routeInfo.value.pathCoords || []) : [],
          animate
        });
      }
    }

    // ---------- 地图交互（点击站点进入详情 / 悬停光标） ----------
    function canvasClick(e, canvas) {
      if (!canvas) return;
      const rect = canvas.getBoundingClientRect();
      const id = MapPainter.hitTest(canvas, e.clientX - rect.left, e.clientY - rect.top);
      if (id == null) return;
      if (selected.value && selected.value.id === id) { loadRoute(); return; }
      const s = stations.value.find(x => x.id === id);
      if (s) goDetail(s);
    }
    function canvasMove(e, canvas) {
      if (!canvas) return;
      const rect = canvas.getBoundingClientRect();
      const hit = MapPainter.hitTest(canvas, e.clientX - rect.left, e.clientY - rect.top);
      canvas.style.cursor = hit != null ? 'pointer' : 'default';
    }
    const onHomeClick = e => canvasClick(e, homeCanvas.value);
    const onHomeMove = e => canvasMove(e, homeCanvas.value);
    const onDetailClick = e => canvasClick(e, detailCanvas.value);
    const onDetailMove = e => canvasMove(e, detailCanvas.value);

    // ---------- 窗口自适应重绘 ----------
    let resizeTimer = null;
    function onResize() {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(() => {
        if (route.value === 'demo') drawHome();
        else if (route.value === 'detail') drawDetail();
      }, 200);
    }

    // ---------- 下载页 ----------
    async function checkApk() {
      try {
        const res = await fetch('downloads/app-debug.apk', { method: 'HEAD' });
        apkState.value = res.ok ? 'yes' : 'no';
      } catch {
        apkState.value = 'no';
      }
    }

    // ---------- Hash 路由 ----------
    function onHash() {
      const h = location.hash || '#/';
      if (h.startsWith('#/detail')) {
        route.value = 'detail';
        const id = parseInt(h.split('/')[2] || '', 10);
        if (id) {
          const s = stations.value.find(x => x.id === id) ||
            (selected.value && selected.value.id === id ? selected.value : null);
          if (s) { selected.value = s; loadRoute(); }
        } else if (selected.value) {
          loadRoute();
        }
      } else if (h.startsWith('#/download')) {
        route.value = 'download';
        checkApk();
      } else if (h.startsWith('#/demo')) {
        route.value = 'demo';
        if (stations.value.length < 2) refresh();
        else nextTick(drawHome);
      } else {
        route.value = 'home';
        window.scrollTo({ top: 0 });
      }
    }

    // ---------- 电量自动下降（下穿 20%/10%/5% 时自动触发兜底） ----------
    function startBatteryDrain() {
      drainTimer = setInterval(() => {
        const before = battery.value;
        if (battery.value > 1) {
          battery.value--;
          updateBatteryText();
        }
        [20, 10, 5].forEach(t => {
          if (battery.value <= t && before > t && !firedLevels.has(t) &&
              (route.value === 'demo' || route.value === 'detail')) {
            firedLevels.add(t);
            simulateLowBattery(t);
          }
        });
      }, 6000);
    }

    // ---------- 路线文案（随引擎/模式变化） ----------
    watch([routeInfo, routeMode], () => {
      const r = routeInfo.value;
      if (!r) { routeText.value = ''; routeEngine.value = '-'; return; }
      const v = r.total_weight || 0;
      routeText.value = routeMode.value === 'distance'
        ? '最短路线：约 ' + Math.round(v) + ' 米'
        : (v >= 60
          ? '最快路线：预计步行 ' + Math.round(v / 60) + ' 分钟（' + v.toFixed(0) + ' 秒）'
          : '最快路线：预计步行 ' + v.toFixed(0) + ' 秒');
      routeEngine.value = r.engine === 'cpp'
        ? 'C++ A*'
        : (r.engine === 'python' ? 'Python Dijkstra 兜底' : '-');
    });

    // ---------- 启动 ----------
    onMounted(() => {
      applyTheme();
      window.addEventListener('hashchange', onHash);
      window.addEventListener('scroll', onScroll, { passive: true });
      window.addEventListener('resize', onResize);
      updateBatteryText();
      startBatteryDrain();
      highlightCode();
      // 后端连通性检测（同时驱动首页统计）
      API.health()
        .then(h => {
          backendOk.value = true;
          statEngine.value = h.route_engine === 'cpp' ? 'C++ 引擎' : 'Python 兜底';
          statStations.value = h.stations != null ? h.stations : '-';
          backendLabel.value = '后端已连接 · ' + statEngine.value;
        })
        .catch(() => {
          backendOk.value = false;
          backendLabel.value = '后端未连接';
        });
      onHash();
    });

    return {
      // 页面
      route, theme, toggleTheme, goHome, jumpTo, copyCode,
      // 文档交互
      searchQuery, filteredStations, openSteps, allStepsOpen, toggleStep, toggleAllSteps,
      showTop, goTop,
      // 演示
      current, stations, selected, routeMode, routeInfo, routeText, routeEngine,
      battery, batteryText, loading, notice, noticeErr, modal,
      homeCanvas, detailCanvas,
      locate, refresh, simulateLowBattery, goDetail, loadRoute, rent, rentBack,
      closeModal, goModalDetail,
      onHomeClick, onHomeMove, onDetailClick, onDetailMove,
      // 下载 / 后端状态
      apkState, backendOk, backendLabel, statStations, statEngine
    };
  }
}).mount('#app');
