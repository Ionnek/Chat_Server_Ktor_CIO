Basic Ktor chat service made by me as a pet-project.
This project is a challenge to me to build product-ready a full-stack Kotlin application.

+ Consists from a backend Server:
    - Stack:(Ktor CIO,rest+websocket,jwt(security), postgreSQL,flyway(db migration))
    - Prepared set-up for Docker env(docker-compose.yml file inside).
+ Frontend client android application: [`chat‑client`](https://github.com/Ionnek/Chat_Client_Android_App)
    - Stack:(retrofit,okhttp, compose UI, +DI(hilt))

Tested for:
- JDK 17
- postgreSQL 15-alpine
- Gradle 8
- Docker и Docker Compose

You must set some environment variables in docker-compose.yml(inside project folder)

for a PostgreSQL:
* POSTGRES_USER: YourDbUser
* POSTGRES_PASSWORD: YourDbPass
* POSTGRES_DB : YourDbName

for a main server:
* DATABASE_URL=jdbc:postgresql://db:5432/InsertYourDbNameHere
* DATABASE_USER=InsertYourDbUserHere
* DATABASE_PASSWORD=InsertYourDbPassHere
* JWT_SECRET=YourSecretKeyForJWT
* JWT_ISSUER=YourUniqueServiceName
* JWT_AUDIENCE=YorAppAudience
* JWT_VALIDITYMS=3600000(Time of validity)

Step by step guide for test

* 1)Open project in the IntellJ IDEA
* 2)Run RoutingTests.kt

Step by step guide for start
* 1)Build project to a shadowJar in bash(You need JDK и Gradle or simple build in IJ)
./gradlew shadowJar 
* 2)Take a Dockerfile from the project folder and docker-compose.yml edited by you
* 3)Place built shadowJar, Dockerfile and docker-compose.yml in the same directory
* 4)Start docker build(You need Docker service):
docker compose up --build
* 5)In the first attempt docker building you may see error that postgre not initialized yet,
in that case run docker compose up --build again or modify dockerfile with a wait-for-it script
https://github.com/vishnubob/wait-for-it/tree/master
* 6)In client app [`chat‑client`](https://github.com/Ionnek/Chat_Client_Android_App),
place public link to server in DI NetworkModule module(Android studio or IJ). 
* 7)Build client app and test