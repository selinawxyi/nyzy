<template>
  <el-cascader
    v-model="selected"
    :options="options"
    :props="cascaderProps"
    clearable
    filterable
    placeholder="选择乡镇/村"
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

// 项目固定服务延吉市, 省/自治州/市三级永远是同一个值, 没必要让用户每次点3次"下一步"才能选到乡镇,
// 这里把级联框直接定位到"延吉市"节点的子级(乡镇)开始选, 选中后再把固定前缀拼回 regionPath
const FIXED_ROOT_NAME = '延吉市'
let fixedPrefixNames = []

const findNodeWithPath = (nodes, targetName, trail = []) => {
  for (const n of nodes || []) {
    const nextTrail = [...trail, n.name]
    if (n.name === targetName) return { node: n, path: nextTrail }
    const found = findNodeWithPath(n.children, targetName, nextTrail)
    if (found) return found
  }
  return null
}

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
  emit('change', { regionId: path[path.length - 1], regionPath: [...fixedPrefixNames, ...names].join('/') })
}

onMounted(async () => {
  const tree = await regionApi.tree()
  const found = findNodeWithPath(tree, FIXED_ROOT_NAME)
  if (found) {
    options.value = found.node.children || []
    fixedPrefixNames = found.path
  } else {
    // 没找到固定根节点(数据异常)时退回完整树, 不影响可用性
    options.value = tree
    fixedPrefixNames = []
  }
})
</script>
