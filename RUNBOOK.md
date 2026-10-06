User: [File: pasted-text.txt]

create the rubook.md for git hub and ad the gif animation and profetional

use.ai: # FlightFinder Backend - AWS 3-Tier CI/CD Runbook

<p align="center">
  <img src="docs/assets/flightfinder-c

FlightFinder Backend - AWS 3-Tier CI/CD Runbook

<p align="center">
  <img src="docs/assets/flightfinder-cicd.gif" alt="FlightFinder CI/CD pipeline animation" width="900">
</p>

<p align="center">
  <strong>Production-style Spring Boot deployment using Jenkins, Docker, AWS Systems Manager, private EC2, and Amazon RDS.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/SpringBoot-Backend-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Jenkins-CI%2FCD-D24939?logo=jenkins&logoColor=white" alt="Jenkins">
  <img src="https://img.shields.io/badge/Docker-Containerized-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/AWS-ap--south--1-FF9900?logo=amazonwebservices&logoColor=white" alt="AWS">
  <img src="https://img.shields.io/badge/MySQL-RDS-4479A1?logo=mysql&logoColor=white" alt="MySQL">
</p>

> [!NOTE]
> The animated GIF must be stored at docs/assets/flightfinder-cicd.gif. A GIF cannot be generated inside Markdown, but the runbook is configured to display it automatically after it is added to that path.

Table of Contents
• Overview
• Architecture
• Technology Stack
• Network Design
• Repository Structure
• Prerequisites
• Docker Configuration
• Jenkins Configuration
• AWS IAM and SSM Configuration
• CI/CD Pipeline
• Deployment Verification
• Rollback Procedure
• Troubleshooting
• Security Considerations
• Production Improvements
• Operational Checklist

Overview

FlightFinder uses a three-tier AWS architecture in which the Spring Boot backend runs inside a Docker container on a private EC2 instance. Jenkins builds, tests, packages, and publishes the application before deploying it through AWS Systems Manager.

The private backend instance does not require a public IP address or inbound SSH access.

``text
Developer
   |
   | git push
   v
GitHub
   |
   v
Jenkins EC2
   |
   | Maven build and test
   | Maven package
   | Docker build
   | Docker push
   v
Docker Hub
   |
   | AWS Systems Manager
   v
Private Backend EC2
   |
   | Spring Boot :8080
   v
Amazon RDS MySQL :3306
`

Deployment Status

| Component | Status |
|---|---|
| GitHub checkout | Working |
| Maven build | Working |
| Maven test stage | Working |
| JAR packaging | Working |
| Docker build | Working |
| Docker Hub push | Working |
| SSM deployment | Working |
| Private EC2 container | Running |
| RDS connectivity | Verify through application logs or a health endpoint |

Architecture

`text
                         Internet
                            |
                            v
                  +-------------------+
                  |   Public Subnet   |
                  |    Jenkins EC2    |
                  +---------+---------+
                            |
                            | IAM + AWS SSM
                            v
                  +-------------------+
                  | Private App Subnet|
                  |    Backend EC2    |
                  |    10.0.11.171    |
                  |  Spring Boot:8080 |
                  +---------+---------+
                            |
                            | MySQL:3306
                            v
                  +-------------------+
                  | Private DB Subnet |
                  | Amazon RDS MySQL  |
                  |     bookingdb     |
                  +-------------------+
`

CI/CD Sequence

`mermaid
sequenceDiagram
    actor Developer
    participant GitHub
    participant Jenkins
    participant DockerHub as Docker Hub
    participant SSM as AWS Systems Manager
    participant Backend as Private Backend EC2
    participant RDS as Amazon RDS MySQL

    Developer->>GitHub: Push to main
    GitHub->>Jenkins: Trigger pipeline
    Jenkins->>Jenkins: Maven test and package
    Jenkins->>Jenkins: Build Docker image
    Jenkins->>DockerHub: Push BUILDNUMBER and latest tags
    Jenkins->>SSM: Send deployment command
    SSM->>Backend: Pull and start container
    Backend->>RDS: Connect on port 3306
