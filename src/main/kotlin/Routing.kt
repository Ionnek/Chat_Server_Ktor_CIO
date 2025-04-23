/*
 * Copyright 2025 Ivan Borzykh
 * SPDX-License-Identifier: MIT
 */

package org.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*


object RoutesModule {
        fun Application.configureRoutes(
            jwtSecret: String,
            jwtIssuer: String,
            jwtAudience: String,
            tokenValidityInMs: Long,
            roomConnectionsMap: MutableMap<Int, MutableSet<DefaultWebSocketServerSession>>,
            repo:IRepo
        ){
            val userChatroomsConnectionsMap = mutableMapOf<Int, MutableSet<WebSocketSession>>()
            routing {

                post("/auth") {

                    val receivedUser = call.receive<User>()

                    val userFromDB = repo.findUser(receivedUser)

                    if (userFromDB != null) {
                        val hashedPasswordFromDb = userFromDB.pass
                        val passwordIsValid = BCrypt.checkpw(receivedUser.pass, hashedPasswordFromDb)

                        if (passwordIsValid) {
                            val token = JWT.create()
                                .withIssuer(jwtIssuer)
                                .withAudience(jwtAudience)
                                .withClaim("username", receivedUser.name)
                                .withClaim("userId", userFromDB.id)
                                .withExpiresAt(Date(System.currentTimeMillis() + tokenValidityInMs))
                                .sign(Algorithm.HMAC256(jwtSecret))

                            call.respond(mapOf("token" to token))
                        } else {
                            call.respondText("invalid pass", status = HttpStatusCode.Unauthorized)
                        }

                    } else {
                        call.respondText("user not found", status = HttpStatusCode.Unauthorized)
                    }
                }

                authenticate("auth-jwt"){
                    get("/getmyuserdata"){
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class)?:-1
                        if(userId == -1){
                            call.respondText("user not found", status = HttpStatusCode.Unauthorized)}else{
                            val user = repo.findUserById(userId)
                            if (user != null) {
                                call.respond(user)
                            } else {
                                call.respondText("user not found", status = HttpStatusCode.NotFound)
                            }
                        }
                    }
                }

                authenticate("auth-jwt") {
                    get("/getUsers") {
                        val usersList = repo.getUsers()
                        call.respond(usersList)
                    }

                }

                post("/register") {

                    val receivedUser = call.receive<User>()
                    val hashedNewUserPass = BCrypt.hashpw(receivedUser.pass, BCrypt.gensalt())

                    repo.addUser(User(id = 0, name = receivedUser.name,
                        email = receivedUser.email, pass = hashedNewUserPass))

                    call.respondText("user added", status = HttpStatusCode.Created)

                }

                get("/ping") {
                    call.respondText("pong", ContentType.Text.Plain)
                }

                authenticate("auth-jwt") {

                    post("/createroom") {
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class) ?: error("No userId in token")
                        val userUsername = principal.getClaim("username", String::class) ?: error("No userName in token")
                        val targetUser = call.receive<PublicUser>()

                        repo.createRoom(userId,userUsername,targetUser)

                        val involvedUsers = listOf(userId, targetUser.id)
                        involvedUsers.forEach { uid ->

                            val updatedRooms = repo.getRooms(uid)
                            val updateMessage = Json.encodeToString(updatedRooms)

                            userChatroomsConnectionsMap[uid]?.forEach { session ->
                                session.send(updateMessage)
                            }
                        }
                    }

                }

                authenticate("auth-jwt") {
                    get("/getrooms") {
                        val logger = call.application.environment.log

                        try {
                            logger.info("resieved GET /getrooms")

                            val principal = call.principal<JWTPrincipal>()
                            if (principal == null) {
                                logger.error("error JWTPrincipal not found")
                                call.respond(HttpStatusCode.Unauthorized, "token not found")
                                return@get
                            }

                            val userId = try {
                                principal.getClaim("userId", Int::class)
                            } catch (e: Exception) {
                                logger.error("user not found: ${e.message}", e)
                                call.respond(HttpStatusCode.Unauthorized, "invalid token")
                                return@get
                            }

                            if (userId == null) {
                                logger.error("user not found in token")
                                call.respond(HttpStatusCode.Unauthorized, "user not found in token")
                                return@get
                            }

                            logger.info("Recieved user id: $userId. begin receiving chatrooms.")

                            val roomList = transaction {
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

                            logger.info("found ${roomList.size} rooms for userId: $userId")
                            call.respond(roomList)
                        } catch (e: Exception) {
                            // Логирование общей ошибки с трассировкой
                            logger.error("error  GET /getrooms: ${e.localizedMessage}", e)
                            call.respond(HttpStatusCode.InternalServerError, "internal server error")
                        }
                    }
                }


                authenticate("auth-jwt") {
                    post("/postmessage") {
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class) ?: error("No userId in token")
                        val message = call.receive<Message>()
                        repo.addMessage(message)
                        call.respond(HttpStatusCode.Created, "Message posted")
                    }
                }

