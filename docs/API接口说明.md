# 农业资源数字化管理平台 — API接口说明

文档版本：1.0.0
文档描述：后端 RESTful API 接口说明

## 1. 通用约定

- **Base Path**：所有接口统一以 `/api` 为前缀。
- **统一响应结构 `Result<T>`**：除文件下载/导出类接口外，所有接口返回 JSON，结构如下：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 描述 |
|---|---|---|
| code | int | 0表示成功，非0表示失败，具体错误code可见各接口说明，未特别说明的默认 400(参数错误)/401(未登录或登录过期)/403(无权限)/500(服务器异常) |
| message | String | 提示信息，成功时固定为"success"，失败时为具体错误描述 |
| data | Object | 业务数据，失败时通常为null |

- **分页响应结构 `PageResult<T>`**：分页查询接口的 `data` 字段为以下结构：

```json
{
  "total": 100,
  "list": []
}
```

| 字段 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array | 当前页数据列表 |

- **认证方式**：除登录接口 `/api/auth/login` 外，其余接口均需在请求头携带 JWT：`Authorization: Bearer <token>`。Token 由 `/api/auth/login` 登录成功后返回，未携带或校验失败时统一返回 `{"code":401,"message":"未登录或登录已过期"}`，HTTP状态码401。Token 中携带用户ID、用户名、角色(role)、所属区域ID(regionId)，部分接口按角色/区域做数据权限过滤（如 `audit-log` 仅管理员可访问）。
- **错误码约定**：业务异常统一通过 `ApiException(code, message)` 抛出，由全局异常处理器 `GlobalExceptionHandler` 转换为 `Result.fail(code, message)`；未指定code时默认400；参数校验失败(如 `@RequestBody` 校验不通过)统一返回400及字段校验提示；未捕获的其他异常返回500及"服务器异常: ..."提示。各接口小节不再重复列出错误响应，非0即失败，具体原因见 message 字段。
- **请求/响应 Content-Type**：JSON接口请求/响应均为 `application/json`；文件上传接口（导入、附件上传）为 `multipart/form-data`；文件下载/导出接口直接返回二进制流，不再包装为 `Result`，详见对应小节说明。
- **逻辑删除与回收站**：大多数业务实体（地块、种植记录、设施等）采用逻辑删除（字段 `deleted`/`is_deleted`），删除时可附带 `reason` 删除原因，删除后的数据进入"回收站"模块（见第8节），可还原或彻底删除。
- **乐观锁**：部分实体（LandParcel、PlantingRecord、LandQuality、SupportFacility、WaterFacility）带 `version` 字段做乐观锁控制，更新时由 MyBatis-Plus 自动处理，前端无需关心。

## 2. 用户认证

### 2.1 登录
接口描述：用户名密码登录，成功后返回 JWT token 及用户基本信息。
| 接口路径 | /api/auth/login |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| username | String | body | 是 | 用户名 |
| password | String | body | 是 | 密码 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| token | String | JWT 访问令牌 |
| user | Object | 用户信息：id、username、nickname、role、phone |

响应示例
成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "id": 1,
      "username": "admin",
      "nickname": "管理员",
      "role": "admin",
      "phone": "13800000000"
    }
  }
}
```

### 2.2 获取当前登录用户信息
接口描述：根据 token 中的用户ID查询当前登录用户详情。
| 接口路径 | /api/auth/me |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| (无，从Token中取当前用户ID) | - | - | - | - |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 用户ID |
| username | String | 用户名 |
| nickname | String | 昵称 |
| role | String | 角色 |
| phone | String | 手机号 |

响应示例
成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "role": "admin",
    "phone": "13800000000"
  }
}
```

## 3. 地块管理

模块说明：管理确权地块基础信息，含分页查询、详情、增删改、批量修改、几何边界编辑、拆分合并、版本历史与地块标注等能力。涉及实体 `LandParcel`（确权地块）、`LandParcelHistory`（版本历史）、`LandAnnotation`（地块标注）。

### 3.1 分页查询地块列表
接口描述：按条件分页查询确权地块。
| 接口路径 | /api/parcel/parcels |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/名称/承包方姓名模糊匹配) |
| landUse | String | query | 否 | 地块用途 |
| areaMin | BigDecimal | query | 否 | 面积范围下限 |
| areaMax | BigDecimal | query | 否 | 面积范围上限 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;LandParcel&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;LandParcel&gt; | 地块列表，字段见下 |

LandParcel 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parcelCode | String | 地块编码 |
| name | String | 地块名称 |
| regionId | Long | 所属区域ID |
| regionPath | String | 坐落位置(区划路径文本) |
| contractorName | String | 承包方姓名 |
| contractorCode | String | 承包方编码 |
| area | BigDecimal | 确权面积(亩) |
| landUse | String | 地块用途 |
| boundEast | String | 东至 |
| boundSouth | String | 南至 |
| boundWest | String | 西至 |
| boundNorth | String | 北至 |
| centerLng | BigDecimal | 中心点经度 |
| centerLat | BigDecimal | 中心点纬度 |
| boundary | String | 地块边界(GeoJSON) |
| mergeStatus | String | 合并状态：NORMAL正常/MERGED已合并入其他地块 |
| mergedIntoCode | String | 合并去向的新地块编码 |
| contractStart | LocalDate | 承包开始日期 |
| contractEnd | LocalDate | 承包结束日期 |
| remark | String | 备注 |
| deleted | Integer | 逻辑删除标志 |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| version | Integer | 乐观锁版本号 |

