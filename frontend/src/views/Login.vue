<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-brand">
        <div class="logo">农</div>
        <div class="brand-text">
          <h1>农业资源数字化管理平台</h1>
          <p>Agricultural Resource Digital Management Platform</p>
        </div>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
      <div class="login-tip">演示账号：admin / admin123（管理员）· operator / 123456</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = ref({ username: 'admin', password: 'admin123' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const submit = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await auth.login(form.value)
      ElMessage.success('登录成功')
      router.push('/')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f6f43 0%, #2e9e5b 100%);
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}
.login-brand {
  display: flex;
  align-items: center;
  margin-bottom: 28px;
}
.logo {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: #2e9e5b;
  color: #fff;
  font-size: 26px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}
.brand-text h1 {
  font-size: 18px;
  color: #1f2d3d;
}
.brand-text p {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.login-btn {
  width: 100%;
  margin-top: 4px;
}
.login-tip {
  margin-top: 18px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