                authenticate("auth-jwt") {
                    get("/getroomdata/{chatRoomId}") {
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class) ?: error("No userId in token")

                        val chatRoomId = call.parameters["chatRoomId"]?.toIntOrNull()
                        if (chatRoomId == null) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid or missing chatRoomId")
                            return@get
                        }

                        val isUserInRoom = repo.checkUserInRoom(chatRoomId=chatRoomId,userId=userId)

                        if (!isUserInRoom) {
                            call.respond(HttpStatusCode.Forbidden, "You are not a member of this room")
                            return@get
                        }

                        val usersList= repo.getRoomUserList(chatRoomId)
                        val messagesList = repo.getRoomMessageList(chatRoomId)

                        call.respond(RoomData(users = usersList, messages = messagesList))
                    }
                }
                authenticate("auth-jwt"){
                    post("/deleteroom"){
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class)?: error("No userId in token")
                        val roomRequest = call.receive<RoomRequest>()
                        transaction {
                            ChatRoomsTable.deleteWhere{
                                (id eq roomRequest.roomId)
                            }
                        }
                        call.respond(HttpStatusCode.OK, "Room deleted")
                    }
                }
                authenticate("auth-jwt"){
                    post("/deleteuser"){
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class)?: error("No userId in token")
                        repo.deleteUser(userId)
                        call.respond(HttpStatusCode.OK, "User deleted")
                    }
                }

                authenticate("auth-jwt"){
                    post("/deletemessage"){
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class)?: error("No userId in token")
                        val message = call.receive<Message>()
                        repo.deleteMessage(message)
                        call.respond(HttpStatusCode.OK, "Message deleted")
                    }
                }
                authenticate("auth-jwt") {
                    webSocket("/ws/userchatrooms") {
                        val logger = call.application.environment.log
                        val principal = call.principal<JWTPrincipal>()
                        val userId = principal?.getClaim("userId", Int::class)
                            ?:kotlin.run{
                                logger.info("User ivalid")
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalidated User"))
                                return@webSocket
                            }
                        userChatroomsConnectionsMap.getOrPut(userId) { mutableSetOf() } += this
                        try {
                            val roomList = repo.getRooms(userId)
                            logger.info("found ${roomList.chatRoomList.size} rooms for userId: $userId")
                            send(Json.encodeToString(roomList))
                            for (frame in incoming) {
                                when(frame) {
                                    is Frame.Text -> {
                                    }
                                    else -> {

                                    }
                                }
                            }
                        } catch (e: ClosedReceiveChannelException) {
                        } catch (e: Throwable) {
                        } finally {
                            userChatroomsConnectionsMap[userId]?.remove(this)
                            if (userChatroomsConnectionsMap[userId]?.isEmpty() == true) {
                                userChatroomsConnectionsMap.remove(userId)
                            }
                        }
                    }
                }
                authenticate("auth-jwt"){
                    webSocket("/ws/{chatRoomId}"){

                        val logger = call.application.environment.log
                        val principal = call.principal<JWTPrincipal>()

                        val userId = principal?.getClaim("userId", Int::class)
                            ?:kotlin.run{
                                logger.info("User invalid")
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalidated User"))
                                return@webSocket
                            }

                        val chatRoomId =call.parameters["chatRoomId"]?.toIntOrNull()

                        if(chatRoomId==null){
                            logger.info("User $userId has invalid id")
                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid chatroom id"))
                        }

                        val isUserInRoom = repo.checkUserInRoom(chatRoomId!!,userId)

                        if (!isUserInRoom) {
                            logger.info("User $userId not the member of this room")
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "You are not a member of this room"))
                            return@webSocket
                        }

                        val connections = roomConnectionsMap.getOrPut(chatRoomId) { mutableSetOf() }

                        connections += this
                        logger.info("User $userId connected")
                        try {
                            send("You are connected to room $chatRoomId")
                            logger.info("User $userId connected to $chatRoomId")
                            for (frame in incoming) {
                                logger.info("User $userId send $frame")
                                when (frame) {
                                    is Frame.Text -> {
                                        val receivedText = frame.readText()
                                        val incomingMessage = try {
                                            Json.decodeFromString<Message>(receivedText)
                                        } catch (e: Exception) {
                                            continue
                                        }
                                        repo.addMessage(message = incomingMessage)
                                        logger.info("User $userId inserted message $incomingMessage")

                                        val messageToSend = Json.encodeToString(incomingMessage)
                                        connections.forEach { session ->
                                            session.send(messageToSend)
                                        }
                                    }
                                    else -> {
                                        logger.info("User $userId sended invalid type of message")
                                    }
                                }
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            logger.info("User $userId not connected error")
                        } catch (e: Throwable) { logger.info("User $userId not connected error")
                        } finally {

                            connections -= this

                            if (connections.isEmpty()) {
                                roomConnectionsMap.remove(chatRoomId)
                            }
                        }
                    }
                }
            }
        }
}