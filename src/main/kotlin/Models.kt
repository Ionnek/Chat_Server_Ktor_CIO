/*
 * Copyright 2025 Ivan Borzykh
 * SPDX-License-Identifier: MIT
 */

package org.example

import kotlinx.serialization.Serializable

@Serializable
data class ChatRoom (
    val id:Int,
    val isGroup:Boolean,
    val name:String
)

@Serializable
data class Message (
    val id:Int,
    val userid:Int,
    val chatId:Int,
    val isRead:Boolean,
    val insertedDate:Long,
    val text:String,
)

@Serializable
data class TokenResponse(val token: String)

@Serializable
data class User(
    val id:Int,
    val name:String,
    val email:String,
    val pass:String
)

@Serializable
data class PublicUser(
    val id:Int,
    val name:String
)

@Serializable
data class ChatRoomList(
    val chatRoomList: List<ChatRoom>
)
@Serializable
data class UsersToChatRoom(
    val userid:Int,
    val chatid:Int
)

@Serializable
data class RoomData(
    val users: List<PublicUser>,
    val messages: List<Message>
)

@Serializable
data class RoomRequest(
    val roomId:Int
)
