# api-proyectos


[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen)](https://spring.io/projects/spring-boot)
[![Apache Kafka - Producer](https://img.shields.io/badge/Kafka--Producer-29092-black?logo=apachekafka)](https://kafka.apache.org/)
[![Hibernate](https://img.shields.io/badge/Hibernate-JPA-orange)](https://hibernate.org/)
[![Docker](https://img.shields.io/badge/Docker-Container-blue?logo=docker)](https://www.docker.com/)

Este microservicio (`api-proyectos`) es el componente central del ecosistema. Se encarga de la creación, actualización y gestión del ciclo de vida de los proyectos y sus tareas (`tasks`). Además, gestiona la asignación de usuarios y propaga estos cambios de forma asíncrona como **Productor de Eventos** en Apache Kafka.

---

##  Responsabilidades en la Arquitectura

1. **Gestión Core:** CRUD de proyectos, prioridades, fechas de entrega y porcentajes de progreso.
2. **Modelo de Datos Complejo:** Controla las relaciones relacionales de proyectos con tareas (`@OneToMany`) y asignaciones de usuarios (`@OneToMany`).
3. **Origen de Datos (Event Sourcing):** Genera y publica eventos hacia Kafka en el tópico `proyecto-asignaciones` cada vez que el estado de un proyecto o sus miembros cambia.

---

##  Configuración del Entorno Docker (`application.properties`)

Al estar dockerizado, se comunica con la base de datos y el broker de Kafka utilizando los nombres de red de Docker Compose:

```properties
server.port=8081

# Conexión interna a la base de datos relacional
spring.datasource.url=jdbc:postgresql://postgres-db:5432/db_proyectos
spring.datasource.username=postgres
spring.datasource.password=secret

# Mostrar SQL para auditoría de queries (Monitoreo de rendimiento N+1)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Configuración del Productor de Kafka (Interno Docker)
spring.kafka.bootstrap-servers=kafka:29092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
# Garantiza que el mensaje se envíe correctamente a los réplicas
spring.kafka.producer.acks=-1
spring.kafka.producer.properties.enable.idempotence=true
