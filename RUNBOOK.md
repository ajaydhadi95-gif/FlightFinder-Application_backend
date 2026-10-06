# FlightFinder Backend – AWS 3-Tier CI/CD Runbook

> **Project:** FlightFinder Backend
> **Architecture:** GitHub → Jenkins → Maven → Docker Hub → AWS SSM → Private Backend EC2 → RDS MySQL
> **Purpose:** This runbook explains the complete project in simple language for GitHub documentation, troubleshooting, maintenance, and interview explanation.

---

## 1. Project Overview

This project implements a production-style AWS 3-tier backend deployment with CI/CD.

### Complete Flow

```text
Developer
    |
    | git push
    v
GitHub - main
    |
    v
Jenkins EC2
    |
    +--> Maven Build & Test
    |
    +--> Maven Package
    |
    +--> Docker Build
    |
    v
Docker Hub
    |
    | AWS SSM
    v
Private Backend EC2
    |
    | Docker Container
    | Spring Boot :8080
    v
RDS MySQL :3306
```

### What happens after a Git push?

1. Developer pushes code to GitHub.
2. Jenkins checks out the `main` branch.
3. Maven builds and tests the application.
4. Maven packages the Spring Boot application as a JAR.
5. Jenkins builds a Docker image.
6. Jenkins pushes the image to Docker Hub.
7. Jenkins uses AWS Systems Manager (SSM).
8. SSM deploys the Docker image to the private backend EC2.
9. The Docker container starts Spring Boot on port `8080`.
10. Spring Boot connects to RDS MySQL on port `3306`.

---

# 2. AWS Architecture

## VPC

```text
VPC: 10.0.0.0/16
Region: ap-south-1
```

## Subnets

```text
Public Subnets
├── 10.0.1.0/24
└── 10.0.2.0/24

Private Application Subnets
├── 10.0.11.0/24
└── 10.0.12.0/24

Private Database Subnets
├── 10.0.21.0/24
└── 10.0.22.0/24
```

## Architecture Diagram

```text
                         Internet
                            |
                            v
                    +---------------+
                    | Public Subnet  |
                    +---------------+
                            |
                            v
                     Jenkins EC2
                            |
                     AWS IAM Role
                            |
                            | SSM
                            v
                +------------------------+
                | Private App Subnet     |
                |                        |
                |   Backend EC2          |
                |   10.0.11.171          |
                |        |               |
                |   Docker Container     |
                |   Spring Boot :8080    |
                +--------+---------------+
                         |
                         | MySQL :3306
                         v
                +------------------------+
                | Private DB Subnet      |
                |                        |
                | RDS MySQL              |
                | bookingdb :3306        |
                +------------------------+
```

### Why is the backend private?

The backend EC2 does not need direct internet exposure.

Keeping it in a private subnet provides better network isolation.

The project uses AWS SSM instead of SSH for deployment, so the backend does not require a public IP for deployment.

---

# 3. AWS Resources

| Resource            | Configuration                      |
| ------------------- | ---------------------------------- |
| Region              | `ap-south-1`                       |
| VPC                 | `10.0.0.0/16`                      |
| Public Subnets      | `10.0.1.0/24`, `10.0.2.0/24`       |
| Private App Subnets | `10.0.11.0/24`, `10.0.12.0/24`     |
| Private DB Subnets  | `10.0.21.0/24`, `10.0.22.0/24`     |
| Backend EC2         | `i-04e08bcedc0870665`              |
| Backend Private IP  | `10.0.11.171`                      |
| Backend Port        | `8080`                             |
| Database            | RDS MySQL                          |
| Database Name       | `bookingdb`                        |
| Database Port       | `3306`                             |
| Jenkins IAM Role    | `jenkins-ec2-role`                 |
| Docker Image        | `ajaydhadi95/flightfinder-backend` |

> **Security:** Never commit database passwords, Docker Hub tokens, AWS access keys, or other secrets to this repository.

---

# 4. Backend Application

## Technology Stack

```text
Java 21
Spring Boot
Maven
MySQL
Docker
AWS EC2
AWS RDS
AWS SSM
Jenkins
GitHub
Docker Hub
```

## Project Structure

