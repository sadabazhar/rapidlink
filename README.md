# RapidLink

RapidLink is a URL shortening service built with **Spring Boot, Redis, and PostgreSQL**.
It converts long URLs into short links and redirects them quickly using caching.

The goal of this project is to practice **scalable backend design, caching strategies, and clean API development**.

## Features

* Shorten long URLs into compact links
* Fast redirects using Redis caching
* Store URL mappings in PostgreSQL
* Track click analytics
* RESTful API design


## Tech Stack

**Backend**

* Java
* Spring Boot
* Spring Web
* Spring Data JPA

**Data**

* PostgreSQL
* Redis

**Build Tool**

* Maven

## Architecture

```
Client
   │
   ▼
Spring Boot API
   │
   ├── Redis (cache for fast lookups)
   └── PostgreSQL (persistent storage)
```

**How it works**

1. A user sends a long URL to the API.
2. The system generates a short code.
3. The mapping is stored in PostgreSQL.
4. Redis caches the mapping for faster redirects.
5. When the short link is opened, Redis is checked first.


## Project Structure

```
rapidlink
 ├── controller
 ├── service
 ├── repository
 ├── model
 ├── dto
 ├── config
 └── util
```


## Running the Project

Clone the repository

```
git clone https://github.com/sadabazhar/rapidlink.git
cd rapidlink
```

Build the project

```
./mvnw clean install
```

Run the application

```
./mvnw spring-boot:run
```

Server runs on:

```
http://localhost:8080
```


## Planned Features

* Custom short URLs
* Link expiration
* Rate limiting
* Analytics dashboard
* Docker deployment


## Author
Sadab Azhar
