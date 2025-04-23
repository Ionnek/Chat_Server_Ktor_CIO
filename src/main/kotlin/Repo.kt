/*
 * Copyright 2025 Ivan Borzykh
 * SPDX-License-Identifier: MIT
 */

package org.example

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

interface IRepo{
    fun findUser(receivedUser:User):User?
    fun findUserById(userId:Int):PublicUser?
    fun getUsers():List<PublicUser>
    fun createRoom(userId: Int,mainUserName:String,targetUser: PublicUser)
    fun getRooms(userId:Int):ChatRoomList
    fun addUser(receivedUser:User)
    fun addMessage(message: Message)
    fun getRoomUserList(chatRoomId: Int):List<PublicUser>
    fun getRoomMessageList(chatRoomId: Int):List<Message>
    fun checkUserInRoom(chatRoomId:Int,userId: Int):Boolean
    fun deleteRoom(roomId: Int)
    fun deleteUser(userId: Int)
    fun deleteMessage(message: Message)
}

class RepoReal:IRepo{
    override fun findUser(receivedUser:User):User? {
        val userRow = transaction {
            UsersTable.select { UsersTable.name eq receivedUser.name }
                .singleOrNull()
        }
        if (userRow!=null){
        val user=User(id=userRow[UsersTable.id], name = userRow[UsersTable.name],
            pass = userRow[UsersTable.passHash], email = userRow[UsersTable.email])
            return user
        }else
        return null
    }
    override fun addUser(receivedUser:User){
        transaction {
            UsersTable.insert {
                it[name] = receivedUser.name
                it[email] = receivedUser.email
                it[passHash] = receivedUser.pass
            }
        }
    }

    override fun findUserById(userId: Int):PublicUser? {
        val result = transaction {
            UsersTable
                .select { UsersTable.id eq userId }
                .singleOrNull()
                ?.let {
                    PublicUser(
                        id = it[UsersTable.id],
                        name = it[UsersTable.name]
                    )
                }
        }
        return result
    }

    override fun getUsers(): List<PublicUser> {
        val result = transaction {
            UsersTable.selectAll().map {
                PublicUser(
                    id = it[UsersTable.id],
                    name = it[UsersTable.name],
                )
            }
        }
        return result
    }

    override fun deleteUser(userId: Int) {
        transaction {
            UsersTable.deleteWhere{
                (id eq userId)
            }
        }
    }

    override fun getRooms(userId:Int): ChatRoomList {
        val result = transaction {
            (UsersToChatrooms innerJoin ChatRoomsTable)
                .select { UsersToChatrooms.userid eq userId }
                .map {
                    ChatRoom(
                        id = it[ChatRoomsTable.id],
                        isGroup = it[ChatRoomsTable.isGroup],
                        name = it[ChatRoomsTable.name]
                    )
                }
        }
        return ChatRoomList(result)
    }

    override fun checkUserInRoom(chatRoomId: Int, userId: Int): Boolean {
        return transaction {
            UsersToChatrooms
                .select {
                    (UsersToChatrooms.chatid eq chatRoomId) and
                            (UsersToChatrooms.userid eq userId)
                }
                .any()
        }
    }

    override fun getRoomMessageList(chatRoomId: Int): List<Message> {
        return transaction {
            MessagesTable
                .select { MessagesTable.chatid eq chatRoomId }
                .map {
                    Message(
                        id = it[MessagesTable.id],
                        userid = it[MessagesTable.userid] ?: -1,
                        chatId = it[MessagesTable.chatid],
                        isRead = it[MessagesTable.isRead],
                        insertedDate = it[MessagesTable.insertedDate],
                        text = it[MessagesTable.text]
                    )
                }
        }
    }

    override fun getRoomUserList(chatRoomId: Int): List<PublicUser> {
        return transaction {
            (UsersToChatrooms innerJoin UsersTable)
                .select { UsersToChatrooms.chatid eq chatRoomId }
                .map {
                    PublicUser(
                        id = it[UsersTable.id],
                        name = it[UsersTable.name]
                    )
                }
        }
    }

    override fun deleteRoom(roomId: Int) {
        transaction {
            ChatRoomsTable.deleteWhere{
                (id eq roomId)
            }
        }
    }

