-- ====================================================================
-- 农业资源数据管理 — 水利设施 + 配套设施(含两级分类) (MySQL 5.7, utf8mb4)
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

-- ----------------------------------------------------------------
-- 水利设施 (机井/泵站/水闸/渠道/滴灌系统/喷灌系统/蓄水池)
-- audit_status: PENDING待审核 / APPROVED通过 / REJECTED退回
-- run_status: 正常 / 维修中 / 废弃 / 待改造
-- ----------------------------------------------------------------
CREATE TABLE water_facility (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  name              VARCHAR(128)  NOT NULL              COMMENT '设施名称',
  type              VARCHAR(32)   NOT NULL              COMMENT '设施类型',
  region_id         BIGINT        DEFAULT NULL,
  region_path       VARCHAR(255)  DEFAULT NULL          COMMENT '所在位置',
  lng               DECIMAL(10,6) DEFAULT NULL          COMMENT '经度',
  lat               DECIMAL(10,6) DEFAULT NULL          COMMENT '纬度',
  build_year        INT           DEFAULT NULL          COMMENT '建设年份',
  cover_area        DECIMAL(10,2) DEFAULT NULL          COMMENT '覆盖面积(亩)',
  benefit_villages  VARCHAR(255)  DEFAULT NULL          COMMENT '受益村组',
  run_status        VARCHAR(16)   NOT NULL DEFAULT '正常' COMMENT '运行状态',
  manager           VARCHAR(64)   DEFAULT NULL          COMMENT '管护责任人',
  phone             VARCHAR(20)   DEFAULT NULL,
  last_maintain_date DATE         DEFAULT NULL          COMMENT '最近维护日期',
  tech_params       VARCHAR(500)  DEFAULT NULL          COMMENT '技术参数(井深/水泵功率/出水量等)',
  audit_status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
  remark            VARCHAR(500)  DEFAULT NULL,
  is_deleted        TINYINT       NOT NULL DEFAULT 0,
  delete_reason     VARCHAR(255)  DEFAULT NULL,
  deleted_by        VARCHAR(64)   DEFAULT NULL,
  deleted_at        DATETIME      DEFAULT NULL,
  created_by        VARCHAR(64)   DEFAULT NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_type (type),
  KEY idx_run_status (run_status),
  KEY idx_audit (audit_status),
  KEY idx_region (region_id),
  KEY idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水利设施';

-- ----------------------------------------------------------------
-- 配套设施分类 (两级: 一级分类 / 二级分类)
-- ----------------------------------------------------------------
CREATE TABLE facility_category (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  parent_id   BIGINT       NOT NULL DEFAULT 0     COMMENT '父级ID, 0为一级',
  name        VARCHAR(64)  NOT NULL               COMMENT '分类名称',
  icon        VARCHAR(64)  DEFAULT NULL           COMMENT '图标',
  sort        INT          NOT NULL DEFAULT 0     COMMENT '排序权重(小靠前)',
  status      TINYINT      NOT NULL DEFAULT 1     COMMENT '1显示0隐藏',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配套设施分类';

-- ----------------------------------------------------------------
-- 配套设施 (烘干中心/仓储/育苗/农资供应点/交易市场/农机维修/冷链节点)
-- ----------------------------------------------------------------
CREATE TABLE support_facility (
  id                BIGINT        NOT NULL AUTO_INCREMENT,
  name              VARCHAR(128)  NOT NULL              COMMENT '设施名称',
  category_id       BIGINT        DEFAULT NULL          COMMENT '二级分类ID',
  region_id         BIGINT        DEFAULT NULL,
  region_path       VARCHAR(255)  DEFAULT NULL,
  lng               DECIMAL(10,6) DEFAULT NULL,
  lat               DECIMAL(10,6) DEFAULT NULL,
  service_range     VARCHAR(255)  DEFAULT NULL          COMMENT '服务范围',
  service_ability   VARCHAR(255)  DEFAULT NULL          COMMENT '服务能力(日处理量/库容/覆盖农户数等)',
  operate_status    VARCHAR(16)   NOT NULL DEFAULT '正常' COMMENT '运营状态:正常/停业/建设中',
  operate_subject   VARCHAR(16)   DEFAULT NULL          COMMENT '运营主体:企业/合作社/个体户',
  phone             VARCHAR(20)   DEFAULT NULL,
  business_hours    VARCHAR(64)   DEFAULT NULL          COMMENT '营业时间',
  qualification     VARCHAR(128)  DEFAULT NULL          COMMENT '资质认证',
  audit_status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
  remark            VARCHAR(500)  DEFAULT NULL,
  is_deleted        TINYINT       NOT NULL DEFAULT 0,
  delete_reason     VARCHAR(255)  DEFAULT NULL,
  deleted_by        VARCHAR(64)   DEFAULT NULL,
  deleted_at        DATETIME      DEFAULT NULL,
  created_by        VARCHAR(64)   DEFAULT NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_category (category_id),
  KEY idx_operate (operate_status),
  KEY idx_region (region_id),
  KEY idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配套设施';

-- ================= 种子数据 =================

-- 水利设施: 覆盖多类型/多运行状态/多审核状态
INSERT INTO water_facility
 (name, type, region_id, region_path, lng, lat, build_year, cover_area, benefit_villages, run_status, manager, phone, last_maintain_date, tech_params, audit_status, remark) VALUES
 ('太平村1号机井','机井',110,'吉林省/延边州/延吉市/太平镇/太平村',129.601200,42.944800,2016,180.00,'太平村一组、二组','正常','金永浩','13700001001','2025-03-15','井深85米, 出水量50m³/h','APPROVED',NULL),
 ('太平村2号机井','机井',110,'吉林省/延边州/延吉市/太平镇/太平村',129.603400,42.945900,2018,150.00,'太平村三组','维修中','金永浩','13700001001','2026-04-20','井深80米','APPROVED','水泵故障维修中'),
 ('红旗村东沟泵站','泵站',111,'吉林省/延边州/延吉市/太平镇/红旗村',129.612800,42.951500,2015,420.00,'红旗村全村','维修中','孙丽华','13700001002','2026-05-10','水泵功率55kW','APPROVED','撂荒治理关联: 东沟泵站损坏待修'),
 ('红旗村蓄水池','蓄水池',111,'吉林省/延边州/延吉市/太平镇/红旗村',129.614100,42.952700,2019,300.00,'红旗村一组','正常','孙丽华','13700001002','2025-08-01','容积2000m³','APPROVED',NULL),
 ('安全村滴灌系统','滴灌系统',100,'吉林省/延边州/延吉市/安全乡/安全村',129.498200,42.889100,2020,260.00,'安全村二组','正常','赵德海','13700001003','2025-09-12','覆盖260亩, 节水30%','APPROVED',NULL),
 ('安全村西渠','渠道',100,'吉林省/延边州/延吉市/安全乡/安全村',129.499900,42.888300,2012,500.00,'安全村全村','待改造','赵德海','13700001003','2024-06-01','明渠长3.2km','APPROVED','老旧渠道待硬化改造'),
 ('丰收村喷灌系统','喷灌系统',101,'吉林省/延边州/延吉市/安全乡/丰收村',129.511500,42.900800,2021,200.00,'丰收村一组','正常','陈建国','13700001004','2025-07-20','喷灌覆盖200亩','APPROVED',NULL),
 ('新农村1号水闸','水闸',112,'吉林省/延边州/延吉市/太平镇/新农村',129.625100,42.960200,2017,NULL,'新农村','正常','吴秀英','13700001005','2025-10-05','节制闸','PENDING','网格员新标注待审核'),
 ('丰收村废弃机井','机井',101,'吉林省/延边州/延吉市/安全乡/丰收村',129.509800,42.899200,2008,0.00,NULL,'废弃','陈建国','13700001004',NULL,'井深60米已干涸','APPROVED','已废弃, 待拆除');

-- 配套设施分类 (两级)
INSERT INTO facility_category (id, parent_id, name, icon, sort, status) VALUES
 (1000, 0,    '产后处理设施', 'Box',        1, 1),
 (1001, 0,    '农资供应设施', 'ShoppingCart',2, 1),
 (1002, 0,    '流通设施',     'Van',        3, 1),
 (1010, 1000, '烘干中心',     NULL, 1, 1),
 (1011, 1000, '仓储设施',     NULL, 2, 1),
 (1012, 1000, '冷链节点',     NULL, 3, 1),
 (1020, 1001, '农资供应点',   NULL, 1, 1),
 (1021, 1001, '育苗中心',     NULL, 2, 1),
 (1030, 1002, '农产品交易市场', NULL, 1, 1),
 (1031, 1002, '农机维修点',   NULL, 2, 1);

-- 配套设施
INSERT INTO support_facility
 (name, category_id, region_id, region_path, lng, lat, service_range, service_ability, operate_status, operate_subject, phone, business_hours, qualification, audit_status, remark) VALUES
 ('太平镇粮食烘干中心', 1010, 110,'吉林省/延边州/延吉市/太平镇/太平村',129.602100,42.946100,'太平镇全镇','日处理能力120吨, 热风烘干','正常','合作社','13800002001','08:00-18:00','绿色食品认证','APPROVED',NULL),
 ('红旗村仓储库', 1011, 111,'吉林省/延边州/延吉市/太平镇/红旗村',129.613200,42.951900,'红旗村及周边','库容6000立方米, 常温仓储','正常','企业','13800002002','全天','—','APPROVED',NULL),
 ('延吉冷链物流节点', 1012, 3,'吉林省/延边州/延吉市',129.508000,42.906000,'延吉市','冷藏库容3000立方米','正常','企业','13800002003','全天','HACCP认证','APPROVED',NULL),
 ('安全乡农资供应点', 1020, 100,'吉林省/延边州/延吉市/安全乡/安全村',129.497500,42.889700,'安全乡','覆盖农户800户','正常','个体户','13800002004','07:30-19:00','农药经营许可证','APPROVED',NULL),
 ('丰收村育苗中心', 1021, 101,'吉林省/延边州/延吉市/安全乡/丰收村',129.512800,42.901500,'安全乡','年育苗能力300万株, 穴盘育苗','建设中','合作社','13800002005','08:00-17:00','—','PENDING','在建, 待审核'),
 ('太平镇农产品交易市场', 1030, 110,'吉林省/延边州/延吉市/太平镇',129.600500,42.943500,'太平镇及周边乡镇','日均交易量50吨','正常','企业','13800002006','05:00-12:00','—','APPROVED',NULL),
 ('新农村农机维修点', 1031, 112,'吉林省/延边州/延吉市/太平镇/新农村',129.624800,42.959800,'太平镇','农机维修保养','停业','个体户','13800002007','—','—','APPROVED','季节性停业');
