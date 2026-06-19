-- ====================================================================
-- 土地资源数据 — 确权地块扩展 + 版本历史 + 地块标注 (MySQL 5.7, utf8mb4)
-- land_parcel 表已在 01_schema.sql 创建, 此处扩展字段并新增关联表
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

-- ---------------- 扩展 land_parcel: 地块四至 + 软删除 ----------------
ALTER TABLE land_parcel
  ADD COLUMN bound_east   VARCHAR(64)  DEFAULT NULL COMMENT '东至' AFTER land_use,
  ADD COLUMN bound_south  VARCHAR(64)  DEFAULT NULL COMMENT '南至' AFTER bound_east,
  ADD COLUMN bound_west   VARCHAR(64)  DEFAULT NULL COMMENT '西至' AFTER bound_south,
  ADD COLUMN bound_north  VARCHAR(64)  DEFAULT NULL COMMENT '北至' AFTER bound_west,
  ADD COLUMN remark        VARCHAR(500) DEFAULT NULL COMMENT '备注' AFTER bound_north,
  ADD COLUMN is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除',
  ADD COLUMN delete_reason VARCHAR(255) DEFAULT NULL,
  ADD COLUMN deleted_by    VARCHAR(64)  DEFAULT NULL,
  ADD COLUMN deleted_at    DATETIME     DEFAULT NULL,
  ADD COLUMN updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  ADD KEY idx_deleted (is_deleted);

-- ---------------- 确权地块变更历史 (版本回溯 A1.5) ----------------
CREATE TABLE land_parcel_history (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  parcel_id     BIGINT       NOT NULL,
  parcel_code   VARCHAR(32)  NOT NULL,
  version       INT          NOT NULL              COMMENT '版本号(每地块自增)',
  change_type   VARCHAR(16)  NOT NULL              COMMENT 'CREATE/UPDATE/DELETE',
  change_fields VARCHAR(500) DEFAULT NULL          COMMENT '变更字段摘要',
  snapshot      LONGTEXT                           COMMENT '该版本地块快照(JSON)',
  operator      VARCHAR(64)  DEFAULT NULL,
  reason        VARCHAR(500) DEFAULT NULL          COMMENT '变更原因',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parcel (parcel_id),
  KEY idx_code (parcel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='确权地块变更历史';

-- ---------------- 地块标注 (A1.6) ----------------
-- type: TEXT文字 / COLOR颜色 / TAG标签
-- visible_scope: SELF仅自己 / ALL所有人 (MVP 两级)
CREATE TABLE land_annotation (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  parcel_id     BIGINT       NOT NULL,
  parcel_code   VARCHAR(32)  NOT NULL,
  type          VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/COLOR/TAG',
  content       VARCHAR(500) DEFAULT NULL          COMMENT '文字内容',
  color         VARCHAR(16)  DEFAULT NULL          COMMENT '颜色标记',
  tags          VARCHAR(255) DEFAULT NULL          COMMENT '标签(逗号分隔)',
  visible_scope VARCHAR(16)  NOT NULL DEFAULT 'SELF' COMMENT 'SELF/ALL',
  owner_id      BIGINT       DEFAULT NULL,
  owner_name    VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parcel (parcel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块标注';

-- ================= 种子数据 =================
-- 为已有地块补充四至信息 (示例)
UPDATE land_parcel SET bound_east='东邻水渠', bound_south='南接机耕路', bound_west='西邻李家地', bound_north='北至防护林'
  WHERE parcel_code IN ('JYA-F002','JYB-T002','JYB-H001');

-- 确权地块历史 (示例: 几块地的初始建档 + 变更记录)
INSERT INTO land_parcel_history (parcel_id, parcel_code, version, change_type, change_fields, snapshot, operator, reason) VALUES
 ((SELECT id FROM land_parcel WHERE parcel_code='JYB-T002'), 'JYB-T002', 1, 'CREATE', '初始建档', '{"contractorName":"李志远","area":18.20,"landUse":"基本农田"}', 'admin', '确权成果数据导入'),
 ((SELECT id FROM land_parcel WHERE parcel_code='JYB-T002'), 'JYB-T002', 2, 'UPDATE', '承包方姓名: 李志-> 李志远', '{"contractorName":"李志远","area":18.20,"landUse":"基本农田"}', 'operator', '更正承包方姓名录入错误'),
 ((SELECT id FROM land_parcel WHERE parcel_code='JYA-F002'), 'JYA-F002', 1, 'CREATE', '初始建档', '{"contractorName":"张秀芳","area":31.00,"landUse":"基本农田"}', 'admin', '确权成果数据导入');

-- 地块标注 (示例)
INSERT INTO land_annotation (parcel_id, parcel_code, type, content, color, tags, visible_scope, owner_id, owner_name) VALUES
 ((SELECT id FROM land_parcel WHERE parcel_code='JYA-F002'), 'JYA-F002', 'TEXT', '该地块土壤贫瘠, 需重点监测撂荒', 'red', '低产田,需关注', 'ALL', 1, '系统管理员'),
 ((SELECT id FROM land_parcel WHERE parcel_code='JYB-T002'), 'JYB-T002', 'TAG', NULL, 'green', '高产田,流转田', 'ALL', 2, '运营张三'),
 ((SELECT id FROM land_parcel WHERE parcel_code='JYB-H001'), 'JYB-H001', 'TEXT', '计划改种有机水稻', 'blue', '示范田', 'SELF', 2, '运营张三');
