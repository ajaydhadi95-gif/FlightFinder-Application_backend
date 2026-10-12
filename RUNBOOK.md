# AWS 3-Tier Booking Application
## Complete DevOps Project Runbook

**Project Type:** Production-style 3-Tier Application  
**Cloud Provider:** AWS  
**Region:** `ap-south-1` (Mumbai)  
**Infrastructure as Code:** Terraform  
**CI/CD:** Jenkins  
**Containerization:** Docker  
**Container Orchestration:** Amazon EKS  
**Container Registry:** Amazon ECR  
**Database:** Amazon RDS MySQL  
**Monitoring:** CloudWatch (planned/verify configuration)

---

# 1. Project Objective

The objective is to deploy a 3-tier application on AWS using DevOps tools and automation.

The project separates the application into three layers:

1. **Presentation Layer:** Frontend application.
2. **Application Layer:** Backend API.
3. **Data Layer:** AWS RDS MySQL database.

Terraform provisions AWS infrastructure, Jenkins automates the CI/CD pipeline, Docker packages the application, and EKS manages the application containers.

## 1.1 Architecture

```text
                         USERS
                           |
                           v
                    Internet Gateway
                           |
                           v
                    Public Load Balancer
                           |
                           v
                  Amazon EKS Cluster
                 /                   \
                v                     v
          Frontend Pods          Backend Pods
                |                     |
                |                     v
                |              RDS MySQL:3306
                |                     |
                +---------------------+
                                      |
                                      v
                              bookingdb Database
                                  bookings Table


CI/CD PIPELINE

Developer
    |
    v
GitHub Repository
    |
    v
Jenkins on EC2
    |
    v
Docker Build
    |
    v
Amazon ECR
    |
    v
Amazon EKS Deployment
    |
    v
Application Running
```

**Important architecture notes:**

- EKS worker nodes should run in private subnets.
- The public application entry point should be a properly configured load balancer or ingress.
- The backend should access RDS privately.
- Jenkins runs on a separate EC2 instance in the current design.
- Terraform provisions infrastructure; Kubernetes manifests deploy the application.
- The exact load-balancer and ingress configuration must be verified in the AWS environment.

---

# 2. Infrastructure Components

| Component | Purpose |
|---|---|
| VPC | Isolated AWS network |
| Public subnets | Internet-facing resources and load-balancer entry point |
| Private subnets | EKS worker nodes and application workloads |
| Database subnets | Private RDS placement |
| Internet Gateway | Internet connectivity for public resources |
| NAT Gateway | Outbound internet access for private resources, if configured |
| Jenkins EC2 | CI/CD automation |
| IAM | AWS permissions |
| Security Groups | Control network traffic |
| Amazon EKS | Kubernetes control plane |
| EKS worker nodes | Run application pods |
| Amazon ECR | Store Docker images |
| Amazon RDS | Managed MySQL database |

---

# 3. Prerequisites

Before starting, verify the following:

- AWS account and required IAM permissions.
- AWS CLI installed and configured.
- Terraform installed.
- Git installed.
- Docker installed where image builds are performed.
- kubectl installed.
- Jenkins installed and running.
- A valid EC2 key pair if SSH access is required.
- Required VPC, subnet and security-group configuration.
- Application source code available in GitHub.

Verify tool installations:

```bash
aws --version
terraform -version
kubectl version --client
docker --version
git --version
```

On the Jenkins EC2 instance, also verify:

```bash
java -version
jenkins --version
```

Run only the commands relevant to the machine being checked.

---

# 4. Terraform Infrastructure Setup

## 4.1 Project Directory

The current Terraform project uses a modular structure similar to:

```text
terraform/
├── provider.tf
├── main.tf
├── variables.tf
└── module/
    ├── vpc/
    ├── security_group/
    ├── iam/
    ├── ec2/
    ├── eks/
    └── rds/
```

The modules separate infrastructure into manageable components.

## 4.2 Configure AWS Provider

The provider configuration uses the AWS region `ap-south-1` and AWS provider version constraint `~> 6.0`.