`

Technology Stack

| Layer | Technology |
|---|---|
| Application | Java 21 and Spring Boot |
| Build | Maven Wrapper |
| CI/CD | Jenkins |
| Containerization | Docker |
| Image registry | Docker Hub |
| Compute | Amazon EC2 |
| Remote deployment | AWS Systems Manager |
| Database | Amazon RDS for MySQL |
| Source control | GitHub |
| AWS region | ap-south-1 |

Network Design
VPC

`text
CIDR:   10.0.0.0/16
Region: ap-south-1
`

Subnets

| Tier | Availability zone placement | CIDR blocks |
|---|---|---|
| Public | Two availability zones | 10.0.1.0/24, 10.0.2.0/24 |
| Private application | Two availability zones | 10.0.11.0/24, 10.0.12.0/24 |
| Private database | Two availability zones | 10.0.21.0/24, 10.0.22.0/24 |

Current Resources

| Resource | Value |
|---|---|
| Backend EC2 instance | i-04e08bcedc0870665 |
| Backend private IP | 10.0.11.171 |
| Application port | 8080 |
| Database | bookingdb |
| Database port | 3306 |
| Docker image | ajaydhadi95/flightfinder-backend |

> [!WARNING]
> Instance IDs, private IP addresses, and image tags can change. Prefer Jenkins environment variables, EC2 tags, or AWS resource discovery instead of permanently hard-coding them.

Repository Structure

`text
backend/
├── .mvn/
├── docs/
│   └── assets/
│       └── flightfinder-cicd.gif
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── flightfinderbackend/
│   │   │       ├── BackendApplication.java
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── exception/
│   │   │       ├── model/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── .gitignore
├── Dockerfile
├── Jenkinsfile
├── RUNBOOK.md
├── mvnw
├── mvnw.cmd
└── pom.xml
`

Git Configuration

The repository tracks the main branch:

`text
https://github.com/ajaydhadi95-gif/FlightFinder-Applicationbackend.git
`

Recommended .gitignore entries:

`gitignore
target/
bin/
.class

.idea/
.vscode/
.iml

.env
.log
`

If bin/ was already committed, remove it from Git tracking:

`bash
git rm -r --cached bin
git add .gitignore
git commit -m "chore: remove generated files from repository"
git push origin main
`

Prerequisites
Jenkins EC2

The Jenkins server requires:

• Jenkins
• Git
• Java 21
• Docker
• AWS CLI
• Network access to GitHub and Docker Hub
• An EC2 instance profile with the required SSM permissions
• Jenkins Docker Hub credentials

Verify the services and tools:

`bash
sudo systemctl status jenkins
sudo systemctl status docker

java --version
git --version
docker --version
aws --version
`

Confirm that the Jenkins user can run Docker:

`bash
sudo -u jenkins docker ps
`

Confirm that the EC2 instance role is available:

`bash
aws sts get-caller-identity
`

Backend EC2

The backend instance requires:

• SSM Agent installed and running
• An EC2 instance profile with AmazonSSMManagedInstanceCore
• Docker installed and running
• Outbound access to AWS SSM endpoints and Docker Hub
• Access to RDS on TCP port 3306

Verify Docker:

`bash
docker --version
systemctl is-active docker
`

Verify SSM Agent:

`bash
sudo systemctl status amazon-ssm-agent
`

> [!IMPORTANT]
> A private instance needs outbound connectivity through a NAT gateway or appropriate VPC endpoints. SSM interface endpoints alone do not provide access to Docker Hub.

Docker Configuration

The application uses a multi-stage Docker build.

`dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src/ src/
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /app/target/.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
`

Build locally:

`bash
docker build -t flightfinder-backend .
`

Run locally:

`bash
docker run --rm \
  --name flightfinder-backend \
  -p 8080:8080 \
  flightfinder-backend