响应示例
成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 1,
    "list": [
      {
        "id": 1,
        "parcelCode": "DK0001",
        "name": "东岗一号地",
        "regionId": 5,
        "regionPath": "XX省/XX市/XX区/XX镇/XX村",
        "contractorName": "张三",
        "contractorCode": "C0001",
        "area": 12.5,
        "landUse": "耕地",
        "boundary": "{...GeoJSON...}",
        "mergeStatus": "NORMAL",
        "contractStart": "2020-01-01",
        "contractEnd": "2050-01-01",
        "createdAt": "2024-01-01T10:00:00",
        "version": 1
      }
    ]
  }
}
```

### 3.2 地块详情
接口描述：根据ID查询单个地块详情。
| 接口路径 | /api/parcel/parcels/{id} |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID |

响应参数(data字段内)：同 3.1 中 LandParcel 字段。

### 3.3 新增地块
接口描述：创建一个新的确权地块。
| 接口路径 | /api/parcel/parcels |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为LandParcel对象)
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| parcelCode | String | body | 否 | 地块编码 |
| name | String | body | 否 | 地块名称 |
| regionId | Long | body | 否 | 所属区域ID |
| regionPath | String | body | 否 | 坐落位置 |
| contractorName | String | body | 否 | 承包方姓名 |
| contractorCode | String | body | 否 | 承包方编码 |
| area | BigDecimal | body | 否 | 确权面积(亩) |
| landUse | String | body | 否 | 地块用途 |
| boundEast/boundSouth/boundWest/boundNorth | String | body | 否 | 四至 |
| centerLng/centerLat | BigDecimal | body | 否 | 中心点经纬度 |
| boundary | String | body | 否 | 地块边界(GeoJSON) |
| contractStart/contractEnd | LocalDate | body | 否 | 承包起止日期 |
| remark | String | body | 否 | 备注 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建地块的ID |

### 3.4 修改地块
接口描述：更新地块信息，可附带变更原因记录版本历史。
| 接口路径 | /api/parcel/parcels/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID |
| reason | String | query | 否 | 变更原因 |
| (body) | LandParcel | body | 是 | 同3.3，更新内容(id以路径参数为准) |

响应参数：无业务数据，data为null。

### 3.5 删除地块
接口描述：逻辑删除地块，可附带删除原因。
| 接口路径 | /api/parcel/parcels/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID |
| reason | String | query | 否 | 删除原因 |

响应参数：无业务数据，data为null。

### 3.6 批量修改地块
接口描述：按ID列表批量更新地块字段。
| 接口路径 | /api/parcel/parcels/batch |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待更新地块ID列表 |
| updates | LandParcel | body | 是 | 待应用的更新内容(同3.3字段) |
| reason | String | body | 否 | 变更原因 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

### 3.7 修改地块几何边界
接口描述：更新地块边界GeoJSON(A1.4)。
| 接口路径 | /api/parcel/parcels/{id}/geometry |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID |
| boundary | String | body | 是 | 新的边界GeoJSON字符串 |
| reason | String | body | 否 | 变更原因 |

响应参数：无业务数据，data为null。

### 3.8 地块拆分
接口描述：按分割线将地块一分为二，生成一个新地块。
| 接口路径 | /api/parcel/parcels/{id}/split |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 待拆分地块ID |
| line | String | body | 是 | 分割线(GeoJSON) |
| newCode | String | body | 是 | 新地块编码 |
| reason | String | body | 否 | 变更原因 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新生成地块的ID |

### 3.9 地块合并
接口描述：将多个地块合并为一个新地块。
| 接口路径 | /api/parcel/parcels/merge |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待合并的地块ID列表 |
| newCode | String | body | 是 | 合并后新地块编码 |
| reason | String | body | 否 | 变更原因 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 合并后新地块的ID |

### 3.10 查询地块版本历史
接口描述：查询某地块的全部历史版本记录。
| 接口路径 | /api/parcel/parcels/{id}/history |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID |

响应参数(data字段内，List&lt;LandParcelHistory&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parcelId | Long | 所属地块ID |
| parcelCode | String | 地块编码 |
| version | Integer | 版本号 |
| changeType | String | 变更类型：CREATE/UPDATE/DELETE |
| changeFields | String | 变更字段说明 |
| snapshot | String | 该版本数据快照(JSON) |
| operator | String | 操作人 |
| reason | String | 变更原因 |
| createdAt | LocalDateTime | 创建时间 |

### 3.11 历史版本对比
接口描述：对比两个历史版本之间的字段差异。
| 接口路径 | /api/parcel/history/compare |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| v1 | Long | query | 是 | 版本记录ID 1 |
| v2 | Long | query | 是 | 版本记录ID 2 |

响应参数(data字段内)：动态生成的差异对象（Map结构，无固定Schema，由后端按字段比对组装）。

### 3.12 地块标注列表
接口描述：查询地块的标注列表(仅展示"所有人可见"或"本人创建"的标注)。
| 接口路径 | /api/parcel/parcels/{id}/annotations |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID |

响应参数(data字段内，List&lt;LandAnnotation&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parcelId | Long | 所属地块ID |
| parcelCode | String | 地块编码 |
| type | String | 标注类型：TEXT文本/COLOR颜色/TAG标签 |
| content | String | 标注内容 |
| color | String | 颜色值 |
| tags | String | 标签 |
| visibleScope | String | 可见范围：SELF仅本人/ALL所有人 |
| ownerId | Long | 创建人ID |
| ownerName | String | 创建人姓名 |
| createdAt | LocalDateTime | 创建时间 |

### 3.13 新增地块标注
接口描述：给地块添加文本/颜色/标签标注。
| 接口路径 | /api/parcel/parcels/{id}/annotations |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 地块ID(覆盖body中parcelId) |
| type | String | body | 否 | 标注类型：TEXT/COLOR/TAG |
| content | String | body | 否 | 标注内容 |
| color | String | body | 否 | 颜色值 |
| tags | String | body | 否 | 标签 |
| visibleScope | String | body | 否 | 可见范围：SELF/ALL |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新增标注的ID |

### 3.14 删除地块标注
接口描述：删除标注(仅本人或管理员可删)。
| 接口路径 | /api/parcel/annotations/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 标注ID |

响应参数：无业务数据，data为null。

## 4. 耕地利用管理

模块说明：管理种植记录(PlantingRecord)与耕地质量评价记录(LandQuality)，支持分页查询、地块历年种植历史、增删改、批量修改/删除等。

### 4.1 分页查询种植记录
接口描述：按条件分页查询种植记录。
| 接口路径 | /api/planting/records |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/地块名/承包方模糊匹配) |
| plantYear | Integer | query | 否 | 种植年份 |
| crop | String | query | 否 | 作物 |
| dataSource | String | query | 否 | 数据来源 |
| status | String | query | 否 | 记录状态 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;PlantingRecord&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;PlantingRecord&gt; | 种植记录列表，字段见下 |

PlantingRecord 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parcelCode | String | 地块编码 |
| parcelName | String | 地块名称 |
| regionId | Long | 所属区域ID |
| regionPath | String | 区域路径 |
| plantYear | Integer | 种植年份 |
| season | String | 季节：SPRING春/SUMMER夏/AUTUMN秋 |
| crop | String | 作物 |
| variety | String | 品种 |
| area | BigDecimal | 种植面积(亩) |
| sowDate | LocalDate | 播种日期 |
| expectHarvestDate | LocalDate | 预计收获日期 |
| actualHarvestDate | LocalDate | 实际收获日期(非空表示已收获) |
| yieldPerMu | BigDecimal | 亩产 |
| dataSource | String | 数据来源：REMOTE遥感/STAT统计/FARMER农户/PATROL巡查 |
| reporter | String | 上报人 |
| reportDate | LocalDate | 上报日期 |
| status | String | 状态：VALID有效/INVALID已无效 |
| remark | String | 备注 |
| deleted | Integer | 逻辑删除标志 |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| createdBy | String | 创建人 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| version | Integer | 乐观锁版本号 |

### 4.2 地块种植历史
接口描述：查询某地块的历年种植记录(不分页)。
| 接口路径 | /api/planting/history |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| parcelCode | String | query | 是 | 地块编码 |

响应参数(data字段内)：List&lt;PlantingRecord&gt;，字段同4.1。

### 4.3 新增种植记录
接口描述：创建一条种植记录。
| 接口路径 | /api/planting/records |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为PlantingRecord对象，字段同4.1，id/deleted/createdAt等由后端管理)

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建记录ID |

### 4.4 修改种植记录
接口描述：更新种植记录。
| 接口路径 | /api/planting/records/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 记录ID |
| (body) | PlantingRecord | body | 是 | 更新内容，字段同4.1 |

响应参数：无业务数据，data为null。

### 4.5 标记记录失效
接口描述：将种植记录状态置为失效(INVALID)。
| 接口路径 | /api/planting/records/{id}/invalid |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 记录ID |

响应参数：无业务数据，data为null。

### 4.6 删除种植记录
接口描述：逻辑删除种植记录。
| 接口路径 | /api/planting/records/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 记录ID |
| reason | String | query | 否 | 删除原因 |

响应参数：无业务数据，data为null。

### 4.7 批量修改种植记录
接口描述：按ID列表批量更新种植记录字段。
| 接口路径 | /api/planting/records/batch |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待更新记录ID列表 |
| updates | PlantingRecord | body | 是 | 待应用的更新内容 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

### 4.8 批量删除种植记录
接口描述：按ID列表批量逻辑删除种植记录。
| 接口路径 | /api/planting/records/batch-delete |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待删除记录ID列表 |
| reason | String | body | 否 | 删除原因 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

### 4.9 分页查询地力评价记录
接口描述：按条件分页查询耕地质量评价记录。
| 接口路径 | /api/quality/records |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/地块名/承包方模糊匹配) |
| evalYear | Integer | query | 否 | 评价年份 |
| gradeMin | Integer | query | 否 | 地力等级范围下限 |
| gradeMax | Integer | query | 否 | 地力等级范围上限 |
| soilType | String | query | 否 | 土壤类型 |
| obstacle | String | query | 否 | 障碍因素 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;LandQuality&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;LandQuality&gt; | 地力评价记录列表，字段见下 |

LandQuality 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parcelCode | String | 地块编码 |
| parcelName | String | 地块名称 |
| regionId | Long | 所属区域ID |
| regionPath | String | 区域路径 |
| contractorName | String | 承包方姓名 |
| evalYear | Integer | 评价年份 |
| grade | Integer | 地力等级，1-10，1最好 |
| score | BigDecimal | 综合评分 |
| soilType | String | 土壤类型 |
| organicMatter | BigDecimal | 有机质含量(g/kg) |
| totalN | BigDecimal | 全氮含量(g/kg) |
| availP | BigDecimal | 有效磷含量(mg/kg) |
| availK | BigDecimal | 速效钾含量(mg/kg) |
| ph | BigDecimal | pH值 |
| obstacle | String | 障碍因素 |
| suitableCrops | String | 适宜作物 |
| org | String | 评价机构 |
| reportFile | String | 报告文件 |
| deleted | Integer | 逻辑删除标志 |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| createdBy | String | 创建人 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| version | Integer | 乐观锁版本号 |

### 4.10 新增地力评价记录
接口描述：创建耕地质量评价记录。
| 接口路径 | /api/quality/records |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为LandQuality对象，字段同4.9)

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建记录ID |

### 4.11 修改地力评价记录
接口描述：更新耕地质量评价记录。
| 接口路径 | /api/quality/records/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 记录ID |
| (body) | LandQuality | body | 是 | 更新内容，字段同4.9 |

响应参数：无业务数据，data为null。

### 4.12 删除地力评价记录
接口描述：逻辑删除耕地质量评价记录。
| 接口路径 | /api/quality/records/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 记录ID |
| reason | String | query | 否 | 删除原因 |

响应参数：无业务数据，data为null。

### 4.13 批量修改地力评价记录
接口描述：按ID列表批量更新耕地质量评价记录字段。
| 接口路径 | /api/quality/batch |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待更新记录ID列表 |
| updates | LandQuality | body | 是 | 待应用的更新内容 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

## 5. 撂荒管理

模块说明：管理撂荒地块台账(AbandonParcel)、撂荒原因填报(AbandonReason)与治理任务(AbandonTask)的全流程：发现登记、原因填报、任务下发、办理反馈、复耕验收。

### 5.1 撂荒地块分页列表
接口描述：按条件分页查询撂荒地块。
| 接口路径 | /api/abandon/parcels |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/地块名/上报人模糊匹配) |
| governStatus | String | query | 否 | 治理状态 |
| abandonYear | Integer | query | 否 | 撂荒年份 |
| source | String | query | 否 | 数据来源 |
| reasonType | String | query | 否 | 原因大类 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;AbandonParcel&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;AbandonParcel&gt; | 撂荒地块列表，字段见下 |

AbandonParcel 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parcelCode | String | 地块编码 |
| parcelName | String | 地块名称 |
| regionId | Long | 所属区域ID |
| regionPath | String | 区域路径 |
| abandonYear | Integer | 撂荒年份 |
| area | BigDecimal | 撂荒面积(亩) |
| partialRatio | BigDecimal | 部分撂荒比例 |
| degree | String | 撂荒程度：FULL全部/PARTIAL部分 |
| startTime | LocalDate | 撂荒开始时间 |
| foundDate | LocalDate | 发现日期 |
| source | String | 来源：REMOTE遥感监测/PATROL网格员巡查/REPORT群众举报 |
| reasonType | String | 原因大类：LABOR劳力/ECON经济/INFRA基础设施/SOIL土壤/DISASTER灾害/TRANSFER流转/OTHER其他 |
| reasonText | String | 原因文字说明 |
| reporter | String | 上报人 |
| governStatus | String | 治理状态：PENDING待审核/UNGOVERNED未治理/GOVERNING治理中/GOVERNED已治理/REJECTED已驳回 |
| manager | String | 责任人/管理人 |
| remark | String | 备注 |
| deleted | Integer | 逻辑删除标志 |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| createdBy | String | 创建人 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| version | Integer | 乐观锁版本号 |

### 5.2 撂荒地块详情
接口描述：查询撂荒地块详情，含原因填报记录与关联治理任务列表。
| 接口路径 | /api/abandon/parcels/{id} |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 撂荒地块ID |

响应参数(data字段内，AbandonDetail)
| 参数名 | 类型 | 描述 |
|---|---|---|
| parcel | AbandonParcel | 地块基本信息，字段同5.1 |
| reasons | Array&lt;AbandonReason&gt; | 原因填报记录列表(字段见下) |
| tasks | Array&lt;AbandonTask&gt; | 关联治理任务列表(字段见下) |

AbandonReason 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| abandonId | Long | 关联撂荒地块ID |
| reasonTypes | String | 原因类型，逗号分隔多选 |
| detail | String | 详细说明 |
| suggestion | String | 治理建议 |
| reporter | String | 填报人 |
| createdAt | LocalDateTime | 创建时间 |

AbandonTask 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| taskNo | String | 任务编号 |
| name | String | 任务名称 |
| abandonId | Long | 关联撂荒地块ID |
| parcelCode | String | 地块编码 |
| description | String | 任务描述 |
| respUnit | String | 责任单位 |
| respPerson | String | 责任人 |
| targetArea | BigDecimal | 目标治理面积(亩) |
| standard | String | 治理标准 |
| plan | String | 治理方案 |
| deadline | LocalDate | 截止日期 |
| progress | Integer | 进度(百分比) |
| taskStatus | String | 任务状态：ISSUED已下发/HANDLING办理中/ACCEPTING待验收/DONE验收通过/RETURNED已退回 |
| createdBy | String | 创建人 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 5.3 撂荒统计数据
接口描述：查询撂荒相关统计数据(按年度)。
| 接口路径 | /api/abandon/stats |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| year | Integer | query | 否 | 统计年份 |

响应参数(data字段内)：统计结果(Map结构，无固定Schema，由后端动态组装)。

### 5.4 新建撂荒地块
接口描述：登记新的撂荒地块。
| 接口路径 | /api/abandon/parcels |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为AbandonParcel对象，字段同5.1)

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建地块ID |

### 5.5 更新撂荒地块
接口描述：更新撂荒地块信息。
| 接口路径 | /api/abandon/parcels/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 撂荒地块ID |
| (body) | AbandonParcel | body | 是 | 更新内容，字段同5.1 |

响应参数：无业务数据，data为null。

### 5.6 变更治理状态
接口描述：变更撂荒地块的治理状态。
| 接口路径 | /api/abandon/parcels/{id}/status |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 撂荒地块ID |
| governStatus | String | body | 否 | 新治理状态 |
| remark | String | body | 否 | 备注 |

响应参数：无业务数据，data为null。

### 5.7 删除撂荒地块
接口描述：逻辑删除撂荒地块。
| 接口路径 | /api/abandon/parcels/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 撂荒地块ID |
| reason | String | query | 否 | 删除原因 |

响应参数：无业务数据，data为null。

### 5.8 添加撂荒原因填报
接口描述：为撂荒地块添加一条原因填报记录。
| 接口路径 | /api/abandon/parcels/{id}/reasons |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 撂荒地块ID(覆盖body中abandonId) |
| reasonTypes | String | body | 否 | 原因类型(逗号分隔多选) |
| detail | String | body | 否 | 详细说明 |
| suggestion | String | body | 否 | 治理建议 |
| reporter | String | body | 否 | 填报人 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建原因记录ID |

### 5.9 创建治理任务
接口描述：为撂荒地块创建一条治理任务。
| 接口路径 | /api/abandon/parcels/{id}/tasks |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 撂荒地块ID(覆盖body中abandonId) |
| taskNo | String | body | 否 | 任务编号 |
| name | String | body | 否 | 任务名称 |
| parcelCode | String | body | 否 | 地块编码 |
| description | String | body | 否 | 任务描述 |
| respUnit | String | body | 否 | 责任单位 |
| respPerson | String | body | 否 | 责任人 |
| targetArea | BigDecimal | body | 否 | 目标治理面积(亩) |
| standard | String | body | 否 | 治理标准 |
| deadline | LocalDate | body | 否 | 截止日期 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建任务ID |

### 5.10 批量创建治理任务
接口描述：对多个撂荒地块按统一模板批量创建治理任务。
| 接口路径 | /api/abandon/parcels/batch-tasks |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| abandonIds | List&lt;Long&gt; | body | 是 | 批量撂荒地块ID列表 |
| template | AbandonTask | body | 是 | 任务模板(字段同5.2中AbandonTask) |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Array&lt;Long&gt; | 新建任务ID列表 |

### 5.11 治理任务列表
接口描述：按任务状态查询治理任务列表。
| 接口路径 | /api/abandon/tasks |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| status | String | query | 否 | 任务状态筛选 |

响应参数(data字段内)：List&lt;AbandonTask&gt;，字段同5.2。

### 5.12 更新任务进度
接口描述：更新治理任务的办理进度百分比。
| 接口路径 | /api/abandon/tasks/{taskId}/progress |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| taskId | Long | path | 是 | 任务ID |
| progress | Integer | body | 是 | 新进度(百分比) |

响应参数：无业务数据，data为null。

### 5.13 提交/更新治理方案
接口描述：提交或更新任务的治理方案文本。
| 接口路径 | /api/abandon/tasks/{taskId}/plan |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| taskId | Long | path | 是 | 任务ID |
| plan | String | body | 是 | 治理方案内容 |

响应参数：无业务数据，data为null。

### 5.14 任务办理反馈
接口描述：提交任务办理过程反馈内容。
| 接口路径 | /api/abandon/tasks/{taskId}/feedback |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| taskId | Long | path | 是 | 任务ID |
| content | String | body | 是 | 反馈内容 |

响应参数：无业务数据，data为null。

### 5.15 任务验收
接口描述：对治理任务进行验收，通过时可附带复耕信息。
| 接口路径 | /api/abandon/tasks/{taskId}/accept |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| taskId | Long | path | 是 | 任务ID |
| pass | Boolean | body | 是 | 是否验收通过 |
| crop | String | body | 否 | 复耕作物(验收通过时填) |
| area | BigDecimal | body | 否 | 复耕面积(验收通过时填) |
| year | Integer | body | 否 | 复耕年份(验收通过时填) |

响应参数：无业务数据，data为null。

## 6. 农业资源管理

模块说明：管理设施分类字典(FacilityCategory)、农业支撑设施(SupportFacility)与水利设施(WaterFacility)，支持分页查询、增删改、审核、批量操作及服务范围维护。

### 6.1 获取设施分类树
接口描述：获取设施分类的嵌套树结构。
| 接口路径 | /api/facility-category/tree |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内，List&lt;FacilityCategory&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| parentId | Long | 父分类ID |
| name | String | 分类名称 |
| icon | String | 图标 |
| sort | Integer | 排序 |
| status | Integer | 状态：1显示/0隐藏 |
| createdAt | LocalDateTime | 创建时间 |
| children | Array&lt;FacilityCategory&gt; | 子分类列表(非数据库字段，组装为树形结构) |

### 6.2 获取叶子分类列表
接口描述：获取所有叶子节点分类(无子分类的末级分类，常用于表单选择器)。
| 接口路径 | /api/facility-category/leaves |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)：List&lt;FacilityCategory&gt;，字段同6.1。

### 6.3 新建设施分类
接口描述：创建一个设施分类(可指定父分类形成多级)。
| 接口路径 | /api/facility-category |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| parentId | Long | body | 否 | 父分类ID |
| name | String | body | 否 | 分类名称 |
| icon | String | body | 否 | 图标 |
| sort | Integer | body | 否 | 排序 |
| status | Integer | body | 否 | 状态：1显示/0隐藏 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建分类ID |

### 6.4 更新设施分类
接口描述：更新设施分类信息。
| 接口路径 | /api/facility-category/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 分类ID |
| (body) | FacilityCategory | body | 是 | 更新内容，字段同6.3 |

响应参数：无业务数据，data为null。

### 6.5 删除设施分类
接口描述：删除设施分类。
| 接口路径 | /api/facility-category/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 分类ID |

响应参数：无业务数据，data为null。

### 6.6 删除分类并转移数据
接口描述：删除分类前，将其下挂的设施数据转移到另一分类。
| 接口路径 | /api/facility-category/{id}/transfer-delete |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 待删除分类ID |
| transferToId | Long | body | 是 | 数据转移目标分类ID |

响应参数：无业务数据，data为null。

### 6.7 支撑设施分页列表
接口描述：按条件分页查询农业支撑设施(如烘干房、仓储、加工厂等)。
| 接口路径 | /api/support/facilities |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 名称模糊搜索 |
| categoryId | Long | query | 否 | 二级分类ID |
| operateStatus | String | query | 否 | 运营状态 |
| operateSubject | String | query | 否 | 运营主体 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;SupportFacility&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;SupportFacility&gt; | 支撑设施列表，字段见下 |

SupportFacility 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| name | String | 设施名称 |
| categoryId | Long | 分类ID |
| categoryName | String | 分类名称(非表字段，关联展示用) |
| regionId | Long | 所属区域ID |
| regionPath | String | 区域路径 |
| lng | BigDecimal | 经度 |
| lat | BigDecimal | 纬度 |
| serviceRange | String | 服务范围描述 |
| serviceArea | String | 服务范围面(GeoJSON Polygon) |
| coverageCount | Integer | 服务范围覆盖地块数(自动统计) |
| coverageVillageCount | Integer | 服务范围覆盖村庄数(自动统计) |
| serviceAbility | String | 服务能力 |
| operateStatus | String | 运营状态：正常/停业/建设中 |
| operateSubject | String | 运营主体：企业/合作社/个体户 |
| phone | String | 联系电话 |
| businessHours | String | 营业时间 |
| qualification | String | 资质信息 |
| auditStatus | String | 审核状态 |
| remark | String | 备注 |
| deleted | Integer | 逻辑删除标志 |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| createdBy | String | 创建人 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| version | Integer | 乐观锁版本号 |

### 6.8 支撑设施详情
接口描述：查询单个支撑设施详情。
| 接口路径 | /api/support/facilities/{id} |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 支撑设施ID |

响应参数(data字段内)：同6.7中SupportFacility字段。

### 6.9 新建支撑设施
接口描述：创建一个农业支撑设施。
| 接口路径 | /api/support/facilities |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为SupportFacility对象，字段同6.7)

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建设施ID |

### 6.10 更新支撑设施
接口描述：更新支撑设施信息。
| 接口路径 | /api/support/facilities/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 支撑设施ID |
| (body) | SupportFacility | body | 是 | 更新内容，字段同6.7 |

响应参数：无业务数据，data为null。

### 6.11 审核支撑设施
接口描述：对支撑设施信息进行审核(通过/不通过)。
| 接口路径 | /api/support/facilities/{id}/audit |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 支撑设施ID |
| pass | boolean | query | 是 | 是否审核通过 |

响应参数：无业务数据，data为null。

### 6.12 删除支撑设施
接口描述：逻辑删除支撑设施。
| 接口路径 | /api/support/facilities/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 支撑设施ID |
| reason | String | query | 否 | 删除原因 |

响应参数：无业务数据，data为null。

### 6.13 更新设施服务范围
接口描述：更新支撑设施的服务范围面(GeoJSON)，并触发覆盖地块/村庄数自动统计。
| 接口路径 | /api/support/facilities/{id}/service-area |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 支撑设施ID |
| serviceArea | String | body | 是 | 服务范围面(GeoJSON Polygon字符串) |

响应参数(data字段内)：更新后的SupportFacility完整对象，字段同6.7(含重新统计的coverageCount/coverageVillageCount)。

### 6.14 批量更新支撑设施
接口描述：按ID列表批量更新支撑设施字段。
| 接口路径 | /api/support/facilities/batch |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待更新设施ID列表 |
| updates | SupportFacility | body | 是 | 待应用的更新内容 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

### 6.15 批量删除支撑设施
接口描述：按ID列表批量逻辑删除支撑设施。
| 接口路径 | /api/support/facilities/batch-delete |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待删除设施ID列表 |
| reason | String | body | 否 | 删除原因 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

### 6.16 水利设施分页列表
接口描述：按条件分页查询水利设施。
| 接口路径 | /api/water/facilities |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 名称/管护责任人模糊搜索 |
| type | String | query | 否 | 设施类型 |
| runStatus | String | query | 否 | 运行状态 |
| auditStatus | String | query | 否 | 审核状态 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;WaterFacility&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;WaterFacility&gt; | 水利设施列表，字段见下 |

WaterFacility 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| name | String | 设施名称 |
| type | String | 设施类型：机井/泵站/水闸/渠道/滴灌系统/喷灌系统/蓄水池 |
| regionId | Long | 所属区域ID |
| regionPath | String | 区域路径 |
| lng | BigDecimal | 经度 |
| lat | BigDecimal | 纬度 |
| buildYear | Integer | 建设年份 |
| coverArea | BigDecimal | 覆盖面积(亩) |
| benefitVillages | String | 受益村庄 |
| runStatus | String | 运行状态：正常/维修中/废弃/待改造 |
| manager | String | 管护责任人 |
| phone | String | 联系电话 |
| lastMaintainDate | LocalDate | 最近维护日期 |
| techParams | String | 技术参数 |
| auditStatus | String | 审核状态：PENDING待审核/APPROVED已通过/REJECTED已退回 |
| remark | String | 备注 |
| deleted | Integer | 逻辑删除标志 |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| createdBy | String | 创建人 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| version | Integer | 乐观锁版本号 |

### 6.17 水利设施详情
接口描述：查询单个水利设施详情。
| 接口路径 | /api/water/facilities/{id} |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 水利设施ID |

响应参数(data字段内)：同6.16中WaterFacility字段。

### 6.18 新建水利设施
接口描述：创建一个水利设施。
| 接口路径 | /api/water/facilities |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为WaterFacility对象，字段同6.16)

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建设施ID |

### 6.19 更新水利设施
接口描述：更新水利设施信息。
| 接口路径 | /api/water/facilities/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 水利设施ID |
| (body) | WaterFacility | body | 是 | 更新内容，字段同6.16 |

响应参数：无业务数据，data为null。

### 6.20 审核水利设施
接口描述：对水利设施信息进行审核(通过/不通过)。
| 接口路径 | /api/water/facilities/{id}/audit |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 水利设施ID |
| pass | boolean | query | 是 | 是否审核通过 |

响应参数：无业务数据，data为null。

### 6.21 删除水利设施
接口描述：逻辑删除水利设施。
| 接口路径 | /api/water/facilities/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 水利设施ID |
| reason | String | query | 否 | 删除原因 |

响应参数：无业务数据，data为null。

### 6.22 批量更新水利设施
接口描述：按ID列表批量更新水利设施字段。
| 接口路径 | /api/water/facilities/batch |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待更新设施ID列表 |
| updates | WaterFacility | body | 是 | 待应用的更新内容 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

### 6.23 批量删除水利设施
接口描述：按ID列表批量逻辑删除水利设施。
| 接口路径 | /api/water/facilities/batch-delete |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| ids | List&lt;Long&gt; | body | 是 | 待删除设施ID列表 |
| reason | String | body | 否 | 删除原因 |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Integer | 受影响的记录数 |

## 7. 种植动态分析

模块说明：基于地块/种植/质量/撂荒等数据做统计聚合分析，包括总览、年度趋势、区域分布、地类构成、优势产区、年度流转桑基图、区域对比等。各接口返回的统计结构均由 AnalysisService 在内存中动态拼装为 `Map<String,Object>`，没有固定的 DTO Schema，下方仅列出查询参数。

### 7.1 获取统计年度列表
接口描述：获取系统中有数据的统计年度列表，供前端年度筛选器使用。
| 接口路径 | /api/analysis/years |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Array&lt;Integer&gt; | 有数据的年度列表 |

### 7.2 总览统计
接口描述：查询农业资源总览统计数据(地块总数、面积、撂荒情况等综合指标)。
| 接口路径 | /api/analysis/overview |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| year | Integer | query | 否 | 统计年份 |

响应参数(data字段内)：总览统计对象(Map结构，无固定Schema)。

### 7.3 年度趋势统计
接口描述：查询历年统计趋势数据。
| 接口路径 | /api/analysis/yearly |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)：年度趋势对象(Map结构，无固定Schema)。

### 7.4 按行政区划统计
接口描述：按行政区划维度统计种植/地块相关数据。
| 接口路径 | /api/analysis/region |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| year | Integer | query | 否 | 统计年份 |

响应参数(data字段内)：List&lt;Map&gt;，每个元素为一个区域的统计数据(无固定Schema)。

### 7.5 土地利用类型统计
接口描述：按地块用途/地类统计面积及占比。
| 接口路径 | /api/analysis/land-use |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| year | Integer | query | 否 | 统计年份 |

响应参数(data字段内)：List&lt;Map&gt;，每个元素为一种地类的统计数据(无固定Schema)。

### 7.6 优势产区统计
接口描述：按作物统计优势种植区域分布。
| 接口路径 | /api/analysis/advantage-zones |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| crop | String | query | 否 | 作物名称 |

响应参数(data字段内)：优势产区统计对象(Map结构，无固定Schema)。

### 7.7 年度间流转桑基图数据
接口描述：查询两个年度之间地类/作物流转的桑基图数据。
| 接口路径 | /api/analysis/sankey |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| fromYear | Integer | query | 否 | 起始年份 |
| toYear | Integer | query | 否 | 目标年份 |

响应参数(data字段内)：桑基图节点/连线数据(Map结构，无固定Schema)。

### 7.8 区域对比统计
接口描述：多区域间统计指标对比。
| 接口路径 | /api/analysis/region-compare |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| year | Integer | query | 否 | 统计年份 |

响应参数(data字段内)：区域对比统计对象(Map结构，无固定Schema)。

## 8. 回收站

模块说明：统一管理各业务模块逻辑删除后的数据，可按业务类型查询、还原或彻底删除。`bizType` 取值对应各业务模块（如 parcel地块/planting种植记录/quality地力评价/abandon撂荒地块/support配套设施/water水利设施等）。

### 8.1 查询回收站条目列表
接口描述：按业务类型查询回收站中的已删除数据条目。
| 接口路径 | /api/recycle/items |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 否 | 业务类型，不传则查询全部类型 |

响应参数(data字段内，List&lt;RecycleItem&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| bizType | String | 业务类型key |
| bizTypeName | String | 业务类型中文名 |
| bizId | Long | 业务数据主键ID |
| title | String | 主标题(名称) |
| subtitle | String | 副标题(编码/年度等) |
| deleteReason | String | 删除原因 |
| deletedBy | String | 删除操作人 |
| deletedAt | LocalDateTime | 删除时间 |
| daysLeft | Integer | 距彻底删除剩余天数 |

### 8.2 还原数据
接口描述：将回收站中的数据还原为正常状态。
| 接口路径 | /api/recycle/restore |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 是 | 业务类型 |
| id | Long | query | 是 | 业务数据ID |

响应参数：无业务数据，data为null。

### 8.3 彻底删除数据
接口描述：物理删除回收站中的数据，不可恢复。
| 接口路径 | /api/recycle/purge |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 是 | 业务类型 |
| id | Long | query | 是 | 业务数据ID |

响应参数：无业务数据，data为null。

## 9. 待审核中心

模块说明：统一管理需审核的业务数据(如配套设施、水利设施新增/变更)的待审核条目，支持查询与审核通过/拒绝。

### 9.1 查询待审核条目列表
接口描述：按业务类型查询待审核数据列表。
| 接口路径 | /api/audit-center/items |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 否 | 业务类型，不传则查询全部类型 |

响应参数(data字段内，List&lt;AuditItem&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| bizType | String | 业务类型(water/support/abandon等模块) |
| bizTypeName | String | 业务类型中文名 |
| bizId | Long | 业务数据主键ID |
| title | String | 主标题 |
| subtitle | String | 副标题 |
| submittedBy | String | 提交人 |
| submittedAt | LocalDateTime | 提交时间 |

### 9.2 审核
接口描述：对某条待审核业务数据进行审核通过或拒绝。
| 接口路径 | /api/audit-center/audit |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 是 | 业务类型 |
| id | Long | query | 是 | 业务数据ID |
| pass | boolean | query | 是 | 是否审核通过(true通过/false拒绝) |

响应参数：无业务数据，data为null。

## 10. GIS空间能力

模块说明：提供确权地块的 Shapefile/KML 批量导入能力，以及基于矩形/圆形/多边形范围的空间查询能力。导入结果统一复用 `ImportResult`(成功/失败/逐行错误)约定。

### 10.1 Shapefile 导入说明
接口描述：获取 Shapefile 批量导入的字段别名映射说明，供前端展示导入规则。
| 接口路径 | /api/import/parcel-shapefile/help |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| desc | String | 导入说明文字 |
| fieldAliases | Object | 字段别名映射表，key为目标字段(parcelCode/name/regionPath/contractorName/contractorCode/area/landUse)，value为可识别的别名字符串数组 |

### 10.2 KML 导入说明
接口描述：获取 KML 批量导入的字段别名映射说明。
| 接口路径 | /api/import/parcel-kml/help |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)：结构同10.1。

### 10.3 导入 Shapefile 批量新建地块
接口描述：上传zip包(含同名.shp+.dbf，可选.shx)批量导入确权地块，每个图形生成一个新地块。
| 接口路径 | /api/import/parcel-shapefile |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | zip压缩包，内含同名.shp与.dbf文件(.dbf建议GBK编码) |

响应参数(data字段内，ImportResult)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | int | 总图形数 |
| success | int | 成功条数 |
| failed | int | 失败条数 |
| errors | Array&lt;String&gt; | 逐条错误信息，格式"第N个图形: 错误描述" |

### 10.4 导入 KML 批量新建地块
接口描述：上传.kml文件批量导入确权地块，每个Placemark对应一个地块面。
| 接口路径 | /api/import/parcel-kml |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | KML文件 |

响应参数(data字段内)：ImportResult，字段同10.3。

### 10.5 空间范围查询
接口描述：按矩形/圆形/多边形范围查询命中的目标对象(地块/水利设施/配套设施/种植记录/质量评价/撂荒地块)。
| 接口路径 | /api/gis/spatial-query |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数(body为SpatialQueryRequest对象)
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| targetType | String | body | 否 | 查询目标类型：water水利设施/support配套设施/parcel地块/planting种植记录/quality质量评价/abandon撂荒地块 |
| shape.type | String | body | 否 | 形状类型：rect矩形/circle圆形/polygon多边形 |
| shape.bounds | Array&lt;Array&lt;Double&gt;&gt; | body | 否 | rect专用：[[lng,lat]西南角, [lng,lat]东北角] |
| shape.center | Array&lt;Double&gt; | body | 否 | circle专用：[lng,lat]圆心 |
| shape.radius | Double | body | 否 | circle专用：半径(米) |
| shape.points | Array&lt;Array&lt;Double&gt;&gt; | body | 否 | polygon专用：[[lng,lat], ...]顶点序列 |

响应参数(data字段内)：List&lt;Map&gt;，每个元素为命中的目标对象，字段视targetType而定(地块/设施/种植/质量/撂荒各自字段不同，无统一Schema)。

## 11. 地图

模块说明：提供"一张图"地图打点聚合接口，将地块、水利设施、配套设施、撂荒地块的坐标/边界统一聚合返回，供前端Leaflet等地图组件渲染。

### 11.1 获取地图打点数据
接口描述：获取地块、水利设施、配套设施、撂荒地块的地图点位与边界数据(坐标统一为WGS84经纬度)。
| 接口路径 | /api/map/points |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| parcel | Array&lt;Object&gt; | 地块点位列表，每项含name/lng/lat/code/contractor/area/landUse/boundary(GeoJSON) |
| water | Array&lt;Object&gt; | 水利设施点位列表，每项含name/lng/lat/subtype(设施类型)/runStatus(运行状态)/manager(管护责任人) |
| support | Array&lt;Object&gt; | 配套设施点位列表，每项含name/lng/lat/operateStatus(运营状态)/ability(服务能力) |
| abandon | Array&lt;Object&gt; | 撂荒地块点位列表(借关联确权地块的中心点定位)，每项含name/lng/lat/code/governStatus(治理状态)/reason(撂荒原因)/area/boundary(借关联地块边界) |

响应示例
成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "parcel": [
      {"name": "东岗一号地", "lng": 113.123456, "lat": 34.123456, "code": "DK0001", "contractor": "张三", "area": 12.5, "landUse": "耕地", "boundary": "{...GeoJSON...}"}
    ],
    "water": [
      {"name": "一号机井", "lng": 113.111, "lat": 34.111, "subtype": "机井", "runStatus": "正常", "manager": "李四"}
    ],
    "support": [
      {"name": "粮食仓储中心", "lng": 113.222, "lat": 34.222, "operateStatus": "正常", "ability": "可存储1000吨"}
    ],
    "abandon": [
      {"name": "西坡地", "lng": 113.333, "lat": 34.333, "code": "DK0002", "governStatus": "UNGOVERNED", "reason": "劳力外出", "area": 3.2, "boundary": "{...GeoJSON...}"}
    ]
  }
}
```

