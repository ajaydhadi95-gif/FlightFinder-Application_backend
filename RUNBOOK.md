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




## 1.1 Architecture

<img width="1536" height="1024" alt="257583ad-51ad-4998-b8a1-54c1caaaf051" src="https://github.com/user-attachments/assets/d79accf4-45fb-4c5e-8fcc-a81067fae6fc" />



**Important architecture notes:**

## ⚠️ Important Architecture Notes

### 🔐 Security
- **The frontend must NOT access the database directly.** Only the backend should talk to RDS.
- **Always keep RDS in a private subnet** with public access disabled.
- In the RDS security group, allow **port 3306 only from the backend security group**.
- Never store secrets (DB username/password) in code. Use Kubernetes Secrets or AWS Secrets Manager.

### 🌐 Networking
- Internet traffic flows only through **Internet Gateway → Public Load Balancer → Frontend Pods**.
- Frontend-to-backend communication uses **internal Kubernetes Services**.
- VPC CIDR is `10.0.0.0/16`, with private subnets for the backend and database.

### 🚀 CI/CD
- Pipeline: **Developer → GitHub → Jenkins (EC2) → Docker Build → Docker Hub → EKS Deployment**.
- Frontend and backend images are built separately and stored in **Docker Hub**.
- Use a unique image tag per build (for example, the build number) instead of relying on `latest`.
- Keep Jenkins credentials (Docker Hub, kubeconfig) in the Jenkins Credentials Store.

### ☸️ Kubernetes
- Create separate **Deployments** and **Services** for the frontend and backend.
- Set resource `requests/limits` and health checks (`readinessProbe`, `livenessProbe`) for pods.
- Use a **Horizontal Pod Autoscaler (HPA)** to handle higher traffic.

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

# Runbook 06: Amazon EKS Setup

**Project:** Three-Tier Application on AWS  
**Cluster Name:** `devops-eks`  
**AWS Region:** `ap-south-1`

## 6. Amazon EKS Setup

Amazon EKS provides the managed Kubernetes control plane for running the application.

### Configuration

| Setting | Value |
|---|---|
| Cluster name | `devops-eks` |
| Kubernetes version | `1.34` |
| AWS Region | `ap-south-1` |
| Desired worker nodes | 2 |
| Minimum worker nodes | 2 |
| Maximum worker nodes | 3 |
| Worker instance type | `t3.medium` |

Verify that Kubernetes version `1.34` is supported by AWS EKS before creating the cluster.

## 6.1 Check EKS Cluster Status

Run in PowerShell:

```powershell
aws eks describe-cluster --name devops-eks --region ap-south-1 --query "cluster.status" --output text
```

**Expected output:**

```text
ACTIVE
```

## 6.2 Configure kubectl

Connect `kubectl` to the EKS cluster:

```powershell
aws eks update-kubeconfig --name devops-eks --region ap-south-1
```

Check worker nodes:

```powershell
kubectl get nodes
```

Check all Kubernetes pods:

```powershell
kubectl get pods -A
```

**Expected result:** Worker nodes should show `Ready` when the node group is provisioned and healthy.

## 6.3 Troubleshooting

If `kubectl` cannot connect, check the following:

1. EKS cluster status.
2. AWS identity and credentials.
3. EKS access entries and cluster authentication permissions.
4. Kubernetes API endpoint connectivity.
5. Network restrictions if the API endpoint is private.
6. Worker node health and IAM permissions.

Useful commands:

```powershell
aws sts get-caller-identity
```

```powershell
aws eks describe-cluster --name devops-eks --region ap-south-1
```

```powershell
kubectl get nodes
```

```powershell
kubectl cluster-info
```

## 6.4 Completion Checklist

- [ ] EKS cluster status is `ACTIVE`.
- [ ] `kubectl` is configured successfully.
- [ ] Worker nodes are `Ready`.
- [ ] Kubernetes pods can be listed.
- [ ] AWS credentials and EKS permissions are working.

**Result:** The EKS cluster is ready for deploying the frontend and backend containers using Kubernetes manifests.

# Runbook 07: Docker Hub Image Management and EKS Deployment

**Project:** Three-Tier Application on AWS  
**Container Registry:** Docker Hub  
**Container Orchestration:** Amazon EKS  
**CI/CD Tool:** Jenkins  
**Infrastructure as Code:** Terraform  
**Region:** ap-south-1

---

## 1. Objective

This runbook describes how to build, tag, and push frontend and backend Docker images to Docker Hub and use those images in Kubernetes Deployments running on Amazon EKS.

## 2. Deployment Workflow

GitHub → Jenkins on EC2 → Docker Build → Docker Hub → Amazon EKS → Kubernetes Pods

## 3. Prerequisites

Before starting, verify that the following tools are installed and configured:

- Docker
- AWS CLI
- kubectl
- Jenkins
- Terraform
- Access to the application GitHub repository
- Docker Hub account
- An existing EKS cluster with working Kubernetes access

