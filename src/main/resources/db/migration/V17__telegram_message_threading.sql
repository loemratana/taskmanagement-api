-- Telegram threading: remember which group message announced a task / a submission, so the
-- next message about it is posted as a reply (assigned -> submitted -> approved/rejected).
-- The chat id is stored too: a reply is only possible in the same chat, and a department's
-- group can be changed later.

ALTER TABLE tasks
    ADD COLUMN telegram_chat_id    VARCHAR(64),
    ADD COLUMN telegram_message_id BIGINT;

ALTER TABLE task_submissions
    ADD COLUMN telegram_chat_id    VARCHAR(64),
    ADD COLUMN telegram_message_id BIGINT;
