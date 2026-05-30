CREATE TABLE IF NOT EXISTS bookmarked_articles (
    id VARCHAR(512) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    category VARCHAR(255),
    author VARCHAR(255),
    image_url TEXT,
    published_at TIMESTAMP,
    read_time_minutes INTEGER
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'bookmarked_articles'
    ) THEN
        ALTER TABLE bookmarked_articles ALTER COLUMN id TYPE VARCHAR(512);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS user_bookmarks (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    article_id VARCHAR(512) NOT NULL REFERENCES bookmarked_articles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, article_id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = current_schema() AND table_name = 'user_bookmarks'
    ) THEN
        ALTER TABLE user_bookmarks ALTER COLUMN article_id TYPE VARCHAR(512);
    END IF;
END $$;