Verify Docker:

```powershell
docker --version
docker info
```

Verify AWS identity:

```powershell
aws sts get-caller-identity
```

Verify Kubernetes connectivity:

```powershell
kubectl get nodes
```

**Expected result:** Docker is operational, AWS identity is displayed, and EKS nodes are accessible.

## 4. Docker Hub Authentication

### Step 4.1 — Log in

```powershell
docker login
```

Authenticate using your Docker Hub username and password or access token.

### Step 4.2 — Verify authentication

```powershell
docker info
```

Check the output for the Docker Hub username.

## 5. Build and Push the Frontend Image

Run these commands from the project root directory, assuming the frontend Dockerfile is located in `./frontend`.

### Step 5.1 — Build the image

```powershell
docker build -t YOUR_DOCKERHUB_USERNAME/booking-frontend:latest ./frontend
```

### Step 5.2 — Verify the image

```powershell
docker images
```

### Step 5.3 — Push the image

```powershell
docker push YOUR_DOCKERHUB_USERNAME/booking-frontend:latest
```

**Validation:** Open Docker Hub and verify that the `booking-frontend` repository contains the pushed image.

## 6. Build and Push the Backend Image

Assumption: The backend Dockerfile is located in `./backend`. Adjust the path if your repository uses a different structure.

### Step 6.1 — Build the image

```powershell
docker build -t YOUR_DOCKERHUB_USERNAME/booking-backend:latest ./backend
```

### Step 6.2 — Push the image

```powershell
docker push YOUR_DOCKERHUB_USERNAME/booking-backend:latest
```

### Step 6.3 — Validate

Verify that the `booking-backend` repository and its image tag are available in Docker Hub.

Replace `YOUR_DOCKERHUB_USERNAME` with your actual username in every command.

## 7. Configure Kubernetes Deployments

Update the image field in the frontend Deployment YAML:

```yaml
spec:
  template:
    spec:
      containers:
        - name: frontend
          image: YOUR_DOCKERHUB_USERNAME/booking-frontend:latest
          ports:
            - containerPort: 80
```

Update the image field in the backend Deployment YAML:

```yaml
spec:
  template:
    spec:
      containers:
        - name: backend
          image: YOUR_DOCKERHUB_USERNAME/booking-backend:latest
          ports:
            - containerPort: 8080
```

The container ports above are examples. Use the actual ports configured in your application and Dockerfiles.

## 8. Deploy to Amazon EKS

Assuming the Kubernetes manifests are stored in a `k8s` directory:

### Step 8.1 — Verify cluster access

```powershell
kubectl get nodes
kubectl get namespaces
```

### Step 8.2 — Apply the Kubernetes manifests

```powershell
kubectl apply -f ./k8s/
```

### Step 8.3 — Monitor Deployments

```powershell
kubectl get deployments -A
kubectl get pods -A
kubectl get services -A
```

### Step 8.4 — Check rollout status

Use the actual namespace and Deployment names:

```powershell
kubectl rollout status deployment/booking-frontend -n default
kubectl rollout status deployment/booking-backend -n default
```

**Expected result:** The Deployments report successful rollout, and their Pods reach the `Running` state.

## 9. Troubleshooting

| Problem | Diagnostic command | Action |
|---|---|---|
| Docker login fails | `docker login` | Check credentials or access token. |
| Image push is denied | `docker push YOUR_DOCKERHUB_USERNAME/booking-frontend:latest` | Verify repository name and permissions. |
| Pod reports `ImagePullBackOff` | `kubectl describe pod POD_NAME -n NAMESPACE` | Verify image path, tag, registry access, and pull secrets if required. |
| Pod reports `CrashLoopBackOff` | `kubectl logs POD_NAME -n NAMESPACE` | Inspect application logs and container configuration. |
| Deployment rollout fails | `kubectl describe deployment DEPLOYMENT_NAME -n NAMESPACE` | Inspect events, image settings, probes, and resource limits. |
| EKS connection fails | `kubectl get nodes` | Check AWS credentials, kubeconfig, and EKS access permissions. |



## 10. Rollback Procedure

If a new Deployment fails, identify the affected Deployment and review its rollout history:

```powershell
kubectl rollout history deployment/booking-frontend -n default
```

Roll back to the previous revision if appropriate:

```powershell
kubectl rollout undo deployment/booking-frontend -n default
```

Verify the rollback:

```powershell
kubectl rollout status deployment/booking-frontend -n default
kubectl get pods -n default
```

Repeat for the backend only if necessary.

## 11. Completion Checklist

- [ ] Docker Hub authentication successful
- [ ] Frontend image built and pushed
- [ ] Backend image built and pushed
- [ ] Kubernetes manifests reference the correct images
- [ ] EKS cluster connectivity verified
- [ ] Kubernetes manifests applied successfully
- [ ] Frontend and backend Deployments healthy
- [ ] Application connectivity tested
- [ ] Logs and rollout status reviewed


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
