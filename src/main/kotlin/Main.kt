/*
 * Copyright 2025 Ivan Borzykh
 * SPDX-License-Identifier: MIT
 */

package org.example

import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.websocket.*
import io.ktor.server.response.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.serialization.kotlinx.json.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.plugins.contentnegotiation.*
import org.example.RoutesModule.configureRoutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

fun Application.module(repo: IRepo,
                       dbUrl: String = System.getenv("DATABASE_URL") ?: error("No DATABASE_URL env var"),
                       dbUser: String = System.getenv("DATABASE_USER") ?: error("No DATABASE_USER env var"),
                       dbPassword: String = System.getenv("DATABASE_PASSWORD") ?: error("No DATABASE_PASSWORD env var"),
                       jwtSecret: String = System.getenv("JWT_SECRET") ?: error("No JWT_SECRET env var"),
                       jwtIssuer: String = System.getenv("JWT_ISSUER") ?: "defaultIssuer",
                       jwtAudience: String = System.getenv("JWT_AUDIENCE") ?: "defaultAudience",
                       tokenValidityInMs: Long = System.getenv("JWT_VALIDITYMS")?.toLong() ?: 3600000L
                       ) {

    val roomConnectionsMap = mutableMapOf<Int, MutableSet<DefaultWebSocketServerSession>>()

    if(dbUrl!="testUrl") {
        initDatabase(dbUrl, dbUser, dbPassword)
    }
    install(ContentNegotiation) {
        json()
    }

    install(WebSockets) {
        pingPeriod = 15.seconds.toJavaDuration()
        timeout = 15.seconds.toJavaDuration()
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate { jwtCredential ->
                if (jwtCredential.payload.getClaim("username").asString().isNotEmpty()) {
                    JWTPrincipal(jwtCredential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respondText("token is invalid or expired", status = HttpStatusCode.Unauthorized)
            }
        }
    }

    configureRoutes(
        jwtSecret = jwtSecret,
        jwtIssuer = jwtIssuer,
        jwtAudience = jwtAudience,
        tokenValidityInMs = tokenValidityInMs,
        roomConnectionsMap = roomConnectionsMap,
        repo = repo
    )
}

fun main() {
    embeddedServer(CIO, port = 8000){
        val repo = RepoReal()
        module(repo)
    }.start(wait = true)
}