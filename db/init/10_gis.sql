-- ====================================================================
-- GIS 进阶: 空间查询 / 服务范围绘制 / 地块几何编辑(分割合并) 配套字段
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

-- ---------------- 配套设施服务范围(面状, GeoJSON) ----------------
ALTER TABLE support_facility
  ADD COLUMN service_area LONGTEXT DEFAULT NULL COMMENT '服务范围面(GeoJSON Polygon, WGS84)' AFTER service_range,
  ADD COLUMN coverage_count INT DEFAULT NULL COMMENT '服务范围覆盖地块数(自动统计)' AFTER service_area,
  ADD COLUMN coverage_village_count INT DEFAULT NULL COMMENT '服务范围覆盖村庄数(按地块所在村去重统计)' AFTER coverage_count;

-- ---------------- 地块合并标记(不物理删除原地块, 保留可追溯) ----------------
ALTER TABLE land_parcel
  ADD COLUMN merge_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/MERGED(已合并入其他地块)' AFTER boundary,
  ADD COLUMN merged_into_code VARCHAR(32) DEFAULT NULL COMMENT '合并去向的新地块编码' AFTER merge_status;
