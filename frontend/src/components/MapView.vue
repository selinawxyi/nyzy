<template>
  <div class="map-view-wrap" :style="{ height: height }">
    <div ref="el" class="map-view"></div>
    <div v-if="drawMode" class="map-hint-banner">
      <span>{{ hintText }}</span>
      <el-button size="small" text @click="$emit('exit-draw')">退出</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet-draw'
import 'leaflet-draw/dist/leaflet.draw.css'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import { activeProvider, DEFAULT_CENTER, DEFAULT_ZOOM } from '../config/mapConfig'
import { wgs84ToGcj02, gcj02ToWgs84, convertGeoJsonToGcj02 } from '../utils/coordConvert'

const props = defineProps({
  // groups: [{ key, name, color, points: [{ lng, lat, name, popup }] }]
  groups: { type: Array, default: () => [] },
  // polygonGroups: [{ key, name, color, features: [{ boundary(GeoJSON str/obj), popup }] }]
  polygonGroups: { type: Array, default: () => [] },
  height: { type: String, default: '640px' },
  // 当前应隐藏的图层 key(对应 overlayLayers 的 key, 如 'poly_parcel'/'water'); 数据变化重建图层时用来保持用户之前关掉的图层仍是关的
  hiddenLayers: { type: Array, default: () => [] },
  // null(不画图) | 'shape'(矩形/圆/多边形, 空间查询/服务范围) | 'line'(分割线) | 'edit-polygon'(编辑已有边界)
  drawMode: { type: String, default: null },
  // edit-polygon 模式下要编辑的现有边界(GeoJSON Polygon, WGS-84)
  editFeature: { type: Object, default: null }
})

const emit = defineEmits(['draw-created', 'edit-saved', 'edit-progress', 'exit-draw'])

const el = ref()
let map = null
let overlayLayers = {}
let baseLayerControl = null
let drawControl = null
let drawnItems = null

// 绘图模式提示文案: 统一做在地图组件里, 三处调用方(编辑边界/分割地块/画范围)不用各自写说明文字
const HINT_TEXT = {
  'edit-polygon': '正在编辑地块边界，拖动节点调整形状，完成后点击右上角工具栏的 ✓ 保存',
  line: '正在绘制分割线，画一条贯穿地块的线即可',
  shape: '正在绘制范围，画矩形/圆/多边形，画完自动提交'
}
const hintText = computed(() => HINT_TEXT[props.drawMode] || '')

const provider = activeProvider()
const needGcj02 = provider.needGcj02 === true

/** 将 WGS-84 点转为地图所需坐标 */
function toMapLatlng(lng, lat) {
  if (needGcj02) {
    const [gLng, gLat] = wgs84ToGcj02(Number(lng), Number(lat))
    return [gLat, gLng]
  }
  return [Number(lat), Number(lng)]
}

/** 将 WGS-84 GeoJSON 转为地图所需坐标系 */
function toMapGeoJson(boundary) {
  if (!boundary) return null
  let geo
  try { geo = typeof boundary === 'string' ? JSON.parse(boundary) : boundary } catch (e) { return null }
  return needGcj02 ? convertGeoJsonToGcj02(geo) : geo
}

/** 构建底图图层组，返回 { name: TileLayer[] } */
function buildBaseLayers() {
  const baseLayers = provider.baseLayers || {}
  const result = {}
  Object.entries(baseLayers).forEach(([name, layerDefs]) => {
    // 多个 TileLayer 叠加时包装为 LayerGroup
    const tiles = layerDefs.map((l) => L.tileLayer(l.url, l.options))
    result[name] = tiles.length === 1 ? tiles[0] : L.layerGroup(tiles)
  })
  return result
}

/** 用 divIcon 画一个小圆点(纯CSS), 替代矢量层 circleMarker, 以兼容 markercluster 的图标类操作 */
function dotIcon(color) {
  return L.divIcon({
    className: 'mv-dot-icon',
    html: `<span style="display:block;width:14px;height:14px;border-radius:50%;background:${color};border:1.5px solid #fff;box-shadow:0 0 2px rgba(0,0,0,.5)"></span>`,
    iconSize: [14, 14],
    iconAnchor: [7, 7]
  })
}

// 业务图层开关(供外部图例点击调用), 取代原来 Leaflet 右上角原生勾选框(与外部图例信息重复)
function toggleLayer(key, visible) {
  const lg = overlayLayers[key]
  if (!lg || !map) return
  if (visible && !map.hasLayer(lg)) lg.addTo(map)
  if (!visible && map.hasLayer(lg)) map.removeLayer(lg)
}