Verify your provider configuration before running Terraform.

## 4.3 Initialize Terraform

Open PowerShell in the Terraform directory:

```powershell
cd "C:\Users\vijay\OneDrive\Desktop\Three Tier Project\terraform"

terraform init
```

Purpose:

- Downloads required providers.
- Initializes Terraform modules.
- Prepares the working directory.

## 4.4 Format and Validate

```powershell
terraform fmt -recursive
terraform validate
```

Expected validation result:

```text
Success! The configuration is valid.
```

## 4.5 Review the Execution Plan

```powershell
terraform plan
```

Review every proposed addition, update, replacement and deletion.

**Do not apply a plan containing unexpected resource destruction.**

## 4.6 Apply Infrastructure

```powershell
terraform apply
```

Review the plan and type `yes` only when the changes are understood and approved.

Verify the output:

```powershell
terraform state list
```

The state list should contain the expected resources that Terraform currently manages.

## 4.7 Important Terraform Safety Rules

- Never commit passwords, private keys or other secrets to GitHub.
- Use a secure remote Terraform backend with state locking for team or production use.
- Back up important state and database data.
- Avoid manually deleting resources managed by Terraform.
- Do not run `terraform destroy` as a routine troubleshooting command.

---

# 5. Jenkins EC2 Setup and Verification

Jenkins is the CI/CD automation server.

## 5.1 Verify Jenkins Service

On the Jenkins EC2 instance:

```bash
sudo systemctl status jenkins
```

If the service is stopped:

```bash
sudo systemctl start jenkins
sudo systemctl enable jenkins
```

Check its logs if necessary:

```bash
sudo journalctl -u jenkins -n 100 --no-pager
```

## 5.2 Verify Jenkins Port

Jenkins commonly listens on port `8080`.

Check the listening port:

```bash
sudo ss -lntp | grep 8080
```

Ensure the Jenkins security group permits access only from approved administrator IP addresses or networks.

Do not expose Jenkins administration publicly without appropriate security controls.

## 5.3 Verify Jenkins Tools

```bash
java -version
docker --version
aws --version
kubectl version --client
terraform -version
helm version
```

If a command is unavailable, install or configure that tool before using it in the pipeline.

## 5.4 Jenkins Executor Troubleshooting

If the build displays:

```text
Still waiting to schedule task
Waiting for next available executor
```

Check:

1. Jenkins node or controller is online.
2. The node has an available executor.
3. The job's label requirements match the node.
4. No configuration restricts the job from running on available nodes.
5. Jenkins logs do not show an executor or node error.

Avoid setting up builds on the controller in a production environment when a separate build agent is available.

---

# 6. Amazon EKS Setup

EKS provides the managed Kubernetes control plane.

The current Terraform configuration specifies:

- Cluster name: `devops-eks`
- Kubernetes version configured in Terraform: `1.34`
- Region: `ap-south-1`
- Desired worker nodes: `2`
- Minimum worker nodes: `2`
- Maximum worker nodes: `3`
- Worker instance type: `t3.medium`

Verify that the configured Kubernetes version is supported by AWS when creating or updating the cluster.

## 6.1 Check EKS Status

From a machine with AWS CLI configured:

```powershell
aws eks describe-cluster `
  --name devops-eks `
  --region ap-south-1 `
  --query "cluster.status" `
  --output text
```

Expected result for a healthy active cluster:

```text
ACTIVE
```

## 6.2 Configure kubectl

```powershell
aws eks update-kubeconfig `
  --name devops-eks `
  --region ap-south-1
```

Verify connectivity:

```powershell
kubectl get nodes
kubectl get pods -A
```

Expected: worker nodes should be `Ready` once the node group is provisioned and healthy.

## 6.3 If kubectl Cannot Connect

Check:

- EKS cluster status.
- AWS identity used by the CLI.
- EKS access entries or required cluster authentication permissions.
- Kubernetes API endpoint reachability.
- Network restrictions if the endpoint is private.
- Worker node status and IAM permissions.

Useful commands:

```powershell
aws sts get-caller-identity

aws eks describe-cluster `
  --name devops-eks `
  --region ap-south-1
```

