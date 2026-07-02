# CloudShield

CloudShield is a Java-based Cloud Resource Management and Deadlock Detection System that simulates cloud infrastructure operations. The project manages users, virtual machines, cloud resources, resource allocation, deadlock detection, security monitoring, and audit logging using PostgreSQL.

---

## Features

- User Authentication and Management
- Virtual Machine Management
- Cloud Resource Management
- Resource Allocation and Release
- Deadlock Detection using Wait-For Graph
- Security Threat Detection
- Login Monitoring
- Security Log Management
- Deadlock Log Generation
- PostgreSQL Database Integration
- Modular Java Project Structure

---

## Technologies Used

- Java
- Eclipse IDE
- PostgreSQL
- pgAdmin 4
- JDBC
- SQL

---

## Project Structure

```
CloudShield/
│
├── src/
│   ├── CloudShield/
│   ├── database/
│   ├── model/
│   ├── service/
│   └── sql/
│
├── bin/
│
├── .project
├── .classpath
└── module-info.java
```

---

## Database

The project uses PostgreSQL.

### Tables

- Users
- Resources
- Virtual Machines
- Resource Requests
- Resource Allocations
- Wait Dependencies
- Login Logs
- Security Logs
- Deadlock Logs

SQL scripts are provided inside the `src/sql` folder.

---

## Database Setup

### Step 1

Create a PostgreSQL database.

```sql
CREATE DATABASE cloudshield;
```

### Step 2

Open pgAdmin 4.

### Step 3

Execute the SQL files in the following order:

1. 01_database.sql
2. 02_tables.sql
3. 03_constraints.sql
4. 04_views.sql
5. 05_functions.sql
6. 06_triggers.sql
7. 07_sample_data.sql
8. 08_queries.sql

---

## Running the Project

1. Clone the repository.

```
git clone https://github.com/zoha-arkati/CloudShield.git
```

2. Open the project in Eclipse.

3. Configure the PostgreSQL database credentials inside:

```
src/database/DBConnection.java
```

Example:

```java
String url = "jdbc:postgresql://localhost:5432/cloudshield";
String username = "postgres";
String password = "your_password";
```

4. Run `Main.java`.

---

## Modules

### User Module

- User Registration
- Login
- Role Management

### Resource Module

- Add Resources
- Update Resources
- Delete Resources
- Allocate Resources

### Virtual Machine Module

- Create Virtual Machines
- Assign Resources

### Deadlock Detection Module

- Wait-For Graph
- Cycle Detection
- Deadlock Logging

### Security Module

- Login Monitoring
- Threat Detection
- Security Logging

---

## Project Objectives

- Simulate cloud resource allocation.
- Detect and prevent deadlocks.
- Maintain security logs.
- Demonstrate Java OOP concepts.
- Integrate Java with PostgreSQL.
- Implement modular application design.

---

## Future Enhancements

- Java Swing GUI
- Spring Boot REST API
- React Frontend
- Role-Based Authentication
- JWT Security
- Docker Deployment
- Cloud Deployment
- Email Notifications

---

## Author

**Iffat Khan, Zoha Arkati & Zahra Khan**


---

## License

This project is developed for educational and internship purposes.