## 12. 附件

模块说明：为各业务对象(地块、设施、撂荒地块等)提供附件上传、查询、下载、删除能力，统一通过 `bizType`(业务类型) + `bizId`(业务数据ID) 关联。

### 12.1 查询附件列表
接口描述：按业务类型+业务ID查询关联的附件列表。
| 接口路径 | /api/attachment/list |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 是 | 业务类型 |
| bizId | Long | query | 是 | 业务数据ID |

响应参数(data字段内，List&lt;Attachment&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| bizType | String | 业务类型 |
| bizId | Long | 业务对象ID |
| fileName | String | 原始文件名 |
| storedName | String | 服务器存储文件名 |
| contentType | String | 文件MIME类型 |
| fileSize | Long | 文件大小(字节) |
| uploadedBy | String | 上传人 |
| createdAt | LocalDateTime | 创建时间 |

### 12.2 上传附件
接口描述：上传文件并关联到指定业务对象。
| 接口路径 | /api/attachment/upload |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 是 | 业务类型 |
| bizId | Long | query | 是 | 业务数据ID |
| file | File | body(form-data) | 是 | 上传的文件 |

响应参数(data字段内)：Attachment对象，字段同12.1。

### 12.3 下载附件
接口描述：根据附件ID下载文件二进制内容。**注意：本接口不返回 `Result` JSON包装，直接返回文件二进制流。**
| 接口路径 | /api/attachment/download/{id} |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | 附件记录中的contentType值，若为空则为application/octet-stream |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 附件ID |

响应参数：响应体为文件二进制内容；响应头 `Content-Disposition: attachment; filename*=UTF-8''<URL编码后的原始文件名>`。

### 12.4 删除附件
接口描述：删除指定附件。
| 接口路径 | /api/attachment/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 附件ID |

响应参数：无业务数据，data为null。

## 13. 导出

模块说明：将各业务模块数据导出为Excel文件，便于线下台账留存。**本模块所有接口均不返回 `Result` JSON包装，而是直接通过HTTP响应写出Excel文件二进制流**，Content-Type固定为 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，响应头 `Content-Disposition: attachment; filename*=UTF-8''<URL编码后的文件名>.xlsx`。查询参数均以GET请求query string平铺传递(同分页查询的Query对象字段)，且导出时内部强制不分页(取全部数据)。各接口枚举值导出时会转换为中文展示。

### 13.1 导出撂荒地块台账
接口描述：导出全部撂荒地块数据为Excel(文件名"撂荒地块台账")。
| 接口路径 | /api/export/abandon |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/地块名/上报人) |
| governStatus | String | query | 否 | 治理状态 |
| abandonYear | Integer | query | 否 | 撂荒年份 |
| source | String | query | 否 | 数据来源 |
| reasonType | String | query | 否 | 原因大类 |