---

7.1 Docker Hub login
docker login


Apna Docker Hub username aur password/access token use karo.
7.2 Frontend image build aur push
docker build -t YOUR_DOCKERHUB_USERNAME/booking-frontend:latest ./frontend


docker push YOUR_DOCKERHUB_USERNAME/booking-frontend:latest


7.3 Backend image build aur push
docker build -t YOUR_DOCKERHUB_USERNAME/booking-backend:latest ./backend


docker push YOUR_DOCKERHUB_USERNAME/booking-backend:latest


YOUR_DOCKERHUB_USERNAME ki jagah apna actual Docker Hub username daalna. ./frontend aur ./backend paths ko apne project ke actual folders ke hisaab se use karna.
7.4 Kubernetes YAML mein images
Frontend Deployment:
image: YOUR_DOCKERHUB_USERNAME/booking-frontend:latest


Backend Deployment:
image: YOUR_DOCKERHUB_USERNAME/booking-backend:latest
## 7.3 Build and Push Images

Example for the frontend:

```bash
docker build -t booking-frontend:latest ./frontend
```

Tag the image:

```bash
docker tag booking-frontend:latest \
  <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/booking-frontend:latest
```

Push:

```bash
docker push \
  <ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/booking-frontend:latest
```

Repeat for the backend, using its actual build context and Dockerfile.

**Production recommendation:** Prefer immutable image tags based on the Git commit or build number instead of relying exclusively on `latest`.

---

# 8. Kubernetes Application Deployment

## 8.1 Recommended Kubernetes Directory

```text
k8s/
├── namespace.yaml
├── frontend/
│   ├── deployment.yaml
│   └── service.yaml
├── backend/
│   ├── deployment.yaml
│   └── service.yaml
└── ingress/
    └── ingress.yaml
```

RDS is managed separately by AWS, so a Kubernetes database deployment is not required when using RDS.

## 8.2 Create Namespace

```bash
kubectl create namespace booking-app
```

If it already exists, verify it rather than recreating it.

## 8.3 Deploy Application Manifests

After updating the manifests with valid image URIs and application configuration:

```bash
kubectl apply -f k8s/ -n booking-app
```

Verify:

```bash
kubectl get deployments -n booking-app
kubectl get pods -n booking-app
kubectl get services -n booking-app
kubectl get ingress -n booking-app
```

## 8.4 Verify Pod Health

```bash
kubectl describe pods -n booking-app
kubectl logs deployment/<BACKEND_DEPLOYMENT_NAME> -n booking-app
```

Replace placeholders with the actual deployment names.

For a multi-container pod, specify the container name when required.

## 8.5 Deployment Rollout

```bash
kubectl rollout status deployment/<DEPLOYMENT_NAME> -n booking-app
```

Check rollout history:

```bash
kubectl rollout history deployment/<DEPLOYMENT_NAME> -n booking-app
```

If a rollout fails, inspect the events and logs before deciding whether to roll back.

---

# 9. Amazon RDS MySQL

## 9.1 Current Database Details

| Item | Verified information |
|---|---|
| Database engine | MySQL |
| Database name | `bookingdb` |
| Application table | `bookings` |
| Port | `3306` |
| Hosting | AWS RDS |

The RDS endpoint and username should be obtained from your actual configuration. Do not put passwords in documentation or source control.

## 9.2 Connect to RDS

```bash
mysql -h <RDS_ENDPOINT> -P 3306 -u <DB_USERNAME> -p
```

Enter the password at the prompt.

## 9.3 Verify Database

```sql
SHOW DATABASES;

USE bookingdb;

SHOW TABLES;

DESCRIBE bookings;

SELECT COUNT(*) FROM bookings;

SELECT * FROM bookings LIMIT 10;
```

The correct table name is `bookings`. The name `actual_table_name` was only a placeholder and is not an existing table in this database.

## 9.4 Application Database Configuration

Configure the backend with the following settings using environment variables or a secure secret-management solution:

```text
DB_HOST=<RDS_ENDPOINT>
DB_PORT=3306
DB_NAME=bookingdb
DB_USERNAME=<DB_USERNAME>
DB_PASSWORD=<SECURE_SECRET>
```

These are configuration keys, not actual credentials.

Do not bake credentials into Docker images or commit them to GitHub.

## 9.5 Database Security

- Keep RDS in private database subnets.
- Permit TCP `3306` only from the required backend security group.
- Avoid public database access unless explicitly required and securely restricted.
- Use TLS for database connections where supported and configured.
- Back up important data before schema changes or destructive operations.

---

# 10. Security Group Configuration

The project contains separate security groups for Jenkins, EKS and RDS.

| Security group | Purpose |
|---|---|
| Jenkins SG | Jenkins web access and administration |
| EKS cluster SG | Cluster communication |
| EKS node SG | Worker node and application traffic |
| RDS SG | Database access |

## 10.1 MySQL Rule

The intended rule is:

```text
Protocol: TCP
Port: 3306
Destination: RDS security group
Source: Approved backend/EKS node security group
```

The exact source should match the network design and actual traffic path. Do not open the database to all IP addresses.

## 10.2 Previously Investigated Rule

The existing rule ID was:

```text
sgr-036d62ad55fa30a7d
```

Its AWS source was the Jenkins security group, not the EKS node security group. Terraform state had initially mapped it incorrectly.

The existing rule was imported under the Terraform resource:

```text
module.security_group.aws_vpc_security_group_ingress_rule.rds_mysql_from_jenkins
```

A separate EKS-to-RDS rule was planned.

**Verify the final Terraform apply and AWS security-group rules before considering this issue closed.**

---

# 11. Jenkins CI/CD Pipeline

The intended pipeline is:

```text
GitHub
   |
   v
Jenkins Pipeline
   |
   v
Checkout Source Code
   |
   v
Build and Test
   |
   v
Docker Build
   |
   v
Push Image to ECR
   |
   v
Deploy to EKS
   |
   v
Verify Rollout
```

## 11.1 Pipeline Stages

1. Checkout the source repository.
2. Install or verify required build tools.
3. Build the frontend or backend application.
4. Run available tests.
5. Build the Docker image.
6. Authenticate to ECR using an approved AWS identity.
7. Push the image to ECR.
8. Update the Kubernetes deployment.
9. Wait for rollout completion.
10. Collect logs and report failures.

The actual stages depend on the application language, repository structure and Jenkinsfile.

## 11.2 Jenkins Credentials

Store required secrets in Jenkins Credentials or an approved secrets manager.

Use least-privilege IAM permissions for ECR and EKS access.

Do not store AWS access keys or database passwords directly in the Jenkinsfile.

## 11.3 Jenkins Pipeline Failure

If a pipeline fails:

- Open the Jenkins build console output.
- Identify the first meaningful error.
- Verify Git access and repository credentials.
- Check build tools and Docker availability.
- Verify ECR authentication and repository permissions.
- Check EKS access and Kubernetes rollout status.

Useful commands:

```bash
docker images
aws sts get-caller-identity
kubectl get nodes
kubectl get pods -A
```

Run the commands on the machine where the corresponding tools and credentials are configured.

---

# 12. Troubleshooting Runbook

## 12.1 Jenkins Build Is Waiting for an Executor

**Symptoms:**

```text
Still waiting to schedule task
Waiting for next available executor
```

**Actions:**

1. Check Jenkins node status.
2. Check executor availability.
3. Review job labels and node restrictions.
4. Review Jenkins system logs.
5. Re-run the job after correcting the cause.

## 12.2 EKS Cluster Is Not Reachable

**Actions:**

1. Verify cluster status in AWS.
2. Verify the current AWS identity.
3. Update kubeconfig.
4. Check endpoint accessibility and EKS access permissions.
5. Verify worker node status.

## 12.3 Kubernetes Pods Are Not Starting

```bash
kubectl get pods -A
kubectl describe pod <POD_NAME> -n booking-app
kubectl logs <POD_NAME> -n booking-app
kubectl get events -n booking-app --sort-by=.metadata.creationTimestamp
```