`

Inspect the logs:

`bash
docker logs -f flightfinder-backend
`

Jenkins Configuration
Docker Hub Credential

Create a Jenkins credential with the following values:

| Field | Value |
|---|---|
| Kind | Username with password |
| Credential ID | dockerhub-credentials |
| Username | Docker Hub username |
| Password | Docker Hub access token |

Do not use the Docker Hub account password in automation.

Recommended Environment Variables

`groovy
environment {
    AWSREGION         = 'ap-south-1'
    BACKENDINSTANCE   = 'i-04e08bcedc0870665'
    IMAGEREPOSITORY   = 'ajaydhadi95/flightfinder-backend'
    CONTAINERNAME     = 'flightfinder-backend'
    APPLICATIONPORT   = '8080'
}
`

Jenkins Workspace

`text
/var/lib/jenkins/workspace/flightfinder-Backend
`

The workspace path is managed by Jenkins and should not be referenced directly by deployment scripts.

AWS IAM and SSM Configuration
Jenkins EC2 Role

The Jenkins EC2 instance uses:

`text
jenkins-ec2-role
`

A least-privilege policy should restrict deployment commands to the intended managed instance and SSM document.

Example policy template:

`json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendDeploymentCommand",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ssm:ap-south-1::document/AWS-RunShellScript",
        "arn:aws:ec2:ap-south-1:ACCOUNTID:instance/i-04e08bcedc0870665"
      ]
    },
    {
      "Sid": "ReadCommandStatus",
      "Effect": "Allow",
      "Action": [
        "ssm:GetCommandInvocation",
        "ssm:ListCommandInvocations",
        "ssm:ListCommands",
        "ssm:DescribeInstanceInformation"
      ],
      "Resource": ""
    }
  ]
}
`

Replace ACCOUNTID before applying the policy.

Backend EC2 Role

Attach an instance profile that includes:

`text
AmazonSSMManagedInstanceCore
`

Check whether the backend is registered with Systems Manager:

`bash
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=i-04e08bcedc0870665" \
  --region ap-south-1