响应参数：Excel文件流。导出列：地块编码、地块名称、坐落位置、撂荒年份、面积(亩)、撂荒原因、上报人、发现日期、治理状态(中文)、责任人。

### 13.2 导出撂荒治理任务台账
接口描述：导出全部撂荒治理任务数据为Excel(文件名"撂荒治理任务台账")。
| 接口路径 | /api/export/abandon-task |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| status | String | query | 否 | 任务状态 |

响应参数：Excel文件流。导出列：任务编号、任务名称、关联地块、责任单位、责任人、治理面积目标(亩)、完成时限、进度(%)、任务状态(中文)。

### 13.3 导出确权地块台账
接口描述：导出全部确权地块数据为Excel(文件名"确权地块台账")。
| 接口路径 | /api/export/parcel |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/名称/承包方姓名) |
| landUse | String | query | 否 | 地块用途 |
| areaMin | BigDecimal | query | 否 | 面积下限 |
| areaMax | BigDecimal | query | 否 | 面积上限 |

响应参数：Excel文件流。导出列：地块编码、地块名称、坐落位置、承包方、承包方编码、确权面积(亩)、地块用途、承包起始、承包终止。

### 13.4 导出水利设施台账
接口描述：导出全部水利设施数据为Excel(文件名"水利设施台账")。
| 接口路径 | /api/export/water |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 名称/管护责任人 |
| type | String | query | 否 | 设施类型 |
| runStatus | String | query | 否 | 运行状态 |
| auditStatus | String | query | 否 | 审核状态 |

