# 农业资源数字化管理平台

数字农业与乡村管理平台 — 农业资源数字化管理应用组件，构建农业"一张图"管理体系。

详细需求见 [docs/需求分析与功能清单.md](docs/需求分析与功能清单.md)。

## 技术栈

| 层 | 技术 |
|---|---|
| 数据库 | MySQL 5.7（utf8mb4） |
| 后端 | Spring Boot 2.7 + JDK 1.8 + MyBatis-Plus + JWT + EasyExcel |
| 前端 | Vite + Vue 3 + Element Plus + Pinia + ECharts + Leaflet |
| 部署 | Docker Compose（mysql / backend / frontend-nginx） |

## 一键启动

```bash
docker compose up -d --build
```

启动后：

- 前端： http://localhost:8000
- 后端 API： http://localhost:8080/api
- MySQL： localhost:13306（库 `nyzy`，root / nyzy123456）

首次启动时 MySQL 容器自动执行 `db/init/*.sql`（建表 + 种子数据），后端启动时自动 BCrypt 加密种子用户密码。
附件文件存于 `uploads-data` 数据卷。

> Apple Silicon 注：`mysql:5.7` 已指定 `platform: linux/amd64`（模拟运行）。主机 3306 若被占用，已将 MySQL 映射到 13306。

## 演示账号

| 账号 | 密码 | 角色 | 说明 |
|---|---|---|---|
| admin | admin123 | 系统管理员 | 删除/审计/用户管理/彻底删除等高权限 |
| operator | 123456 | 运营人员 | 业务录入与处理 |
| gridman | 123456 | 网格员 | 巡查上报 |

## 功能模块（菜单结构）

| 导航 | 子页面 | 能力 |
|---|---|---|
| **一张图** | 农业资源一张图 | Leaflet 地图：确权地块/撂荒地块（按治理状态分色）绘面、水利/配套设施点位、图层开关。底图可一键切换天地图（见下） |
| **土地资源** | 确权地块 | 列表/条件查询、增删改（关键字段变更留原因）、**版本历史回溯 + 版本对比**、**地块标注**（文字/标签/颜色/可见范围）、**附件**、关联删除防护、导出 |
| **撂荒** | 撂荒管理 | 三来源入库、原因填报、**治理任务闭环**（下发→进度填报→验收→已治理+回写种植）、状态机分色、现场附件、导出 + 治理台账 |
| **耕地利用** | 种植记录 / 耕地质量 | CRUD/查询、**种植历史**（轮作链+产量趋势）、标记无效、地力等级评价、Excel 导入/导出 |
| **农业资源** | 水利设施 / 配套设施 / 设施分类 | 标注审核流、CRUD、两级分类（删除防护）、Excel 导入/导出 |
| **分析** | 种植动态分析 | ECharts：种植结构/年度趋势/区划分析/耕地利用类型/**E1.4 优势产区识别**/**E1.6 时间轴动态演变** |
| **系统管理** | 待审核中心 / 用户管理 / 审计日志 / 回收站 | 跨模块待审核统一审批、用户 CRUD、操作留痕查询、软删除 90 天回收/恢复/彻底删除 |

## 通用能力

- **数据权限 / 可见范围**：基于 `sys_user.region_id`，管理员看全部，其他角色仅见所属区划及下级（区划树下钻），套入全部列表查询
- **站内信通知**：上报待审核→通知管理员、审核结果/治理任务/验收→通知提交人，铃铛真实未读数 + 30s 轮询 + 点击跳转
- **附件上传**：`/api/attachment`，本地存储 + 业务关联，图片/PDF/Office，≤20MB；接入撂荒/地块/水利/配套/种植/质量
- **Excel 导入/导出**：EasyExcel，导出按当前筛选；导入（地块/配套/水利/种植/质量 + 确权批量更新）带模板下载 + 逐行校验报告
- **批量操作**：质量评价/水利设施多选批量修改
- **行政区划五级联动**：`RegionCascader` 省→市→区县→乡镇→村
- **统一回收站**：6 张软删除表聚合，`@Scheduled` 每日 03:00 物理清理超 90 天记录
- **审计日志**：增删改/状态变更/进度/方案/反馈全平台留痕
- **JWT 鉴权** + 角色权限（删除/审计/用户管理限管理员）+ 数据权限

## 地图底图切换（OpenStreetMap → 天地图）

当前临时使用 OpenStreetMap（非 Google、免 key）。切换天地图：

1. 在 https://console.tianditu.gov.cn/ 申请密钥
2. 编辑 [frontend/src/config/mapConfig.js](frontend/src/config/mapConfig.js)：把 `tianditu.key` 填上真实 key，`ACTIVE_PROVIDER` 改为 `'tianditu'`
3. 重新构建前端即可，业务代码与地图组件无需改动（坐标同为 WGS84 经纬度）

配置里另含高德（`gaode`）备选，天地图 `vec`+`cva` 双层 WMTS 模板已写好。

## 目录结构

```
.
├── docker-compose.yml
├── db/init/                     # 建表 + 种子数据 SQL（01~07，按序自动执行）
├── docs/                        # 需求分析与功能清单
├── backend/  src/main/java/com/nyzy/
│   ├── auth/                    # JWT 登录鉴权 + 当前用户上下文
│   ├── common/                  # Result / 异常 / 分页 / ExcelUtil
│   ├── config/                  # MyBatis-Plus / CORS / 拦截器 / 数据初始化
│   ├── system/                  # 用户 / 角色 / 行政区划 / 审计日志
│   ├── land/                    # 确权地块 / 版本历史 / 标注
│   ├── abandon/                 # 撂荒地块 / 原因 / 治理任务
│   ├── cultivation/             # 种植记录 / 耕地质量
│   ├── resource/                # 水利设施 / 配套设施 / 分类
│   ├── analysis/                # 种植动态分析
│   ├── map/                     # 一张图点位聚合
│   ├── audit/                   # 待审核统一中心
│   ├── recycle/                 # 回收站
│   ├── attachment/              # 附件上传
│   ├── export/ · imports/       # Excel 导出 / 导入
└── frontend/ src/
    ├── api/                     # axios 封装 + 各模块接口
    ├── components/              # MapView / EChart / AttachmentPanel / RegionCascader / ImportButton
    ├── config/mapConfig.js      # 地图底图配置（OSM / 天地图 / 高德）
    ├── layout/ · router/ · store/
    ├── views/                   # 各模块页面
    └── constants/dict.js        # 字典（状态/来源/原因/作物等）
```

## 后端测试

```bash
# 本机无 JDK，可用 Maven 容器跑（38 个单元测试）
docker run --rm -v "$PWD/backend":/app -v nyzy-m2:/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-8 mvn -B test
```

覆盖：撂荒状态机/删除权限、种植面积与产量约束、质量指标校验、资源审核与分类删除防护、
确权关联删除、回收站白名单防注入、用户管理守卫、审核中心委托路由、优势产区评价等。

## 尚未实现（GIS 重型，计划接入天地图后做）

- A1.1 确权数据 **Shapefile** 导入（GeoJSON/Excel 已支持）
- A1.4 地块几何编辑（节点/分割/合并，需 leaflet-draw）
- 设施空间范围绘制查询（地图框选）
- E2 产地分布图层 / 产地详情 / 产地供应链