`

CI/CD Pipeline

The pipeline executes the following stages:

Checkout the main branch.
Run Maven tests.
Package the Spring Boot JAR.
Build the Docker image.
Tag the image with the Jenkins build number and latest.
Push both tags to Docker Hub.
Deploy the immutable build-number tag through SSM.
Verify command completion and container status.

Example Jenkinsfile

`groovy
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        AWSREGION       = 'ap-south-1'
        BACKENDINSTANCE = 'i-04e08bcedc0870665'
        IMAGEREPOSITORY = 'ajaydhadi95/flightfinder-backend'
        CONTAINERNAME   = 'flightfinder-backend'
        APPLICATIONPORT = '8080'
        IMAGETAG        = "${BUILDNUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Maven Build and Test') {
            steps {
                sh '''
                    chmod +x mvnw
                    ./mvnw clean test
                '''
            }
        }

        stage('Package JAR') {
            steps {
                sh './mvnw package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build \
                      -t ${IMAGEREPOSITORY}:${IMAGETAG} \
                      -t ${IMAGEREPOSITORY}:latest \
                      .
                '''
            }
        }

        stage('Docker Login and Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKERHUBUSERNAME',
                        passwordVariable: 'DOCKERHUBTOKEN'
                    )
                ]) {
                    sh '''
                        echo "$DOCKERHUBTOKEN" |
                          docker login \
                            --username "$DOCKERHUBUSERNAME" \
                            --password-stdin

                        docker push ${IMAGEREPOSITORY}:${IMAGETAG}
                        docker push ${IMAGEREPOSITORY}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy Through SSM') {
            steps {
                script {
                    def parameters = """{
                      "commands": [
                        "set -e",
                        "docker pull ${IMAGEREPOSITORY}:${IMAGETAG}",
                        "docker stop ${CONTAINERNAME} || true",
                        "docker rm ${CONTAINERNAME} || true",
                        "docker run -d --name ${CONTAINERNAME} --restart unless-stopped -p ${APPLICATIONPORT}:${APPLICATIONPORT} ${IMAGEREPOSITORY}:${IMAGETAG}",
                        "docker ps --filter name=${CONTAINERNAME}",
                        "docker logs --tail 50 ${CONTAINERNAME}"
                      ]
                    }"""

                    def commandId = sh(
                        script: """
                            aws ssm send-command \
                              --instance-ids '${BACKENDINSTANCE}' \
                              --document-name 'AWS-RunShellScript' \
                              --parameters '${parameters}' \
                              --region '${AWSREGION}' \
                              --query 'Command.CommandId' \
                              --output text
                        """,
                        returnStdout: true
                    ).trim()

                    sh """
                        aws ssm wait command-executed \
                          --command-id '${commandId}' \
                          --instance-id '${BACKENDINSTANCE}' \
                          --region '${AWSREGION}'

                        aws ssm get-command-invocation \
                          --command-id '${commandId}' \
                          --instance-id '${BACKENDINSTANCE}' \
                          --region '${AWSREGION}'
                    """
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout || true'
            cleanWs()
        }

        success {
            echo "FlightFinder backend deployment completed successfully."
        }

        failure {
            echo "FlightFinder backend deployment failed. Review the stage logs."
        }
    }
}
`

> [!CAUTION]
> The deployment above stops the old container before confirming that the new container is healthy. For production, use an ALB with rolling or blue-green deployment to avoid downtime.

Deployment Verification
Check SSM Command Status

Send a diagnostic command:

`bash
COMMANDID=$(aws ssm send-command \
  --instance-ids "i-04e08bcedc0870665" \
  --document-name "AWS-RunShellScript" \
  --parameters 'commands=["docker ps","docker logs --tail 100 flightfinder-backend"]' \
  --region ap-south-1 \
  --query "Command.CommandId" \
  --output text)

echo "$COMMANDID"
`

Retrieve the result:

`bash
aws ssm get-command-invocation \
  --command-id "$COMMANDID" \
  --instance-id "i-04e08bcedc0870665" \
  --region ap-south-1
`

A successful invocation should report:

`text
Status: Success
ResponseCode: 0
`

Check the Container

Run through SSM:

`bash
docker ps --filter name=flightfinder-backend
docker inspect flightfinder-backend
docker logs --tail 100 flightfinder-backend
`

Test the Backend

From a resource with network connectivity to the private application subnet:

`bash
curl --fail --show-error http://10.0.11.171:8080/
`

For an API endpoint:

`bash
curl --fail --show-error \
  http://10.0.11.171:8080/<API-ENDPOINT>
`

If Spring Boot Actuator is configured:

`bash
curl --fail --show-error \
  http://10.0.11.171:8080/actuator/health
`

Expected response:

`json
{
  "status": "UP"
}
`

Check Database Connectivity

Review startup logs:

`bash
docker logs flightfinder-backend 2>&1 |
  grep -Ei "mysql|datasource|hikari|database|exception|error"
`

Test network connectivity from the backend EC2:

`bash
nc -zv <RDS-ENDPOINT> 3306
`

Do not use the private EC2 URL from a public browser. 10.0.11.171 is a private address and is reachable only through the VPC, VPN, peering, or another connected network path.

Rollback Procedure

Use a previously known-good build-number tag rather than latest.

`bash
PREVIOUSTAG="<KNOWNGOODBUILDNUMBER>"
`

Send the rollback command:

`bash
aws ssm send-command \
  --instance-ids "i-04e08bcedc0870665" \
  --document-name "AWS-RunShellScript" \
  --parameters "commands=[
    \"set -e\",
    \"docker pull ajaydhadi95/flightfinder-backend:${PREVIOUSTAG}\",
    \"docker stop flightfinder-backend || true\",
    \"docker rm flightfinder-backend || true\",
    \"docker run -d --name flightfinder-backend --restart unless-stopped -p 8080:8080 ajaydhadi95/flightfinder-backend:${PREVIOUSTAG}\",
    \"docker ps --filter name=flightfinder-backend\",
    \"docker logs --tail 50 flightfinder-backend\"
  ]" \
  --region ap-south-1