响应参数：Excel文件流。导出列：设施名称、类型、所在位置、建设年份、覆盖面积(亩)、运行状态、管护责任人、联系电话、审核状态(中文)。

### 13.5 导出配套设施台账
接口描述：导出全部农业支撑设施数据为Excel(文件名"配套设施台账")。
| 接口路径 | /api/export/support |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 名称模糊搜索 |
| categoryId | Long | query | 否 | 二级分类ID |
| operateStatus | String | query | 否 | 运营状态 |
| operateSubject | String | query | 否 | 运营主体 |

响应参数：Excel文件流。导出列：设施名称、分类、所在位置、服务能力、运营状态、运营主体、联系电话、审核状态(中文)。

### 13.6 导出种植记录台账
接口描述：导出全部种植记录数据为Excel(文件名"种植记录台账")。
| 接口路径 | /api/export/planting |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/地块名/承包方) |
| plantYear | Integer | query | 否 | 种植年度 |
| crop | String | query | 否 | 作物 |
| dataSource | String | query | 否 | 数据来源 |
| status | String | query | 否 | 状态 |

响应参数：Excel文件流。导出列：地块编码、地块名称、种植年度、作物、品种、面积(亩)、产量(kg/亩)、数据来源(中文)、状态(中文)。

### 13.7 导出耕地质量台账
接口描述：导出全部耕地质量评价数据为Excel(文件名"耕地质量台账")。
| 接口路径 | /api/export/quality |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字(地块编码/地块名/承包方) |
| evalYear | Integer | query | 否 | 评价年度 |
| gradeMin | Integer | query | 否 | 地力等级下限 |
| gradeMax | Integer | query | 否 | 地力等级上限 |
| soilType | String | query | 否 | 土壤类型 |
| obstacle | String | query | 否 | 障碍因素 |

