// ============================================================
// 地图底图配置
//
// 当前：高德地图（国内可访问，无需 API Key）
//   坐标系：GCJ-02（"火星坐标"）
//   MapView.vue 会自动将数据库中的 WGS-84 坐标转换后渲染。
//
// 切换天地图：
//   1) https://console.tianditu.gov.cn/ 申请密钥
//   2) 将 tianditu.key 填入真实 key
//   3) 将 ACTIVE_PROVIDER 改为 'tianditu'（WGS-84，无需坐标转换）
// ============================================================

export const ACTIVE_PROVIDER = 'gaode'   // 'gaode' | 'gaode_satellite' | 'tianditu' | 'osm'

// 默认视图中心（延吉）与缩放级别
export const DEFAULT_CENTER = [42.92, 129.55]
export const DEFAULT_ZOOM = 12

export const providers = {

  // —— 高德矢量路网图（GCJ-02，国内直接访问，无需 Key）——
  gaode: {
    name: '高德地图',
    needGcj02: true,   // 告知 MapView 需要坐标转换
    baseLayers: {
      '矢量路网': [
        {
          url: 'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
          options: { maxZoom: 18, subdomains: ['1', '2', '3', '4'], attribution: '© 高德地图 AutoNavi' }
        }
      ],
      '卫星影像': [
        {
          url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
          options: { maxZoom: 18, subdomains: ['1', '2', '3', '4'], attribution: '© 高德地图 AutoNavi' }
        },
        // 卫星图上叠加路名注记
        {
          url: 'https://webst0{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
          options: { maxZoom: 18, subdomains: ['1', '2', '3', '4'], opacity: 1 }
        }
      ]
    },
    defaultBase: '矢量路网'
  },

  // —— 天地图（WGS-84，需 Key）——
  tianditu: {
    name: '天地图',
    needGcj02: false,
    key: 'YOUR_TIANDITU_KEY',
    get baseLayers() {
      const k = this.key
      const sub = ['0', '1', '2', '3', '4', '5', '6', '7']
      return {
        '矢量底图': [
          {
            url: `https://t{s}.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${k}`,
            options: { maxZoom: 18, subdomains: sub, attribution: '© 天地图' }
          },
          {
            url: `https://t{s}.tianditu.gov.cn/cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${k}`,
            options: { maxZoom: 18, subdomains: sub }
          }
        ]
      }
    },
    defaultBase: '矢量底图'
  },

  // —— OpenStreetMap（备用，国内可能较慢）——
  osm: {
    name: 'OpenStreetMap',
    needGcj02: false,
    baseLayers: {
      'OpenStreetMap': [
        {
          url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
          options: { maxZoom: 19, attribution: '© OpenStreetMap contributors' }
        }
      ]
    },
    defaultBase: 'OpenStreetMap'
  }
}

export function activeProvider() {
  return providers[ACTIVE_PROVIDER] || providers.gaode
}