`

After rollback:

Confirm that the SSM command returned Success.
Check the Docker container state.
Check Spring Boot startup logs.
Call the application health endpoint.
Record the failed and restored image tags.

Troubleshooting
Git Reports Untracked Files

Symptom:

`text
nothing added to commit but untracked files present
`

Resolution:

`bash
git status
git add .
git commit -m "chore: add backend project files"
git push origin main
`

Duplicate bin/ Directory

Symptom: Generated classes and duplicate source files are tracked.

Resolution:

`bash
git rm -r --cached bin
printf "\nbin/\ntarget/\n.class\n" >> .gitignore
git add .gitignore
git commit -m "chore: remove generated build files"
git push origin main
`

AWS CLI Returns NoCredentials

Cause: Jenkins EC2 does not have a usable IAM instance profile.

Checks:

`bash
aws sts get-caller-identity
curl -s http://169.254.169.254/latest/meta-data/iam/info
`

Resolution: Attach the intended IAM role to Jenkins EC2 and verify that its policy permits the required SSM actions.

SSM Permission Is Denied

Symptom: AccessDeniedException for actions such as ssm:SendCommand or ssm:DescribeInstanceInformation.

Resolution: Update the Jenkins instance role with the missing action and restrict the resource scope where AWS supports it.

Backend Is Not Online in SSM

Check the SSM Agent:

`bash
sudo systemctl status amazon-ssm-agent
sudo systemctl restart amazon-ssm-agent
`

Also verify:

• The backend instance has the correct IAM instance profile.
• DNS resolution is enabled in the VPC.
• The instance can reach SSM service endpoints.
• Security groups and network ACLs allow outbound HTTPS.
• The system clock is synchronized.

Docker Is Missing

Symptom:

`text
docker: not found
`

Install Docker using the package process appropriate for the backend instance's Linux distribution, then verify:

`bash
docker --version
sudo systemctl enable --now docker
systemctl is-active docker
`

Docker Permission Is Denied

Check access as the Jenkins user:

`bash
sudo -u jenkins docker ps
`

If Jenkins needs group access:

`bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
`

For an administrative Ubuntu user:

`bash
sudo usermod -aG docker ubuntu
`

A new login session is required after changing group membership.

> [!WARNING]
> Membership in the docker group provides privileges comparable to root access. Limit membership to trusted administrative and automation accounts.

No Existing Container

Message:

`text
No such container: flightfinder-backend
`

This is expected during the first deployment. The deployment handles it with:

`bash
docker stop flightfinder-backend || true
docker rm flightfinder-backend || true
`

Container Starts and Immediately Exits

Inspect its state and logs:

`bash
docker ps -a --filter name=flightfinder-backend
docker inspect flightfinder-backend \
  --format '{{.State.Status}} {{.State.ExitCode}} {{.State.Error}}'