响应参数：Excel文件流。导出列：地块编码、地块名称、承包方、评价年度、地力等级、综合得分、土壤类型、pH、障碍因素、适宜作物。

## 14. 导入

模块说明：提供确权地块、配套设施、种植记录、耕地质量、水利设施的Excel批量导入及对应导入模板下载。模板下载接口返回Excel文件流；导入接口上传Excel文件并返回 `ImportResult`(成功/失败/逐行错误)JSON。内部使用 EasyExcel 按行解析(从第2行开始)，空行自动跳过且不计入total；单行解析/校验失败会记录到 `errors` 列表(格式"第N行: 错误信息")，不影响其他行继续处理，HTTP仍返回200。

### 14.1 下载确权地块导入模板
接口描述：下载"确权地块导入模板"Excel(含示例行)。**本接口不返回 `Result` JSON包装，直接返回文件二进制流。**
| 接口路径 | /api/import/parcel/template |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数：无

响应参数：Excel文件流。表头：地块编码*、地块名称*、坐落位置、承包方姓名、承包方编码、确权面积(亩)、地块用途、承包起始(yyyy-MM-dd)、承包终止(yyyy-MM-dd)。标*为必填列。

### 14.2 批量导入确权地块
接口描述：批量导入确权地块(新建)，按行依次落库为 LandParcel。
| 接口路径 | /api/import/parcel |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | Excel文件，列顺序需对应14.1模板 |

