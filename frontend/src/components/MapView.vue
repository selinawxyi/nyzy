<template>
  <div ref="el" class="map-view" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { activeProvider, DEFAULT_CENTER, DEFAULT_ZOOM } from '../config/mapConfig'
import { wgs84ToGcj02, convertGeoJsonToGcj02 } from '../utils/coordConvert'

const props = defineProps({
  // groups: [{ key, name, color, points: [{ lng, lat, name, popup }] }]
  groups: { type: Array, default: () => [] },
  // polygonGroups: [{ key, name, color, features: [{ boundary(GeoJSON str/obj), popup }] }]
  polygonGroups: { type: Array, default: () => [] },
  height: { type: String, default: '640px' }
})

const el = ref()
let map = null
let overlayLayers = {}
let baseLayerControl = null
let overlayControl = null

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

const renderGroups = () => {
  if (!map) return

  // 清除旧业务图层
  Object.values(overlayLayers).forEach((lg) => map.removeLayer(lg))
  overlayLayers = {}
  if (overlayControl) { overlayControl.remove(); overlayControl = null }

  const overlaysForControl = {}
  let allBounds = []

  // 多边形图层（确权地块面）
  props.polygonGroups.forEach((g) => {
    const lg = L.layerGroup()
    let cnt = 0
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
      cnt++
    })
    lg.addTo(map)
    overlayLayers[`poly_${g.key}`] = lg
    overlaysForControl[`${g.name}（${cnt}）`] = lg
  })

  // 点位图层
  props.groups.forEach((g) => {
    const lg = L.layerGroup()
    g.points.forEach((p) => {
      if (p.lng == null || p.lat == null) return
      const latlng = toMapLatlng(p.lng, p.lat)
      allBounds.push(latlng)
      L.circleMarker(latlng, {
        radius: 7, color: '#fff', weight: 1.5,
        fillColor: g.color, fillOpacity: 0.9
      }).bindPopup(p.popup || p.name || '').addTo(lg)
    })
    lg.addTo(map)
    overlayLayers[g.key] = lg
    overlaysForControl[`${g.name}（${g.points.length}）`] = lg
  })

  if (Object.keys(overlaysForControl).length) {
    overlayControl = L.control.layers(null, overlaysForControl, { collapsed: false, position: 'topright' }).addTo(map)
  }

  if (allBounds.length) {
    map.fitBounds(allBounds, { padding: [40, 40], maxZoom: 14 })
  }
}

onMounted(async () => {
  await nextTick()
  map = L.map(el.value, { center: DEFAULT_CENTER, zoom: DEFAULT_ZOOM })

  // 底图 + 切换控件
  const baseLayers = buildBaseLayers()
  const defaultName = provider.defaultBase || Object.keys(baseLayers)[0]
  const firstBase = baseLayers[defaultName] || Object.values(baseLayers)[0]
  if (firstBase) firstBase.addTo(map)

  // 仅在有多底图时显示切换控件
  if (Object.keys(baseLayers).length > 1) {
    baseLayerControl = L.control.layers(baseLayers, null, { collapsed: false, position: 'topleft' }).addTo(map)
  }

  renderGroups()
})

onBeforeUnmount(() => {
  if (map) { map.remove(); map = null }
})

watch(() => [props.groups, props.polygonGroups], renderGroups, { deep: true })
</script>

<style scoped>
.map-view { width: 100%; border-radius: 6px; overflow: hidden; }
</style>