    override fun createRoom(userId: Int,mainUserName:String,targetUser: PublicUser) {
        var newChatRoomId:Int
        transaction {
            newChatRoomId = ChatRoomsTable.insert {
                it[isGroup] = false
                it[name] = "chat ${mainUserName} with ${targetUser.name}"
            } get ChatRoomsTable.id
            UsersToChatrooms.insert {
                it[userid] = userId
                it[chatid] = newChatRoomId
            }
            UsersToChatrooms.insert {
                it[userid] = targetUser.id
                it[chatid] = newChatRoomId
            }
        }
    }

    override fun addMessage(message: Message) {
        transaction {
            MessagesTable.insert {
                it[chatid] = message.chatId
                it[userid] = message.userid
                it[isRead] = false
                it[insertedDate] = System.currentTimeMillis()
                it[text] = message.text
            }

        }
    }

    override fun deleteMessage(message: Message) {
        transaction {
            MessagesTable.deleteWhere{
                (id eq message.id)
            }
        }
    }
}

class RepoFake:IRepo{
    val userList= mutableListOf<User>()
    val messageList= mutableListOf<Message>()
    val chatRoomList= mutableListOf<ChatRoom>()
    val usersToChatRoomList= mutableListOf<UsersToChatRoom>()

    override fun findUser(receivedUser:User):User? {
        val user=userList.find { user:User-> receivedUser.name==user.name }
        return user
    }

    override fun findUserById(userId: Int): PublicUser? {
        val user=userList.find { user:User-> userId==user.id }
        if (user != null) {
            return PublicUser(user.id,user.name)
        }
        return null
    }

    override fun getUsers(): List<PublicUser> {
        val PublicUserLust= mutableListOf<PublicUser>()
        for(user in userList){
            PublicUserLust.add(PublicUser(user.id,user.name))
        }
        return PublicUserLust
    }

    override fun createRoom(userId: Int, mainUserName: String, targetUser: PublicUser) {
        val roomId=chatRoomList.size+1
        chatRoomList.add(ChatRoom(roomId,false, "$userId $targetUser"))
        usersToChatRoomList.add(UsersToChatRoom(userid=userId,chatid=roomId))
        usersToChatRoomList.add(UsersToChatRoom(userid=targetUser.id,chatid=roomId))
    }

    override fun getRooms(userId: Int): ChatRoomList {
        val chatRoomIds= mutableListOf<Int>()
        val resultRoomList= mutableListOf<ChatRoom>()
        for(chaToRoom in usersToChatRoomList){
            if(chaToRoom.userid==userId){
                chatRoomIds.add(chaToRoom.chatid)
            }
        }
        for(chat in chatRoomList){
            if(chat.id in chatRoomIds){
                resultRoomList.add(chat)
            }
        }
        return ChatRoomList(resultRoomList)
    }

    override fun addUser(receivedUser:User){
        val id=userList.size+1
        userList.add(receivedUser.copy(id=id))
    }

    override fun addMessage(message: Message) {
        val id=messageList.size+1
        messageList.add(message.copy(id=id))
    }

    override fun getRoomUserList(chatRoomId: Int): List<PublicUser> {
        val userListId= mutableListOf<Int>()
        val resultUserList= mutableListOf<PublicUser>()
        for (userToRoom in usersToChatRoomList){
            if(userToRoom.chatid==chatRoomId){
                userListId.add(userToRoom.userid)
            }
        }
        for (user in userList){
            if(user.id in userListId){
                resultUserList.add(PublicUser(id=user.id, name = user.name))
            }
        }
        return resultUserList
    }

    override fun getRoomMessageList(chatRoomId: Int): List<Message> {
        return messageList.filter { it.chatId==chatRoomId }
    }

    override fun checkUserInRoom(chatRoomId: Int, userId: Int): Boolean {
        return usersToChatRoomList.contains(UsersToChatRoom(userid=userId,chatid=chatRoomId))
    }

    override fun deleteRoom(roomId: Int) {
        chatRoomList.removeIf{it.id==roomId}
    }

    override fun deleteUser(userId: Int) {
        userList.removeIf{it.id==userId}
    }

    override fun deleteMessage(message: Message) {
        messageList.remove(message)
    }

}