const renderGroups = () => {
  if (!map) return

  // 清除旧业务图层
  Object.values(overlayLayers).forEach((lg) => map.removeLayer(lg))
  overlayLayers = {}

  let allBounds = []

  // 多边形图层（确权地块面）
  props.polygonGroups.forEach((g) => {
    const lg = L.layerGroup()
    g.features.forEach((f) => {
      const geo = toMapGeoJson(f.boundary)
      if (!geo) return
      const c = f.color || g.color
      const layer = L.geoJSON(geo, {
        style: { color: c, weight: 2, fillColor: c, fillOpacity: 0.3 }
      })
      if (f.popup) layer.bindPopup(f.popup)
      layer.addTo(lg)
      const b = layer.getBounds()
      if (b.isValid()) { allBounds.push(b.getNorthEast(), b.getSouthWest()) }
    })
    lg.addTo(map)
    overlayLayers[`poly_${g.key}`] = lg
    if (props.hiddenLayers.includes(`poly_${g.key}`)) map.removeLayer(lg)
  })

  // 点位图层: 用聚合分组(markercluster), 点位密集时自动合并成数字气泡, 放大后自动展开, 避免重叠看不清
  // 注意: markercluster 在重新聚合/展开时会调用图标类(L.Marker)专属的内部方法,
  // 不能直接放 L.circleMarker(矢量图层, 没有这些方法), 否则缩放时会报错且点位渲染不全;
  // 这里用 L.marker + 自定义 divIcon(纯CSS画的小圆点) 来还原一样的视觉效果, 同时保证兼容
  props.groups.forEach((g) => {
    const lg = L.markerClusterGroup({ maxClusterRadius: 50, spiderfyOnMaxZoom: true, showCoverageOnHover: false })
    g.points.forEach((p) => {
      if (p.lng == null || p.lat == null) return
      const latlng = toMapLatlng(p.lng, p.lat)
      allBounds.push(latlng)
      const marker = L.marker(latlng, { icon: dotIcon(g.color) }).bindPopup(p.popup || p.name || '')
      marker.addTo(lg)
    })
    lg.addTo(map)
    overlayLayers[g.key] = lg
    if (props.hiddenLayers.includes(g.key)) map.removeLayer(lg)
  })

  if (allBounds.length) {
    map.fitBounds(allBounds, { padding: [40, 40], maxZoom: 14 })
  }
}

/** latlngs(Leaflet LatLng数组) -> WGS-84 [lng,lat] 点数组 */
function latlngsToWgs84Points(latlngs) {
  return latlngs.map((ll) => {
    if (!needGcj02) return [ll.lng, ll.lat]
    const [lng, lat] = gcj02ToWgs84(ll.lng, ll.lat)
    return [lng, lat]
  })
}

function setupDraw() {
  teardownDraw()
  if (!map || !props.drawMode) return
  drawnItems = new L.FeatureGroup()
  map.addLayer(drawnItems)

  if (props.drawMode === 'edit-polygon' && props.editFeature) {
    const geo = toMapGeoJson(props.editFeature)
    if (geo) {
      L.geoJSON(geo, { style: { color: '#e6a23c', weight: 2 } }).eachLayer((l) => {
        drawnItems.addLayer(l)
        // leaflet-draw 的 L.Edit.Poly 在拖动节点时会在图层上触发 'edit' 事件, 用于实时反馈坐标/面积
        l.on('edit', () => emit('edit-progress', latlngsToWgs84Points(l.getLatLngs()[0])))
      })
    }
    drawControl = new L.Control.Draw({
      draw: false,
      edit: { featureGroup: drawnItems, remove: false }
    })
    map.addControl(drawControl)
    map.on(L.Draw.Event.EDITED, (e) => {
      e.layers.eachLayer((layer) => {
        const latlngs = layer.getLatLngs()[0]
        const points = latlngsToWgs84Points(latlngs)
        emit('edit-saved', toClosedPolygonGeoJson(points))
      })
    })
    return
  }

  if (props.drawMode === 'shape') {
    drawControl = new L.Control.Draw({
      draw: { rectangle: {}, circle: {}, polygon: {}, marker: false, polyline: false, circlemarker: false },
      edit: false
    })
  } else if (props.drawMode === 'line') {
    drawControl = new L.Control.Draw({
      draw: { polyline: {}, rectangle: false, circle: false, polygon: false, marker: false, circlemarker: false },
      edit: false
    })
  } else {
    return
  }
  map.addControl(drawControl)
  map.on(L.Draw.Event.CREATED, (e) => {
    const layer = e.layer
    drawnItems.addLayer(layer)
    if (layer instanceof L.Circle) {
      const center = layer.getLatLng()
      const [lng, lat] = needGcj02 ? gcj02ToWgs84(center.lng, center.lat) : [center.lng, center.lat]
      emit('draw-created', { type: 'circle', center: [lng, lat], radius: layer.getRadius() })
    } else if (layer instanceof L.Polyline && !(layer instanceof L.Polygon)) {
      const points = latlngsToWgs84Points(layer.getLatLngs())
      emit('draw-created', { type: 'line', points })
    } else {
      const latlngs = layer.getLatLngs()[0]
      const points = latlngsToWgs84Points(latlngs)
      emit('draw-created', { type: 'polygon', points })
    }
  })
}