响应参数(data字段内，ImportResult)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | int | 总记录数(不含空行) |
| success | int | 成功条数 |
| failed | int | 失败条数 |
| errors | Array&lt;String&gt; | 逐行错误信息，格式"第N行: 错误描述" |

### 14.3 下载确权地块批量更新模板
接口描述：下载"确权地块批量更新模板"Excel。
| 接口路径 | /api/import/parcel-update/template |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数：无

响应参数：Excel文件流。表头：地块编码*、承包方姓名、承包方编码、确权面积(亩)、地块用途。

### 14.4 批量更新确权地块
接口描述：按地块编码匹配现有地块并更新字段(地块编码必填且必须已存在)。
| 接口路径 | /api/import/parcel-update |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | Excel文件，列顺序需对应14.3模板 |

响应参数(data字段内)：ImportResult，字段同14.2。

### 14.5 下载配套设施导入模板
接口描述：下载"配套设施导入模板"Excel。
| 接口路径 | /api/import/support/template |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数：无

响应参数：Excel文件流。表头：设施名称*、分类名称*、所在位置、经度、纬度、服务范围、服务能力、运营状态、运营主体、联系电话。

### 14.6 批量导入配套设施
接口描述：按分类名称匹配二级分类后批量导入配套设施(分类名称不存在则该行失败)。
| 接口路径 | /api/import/support |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | Excel文件，列顺序需对应14.5模板 |

响应参数(data字段内)：ImportResult，字段同14.2。

### 14.7 下载种植数据导入模板
接口描述：下载"种植数据导入模板"Excel。
| 接口路径 | /api/import/planting/template |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数：无

响应参数：Excel文件流。表头：地块编码*、种植年度*、季节(春/夏/秋)、作物*、品种、面积(亩)、产量(kg/亩)、数据来源(遥感/统计/农户/巡查)。

