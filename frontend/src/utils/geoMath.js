/**
 * 轻量几何量算(纯前端预览用, 与后端 GeoUtil 的等距投影近似算法保持一致)。
 * 仅用于编辑过程中的实时数值反馈, 最终面积以后端提交后的计算结果为准。
 */

const DEG_TO_M_LAT = 111320.0

/** 多边形面积(亩), points: [[lng,lat], ...] */
export function polygonAreaMu(points) {
  if (!points || points.length < 3) return 0
  const lat = points.reduce((s, p) => s + p[1], 0) / points.length
  const mPerDegLng = DEG_TO_M_LAT * Math.cos((lat * Math.PI) / 180)
  let sum = 0
  for (let i = 0; i < points.length; i++) {
    const [x1, y1] = points[i]
    const [x2, y2] = points[(i + 1) % points.length]
    sum += x1 * y2 - x2 * y1
  }
  const areaDeg2 = Math.abs(sum) / 2
  const areaM2 = areaDeg2 * DEG_TO_M_LAT * mPerDegLng
  return areaM2 / 666.67
}

/** 两点间球面距离(米) */
export function haversineMeters(lng1, lat1, lng2, lat2) {
  const R = 6371000
  const dLat = ((lat2 - lat1) * Math.PI) / 180
  const dLng = ((lng2 - lng1) * Math.PI) / 180
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

/** 多边形周长(米) */
export function polygonPerimeterMeters(points) {
  if (!points || points.length < 2) return 0
  let total = 0
  for (let i = 0; i < points.length; i++) {
    const [x1, y1] = points[i]
    const [x2, y2] = points[(i + 1) % points.length]
    total += haversineMeters(x1, y1, x2, y2)
  }
  return total
}