docker logs --tail 200 flightfinder-backend
`

Common causes include:

• Missing database environment variables
• Invalid RDS endpoint or credentials
• RDS security group blocking port 3306
• Incorrect application profile
• Port conflicts
• Unsupported JAR or Java configuration

Image Pull Fails

Test the image manually:

`bash
docker pull ajaydhadi95/flightfinder-backend:<IMAGE_TAG>
`

Check:

• The requested tag exists.
• The backend has outbound internet access.
• Docker Hub is reachable.
• Registry credentials are configured if the repository is private.

Security Considerations

The existing design avoids direct SSH deployment, but production deployments should also implement the following controls:

• Use least-privilege IAM policies instead of unrestricted deployment permissions.
• Keep the backend EC2 instance in a private subnet without a public IP.
• Do not open inbound port 22 unless there is a documented operational requirement.
• Allow backend port 8080 only from an ALB security group or trusted internal source.
• Allow RDS port 3306 only from the backend security group.
• Store database credentials in AWS Secrets Manager or SSM Parameter Store.
• Encrypt RDS storage, snapshots, EBS volumes, and secrets with AWS KMS.
• Enable CloudTrail, CloudWatch Logs, VPC Flow Logs, and SSM command logging.
• Use Docker Hub access tokens rather than account passwords.
• Deploy immutable build-number or digest references rather than relying on latest.
• Add image vulnerability scanning and dependency scanning to the pipeline.
• Rotate credentials and tokens according to organizational policy.
• Do not commit passwords, tokens, .env files, or private keys.

Production Improvements
Application Load Balancer

Expose the API through an internet-facing ALB while keeping backend instances private:

`text
Internet
   |
   v
Application Load Balancer
   |
   v
Private Backend EC2
   |
   v
Amazon RDS MySQL
`

High Availability

Run multiple backend instances across availability zones:

`text
                         ALB
                          |
                +---------+---------+
                |                   |
                v                   v
        Backend EC2 AZ-1     Backend EC2 AZ-2
                |                   |
                +---------+---------+
                          |
                          v
                    Amazon RDS
`

Recommended additions:

• Auto Scaling Group for backend instances
• ALB health checks
• RDS Multi-AZ
• HTTPS with AWS Certificate Manager
• Route 53 DNS
• AWS WAF
• CloudWatch dashboards and alarms
• Centralized application logs
• Automated database backups
• Blue-green or rolling deployment
• Automated rollback on failed health checks
• ECS, EKS, or AWS CodeDeploy for container orchestration and safer releases

Operational Checklist
Before Deployment
• [ ] The main branch contains the approved changes.
• [ ] Jenkins is active.
• [ ] Docker is active on Jenkins and backend EC2.
• [ ] The Jenkins user can access Docker.
• [ ] Jenkins can call aws sts get-caller-identity.
• [ ] Backend EC2 is online in Systems Manager.
• [ ] Docker Hub credentials are valid.
• [ ] Required application secrets are available.
• [ ] RDS is available and its security group permits backend access.
• [ ] A rollback image tag has been identified.

After Deployment
• [ ] Jenkins completed with SUCCESS.
• [ ] SSM returned response code 0.
• [ ] The expected build-number image is running.
• [ ] The container restart policy is enabled.
• [ ] Spring Boot started without errors.
• [ ] The health endpoint reports UP.
• [ ] Database connectivity is working.
• [ ] Application logs contain no unexpected exceptions.
• [ ] Deployment details were recorded.

GIF Animation Setup

Place the GIF at:

`text
docs/assets/flightfinder-cicd.gif
`

A useful animation sequence is:

`text
Developer Push
      |
      v
GitHub main
      |
      v
Jenkins Build and Test
      |
      v
Docker Build
      |
      v
Docker Hub
      |
      v
AWS Systems Manager
      |
      v
Private Backend EC2
      |
      v
Amazon RDS MySQL
`

Recommended GIF settings:

| Property | Recommendation |
|---|---|
| Canvas | 1600 x 900 |
| Aspect ratio | 16:9 |
| Duration | 8-12 seconds |
| Frame rate | 12-20 FPS |
| Loop | Infinite |
| File size | Below 10 MB where practical |
| Theme | Dark AWS architecture style |
| Text | Large and readable |
| Sensitive information | Exclude credentials and database endpoints |

Commit the runbook and animation:

`bash
mkdir -p docs/assets

git add RUNBOOK.md docs/assets/flightfinder-cicd.gif
git commit -m "docs: add professional CI/CD deployment runbook"
git push origin main
``

<p align="center">
  <strong>FlightFinder Backend CI/CD</strong><br>
  GitHub to Jenkins to Docker Hub to AWS SSM to private EC2 to Amazon RDS
</p>
