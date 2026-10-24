/* api.js —— 后端接口封装（与 FastAPI 契约一致） */
class ApiClient {
  constructor(base = '') { this.base = base; }

  async _fetch(path, options = {}) {
    let res;
    try {
      res = await fetch(this.base + path, options);
    } catch {
      throw new Error('无法连接后端，请先启动 uvicorn');
    }
    if (!res.ok) {
      let msg = 'HTTP ' + res.status;
      try { const j = await res.json(); if (j.detail) msg = j.detail; } catch (_) {}
      throw new Error(msg);
    }
    return res.json();
  }

  get(path, params = {}) {
    const q = new URLSearchParams(params).toString();
    return this._fetch(path + (q ? '?' + q : ''));
  }

  post(path, body) {
    return this._fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
  }

  // ---- 业务接口 ----
  health()            { return this.get('/health'); }
  stations(lat, lng, radius = 5000) {
    return this.get('/api/stations', { lat, lng, radius });
  }
  route(fromLat, fromLng, stationId, mode = 'time') {
    return this.get('/api/route', { from_lat: fromLat, from_lng: fromLng, station_id: stationId, mode });
  }
  emergency(lat, lng) { return this.get('/api/emergency', { lat, lng }); }
  rent(stationId)     { return this.post('/api/rent', { station_id: stationId }); }
  returnBank(stationId) { return this.post('/api/return', { station_id: stationId }); }
}

const API = new ApiClient();