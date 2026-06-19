<template>
  <el-cascader
    v-model="selected"
    :options="options"
    :props="cascaderProps"
    clearable
    filterable
    placeholder="选择省/市/区县/乡镇/村"
    style="width: 100%"
    @change="onChange"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { regionApi } from '../api'

// 选中任意级别即可（checkStrictly），回填 regionId 与 regionPath
const emit = defineEmits(['change'])

const options = ref([])
const selected = ref([])

const cascaderProps = {
  value: 'id',
  label: 'name',
  children: 'children',
  checkStrictly: true,
  emitPath: true
}

const onChange = (path) => {
  if (!path || !path.length) {
    emit('change', { regionId: null, regionPath: '' })
    return
  }
  // path 是 id 数组，沿树解析出名称数组
  const names = []
  let level = options.value
  for (const id of path) {
    const node = (level || []).find((n) => n.id === id)
    if (!node) break
    names.push(node.name)
    level = node.children
  }
  emit('change', { regionId: path[path.length - 1], regionPath: names.join('/') })
}

onMounted(async () => {
  options.value = await regionApi.tree()
})
</script>