```text
backend/
├── .mvn/
├── Dockerfile
├── Jenkinsfile
├── mvnw
├── mvnw.cmd
├── pom.xml
├── RUNBOOK.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── flightfinder_backend/
    │   │       ├── BackendApplication.java
    │   │       ├── config/
    │   │       ├── controller/
    │   │       ├── exception/
    │   │       ├── model/
    │   │       ├── repository/
    │   │       └── service/
    │   └── resources/
    │       └── application.properties
    └── test/
```

---

# 5. GitHub Repository

Repository:

```text
FlightFinder-Application_backend
```

GitHub:

```text
https://github.com/ajaydhadi95-gif/FlightFinder-Application_backend.git
```

Branch:

```text
main
```

GitHub is the source-code repository.

### Git Flow

```text
Developer
    |
    | git add .
    | git commit
    | git push
    v
GitHub
    |
    v
Jenkins
```

---

# 6. Git Cleanup

During development, an unwanted `bin/` directory was found.

It contained duplicate project files and compiled `.class` files.

It was removed from Git tracking:

```bash
git rm -r --cached bin
```

`.gitignore` was updated:

```gitignore
target/
bin/
*.class
```

Then:

```bash
git add .
git commit -m "Initial backend project setup"
git push origin main
```

### Why `.gitignore`?

Generated build files should not normally be committed to Git.

Examples:

```text
target/
bin/
*.class
```

These files can be recreated during the build.

---

# 7. Dockerfile

The application uses a multi-stage Docker build.

## Build Stage

```text
eclipse-temurin:21-jdk-alpine
```

This stage contains the JDK and builds the Spring Boot application.

## Runtime Stage

```text
eclipse-temurin:21-jre-alpine
```

Only the JRE is required to run the final JAR.

### Why multi-stage Docker?

It keeps the final runtime image smaller because build tools are not required in the runtime container.

The container also runs as a non-root user:

```text
appuser
```

Container port:

```text
8080
```

Docker image:

```text
ajaydhadi95/flightfinder-backend
```

---

# 8. Local Docker Verification

The Dockerfile was tested locally:

```bash
docker build -t flightfinder-backend .
```

A successful build verified that the following work together:

```text
Dockerfile
Maven wrapper
pom.xml
Spring Boot source
Java 21
```

---

# 9. Jenkins Server

Jenkins is running on an AWS EC2 instance.

Check Jenkins:

```bash
sudo systemctl status jenkins
```

Expected:

```text
Active: active (running)
```

Jenkins workspace:

```text
/var/lib/jenkins/workspace/flightfinder-Backend
```

Jenkins performs the CI/CD process.

---

# 10. Jenkins Docker Permission

Jenkins must be able to execute Docker commands.

Verification:

```bash
sudo -u jenkins docker ps
```

This worked successfully.

Therefore Jenkins can execute:

```text
docker build
docker login
docker push
```

### Important

If the Ubuntu user manually runs:

```bash
docker ps
```

and receives a Docker socket permission error, that does not mean Jenkins is broken.

Jenkins uses the `jenkins` user.

Optional fix for manual Ubuntu Docker access:

```bash
sudo usermod -aG docker ubuntu
```

Then log out and log in again.

---

# 11. Jenkins AWS IAM Role

Initially AWS CLI returned:

```text
NoCredentials
```

Instead of storing AWS access keys on Jenkins, an IAM role was attached to the Jenkins EC2.

IAM role:

```text
jenkins-ec2-role
```

Verification:

```bash
aws sts get-caller-identity
```

This worked successfully after the role was attached.

### Why IAM Role?

```text
Jenkins EC2
    |
    | IAM Role
    v
AWS Services
```

This avoids storing long-lived AWS access keys on the Jenkins server.

---

# 12. AWS Systems Manager – SSM

The backend EC2 is private.

Jenkins deploys to it using AWS Systems Manager instead of SSH.

Required permissions include:

```text
ssm:SendCommand
ssm:GetCommandInvocation
ssm:ListCommandInvocations
ssm:ListCommands
ssm:DescribeInstanceInformation
```

For the lab/testing setup:

```text
Resource: *
```

For production, IAM permissions should be restricted as much as practical.

---

# 13. Backend EC2

Backend instance:

```text
Instance ID:
i-04e08bcedc0870665
```

Private IP:

```text
10.0.11.171
```

The instance is in the private application subnet.

SSM Agent was configured and the instance appeared as:

```text
Online
```

This allows Jenkins to execute remote commands without SSH.

---

