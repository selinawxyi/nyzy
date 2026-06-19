-- ====================================================================
-- 耕地利用管理 — 地块种植作物 + 耕地质量评价 (MySQL 5.7, utf8mb4)
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

-- ----------------------------------------------------------------
-- 地块种植作物 (地块级"种植日记", 按 parcel_code 关联确权地块)
-- status: VALID有效 / INVALID已标记无效
-- ----------------------------------------------------------------
CREATE TABLE planting_record (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  parcel_code         VARCHAR(32)   NOT NULL              COMMENT '关联确权地块编码',
  parcel_name         VARCHAR(128)  DEFAULT NULL,
  region_id           BIGINT        DEFAULT NULL,
  region_path         VARCHAR(255)  DEFAULT NULL,
  plant_year          INT           NOT NULL              COMMENT '种植年度',
  season              VARCHAR(16)    DEFAULT 'SPRING'     COMMENT '季节:SPRING春/SUMMER夏/AUTUMN秋',
  crop                VARCHAR(32)   NOT NULL              COMMENT '作物(水稻/玉米/大豆/小麦)',
  variety             VARCHAR(64)   DEFAULT NULL          COMMENT '品种(如吉粳88)',
  area                DECIMAL(10,2) DEFAULT NULL          COMMENT '种植面积(亩)',
  sow_date            DATE          DEFAULT NULL          COMMENT '播种日期',
  expect_harvest_date DATE          DEFAULT NULL          COMMENT '预计收获日期',
  actual_harvest_date DATE          DEFAULT NULL          COMMENT '实际收获日期(非空=已收获)',
  yield_per_mu        DECIMAL(10,2) DEFAULT NULL          COMMENT '产量(公斤/亩)',
  data_source         VARCHAR(16)   DEFAULT 'PATROL'      COMMENT '来源:REMOTE遥感/STAT统计/FARMER农户/PATROL巡查',
  reporter            VARCHAR(64)   DEFAULT NULL          COMMENT '填报人',
  report_date         DATE          DEFAULT NULL,
  status              VARCHAR(16)   NOT NULL DEFAULT 'VALID' COMMENT 'VALID/INVALID',
  remark              VARCHAR(500)  DEFAULT NULL,
  is_deleted          TINYINT       NOT NULL DEFAULT 0,
  delete_reason       VARCHAR(255)  DEFAULT NULL,
  deleted_by          VARCHAR(64)   DEFAULT NULL,
  deleted_at          DATETIME      DEFAULT NULL,
  created_by          VARCHAR(64)   DEFAULT NULL,
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parcel (parcel_code),
  KEY idx_year (plant_year),
  KEY idx_crop (crop),
  KEY idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块种植记录';

-- ----------------------------------------------------------------
-- 耕地质量评价 (地力等级 1-10 等, 参照 GB/T 33469)
-- ----------------------------------------------------------------
CREATE TABLE land_quality (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  parcel_code     VARCHAR(32)   NOT NULL              COMMENT '关联确权地块编码',
  parcel_name     VARCHAR(128)  DEFAULT NULL,
  region_id       BIGINT        DEFAULT NULL,
  region_path     VARCHAR(255)  DEFAULT NULL,
  contractor_name VARCHAR(64)   DEFAULT NULL,
  eval_year       INT           NOT NULL              COMMENT '评价年度',
  grade           INT           NOT NULL              COMMENT '地力等级 1-10 (1最好)',
  score           DECIMAL(5,2)  DEFAULT NULL          COMMENT '综合得分(百分制)',
  soil_type       VARCHAR(32)   DEFAULT NULL          COMMENT '土壤类型(黑土/棕壤/潮土/砂土)',
  organic_matter  DECIMAL(6,2)  DEFAULT NULL          COMMENT '有机质(g/kg)',
  total_n         DECIMAL(6,2)  DEFAULT NULL          COMMENT '全氮(g/kg)',
  avail_p         DECIMAL(6,2)  DEFAULT NULL          COMMENT '有效磷(mg/kg)',
  avail_k         DECIMAL(6,2)  DEFAULT NULL          COMMENT '速效钾(mg/kg)',
  ph              DECIMAL(4,2)  DEFAULT NULL          COMMENT 'pH值',
  obstacle        VARCHAR(32)   DEFAULT NULL          COMMENT '障碍因素(盐碱/瘠薄/渍涝/沙化)',
  suitable_crops  VARCHAR(128)  DEFAULT NULL          COMMENT '适宜作物',
  org             VARCHAR(128)  DEFAULT NULL          COMMENT '评价机构',
  report_file     VARCHAR(255)  DEFAULT NULL          COMMENT '评价报告附件',
  is_deleted      TINYINT       NOT NULL DEFAULT 0,
  delete_reason   VARCHAR(255)  DEFAULT NULL,
  deleted_by      VARCHAR(64)   DEFAULT NULL,
  deleted_at      DATETIME      DEFAULT NULL,
  created_by      VARCHAR(64)   DEFAULT NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_parcel (parcel_code),
  KEY idx_year (eval_year),
  KEY idx_grade (grade),
  KEY idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='耕地质量评价';

-- ================= 种子数据 =================
-- 种植记录: 多地块 × 多年度(2021-2024) × 多作物, 含连作/轮作/产量趋势样本
INSERT INTO planting_record
 (parcel_code, parcel_name, region_id, region_path, plant_year, season, crop, variety, area, sow_date, expect_harvest_date, actual_harvest_date, yield_per_mu, data_source, reporter, report_date, status, remark) VALUES
 -- JYB-T001 太平村一组2号地: 水稻连作 + 产量趋势上升
 ('JYB-T001','太平村一组2号地',110,'吉林省/延边州/延吉市/太平镇/太平村/一组',2021,'SPRING','水稻','吉粳88',24.60,'2021-05-10','2021-09-25','2021-09-28',520.00,'FARMER','金永浩','2021-05-12','VALID','连作第一年'),
 ('JYB-T001','太平村一组2号地',110,'吉林省/延边州/延吉市/太平镇/太平村/一组',2022,'SPRING','水稻','吉粳88',24.60,'2022-05-08','2022-09-22','2022-09-26',545.00,'FARMER','金永浩','2022-05-10','VALID','连作第二年'),
 ('JYB-T001','太平村一组2号地',110,'吉林省/延边州/延吉市/太平镇/太平村/一组',2023,'SPRING','玉米','先玉335',24.60,'2023-04-28','2023-10-05','2023-10-08',680.00,'PATROL','网格员李四','2023-05-01','VALID','改种玉米'),
 ('JYB-T001','太平村一组2号地',110,'吉林省/延边州/延吉市/太平镇/太平村/一组',2024,'SPRING','大豆','黑农84',24.60,'2024-05-12','2024-09-30',NULL,NULL,'STAT','统计上报','2024-05-15','VALID','轮作大豆, 用养结合'),
 -- JYA-A001 安全村一组1号地: 玉米连作
 ('JYA-A001','安全村一组1号地',100,'吉林省/延边州/延吉市/安全乡/安全村/一组',2022,'SPRING','玉米','先玉335',19.20,'2022-04-25','2022-10-02','2022-10-06',640.00,'FARMER','赵德海','2022-04-27','VALID',NULL),
 ('JYA-A001','安全村一组1号地',100,'吉林省/延边州/延吉市/安全乡/安全村/一组',2023,'SPRING','玉米','先玉335',19.20,'2023-04-26','2023-10-03','2023-10-07',620.00,'FARMER','赵德海','2023-04-28','VALID','连作产量略降'),
 ('JYA-A001','安全村一组1号地',100,'吉林省/延边州/延吉市/安全乡/安全村/一组',2024,'SPRING','玉米','京科968',19.20,'2024-04-24','2024-10-01',NULL,NULL,'REMOTE','遥感解译','2024-06-01','VALID','连作第三年'),
 -- JYA-F001 丰收村一组3号地: 小麦/大豆轮作
 ('JYA-F001','丰收村一组3号地',101,'吉林省/延边州/延吉市/安全乡/丰收村/一组',2023,'SPRING','小麦','龙麦35',15.80,'2023-03-20','2023-07-15','2023-07-18',410.00,'STAT','统计上报','2023-03-22','VALID',NULL),
 ('JYA-F001','丰收村一组3号地',101,'吉林省/延边州/延吉市/安全乡/丰收村/一组',2024,'SPRING','大豆','黑农84',15.80,'2024-05-10','2024-09-28',NULL,NULL,'FARMER','陈建国','2024-05-12','VALID','轮作'),
 -- JYB-X001 新农村一组1号地: 含一条无效记录(遥感误判)
 ('JYB-X001','新农村一组1号地',112,'吉林省/延边州/延吉市/太平镇/新农村/一组',2024,'SPRING','水稻','吉粳83',13.40,'2024-05-15','2024-09-30',NULL,NULL,'FARMER','吴秀英','2024-05-16','VALID',NULL),
 ('JYB-X001','新农村一组1号地',112,'吉林省/延边州/延吉市/太平镇/新农村/一组',2024,'SPRING','玉米','先玉335',13.40,'2024-04-20','2024-10-01',NULL,NULL,'REMOTE','遥感解译','2024-06-05','INVALID','遥感解译重复, 已标记无效'),
 -- JYB-H002 红旗村二组3号地
 ('JYB-H002','红旗村二组3号地',111,'吉林省/延边州/延吉市/太平镇/红旗村/二组',2023,'SPRING','水稻','吉粳88',11.30,'2023-05-09','2023-09-24','2023-09-27',505.00,'PATROL','网格员李四','2023-05-11','VALID',NULL);

-- 耕地质量评价: 覆盖不同地力等级/土壤类型/障碍因素
INSERT INTO land_quality
 (parcel_code, parcel_name, region_id, region_path, contractor_name, eval_year, grade, score, soil_type, organic_matter, total_n, avail_p, avail_k, ph, obstacle, suitable_crops, org) VALUES
 ('JYB-T001','太平村一组2号地',110,'吉林省/延边州/延吉市/太平镇/太平村/一组','金永浩',2024,2,88.50,'黑土',38.20,2.15,28.60,135.00,6.80,NULL,'水稻、玉米、大豆','延边州耕地质量监测站'),
 ('JYA-A001','安全村一组1号地',100,'吉林省/延边州/延吉市/安全乡/安全村/一组','赵德海',2024,3,82.30,'黑土',32.50,1.95,22.40,118.00,6.50,NULL,'玉米、大豆','延边州耕地质量监测站'),
 ('JYA-F002','丰收村二组1号地',101,'吉林省/延边州/延吉市/安全乡/丰收村/二组','张秀芳',2024,6,65.40,'砂土',18.30,1.20,12.50,78.00,5.40,'瘠薄','玉米、谷子','延边州耕地质量监测站'),
 ('JYA-A004','安全村三组2号地',100,'吉林省/延边州/延吉市/安全乡/安全村/三组','刘志成',2024,8,52.10,'潮土',14.20,0.95,9.80,65.00,8.30,'盐碱','耐盐碱作物','延边州耕地质量监测站'),
 ('JYB-H001','红旗村一组1号地',111,'吉林省/延边州/延吉市/太平镇/红旗村/一组','张秀芳',2024,1,92.10,'黑土',42.60,2.45,32.10,148.00,6.90,NULL,'水稻、玉米、大豆','延边州耕地质量监测站'),
 ('JYB-X002','新农村二组1号地',112,'吉林省/延边州/延吉市/太平镇/新农村/二组','周伟明',2023,5,70.20,'棕壤',24.80,1.55,18.20,95.00,5.90,'渍涝','水稻','延边州耕地质量监测站'),
 ('JYB-T002','太平村二组1号地',110,'吉林省/延边州/延吉市/太平镇/太平村/二组','李志远',2024,4,76.80,'黑土',28.40,1.78,20.50,108.00,6.30,NULL,'水稻、玉米','延边州耕地质量监测站');
