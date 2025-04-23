/*
 * Copyright 2025 Ivan Borzykh
 * SPDX-License-Identifier: MIT
 */

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyApplicationTest {
    val user = User(name = "testUser", pass = "testPass", email = "testEmail", id = 0)
    val user2 = User(name = "testUser2", pass = "testPass2", email = "testEmail2", id = 0)
    val publicUser=PublicUser(name = "testUser",id = 1)
    val publicUser2=PublicUser(name = "testUser2",id = 2)
    val repo:IRepo=RepoFake()
    var updatedRoomList:ChatRoomList=ChatRoomList(chatRoomList = emptyList())

    @Test
    fun fullRoutingTest(): Unit = testApplication {
        application {
            module(
                repo = repo,
                dbUrl = "testUrl",
                dbUser = "testUser",
                dbPassword = "testPass",
                jwtSecret = "testSecret",
                jwtIssuer = "testIssuer",
                jwtAudience = "testAudience",
                tokenValidityInMs = 9999
            )
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
        }

        val registerResponse: HttpResponse = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status,"User not created")

        val register2Response: HttpResponse = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(user2)
        }

        assertEquals(HttpStatusCode.Created, register2Response.status,"User not created")

        val authResponse = client.post("/auth") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }

        assertEquals(HttpStatusCode.OK, authResponse.status,"User autorized")

        val tokenResponse = authResponse.body<TokenResponse>()

        assertTrue(tokenResponse.token.isNotBlank(),"Token is empty")

        val userResponse = client.get("/getmyuserdata") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${tokenResponse.token}")
        }

        val userDataResponse = userResponse.body<PublicUser>()

        assertTrue(userDataResponse.name==user.name, "User data correct")

        val getUsersResponse = client.get("/getUsers") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${tokenResponse.token}")
        }
        val userList = getUsersResponse.body<List<PublicUser>>()
        assertTrue(
            userList.containsAll(listOf(publicUser, publicUser2)),
            "User list is not contain right elements"
        )
        val session = client.webSocketSession {
            url("/ws/userchatrooms")
            header("Authorization", "Bearer ${tokenResponse.token}")
        }
        try {
            val initialFrame = session.incoming.receive() as? Frame.Text
                ?: error("Expected Frame.Text")

            println("Initial frame: ${initialFrame.readText()}")

            client.post("/createroom") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${tokenResponse.token}")
                setBody(publicUser2)
            }

            val updatedFrame = withTimeout(3000) {
                session.incoming.receive() as? Frame.Text
            } ?: error("No update frame received")

            val updatedText = updatedFrame.readText()
            println("Updated frame: $updatedText")

            updatedRoomList = Json.decodeFromString<ChatRoomList>(updatedText)
            assertTrue(updatedRoomList.chatRoomList.isNotEmpty(), "Room is not created")
        } finally {
            session.close()
        }
        val messageResponse = client.post("/postmessage") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${tokenResponse.token}")
            setBody(Message(id = 0,user.id, chatId = updatedRoomList.chatRoomList.first().id,false,0,"hi"))
        }
        assertEquals(HttpStatusCode.Created, messageResponse.status,"Message not sent")

        val chatRoomId = updatedRoomList.chatRoomList.first().id
        val roomdataResponse = client.get("/getroomdata/$chatRoomId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${tokenResponse.token}")
        }
        val roomData=roomdataResponse.body<RoomData>()
        assertTrue(roomData.messages.first().text=="hi", "Message not arrived")

        val sessionRoom = client.webSocketSession {

            url("/ws/$chatRoomId")
            header("Authorization", "Bearer ${tokenResponse.token}")
        }

        try {
            val initialFrame = sessionRoom.incoming.receive() as? Frame.Text
                ?: error("Expected Frame.Text")
            println("Initial frame (server greeting): ${initialFrame.readText()}")

            val messageToSend = Message(
                id = 0,
                userid = user.id,
                chatId = chatRoomId,
                isRead = false,
                insertedDate = 0,
                text = "hello"
            )
            val messageJson = Json.encodeToString(messageToSend)

            sessionRoom.send(messageJson)

            val responseFrame = withTimeout(3000) {
                sessionRoom.incoming.receive() as? Frame.Text
            } ?: error("No message frame received")

            val responseJson = responseFrame.readText()
            val responseMessage = Json.decodeFromString<Message>(responseJson)
            println("Got broadcast from server: $responseMessage")

            assertEquals("hello", responseMessage.text, "Message is not correct")

        } finally {
            sessionRoom.close()
        }
        val roomdataResponse2 = client.get("/getroomdata/$chatRoomId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${tokenResponse.token}")
        }
        val roomData2=roomdataResponse2.body<RoomData>()
        assertTrue(roomData2.messages.last().text=="hello", "Messages in not correct")
    }
}