# 14. Docker on Backend EC2

The first SSM test showed:

```text
docker: not found
```

Docker was installed on the backend EC2.

Verification:

```bash
docker --version
```

Result:

```text
Docker version 29.1.3
```

Docker service:

```bash
systemctl is-active docker
```

Result:

```text
active
```

Final relationship:

```text
Jenkins EC2
     |
     | AWS SSM
     v
Private Backend EC2
     |
     v
Docker
     |
     v
Spring Boot Container
```

---

# 15. Docker Hub

Docker Hub repository:

```text
ajaydhadi95/flightfinder-backend
```

Jenkins credential ID:

```text
dockerhub-credentials
```

Credential contains:

```text
Username: ajaydhadi95
Password: Docker Hub Access Token
```

> Never put the actual token in this file or GitHub.

Images are tagged with the Jenkins build number.

Example:

```text
ajaydhadi95/flightfinder-backend:4
```

The `latest` tag is also pushed:

```text
ajaydhadi95/flightfinder-backend:latest
```

---

# 16. Jenkins CI/CD Pipeline

The pipeline has six main stages:

```text
1. Checkout
2. Maven Build & Test
3. Package JAR
4. Docker Build
5. Docker Login & Push
6. Deploy to Backend via SSM
```

Complete pipeline:

```text
GitHub
   |
   v
Checkout
   |
   v
Maven Test
   |
   v
Maven Package
   |
   v
Docker Build
   |
   v
Docker Hub Push
   |
   v
AWS SSM
   |
   v
Private Backend EC2
```

---

# 17. Checkout Stage

Jenkins checks out:

```text
Repository: FlightFinder-Application_backend
Branch: main
```

Successful build checked out:

```text
Commit:
626c867611e620953ac297a4ceff05c952db822c
```

Commit message:

```text
Initial backend project setup
```

---

# 18. Maven Build & Test

Jenkins runs:

```bash
chmod +x mvnw
./mvnw clean test
```

Result:

```text
BUILD SUCCESS
```

The project currently has no test source files:

```text
No tests to run.
```

This is not a pipeline failure.

### Purpose

This stage validates that the Java application can compile and that available tests pass.

---

# 19. Maven Package

Jenkins runs:

```bash
./mvnw clean package -DskipTests
```

JAR generated:

```text
target/backend-0.0.1-SNAPSHOT.jar
```

Result:

```text
BUILD SUCCESS
```

### Why package again?

The test stage validates the project.

The package stage creates the deployable Spring Boot JAR.

---

# 20. Docker Build

Jenkins creates two tags:

```bash
docker build \
  -t ajaydhadi95/flightfinder-backend:${BUILD_NUMBER} \
  -t ajaydhadi95/flightfinder-backend:latest \
  .
```

For build `4`:

```text
ajaydhadi95/flightfinder-backend:4
ajaydhadi95/flightfinder-backend:latest
```

### Why build number?

Every deployment gets a traceable version.

Example:

```text
Build 4 → Image :4
Build 5 → Image :5
Build 6 → Image :6
```

This is safer than relying only on `latest`.

---

# 21. Docker Push

Jenkins logs into Docker Hub using:

```text
dockerhub-credentials
```

Then pushes:

```bash
docker push ajaydhadi95/flightfinder-backend:4
docker push ajaydhadi95/flightfinder-backend:latest
```

Both images were successfully pushed.

Successful image digest:

```text
sha256:69b8e3a83c5c82f7536357bc118214b92f48a067337fd3f9fafdc1e9986
```

---

# 22. SSM Deployment

After Docker Hub push, Jenkins sends an SSM command to:

```text
i-04e08bcedc0870665
```

Deployment commands:

```bash
docker pull ajaydhadi95/flightfinder-backend:${IMAGE_TAG}

docker stop flightfinder-backend || true

docker rm flightfinder-backend || true

docker run -d \
  --name flightfinder-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  ajaydhadi95/flightfinder-backend:${IMAGE_TAG}
```

### What happens?

```text
1. Pull new image
2. Stop old container
3. Remove old container
4. Start new container
5. Restart automatically if the server/container restarts
```

---

# 23. First Deployment – Expected Message

On the first deployment:

```text
Error response from daemon:
No such container: flightfinder-backend
```

This happened because there was no old container.

The commands use:

```bash
|| true
```

Therefore the deployment continued.

