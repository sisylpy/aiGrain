-- AI 会话：区分单店(STORE)与集团(GROUP)；GROUP 下可不绑定单一门店父部门。
-- 执行前请备份。若列已存在请跳过对应语句。

ALTER TABLE gb_ai_conversation
    ADD COLUMN gb_ai_conversation_scope_mode TINYINT NOT NULL DEFAULT 0
        COMMENT '0=STORE单店(父部门子树) 1=GROUP集团(dis 下全部门)'
        AFTER gb_ai_conversation_distributer_id;

ALTER TABLE gb_ai_conversation
    MODIFY COLUMN gb_ai_conversation_department_id BIGINT NULL
        COMMENT '单店模式下为门店父部门ID；集团模式可空';
