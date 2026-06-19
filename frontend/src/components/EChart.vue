<template>
  <div ref="el" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '320px' }
})

const el = ref()
let chart = null

const render = () => {
  if (!chart) return
  chart.setOption(props.option, true)
}
const resize = () => chart && chart.resize()

onMounted(async () => {
  await nextTick()
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart && chart.dispose()
})
watch(() => props.option, render, { deep: true })
</script>
