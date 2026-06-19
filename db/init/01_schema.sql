-- ====================================================================
-- 农业资源数字化管理平台 — 数据库结构 (MySQL 5.7, utf8mb4)
-- MVP 范围: 系统(用户/角色/区划) + 土地资源(确权地块) + 撂荒管理(地块/原因/任务)
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

-- ----------------------------------------------------------------
-- 行政区划字典 (自关联五级: 省/市/区县/乡镇/村组)
-- ----------------------------------------------------------------
CREATE TABLE sys_region (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  parent_id   BIGINT       NOT NULL DEFAULT 0     COMMENT '父级ID, 0为根',
  name        VARCHAR(64)  NOT NULL               COMMENT '区划名称',
  code        VARCHAR(32)  NOT NULL               COMMENT '区划编码',
  level       TINYINT      NOT NULL               COMMENT '层级:1省2市3区县4乡镇5村6组',
  sort        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id),
  KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划';

-- ----------------------------------------------------------------
-- 用户 / 角色
-- ----------------------------------------------------------------
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  username    VARCHAR(64)  NOT NULL               COMMENT '登录名',
  password    VARCHAR(128) NOT NULL               COMMENT 'BCrypt密码',
  nickname    VARCHAR(64)  NOT NULL               COMMENT '显示名',
  role        VARCHAR(32)  NOT NULL DEFAULT 'operator' COMMENT '角色:admin超管/operator运营/gridman网格员',
  phone       VARCHAR(20)  DEFAULT NULL,
  region_id   BIGINT       DEFAULT NULL           COMMENT '所属区划(数据权限)',
  status      TINYINT      NOT NULL DEFAULT 1      COMMENT '1启用0停用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- ----------------------------------------------------------------
