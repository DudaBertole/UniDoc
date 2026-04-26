CREATE TABLE users (
       id VARCHAR(255) NOT NULL, -- Firebase UID
       first_name VARCHAR(255) NOT NULL,
       last_name VARCHAR(255) NOT NULL,
       email VARCHAR(255) UNIQUE,
       birth_date DATE NOT NULL,
       university VARCHAR(255) NOT NULL,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       CONSTRAINT pk_users PRIMARY KEY (id)
);

-- 1 article to 1 user
CREATE TABLE articles (
          id UUID NOT NULL,
          title VARCHAR(500) NOT NULL,
          publication_date DATE NOT NULL,
          work_type VARCHAR(50) NOT NULL,
          abstract TEXT NOT NULL,
          cover_key TEXT, -- S3 object key for the cover image
          pdf_key TEXT, -- S3 object key for the PDF document
          published_by VARCHAR(255) NOT NULL, -- Firebase UID of the user who published the article
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT pk_articles PRIMARY KEY (id),
          CONSTRAINT fk_articles_owner FOREIGN KEY (published_by) REFERENCES users(id)
);

-- 1 article to N authors
CREATE TABLE article_authors (
         article_id UUID NOT NULL,
         author_name VARCHAR(255) NOT NULL,
         CONSTRAINT fk_authors_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

-- N article boost to N users
CREATE TABLE article_boosts (
        user_id VARCHAR(255) NOT NULL,
        article_id UUID NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT pk_article_boosts PRIMARY KEY (user_id, article_id), -- Ensures 1 boost per user per article
        CONSTRAINT fk_boosts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        CONSTRAINT fk_boosts_article FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE
);

-- Indexes to optimize articles search
CREATE INDEX idx_articles_title ON articles(title);
CREATE INDEX idx_articles_work_type ON articles(work_type);
CREATE INDEX idx_articles_pub_date ON articles(publication_date);