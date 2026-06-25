-- ====================================================================
-- 并发控制: 给核心可编辑表加乐观锁版本号, 防止两人同时编辑互相覆盖
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

ALTER TABLE land_parcel      ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
ALTER TABLE water_facility   ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
ALTER TABLE support_facility ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
ALTER TABLE planting_record  ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
ALTER TABLE land_quality     ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
ALTER TABLE abandon_parcel   ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号';
