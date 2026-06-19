-- ====================================================================
-- 站内信通知
-- ====================================================================
SET NAMES utf8mb4;
USE nyzy;

CREATE TABLE sys_notification (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  recipient   VARCHAR(64)  NOT NULL              COMMENT '接收人用户名',
  title       VARCHAR(128) NOT NULL,
  content     VARCHAR(500) DEFAULT NULL,
  biz_type    VARCHAR(32)  DEFAULT NULL          COMMENT '关联业务类型',
  biz_id      BIGINT       DEFAULT NULL          COMMENT '关联业务ID',
  is_read     TINYINT      NOT NULL DEFAULT 0    COMMENT '0未读1已读',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_recipient (recipient, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内信通知';

-- 示例: 给 admin 几条
INSERT INTO sys_notification (recipient, title, content, biz_type, biz_id, is_read) VALUES
 ('admin', '新撂荒地块待审核', '丰收村二组1号地(JYA-F002)由 test-invalid-user 上报，请及时核查', 'abandon', 1, 0),
 ('admin', '新设施标注待审核', '新农村1号水闸 已提交标注，待审核', 'water', 8, 0);
