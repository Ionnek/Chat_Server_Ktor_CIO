CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    pass_hash VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS chat_rooms (
    id SERIAL PRIMARY KEY,
    is_group BOOLEAN NOT NULL,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS users_to_chat_rooms (
    user_id INT NOT NULL,
    chat_id INT NOT NULL,
    PRIMARY KEY (user_id, chat_id),
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room FOREIGN KEY (chat_id) REFERENCES chat_rooms(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    chat_id INT NOT NULL,
    user_id INT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    inserted_date BIGINT NOT NULL,
    text TEXT NOT NULL,
    CONSTRAINT fk_chat_id FOREIGN KEY(chat_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_id FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE SET NULL
);