### 14.8 批量导入种植记录
接口描述：批量导入种植记录，季节/数据来源中文自动转换为内部代码(未匹配时季节默认SPRING，数据来源默认STAT)。
| 接口路径 | /api/import/planting |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | Excel文件，列顺序需对应14.7模板 |

响应参数(data字段内)：ImportResult，字段同14.2。

### 14.9 下载耕地质量导入模板
接口描述：下载"耕地质量导入模板"Excel。
| 接口路径 | /api/import/quality/template |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数：无

响应参数：Excel文件流。表头：地块编码*、评价年度*、地力等级(1-10)*、综合得分、土壤类型、有机质(g/kg)、全氮(g/kg)、有效磷(mg/kg)、速效钾(mg/kg)、pH、障碍因素、适宜作物。

### 14.10 批量导入耕地质量评价数据
接口描述：批量导入耕地质量评价记录。
| 接口路径 | /api/import/quality |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | Excel文件，列顺序需对应14.9模板 |

响应参数(data字段内)：ImportResult，字段同14.2。

### 14.11 下载水利设施导入模板
接口描述：下载"水利设施导入模板"Excel。
| 接口路径 | /api/import/water/template |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |

请求参数：无

响应参数：Excel文件流。表头：设施名称*、类型*、所在位置、经度、纬度、建设年份、覆盖面积(亩)、运行状态、管护责任人、联系电话。

### 14.12 批量导入水利设施
接口描述：批量导入水利设施数据。
| 接口路径 | /api/import/water |
| 请求方法 | POST |
| 请求 Content-Type | multipart/form-data |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| file | File | body(form-data) | 是 | Excel文件，列顺序需对应14.11模板 |

响应参数(data字段内)：ImportResult，字段同14.2。

## 15. 通知

模块说明：管理当前登录用户的系统通知，支持查询未读数量、分页查询、标记已读。

### 15.1 获取未读通知数量
接口描述：查询当前登录用户的未读通知数量。
| 接口路径 | /api/notification/unread-count |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| count | Long | 未读通知数量 |

### 15.2 分页查询通知列表
接口描述：分页查询当前登录用户的通知，可选仅看未读。
| 接口路径 | /api/notification/list |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| onlyUnread | boolean | query | 否 | 是否只看未读，默认false |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;Notification&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;Notification&gt; | 通知列表，字段见下 |

Notification 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键，自增 |
| recipient | String | 接收人 |
| title | String | 标题 |
| content | String | 内容 |
| bizType | String | 关联业务类型 |
| bizId | Long | 关联业务ID |
| isRead | Integer | 是否已读：0未读/1已读 |
| createdAt | LocalDateTime | 创建时间 |

### 15.3 标记单条通知为已读
接口描述：将指定通知标记为已读。
| 接口路径 | /api/notification/{id}/read |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 通知ID |

响应参数：无业务数据，data为null。

### 15.4 全部标记为已读
接口描述：将当前用户全部未读通知标记为已读。
| 接口路径 | /api/notification/read-all |
| 请求方法 | POST |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数：无业务数据，data为null。

## 16. 系统管理

模块说明：包含健康检查、行政区划字典、用户管理、操作审计日志查询等系统基础能力。

### 16.1 健康检查
接口描述：服务存活探测接口，可用于负载均衡/容器健康检查。
| 接口路径 | /api/health |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | String | 固定返回"ok" |

响应示例
成功响应：
```json
{
  "code": 0,
  "message": "success",
  "data": "ok"
}
```

### 16.2 获取行政区划树
接口描述：获取完整的行政区划嵌套树(省→市→区县→乡镇→村)，仅返回顶层根节点，子节点通过children递归挂载。
| 接口路径 | /api/region/tree |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数：无

响应参数(data字段内，List&lt;Region&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键ID |
| parentId | Long | 父级区划ID |
| name | String | 区划名称 |
| code | String | 区划编码 |
| level | Integer | 层级：1省2市3区县4乡镇5村 |
| sort | Integer | 排序权重 |
| children | Array&lt;Region&gt; | 子级区划列表(非数据库字段，递归挂载形成树) |

### 16.3 分页查询用户列表
接口描述：按条件分页查询系统用户。
| 接口路径 | /api/user/list |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| keyword | String | query | 否 | 关键字搜索 |
| role | String | query | 否 | 角色筛选 |
| status | Integer | query | 否 | 状态筛选：1启用/0停用 |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认10 |

响应参数(data字段内，PageResult&lt;User&gt;)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;User&gt; | 用户列表，字段见下 |

User 字段说明（注意：password字段为明文存储字段，接口返回时未做脱敏过滤，前端展示需自行屏蔽该字段）
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键ID |
| username | String | 用户名(登录账号) |
| password | String | 密码(BCrypt加密存储) |
| nickname | String | 昵称 |
| role | String | 角色：admin超管/operator运营/gridman网格员 |
| phone | String | 手机号 |
| regionId | Long | 所属行政区划ID(数据权限范围) |
| status | Integer | 状态：1启用/0停用 |
| createdAt | LocalDateTime | 创建时间 |

### 16.4 新建用户
接口描述：创建一个系统用户。
| 接口路径 | /api/user |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| username | String | body | 否 | 用户名 |
| nickname | String | body | 否 | 昵称 |
| role | String | body | 否 | 角色 |
| phone | String | body | 否 | 手机号 |
| regionId | Long | body | 否 | 所属行政区划ID |
| status | Integer | body | 否 | 状态：1启用/0停用 |
| password | String | body | 否 | 登录密码(明文传输，后端加密存储) |

响应参数(data字段内)
| 参数名 | 类型 | 描述 |
|---|---|---|
| data | Long | 新建用户ID |

### 16.5 更新用户信息
接口描述：更新用户信息。
| 接口路径 | /api/user/{id} |
| 请求方法 | PUT |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 用户ID |
| (body) | User | body | 是 | 更新内容，字段同16.3(id以路径参数为准) |

响应参数：无业务数据，data为null。

### 16.6 重置用户密码
接口描述：重置指定用户的登录密码。
| 接口路径 | /api/user/{id}/reset-password |
| 请求方法 | POST |
| 请求 Content-Type | application/json |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 用户ID |
| password | String | body | 否 | 新密码，不传则body整体可为空，由后端生成默认密码 |

响应参数：无业务数据，data为null。

### 16.7 删除用户
接口描述：删除指定用户。
| 接口路径 | /api/user/{id} |
| 请求方法 | DELETE |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| id | Long | path | 是 | 用户ID |

响应参数：无业务数据，data为null。

### 16.8 分页查询操作审计日志
接口描述：分页查询系统操作审计日志，**仅管理员可访问**，非管理员调用返回403。
| 接口路径 | /api/audit-log/list |
| 请求方法 | GET |
| 请求 Content-Type | - |
| 响应 Content-Type | application/json |

请求参数
| 参数名 | 类型 | 位置(path/query/body) | 是否必须 | 描述 |
|---|---|---|---|---|
| bizType | String | query | 否 | 业务类型(精确匹配) |
| action | String | query | 否 | 操作类型(精确匹配) |
| operator | String | query | 否 | 操作人(模糊匹配) |
| page | long | query | 否 | 页码，默认1 |
| size | long | query | 否 | 每页条数，默认15 |

响应参数(data字段内，PageResult&lt;AuditLog&gt;，按id倒序)
| 参数名 | 类型 | 描述 |
|---|---|---|
| total | long | 总记录数 |
| list | Array&lt;AuditLog&gt; | 审计日志列表，字段见下 |

AuditLog 字段说明
| 参数名 | 类型 | 描述 |
|---|---|---|
| id | Long | 主键ID |
| bizType | String | 业务类型 |
| bizId | String | 业务数据ID |
| action | String | 操作类型(新增/修改/删除/审核等) |
| operator | String | 操作人 |
| detail | String | 操作详情/备注 |
| createdAt | LocalDateTime | 操作发生时间 |