Investigate common causes:

- Image pull errors.
- Missing configuration or secrets.
- Incorrect container port.
- Failed health probes.
- Insufficient CPU or memory.
- Application startup failures.

## 12.4 Backend Cannot Connect to RDS

Check the following:

1. RDS instance status.
2. Correct endpoint, database name and credentials.
3. RDS security-group ingress.
4. EKS node or pod network path to RDS.
5. DNS resolution and port reachability.
6. Backend application logs.

Do not make RDS public simply to bypass a connectivity problem.

## 12.5 Terraform Reports Unexpected Changes

```powershell
terraform plan
```

Review:

- Resource replacements.
- Planned deletions.
- Security-group changes.
- RDS changes.
- State mismatches.

Do not apply the plan until unexpected changes are understood.

---

# 13. Monitoring and Logging

Monitoring is part of the intended project and should be verified or implemented.

Recommended monitoring:

| Component | Monitoring |
|---|---|
| Jenkins EC2 | CPU, disk, memory and service health |
| EKS | Node health, pod status, restarts and resource usage |
| RDS | CPU, storage, connections and database events |
| Application | Error rate, latency and health endpoints |
| CI/CD | Build failures and deployment status |

AWS CloudWatch can collect supported AWS metrics and logs. Kubernetes container and application logs require the appropriate collection configuration.

Set alerts for meaningful conditions, such as high CPU, low database storage, repeated pod restarts and failed deployments.

---

# 14. Backup and Recovery

## 14.1 RDS Backup

1. Check automated-backup retention.
2. Verify recent snapshots or recovery points.
3. Take an approved snapshot before risky database changes.
4. Test restoring to a separate database instance.
5. Document recovery time and data-loss objectives.

## 14.2 Infrastructure Recovery

1. Inspect Terraform state.
2. Verify the AWS resources that currently exist.
3. Restore the intended Terraform configuration.
4. Review the execution plan.
5. Apply only after reviewing the changes.
6. Verify the application and database afterwards.

**Never use `terraform destroy` as a general fix for a configuration problem.** It can delete the cluster, EC2 instances, networking resources and potentially database resources, depending on configuration and deletion safeguards.

---

# 15. Daily Operational Checklist

- [ ] Jenkins service is running.
- [ ] Recent pipeline builds completed successfully.
- [ ] EKS cluster is active.
- [ ] Worker nodes are ready.
- [ ] Frontend and backend pods are healthy.
- [ ] Application endpoints respond correctly.
- [ ] Backend can connect to RDS.
- [ ] `bookingdb` and `bookings` are accessible.
- [ ] Security groups follow least-privilege rules.
- [ ] CloudWatch monitoring and alarms are configured.
- [ ] RDS backups are available.
- [ ] No unexpected Terraform changes are pending.

---

# 16. Project Completion Criteria

The project should be considered successfully deployed only after verifying all of the following:

1. Terraform provisions the intended AWS resources.
2. Jenkins can access the repository and run the pipeline.
3. Docker images are pushed to ECR.
4. EKS workloads deploy successfully.
5. The frontend is reachable through its intended entry point.
6. The backend can connect to RDS privately.
7. Booking operations work end to end.
8. Logs, monitoring and alerts are configured.
9. Database backups and recovery are tested.
10. No unexpected destructive Terraform changes remain.

---

# 17. Interview Explanation

“I built a three-tier application deployment architecture on AWS using Terraform, Jenkins, Docker, Amazon EKS, ECR and Amazon RDS MySQL. Terraform manages the infrastructure through reusable modules for networking, security groups, IAM, EC2, EKS and RDS. Jenkins automates the application build and deployment process, Docker packages the services, EKS orchestrates the containers, and RDS stores application data. I also worked on Terraform state reconciliation, security-group troubleshooting, database verification and operational runbook documentation.”

**Documentation note:** This runbook describes the intended architecture and the configuration and troubleshooting work completed so far. Mark each deployment, monitoring and recovery item complete only after verifying it in the actual AWS environment.
