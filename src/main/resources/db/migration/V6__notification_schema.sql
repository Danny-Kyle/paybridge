CREATE TABLE notification (
                              id          UUID PRIMARY KEY,
                              customer_id UUID         NOT NULL,
                              channel     VARCHAR(10)  NOT NULL,
                              type        VARCHAR(30)  NOT NULL,
                              subject     VARCHAR(200) NOT NULL,
                              body        TEXT         NOT NULL,
                              status      VARCHAR(10)  NOT NULL,
                              created_at  TIMESTAMPTZ  NOT NULL,
                              updated_at  TIMESTAMPTZ  NOT NULL,
                              version     BIGINT       NOT NULL DEFAULT 0
);