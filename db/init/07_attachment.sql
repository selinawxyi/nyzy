-- ====================================================================
-- 通用附件表 (各业务模块共用: 照片/报告/合同扫描件等)
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

CREATE TABLE sys_attachment (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  biz_type      VARCHAR(32)  NOT NULL              COMMENT '业务类型(abandon/parcel/water/support/planting/quality)',
  biz_id        BIGINT       NOT NULL              COMMENT '业务记录ID',
  file_name     VARCHAR(255) NOT NULL              COMMENT '原始文件名',
  stored_name   VARCHAR(128) NOT NULL              COMMENT '存储文件名(UUID)',
  content_type  VARCHAR(128) DEFAULT NULL,
  file_size     BIGINT       DEFAULT NULL          COMMENT '字节',
  uploaded_by   VARCHAR(64)  DEFAULT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用附件';