-- 确权地块 (数据底座, 被各业务模块按 parcel_code 关联)
-- ----------------------------------------------------------------
CREATE TABLE land_parcel (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  parcel_code     VARCHAR(32)   NOT NULL              COMMENT '地块编码(唯一)',
  name            VARCHAR(128)  NOT NULL              COMMENT '地块名称',
  region_id       BIGINT        DEFAULT NULL          COMMENT '所属村组区划',
  region_path     VARCHAR(255)  DEFAULT NULL          COMMENT '坐落位置文本(省/市/.../组)',
  contractor_name VARCHAR(64)   DEFAULT NULL          COMMENT '承包方姓名',
  contractor_code VARCHAR(64)   DEFAULT NULL          COMMENT '承包方编码',
  area            DECIMAL(10,2) DEFAULT NULL          COMMENT '确权面积(亩)',
  land_use        VARCHAR(32)   DEFAULT NULL          COMMENT '地块用途',
  center_lng      DECIMAL(10,6) DEFAULT NULL          COMMENT '中心点经度',
  center_lat      DECIMAL(10,6) DEFAULT NULL          COMMENT '中心点纬度',
  boundary        LONGTEXT                            COMMENT '边界GeoJSON',
  contract_start  DATE          DEFAULT NULL,
  contract_end    DATE          DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_parcel_code (parcel_code),
  KEY idx_region (region_id),
  KEY idx_contractor (contractor_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='确权地块';

-- ----------------------------------------------------------------
-- 撂荒地块
-- 状态机: PENDING待审核 -> UNGOVERNED未治理 -> GOVERNING治理中 -> GOVERNED已治理 ; REJECTED已驳回
-- ----------------------------------------------------------------
CREATE TABLE abandon_parcel (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  parcel_code     VARCHAR(32)   NOT NULL              COMMENT '关联确权地块编码',
  parcel_name     VARCHAR(128)  DEFAULT NULL          COMMENT '地块名称(冗余便于展示)',
  region_id       BIGINT        DEFAULT NULL,
  region_path     VARCHAR(255)  DEFAULT NULL          COMMENT '坐落位置',
  abandon_year    INT           DEFAULT NULL          COMMENT '撂荒年份',
  area            DECIMAL(10,2) DEFAULT NULL          COMMENT '撂荒面积(亩)',
  partial_ratio   DECIMAL(5,2)  DEFAULT NULL          COMMENT '占确权地块面积比例(%)',
  degree          VARCHAR(16)   DEFAULT 'FULL'        COMMENT '撂荒程度:FULL完全/PARTIAL部分',
  start_time      DATE          DEFAULT NULL          COMMENT '撂荒起始时间',
  found_date      DATE          DEFAULT NULL          COMMENT '发现日期',
  source          VARCHAR(16)   DEFAULT 'REMOTE'      COMMENT '来源:REMOTE遥感/PATROL巡查/REPORT举报',
  reason_type     VARCHAR(16)   DEFAULT NULL          COMMENT '撂荒原因大类:LABOR/ECON/INFRA/SOIL/DISASTER/TRANSFER/OTHER',
  reason_text     VARCHAR(64)   DEFAULT NULL          COMMENT '撂荒原因(展示文本)',
  reporter        VARCHAR(64)   DEFAULT NULL          COMMENT '上报人',
  govern_status   VARCHAR(16)   NOT NULL DEFAULT 'UNGOVERNED' COMMENT '治理状态',
  manager         VARCHAR(64)   DEFAULT NULL          COMMENT '治理责任人',
  remark          VARCHAR(500)  DEFAULT NULL,
  is_deleted      TINYINT       NOT NULL DEFAULT 0    COMMENT '软删除标记',
  delete_reason   VARCHAR(255)  DEFAULT NULL,
  deleted_by      VARCHAR(64)   DEFAULT NULL,
  deleted_at      DATETIME      DEFAULT NULL,
  created_by      VARCHAR(64)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parcel_code (parcel_code),
  KEY idx_govern_status (govern_status),
  KEY idx_region (region_id),
  KEY idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='撂荒地块';

-- ----------------------------------------------------------------
-- 撂荒原因填报
-- ----------------------------------------------------------------
CREATE TABLE abandon_reason (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  abandon_id      BIGINT        NOT NULL             COMMENT '撂荒地块ID',
  reason_types    VARCHAR(128)  DEFAULT NULL         COMMENT '原因大类(逗号分隔多选)',
  detail          VARCHAR(500)  DEFAULT NULL         COMMENT '详细说明',
  suggestion      VARCHAR(500)  DEFAULT NULL         COMMENT '建议措施',
  reporter        VARCHAR(64)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_abandon (abandon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='撂荒原因填报';

-- ----------------------------------------------------------------
-- 撂荒治理任务
-- 状态机: ISSUED已下发 -> HANDLING办理中 -> ACCEPTING待验收 -> DONE验收通过 / RETURNED退回
-- ----------------------------------------------------------------
CREATE TABLE abandon_task (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  task_no         VARCHAR(64)   NOT NULL             COMMENT '任务编号',
  name            VARCHAR(128)  NOT NULL             COMMENT '任务名称',
  abandon_id      BIGINT        NOT NULL             COMMENT '撂荒地块ID',
  parcel_code     VARCHAR(32)   DEFAULT NULL,
  description     VARCHAR(500)  DEFAULT NULL         COMMENT '任务描述',
  resp_unit       VARCHAR(128)  DEFAULT NULL         COMMENT '责任单位',
  resp_person     VARCHAR(64)   DEFAULT NULL         COMMENT '责任人',
  target_area     DECIMAL(10,2) DEFAULT NULL         COMMENT '治理面积目标(亩)',
  standard        VARCHAR(255)  DEFAULT NULL         COMMENT '治理标准',
  deadline        DATE          DEFAULT NULL         COMMENT '要求完成时限',
  progress        INT           NOT NULL DEFAULT 0   COMMENT '进度百分比',
  task_status     VARCHAR(16)   NOT NULL DEFAULT 'ISSUED' COMMENT '任务状态',
  created_by      VARCHAR(64)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_abandon (abandon_id),
  KEY idx_status (task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='撂荒治理任务';

-- ----------------------------------------------------------------
-- 操作审计日志
-- ----------------------------------------------------------------
CREATE TABLE sys_audit_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  biz_type    VARCHAR(32)  DEFAULT NULL,
  biz_id      VARCHAR(64)  DEFAULT NULL,
  action      VARCHAR(32)  DEFAULT NULL              COMMENT 'CREATE/UPDATE/DELETE/STATUS',
  operator    VARCHAR(64)  DEFAULT NULL,
  detail      VARCHAR(1000) DEFAULT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';
