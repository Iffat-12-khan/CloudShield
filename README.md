# CloudShield – Cloud Resource Management, Deadlock Detection & Cybersecurity Threat Detection System

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/)
[![Architecture](https://img.shields.io/badge/Architecture-Model--DAO--Service--Controller-purple.svg)]()
[![Database](https://img.shields.io/badge/Database-PostgreSQL%2018-blue.svg)](https://www.postgresql.org/)
[![UI](https://img.shields.io/badge/Theme-Dark%20Cybersecurity%20Dashboard-8b5cf6.svg)]()

> **Full-Stack BCA Final Year Project**  
> Developed using Core Java (JDBC, Servlets), PostgreSQL, HTML5, CSS3, and Vanilla JavaScript.  
> *No Spring Boot, No Hibernate, No React, No Angular, No AI/ML.* Pure, modular standard web architecture.

---

## 🌟 Project Overview

**CloudShield** simulates cloud infrastructure management, operating system resource scheduling, and multi-tenant security operations:
1. **Cloud Computing**: Manages physical resource pools (CPU, RAM, NVMe Storage, Bandwidth) and provisions Virtual Machines (Ubuntu, Debian, RedHat, Alpine).
2. **Operating Systems (OS)**: Deadlock Detection using **Wait-For Graph (WFG)** and **3-Color Depth-First Search (DFS)** cycle finding algorithm with automated preemption recovery.
3. **Cybersecurity**: Salted **SHA-256** password hashing, **PreparedStatement** defenses against SQL Injection, **Role-Based Access Control (RBAC)**, **Brute-Force Lockout** (locks account after 3 consecutive failed logins), **Resource Abuse Anomaly Scanning**, and comprehensive **Security Audit Logging**.
4. **Single-Page Scrolldown Application**: Designed after high-end cybersecurity telemetry dashboards with 3D purple cloud branding ("clooo" inspired), Synaptix dark glassmorphic styling, waveforms, circular speedometer gauge, and interactive HTML5 Canvas.

---

## 🚀 Quick Start (1-Click Run)

### Method 1: Standalone Built-in Server (Recommended for Viva)
CloudShield comes with an embedded HTTP server built directly into `CloudShield.Main`. You do not even need Tomcat installed!

```powershell
# 1. Compile Java files
javac -cp "lib/postgresql.jar;lib/servlet-api.jar" -d bin (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)

# 2. Run the application
java -cp "bin;lib/postgresql.jar;lib/servlet-api.jar" CloudShield.Main
```

Open your browser at:  
👉 **`http://localhost:8080`**

### Demo Accounts & Credentials:
| Username | Password | Role | Permissions |
| :--- | :--- | :--- | :--- |
| **`admin`** | `admin123` | **ADMIN** | Full system control, unblock accounts, resolve deadlocks, deploy VMs |
| **`john`** | `john123` | **OPERATOR** | Deploy VMs, request resources, approve allocations, manage instances |
| **`alex`** | `alex123` | **VIEWER** | Read-only access to metrics, logs, and telemetry |

---

## 🗄️ PostgreSQL Database Setup

### Step 1: Open pgAdmin 4 or psql
Create the database:
```sql
CREATE DATABASE cloudshield;
```

### Step 2: Run SQL Scripts in Order
Execute the scripts located in `src/sql/`:
1. `01_database.sql` – Database creation and schema configuration.
2. `02_tables.sql` – 9 relational tables with PK, FK, CHECK constraints, and DEFAULT values.
3. `03_constraints.sql` – Unique indexes and performance indexes for foreign keys.
4. `04_views.sql` – Analytical reporting views (`view_vm_resource_summary`, `view_system_kpis`).
5. `05_functions.sql` – Stored procedures for ACID transactional allocation and security logging.
6. `06_triggers.sql` – Triggers for brute-force account lockout and negative balance guards.
7. `07_sample_data.sql` – Realistic seed data pre-configured to demonstrate deadlocks and attacks.
8. `08_queries.sql` – Advanced SQL viva queries (JOINs, GROUP BY, Window functions).

*(Alternatively, run the consolidated script `src/database/databse.sql` in a single click).*

### Step 3: Configure Credentials (Optional)
In `src/database/DBConnection.java`:
```java
private static String dbUrl = "jdbc:postgresql://localhost:5432/cloudshield";
private static String dbUser = "postgres";
private static String dbPassword = "your_password";
```
> **Note**: CloudShield has an automatic fallback mode: If PostgreSQL is offline or local credentials differ during your viva presentation, CloudShield automatically engages its built-in in-memory engine with seeded data so the presentation continues without interruption!

---

## 📂 Project Architecture & File Inventory

The backend strictly follows the **Model → DAO → Service → Controller** architecture:

```
CloudShield/
├── lib/
│   ├── postgresql.jar                 # PostgreSQL JDBC 42.7 Driver
│   └── servlet-api.jar                # Jakarta Servlet API
├── src/
│   ├── CloudShield/
│   │   └── Main.java                  # Standalone Runner with built-in HTTP Server
│   ├── model/                         # Java POJO Bean Entities (OOP Encapsulation)
│   │   ├── User.java                  # User credentials, salt, role (ADMIN/OPERATOR/VIEWER), status
│   │   ├── Resource.java              # Hardware pool: CPU, RAM, Storage, Network
│   │   ├── VirtualMachine.java        # Compute instance specs, status (RUNNING/WAITING/STOPPED)
│   │   ├── ResourceRequest.java       # Queued resource requests
│   │   ├── Allocation.java            # Granted resource leases
│   │   ├── WaitDependency.java        # Directed edge in Wait-For Graph (Waiting VM -> Holding VM)
│   │   ├── DeadlockLog.java           # Detected deadlock cycle incidents
│   │   ├── LoginLog.java              # Authentication attempts with IP and failure reason
│   │   └── SecurityLog.java           # Security events with severity (LOW, MEDIUM, HIGH, CRITICAL)
│   ├── database/
│   │   ├── DBConnection.java          # JDBC Connection Manager with fail-safe detection
│   │   └── InMemoryStore.java         # Thread-safe in-memory database simulation for viva backup
│   ├── dao/                           # Data Access Objects (JDBC PreparedStatements & Transactions)
│   │   ├── UserDAO.java               # Authentication, failed attempt counter, lock/unlock
│   │   ├── ResourceDAO.java           # Capacity checks and pool updates
│   │   ├── VirtualMachineDAO.java     # VM CRUD and status transitions
│   │   ├── ResourceRequestDAO.java    # Request queue management
│   │   ├── AllocationDAO.java         # ACID Transactions (setAutoCommit(false), commit, rollback)
│   │   ├── WaitDependencyDAO.java     # WFG directed edge persistence
│   │   ├── DeadlockLogDAO.java        # Deadlock incident logging
│   │   ├── LoginLogDAO.java           # Rolling window failed login counter
│   │   └── SecurityLogDAO.java        # Filterable security incident logging
│   ├── service/                       # Business Logic Layer
│   │   ├── SecurityManager.java       # Salted SHA-256 hashing, RBAC, brute-force lockout
│   │   ├── ThreatDetector.java        # Vulnerability scanning, resource abuse detection
│   │   ├── WaitForGraph.java          # Directed graph G = (V, E) data structure
│   │   ├── DeadlockDetector.java      # 3-Color DFS cycle finding & automated preemption
│   │   └── ResourceManager.java       # Multi-tenant cloud provisioning coordinator
│   ├── controller/
│   │   └── ApiController.java         # Unified RESTful JSON API dispatcher
│   ├── servlet/
│   │   └── CloudShieldServlet.java    # Standard HttpServlet for Apache Tomcat deployment
│   └── sql/                           # DDL, DML, Triggers, Views, Functions
│       ├── 01_database.sql to 08_queries.sql
├── web/                               # Frontend Single-Page Scrolldown Application
│   ├── index.html                     # 9 Scrolldown Modules + Viva Guide + Modals
│   ├── css/style.css                  # Dark cybersecurity theme, waveforms, glowing cards
│   └── js/
│       ├── app.js                     # State management, periodic polling, RBAC controls
│       └── wfg-canvas.js              # HTML5 Canvas physics renderer for Wait-For Graph
└── README.md
```

---

## 🎯 9 Application Modules (Scrolldown Screens)

1. **Dashboard Overview (Synaptix Aesthetic)**:
   - Greeting hero banner with quick simulation controls.
   - 4 Stat Cards with SVG mini sparkline waveforms.
   - Performance index spline chart with glowing vertex dots and peak pillar highlight.
   - Circular SVG speedometer gauge with neon cyan gradient stroke showing live health.
2. **Virtual Machine Management**:
   - Provision, Stop, Start, and Terminate VMs.
   - Live CPU, RAM, and NVMe Storage hardware footprint tags.
3. **Cloud Resource Pools**:
   - Live progress bars with glow indicators for CPU, RAM, Storage, Network.
4. **Resource Requests & Allocations**:
   - Request submission form and approval workflow.
   - Explicit **ACID Transaction** allocation button.
5. **Deadlock Detection & WFG Visualizer**:
   - Interactive HTML5 Canvas: nodes are VMs, directed arrows are wait dependencies.
   - Runs 3-Color DFS cycle detection algorithm in Core Java.
   - Shows animated crimson cycle alert banner with exact cycle path: `VM-3 -> VM-2 -> VM-1 -> VM-3`.
   - 1-click **Auto-Resolve Deadlock** button to break the cycle via victim preemption.
6. **Cybersecurity Threat Detection**:
   - Live Incident Telemetry Radar.
   - Brute-Force Attack Simulator.
   - Resource Abuse Spike Simulator (>80% capacity anomaly detection).
   - Blocked Accounts Manager with 1-click unlock.
7. **System & Security Audit Logs**:
   - Tabbed view: Security Logs, Login Logs, Deadlock Incident Logs.
   - Filter by severity (CRITICAL, HIGH, MEDIUM, LOW) and search.
8. **Reports & SQL Views**:
   - Aggregated metrics querying PostgreSQL views.
   - Print / Export PDF feature.
9. **BCA Viva & Academic Architecture Reference**:
   - Interactive presentation cheat-sheet covering OOP, OS, Cloud, and SQL concepts.

---

## 🎓 BCA Viva Examination Q&A Cheat-Sheet

#### 1. Why didn't you use Spring Boot or React?
> *"This project was developed strictly using Core Java (JDBC, Servlets), PostgreSQL, and Vanilla JavaScript to demonstrate foundational computer science competencies in Object-Oriented Programming, manual transaction control, SQL trigger design, and algorithm implementation without relying on third-party frameworks."*

#### 2. How does the Deadlock Detection algorithm work?
> *"We construct a directed Wait-For Graph (WFG) where vertices are Virtual Machines and directed edges \(V_i \to V_j\) represent that \(V_i\) is waiting for a resource held by \(V_j\). We run a 3-Color Depth-First Search (DFS) algorithm where vertices are colored WHITE (unvisited), GRAY (visiting / on current recursion stack), or BLACK (explored). If DFS encounters an edge pointing to a GRAY vertex, a back-edge is identified, which mathematically proves a circular wait deadlock condition."*

#### 3. How are ACID transactions implemented in Java?
> *"In `AllocationDAO.java`, we invoke `connection.setAutoCommit(false)` to start the transaction. We lock the target resource row using `SELECT ... FOR UPDATE`, verify available units, deduct the balance, insert into `resource_allocations`, update the request status, and delete the wait dependency. Finally, `connection.commit()` commits the changes atomically. If any exception occurs, `connection.rollback()` restores state."*

#### 4. How does the application protect against SQL Injection?
> *"Every single query across all 9 DAOs utilizes `PreparedStatement` with placeholder parameters (`?`). The SQL parser compiles the query structure beforehand, treating all user inputs as literal string constants, completely neutralizing SQL injection attacks."*

#### 5. How does Brute-Force lockout work?
> *"Upon every login attempt, a record is inserted into `login_logs`. `SecurityManager.java` tracks consecutive failed attempts. When 3 failed attempts are reached within 5 minutes, the user's `account_status` is updated to `'BLOCKED'`, and a `CRITICAL` alert is logged to `security_logs`."*

---

## 👥 Authors
- **Iffat Khan, Zoha Arkati & Zahra Khan**
- BCA Project – CloudShield System