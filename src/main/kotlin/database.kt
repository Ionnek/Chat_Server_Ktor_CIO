/*
 * Copyright 2025 Ivan Borzykh
 * SPDX-License-Identifier: MIT
 */

package org.example

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object ChatRoomsTable : Table("chat_rooms") {
    val id = integer("id").autoIncrement()
    val isGroup = bool("is_group")
    val name = varchar("name", 255)
    override val primaryKey = PrimaryKey(id)
}

object MessagesTable : Table("messages") {
    val id = integer("id").autoIncrement()
    val chatid= integer("chat_id").references(ChatRoomsTable.id,onDelete = ReferenceOption.CASCADE)
    val userid= integer("user_id").references(UsersTable.id,onDelete = ReferenceOption.SET_NULL).nullable()
    val isRead= bool("is_read").default(false)
    val insertedDate = long("inserted_date")
    val text = text("text")
    override val primaryKey = PrimaryKey(id)
}

object UsersToChatrooms : Table("users_to_chat_rooms") {
    val userid =integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val chatid =integer("chat_id").references(ChatRoomsTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(userid, chatid)
}

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passHash = varchar("pass_hash", 255)
    override val primaryKey = PrimaryKey(id)
}

fun initDatabase(dbUrl: String, dbUser: String, dbPassword: String) {
    println("Connecting to database at $dbUrl")
    Database.connect(
        url = dbUrl,
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword
    )

    val flyway = Flyway.configure()
        .dataSource(dbUrl,dbUser,dbPassword)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .loggers("slf4j")
        .load()
    println("Running migrations...")
    flyway.migrate()

}