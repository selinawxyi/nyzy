/**
 * WGS-84 ↔ GCJ-02 坐标转换
 * 高德/腾讯等国内地图使用 GCJ-02（"火星坐标"），数据库存储 WGS-84（GPS原始坐标）。
 * 在高德底图上显示时需将 WGS-84 转为 GCJ-02，否则会有 ~100-500m 偏移。
 */

const A = 6378245.0        // 克拉索夫斯基椭球长半轴
const EE = 0.00669342162296594323  // 第一偏心率的平方

function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function transformLat(x, y) {
  let r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  r += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
  r += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
  return r
}

function transformLng(x, y) {
  let r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
  r += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
  r += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
  return r
}

/**
 * WGS-84 → GCJ-02
 * @param {number} lng 经度
 * @param {number} lat 纬度
 * @returns {[number, number]} [gcjLng, gcjLat]
 */
export function wgs84ToGcj02(lng, lat) {
  if (outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * Math.PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * Math.PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * Math.PI)
  return [lng + dLng, lat + dLat]
}

/**
 * GCJ-02 → WGS-84（迭代近似，精度约 0.5m）
 */
export function gcj02ToWgs84(lng, lat) {
  if (outOfChina(lng, lat)) return [lng, lat]
  let [wLng, wLat] = [lng, lat]
  for (let i = 0; i < 10; i++) {
    const [gLng, gLat] = wgs84ToGcj02(wLng, wLat)
    wLng -= gLng - lng
    wLat -= gLat - lat
  }
  return [wLng, wLat]
}

/**
 * 深度转换 GeoJSON 坐标（WGS-84 → GCJ-02）
 * 支持 Point / LineString / Polygon / MultiPolygon / Feature / FeatureCollection
 */
export function convertGeoJsonToGcj02(geojson) {
  if (!geojson) return geojson
  const obj = typeof geojson === 'string' ? JSON.parse(geojson) : geojson
  return deepConvert(obj, wgs84ToGcj02)
}

/** 深度转换 GeoJSON 坐标（GCJ-02 → WGS-84）, 用于把地图上画的图形转回数据库坐标系 */
export function convertGeoJsonToWgs84(geojson) {
  if (!geojson) return geojson
  const obj = typeof geojson === 'string' ? JSON.parse(geojson) : geojson
  return deepConvert(obj, gcj02ToWgs84)
}

function deepConvert(obj, converter) {
  if (!obj) return obj
  switch (obj.type) {
    case 'FeatureCollection':
      return { ...obj, features: obj.features.map((f) => deepConvert(f, converter)) }
    case 'Feature':
      return { ...obj, geometry: deepConvert(obj.geometry, converter) }
    case 'Point':
      return { ...obj, coordinates: convertCoord(obj.coordinates, converter) }
    case 'MultiPoint':
    case 'LineString':
      return { ...obj, coordinates: obj.coordinates.map((c) => convertCoord(c, converter)) }
    case 'MultiLineString':
    case 'Polygon':
      return { ...obj, coordinates: obj.coordinates.map((ring) => ring.map((c) => convertCoord(c, converter))) }
    case 'MultiPolygon':
      return {
        ...obj,
        coordinates: obj.coordinates.map((poly) =>
          poly.map((ring) => ring.map((c) => convertCoord(c, converter)))
        )
      }
    default:
      return obj
  }
}

function convertCoord([lng, lat], converter) {
  const [cLng, cLat] = converter(lng, lat)
  return [cLng, cLat]
}
