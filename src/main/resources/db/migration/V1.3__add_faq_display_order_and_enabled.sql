-- Add display_order and is_enabled columns to faq table
ALTER TABLE faq
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 COMMENT '显示顺序（越小越靠前）' AFTER category,
    ADD COLUMN is_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用' AFTER display_order;

-- Add index for common queries
CREATE INDEX idx_enabled_order ON faq (is_enabled, display_order);