The new container was created successfully.

---

# 24. Successful Deployment

SSM returned:

```text
ResponseCode: 0
Status: Success
StatusDetails: Success
```

Image deployed:

```text
ajaydhadi95/flightfinder-backend:4
```

Final Jenkins result:

```text
Finished: SUCCESS
```

---

# 25. Verified Pipeline Result

| Stage               | Result    |
| ------------------- | --------- |
| Checkout            | ✅ SUCCESS |
| Maven Build & Test  | ✅ SUCCESS |
| Maven Package       | ✅ SUCCESS |
| Docker Build        | ✅ SUCCESS |
| Docker Login & Push | ✅ SUCCESS |
| SSM Deployment      | ✅ SUCCESS |

---

# 26. Current Deployment

Current image:

```text
ajaydhadi95/flightfinder-backend:4
```

Latest tag:

```text
ajaydhadi95/flightfinder-backend:latest
```

Backend:

```text
Instance: i-04e08bcedc0870665
Private IP: 10.0.11.171
Application Port: 8080
```

Database:

```text
RDS MySQL
Database: bookingdb
Port: 3306
```

---

# 27. Check Backend Container Through SSM

Because the backend is private, use SSM.

From Jenkins EC2:

```bash
aws ssm send-command \
  --instance-ids "i-04e08bcedc0870665" \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=["docker ps","docker logs --tail 50 flightfinder-backend"]' \
  --region ap-south-1
```

The command returns a Command ID.

Then:

```bash
aws ssm get-command-invocation \
  --command-id "COMMAND_ID" \
  --instance-id "i-04e08bcedc0870665" \
  --region ap-south-1
```

This verifies:

```text
Docker container status
Spring Boot startup
Application logs
Database connection errors
Application errors
```

---

# 28. Testing the Private Backend

Private IP:

```text
10.0.11.171
```

This is **not a public browser URL**.

A resource with network connectivity to the private subnet can test it.

Example:

```bash
curl http://10.0.11.171:8080
```

For a known API endpoint:

```bash
curl http://10.0.11.171:8080/<API-ENDPOINT>
```

> Replace `<API-ENDPOINT>` with an actual endpoint from the application controller.

---

# 29. Why SSM Instead of SSH?

Traditional deployment:

```text
Jenkins
   |
   | SSH + Private Key
   v
Backend EC2
```

Our deployment:

```text
Jenkins EC2
    |
    | IAM Role
    v
AWS Systems Manager
    |
    | SSM Agent
    v
Private Backend EC2
```

## Benefits

* No SSH private key required for deployment.
* Backend does not need a public IP.
* No need to expose SSH to the internet.
* IAM-based access.
* Centralized command execution.
* Suitable for automated deployments.
* Better separation between public and private layers.

---

# 30. Troubleshooting Performed

## Issue 1 – Git untracked files

Problem:

```text
nothing added to commit but untracked files present
```

Solution:

```bash
git add .
git commit -m "Initial backend project setup"
```

---

## Issue 2 – Duplicate `bin/` directory

Problem:

```text
bin/
*.class
```

were present.

Solution:

```bash
git rm -r --cached bin
```

`.gitignore`:

```gitignore
bin/
target/
*.class
```

---

## Issue 3 – Jenkins AWS credentials

Problem:

```text
NoCredentials
```

Solution:

Attach:

```text
jenkins-ec2-role
```

to Jenkins EC2.

Verify:

```bash
aws sts get-caller-identity
```

---

## Issue 4 – SSM permission

Problem:

Jenkins initially lacked all required SSM permissions.

One missing permission was:

```text
ssm:DescribeInstanceInformation
```

It was added to the Jenkins IAM role.

---

## Issue 5 – Docker missing on backend

Problem:

```text
docker: not found
```

Solution:

Install Docker on backend EC2.

Verify:

```bash
docker --version
systemctl is-active docker
```

---

## Issue 6 – Ubuntu Docker permission

Problem:

Running:

```bash
docker ps
```

as the Ubuntu user on Jenkins EC2 produced a Docker socket permission error.

Important:

This did not affect Jenkins because the Jenkins service already had Docker permission.

Optional fix:

```bash
sudo usermod -aG docker ubuntu
```

Then log out and log in again.

---

# 31. How to Explain This Project in an Interview

### Short Interview Answer

> I implemented a production-style three-tier