function toClosedPolygonGeoJson(points) {
  const coords = points.map((p) => [p[0], p[1]])
  const first = coords[0], last = coords[coords.length - 1]
  if (first[0] !== last[0] || first[1] !== last[1]) coords.push(first)
  return { type: 'Polygon', coordinates: [coords] }
}

function teardownDraw() {
  if (drawControl && map) { map.removeControl(drawControl); drawControl = null }
  if (drawnItems && map) { map.removeLayer(drawnItems); drawnItems = null }
  if (map) { map.off(L.Draw.Event.CREATED); map.off(L.Draw.Event.EDITED) }
}

/** 清空当前已画的图形(画错重画) */
function clearDrawn() {
  if (drawnItems) drawnItems.clearLayers()
}

let highlightLayer = null

/** 定位到搜索命中的目标(面用边界缩放, 点用坐标飞行), 并加一个临时高亮圈/边框, 方便用户一眼找到 */
function focusFeature(target) {
  if (!map || !target) return
  if (highlightLayer) { map.removeLayer(highlightLayer); highlightLayer = null }
  if (target.boundary) {
    const geo = toMapGeoJson(target.boundary)
    if (geo) {
      const layer = L.geoJSON(geo)
      const bounds = layer.getBounds()
      if (bounds.isValid()) {
        map.flyToBounds(bounds, { padding: [60, 60], maxZoom: 16, duration: 0.8 })
        highlightLayer = L.geoJSON(geo, { style: { color: '#ff4d4f', weight: 4, fillOpacity: 0.12, dashArray: '6,4' } }).addTo(map)
        return
      }
    }
  }
  if (target.lng != null && target.lat != null) {
    const latlng = toMapLatlng(target.lng, target.lat)
    map.flyTo(latlng, 16, { duration: 0.8 })
    highlightLayer = L.circleMarker(latlng, { radius: 16, color: '#ff4d4f', weight: 3, fillOpacity: 0.08 }).addTo(map)
  }
}

defineExpose({ clearDrawn, focusFeature, toggleLayer })

onMounted(async () => {
  await nextTick()
  map = L.map(el.value, { center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM })

  // 底图 + 切换控件
  const baseLayers = buildBaseLayers()
  const defaultName = provider.defaultBase || Object.keys(baseLayers)[0]
  const firstBase = baseLayers[defaultName] || Object.values(baseLayers)[0]
  if (firstBase) firstBase.addTo(map)

  // 仅在有多底图时显示切换控件; 放左下角, 避免和左上角缩放按钮贴在一起
  if (Object.keys(baseLayers).length > 1) {
    baseLayerControl = L.control.layers(baseLayers, null, { collapsed: false, position: 'bottomleft' }).addTo(map)
  }

  renderGroups()
  setupDraw()
})

onBeforeUnmount(() => {
  teardownDraw()
  if (map) { map.remove(); map = null }
})

watch(() => [props.groups, props.polygonGroups], renderGroups, { deep: true })
watch(() => [props.drawMode, props.editFeature], setupDraw)
</script>

<style scoped>
.map-view-wrap { position: relative; width: 100%; }
.map-view { width: 100%; height: 100%; border-radius: 6px; overflow: hidden; }
.map-hint-banner {
  position: absolute; top: 10px; left: 50%; transform: translateX(-50%); z-index: 1000;
  display: flex; align-items: center; gap: 8px;
  background: #fff; border-radius: 20px; padding: 6px 8px 6px 16px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.18);
  font-size: 13px; color: #1f2d3d; max-width: 90%;
}
</style>
