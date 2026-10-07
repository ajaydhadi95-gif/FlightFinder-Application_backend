git push origin main\# FlightFinder Backend: Production Runbook



| Field | Value |

|---|---|

| Service | FlightFinder Backend (Spring Boot, Java 21) |

| Environment | Production (AWS `ap-south-1`) |

| Document owner | Ajay Dhadi (update with team / on-call alias when applicable) |

| Last reviewed | 2026-10-06 |

| Review cadence | Every 3 months, and after every incident or architecture change |

| Status of this document | Items marked \*\*\[VERIFY]\*\* are not confirmed in the source notes and must be checked before this runbook is relied on during an incident. Items marked \*\*\[RECOMMENDED]\*\* are target-state improvements, not the current state. |



\---



\## 1. Purpose and Scope



This runbook describes how to deploy, operate, monitor, troubleshoot, roll back, and recover the FlightFinder backend service. It is written so that an engineer who did not build the system can operate it at 3 AM.



\*\*In scope:\*\* Jenkins CI/CD pipeline, Docker image lifecycle, the private backend EC2 instance, AWS SSM-based deployment, connectivity to RDS MySQL.



\*\*Out of scope:\*\* Frontend (Vite + React) deployment, DNS and CDN (not yet implemented), application feature documentation.



\---



\## 2. Service Summary



| Item | Value |

|---|---|

| Application | Spring Boot (Maven), Java 21 |

| Container image | `ajaydhadi95/flightfinder-backend:<BUILD\_NUMBER>` and `:latest` |

| Registry | Docker Hub |

| Source repository | `https://github.com/ajaydhadi95-gif/FlightFinder-Application\_backend.git` (branch `main`) |

| CI/CD | Jenkins on EC2 (job workspace `/var/lib/jenkins/workspace/flightfinder-Backend`) |

| Runtime host | Private backend EC2 `i-04e08bcedc0870665`, private IP `10.0.11.171` |

| Container name | `flightfinder-backend` |

| App port | `8080` |

| Database | RDS MySQL, port `3306`, database `bookingdb` |

| Deployment channel | AWS Systems Manager (SSM) `AWS-RunShellScript`, no SSH |

| Container user | `appuser` (non-root) |



\### Dependencies



| Dependency | Failure impact |

|---|---|

| GitHub | No new builds. Running service is unaffected. |

| Jenkins EC2 | No deployments or rollbacks through the pipeline. Running service is unaffected. Manual rollback via SSM from any admin workstation is still possible (Section 9.2). |

| Docker Hub | New deploys and rollbacks that need an image not cached on the host will fail. The running container is unaffected. |

| AWS SSM | Cannot deploy or run remote commands. Running service is unaffected. |

| RDS MySQL | \*\*Service outage.\*\* Backend cannot serve booking data. |

| Backend EC2 | \*\*Service outage\*\* (single instance, no redundancy today). |



\---



\## 3. Architecture



\### 3.1 Network



```text

VPC 10.0.0.0/16  (ap-south-1)



Public subnets            10.0.1.0/24, 10.0.2.0/24     (Jenkins EC2, NAT)

Private app subnets       10.0.11.0/24, 10.0.12.0/24   (Backend EC2 10.0.11.171)

Private DB subnets        10.0.21.0/24, 10.0.22.0/24   (RDS MySQL)

```



\### 3.2 Request and deployment flow



```text

Developer -> git push -> GitHub (main)

&#x20;                          |

&#x20;                          v

&#x20;                    Jenkins EC2

&#x20;       Maven test -> Maven package -> Docker build -> Docker push

&#x20;                          |

&#x20;                          v

&#x20;                      Docker Hub

&#x20;                          |

&#x20;                 AWS SSM SendCommand (IAM role)

&#x20;                          |

&#x20;                          v

&#x20;             Private Backend EC2 (10.0.11.171)

&#x20;                 Docker container :8080

&#x20;                          |

&#x20;                          v

&#x20;                   RDS MySQL :3306

```



\### 3.3 Key design decisions



\- Backend is in a private subnet with no public IP. Reduces attack surface.

\- Deployments use SSM, not SSH. No SSH keys to manage, access is IAM-controlled and auditable in CloudTrail.

\- Jenkins uses an instance IAM role (`jenkins-ec2-role`), so no AWS access keys are stored on the server.

\- Every image is tagged with the Jenkins build number, which makes rollback to a known version possible.



\---



\## 4. Access and Permissions



\### 4.1 Who can do what



| Action | Required access |

|---|---|

| Trigger / view Jenkins pipeline | Jenkins login \*\*\[VERIFY: user list and auth method]\*\* |

| Run commands on backend EC2 | IAM permission for `ssm:SendCommand` on the instance |

| View logs | SSM command output or CloudWatch (once configured, see 8.3) |

| Change RDS / security groups | AWS admin IAM role \*\*\[VERIFY]\*\* |



\### 4.2 IAM role `jenkins-ec2-role` (attached to Jenkins EC2)



Current permissions:



```text

ssm:SendCommand

ssm:GetCommandInvocation

ssm:ListCommandInvocations

ssm:ListCommands

ssm:DescribeInstanceInformation

```



\*\*Current state:\*\* resources are `\*` (lab setup).

\*\*\[RECOMMENDED] before calling this production:\*\* scope `ssm:SendCommand` to the specific instance ARN and document ARN:



```json

{

&#x20; "Effect": "Allow",

&#x20; "Action": "ssm:SendCommand",

&#x20; "Resource": \[

&#x20;   "arn:aws:ec2:ap-south-1:<ACCOUNT\_ID>:instance/i-04e08bcedc0870665",

&#x20;   "arn:aws:ssm:ap-south-1::document/AWS-RunShellScript"

&#x20; ]

}

```



\### 4.3 Backend EC2 role



Must include `AmazonSSMManagedInstanceCore` so the SSM agent shows \*\*Online\*\*. \*\*\[VERIFY: role name]\*\*



\### 4.4 Credentials inventory



| Secret | Location | Rotation |

|---|---|---|

| Docker Hub access token | Jenkins credential `dockerhub-credentials` (username `ajaydhadi95`) | \*\*\[RECOMMENDED]\*\* rotate every 90 days |

| AWS access | Instance role, no static keys | Automatic |

| RDS username / password | \*\*\[VERIFY]\*\* how the container receives it (env var, `application.properties`, Secrets Manager). See Section 12.1 | \*\*\[RECOMMENDED]\*\* rotate every 90 days |

| GitHub access for Jenkins | \*\*\[VERIFY]\*\* | \*\*\[RECOMMENDED]\*\* deploy key or fine-grained token |



\---



\## 5. Configuration Reference



\### 5.1 Build



| Stage | Command |

|---|---|

| Test | `chmod +x mvnw \&\& ./mvnw clean test` |

| Package | `./mvnw clean package -DskipTests` |

| Artifact | `target/backend-0.0.1-SNAPSHOT.jar` |



Note: the project currently has \*\*no test sources\*\*, so "BUILD SUCCESS" on the test stage does not validate behavior (see Section 14).



\### 5.2 Docker image



\- Multi-stage Dockerfile: build on `eclipse-temurin:21-jdk-alpine`, run on `eclipse-temurin:21-jre-alpine`.

\- Runs as non-root `appuser`. Exposes `8080`.



\### 5.3 Runtime container



```bash

docker run -d \\

&#x20; --name flightfinder-backend \\

&#x20; --restart unless-stopped \\

&#x20; -p 8080:8080 \\

&#x20; ajaydhadi95/flightfinder-backend:<TAG>

```



\*\*\[VERIFY]\*\* How database settings reach the container. The command above passes no `-e` variables, so connection details must be baked into `application.properties`. Hardcoded credentials in an image are a production risk (Section 12.1).



\### 5.4 Repository hygiene



`.gitignore` must contain `target/`, `bin/`, `\*.class`.



\---



\## 6. CI/CD Pipeline



\### 6.1 Stages



| # | Stage | Success criteria |

|---|---|---|

| 1 | Checkout | `main` checked out at expected commit |

| 2 | Maven Build \& Test | `BUILD SUCCESS` |

| 3 | Package JAR | JAR produced, Spring Boot repackage succeeded |

| 4 | Docker Build | Tags `:<BUILD\_NUMBER>` and `:latest` created |

| 5 | Docker Login \& Push | Both tags pushed, digest printed |

| 6 | Deploy via SSM | SSM status `Success`, `ResponseCode: 0` |



\### 6.2 Known benign messages



\- `No tests to run.` Expected today. Not a failure, but see Section 14.

\- `Error response from daemon: No such container: flightfinder-backend` on the very first deploy. Expected. The `|| true` guards handle it.



\### 6.3 Pipeline pre-requisites checklist



\- \[ ] Jenkins service active: `sudo systemctl status jenkins`

\- \[ ] Jenkins user can run Docker: `sudo -u jenkins docker ps`

\- \[ ] Jenkins EC2 has role: `aws sts get-caller-identity`

\- \[ ] Backend EC2 is SSM \*\*Online\*\*:



```bash

aws ssm describe-instance-information \\

&#x20; --filters "Key=InstanceIds,Values=i-04e08bcedc0870665" \\

&#x20; --region ap-south-1 \\

&#x20; --query "InstanceInformationList\[0].PingStatus"

```



Expected output: `"Online"`.



\---



\## 7. Standard Deployment Procedure



\### 7.1 Pre-deployment checklist



\- \[ ] Change is merged to `main` via reviewed pull request \*\*\[RECOMMENDED: enforce with branch protection]\*\*

\- \[ ] Not deploying during a peak traffic window, or the change is approved for it

\- \[ ] Previous good image tag is known (see 7.4 / `docker ps` on the host)

\- \[ ] RDS is healthy (no maintenance in progress)

\- \[ ] Any database schema change is backward compatible with the currently running version



\### 7.2 Deploy



1\. Push or merge to `main`.

2\. Jenkins runs the pipeline (or trigger manually: Jenkins -> `flightfinder-Backend` -> Build Now).

3\. Watch the Console Output until `Finished: SUCCESS`.

4\. Note the build number. This is the new `IMAGE\_TAG`.



What the deploy stage executes on the backend EC2 via SSM:



```bash

docker pull ajaydhadi95/flightfinder-backend:${IMAGE\_TAG}

docker stop flightfinder-backend || true

docker rm flightfinder-backend || true

docker run -d --name flightfinder-backend --restart unless-stopped \\

&#x20; -p 8080:8080 ajaydhadi95/flightfinder-backend:${IMAGE\_TAG}

```



\*\*Important:\*\* this procedure stops the old container before the new one is verified. There is a downtime window of a few seconds to about a minute (Spring Boot startup). This is acceptable for the current single-instance setup. See Section 15 for zero-downtime options.



\### 7.3 Post-deployment verification (required)



Run via SSM from Jenkins EC2 or an admin workstation:



```bash

CMD\_ID=$(aws ssm send-command \\

&#x20; --instance-ids "i-04e08bcedc0870665" \\

&#x20; --document-name "AWS-RunShellScript" \\

&#x20; --parameters 'commands=\["docker ps --filter name=flightfinder-backend","docker logs --tail 80 flightfinder-backend","curl -s -o /dev/null -w \\"HTTP %{http\_code}\\\\n\\" http://localhost:8080/"]' \\

&#x20; --region ap-south-1 \\

&#x20; --query "Command.CommandId" --output text)



sleep 20



aws ssm get-command-invocation \\

&#x20; --command-id "$CMD\_ID" \\

&#x20; --instance-id "i-04e08bcedc0870665" \\

&#x20; --region ap-south-1 \\

&#x20; --query "{Status:Status,Output:StandardOutputContent,Err:StandardErrorContent}"

```



Pass criteria:



\- \[ ] Container status is `Up`, not `Restarting`

\- \[ ] Logs show Spring Boot `Started BackendApplication` and no `Communications link failure` or `Access denied` for MySQL

\- \[ ] HTTP check returns an expected code (200/401/404 all prove the app is listening; 5xx or connection refused does not)

\- \[ ] A real API smoke test passes: `curl http://localhost:8080/<API-ENDPOINT>` \*\*\[VERIFY: define 2-3 smoke-test endpoints and expected responses and list them here]\*\*



If any check fails, \*\*roll back immediately\*\* (Section 9).



\### 7.4 Recording the deployment



Record in the team channel / change log: date and time, build number, git commit SHA, who deployed, verification result.



\---



\## 8. Operations



\### 8.1 Run a command on the backend (no SSH)



```bash

aws ssm send-command \\

&#x20; --instance-ids "i-04e08bcedc0870665" \\

&#x20; --document-name "AWS-RunShellScript" \\

&#x20; --parameters 'commands=\["<COMMAND>"]' \\

&#x20; --region ap-south-1



aws ssm get-command-invocation \\

&#x20; --command-id "<COMMAND\_ID>" \\

&#x20; --instance-id "i-04e08bcedc0870665" \\

&#x20; --region ap-south-1

```



\### 8.2 Interactive shell via Session Manager (preferred for debugging)



```bash

aws ssm start-session --target i-04e08bcedc0870665 --region ap-south-1

```



Requires the Session Manager plugin on the workstation. Sessions are logged and IAM-controlled.



\### 8.3 Logs



| Source | How |

|---|---|

| Application logs | `docker logs --tail 200 flightfinder-backend` |

| Follow live | `docker logs -f --tail 50 flightfinder-backend` (via Session Manager) |

| Jenkins build logs | Jenkins console output |

| SSM command history | AWS console -> Systems Manager -> Run Command |

| Audit of who ran what | CloudTrail |



\*\*\[RECOMMENDED]\*\* Container logs live only on the instance. Ship them to CloudWatch Logs (Docker `awslogs` log driver or the CloudWatch agent) so they survive instance replacement, and set retention (for example 30 days).



\### 8.4 Routine health commands



| Purpose | Command (run on backend EC2) |

|---|---|

| Container state | `docker ps -a --filter name=flightfinder-backend` |

| Restart count | `docker inspect -f '{{.RestartCount}}' flightfinder-backend` |

| Resource usage | `docker stats --no-stream flightfinder-backend` |

| Disk | `df -h /` and `docker system df` |

| Memory | `free -m` |

| Running image tag | `docker inspect -f '{{.Config.Image}}' flightfinder-backend` |

| DB reachability | `nc -zv <RDS\_ENDPOINT> 3306` |



\### 8.5 Housekeeping



Old images accumulate on the backend EC2 and Jenkins EC2 and can fill the disk.



```bash

\# keep recent images, remove dangling and unused older than 7 days

docker image prune -a --filter "until=168h" -f

```



Run weekly or schedule it. \*\*Do not\*\* prune immediately before a rollback window closes: keep at least the last 3 tags available.



\---



\## 9. Rollback



\### 9.1 When to roll back



Roll back first, investigate second, when after a deployment: the container is crash-looping, the API returns 5xx for core endpoints, DB connection errors appear, or error rate or latency is clearly worse than before.



\### 9.2 Procedure



1\. Identify the last known good tag (previous successful Jenkins build number, or Docker Hub tags list).

2\. Deploy that tag via SSM:



```bash

GOOD\_TAG=3   # replace with last known good build number



aws ssm send-command \\

&#x20; --instance-ids "i-04e08bcedc0870665" \\

&#x20; --document-name "AWS-RunShellScript" \\

&#x20; --region ap-south-1 \\

&#x20; --parameters "commands=\[\\"docker pull ajaydhadi95/flightfinder-backend:${GOOD\_TAG}\\",\\"docker stop flightfinder-backend || true\\",\\"docker rm flightfinder-backend || true\\",\\"docker run -d --name flightfinder-backend --restart unless-stopped -p 8080:8080 ajaydhadi95/flightfinder-backend:${GOOD\_TAG}\\"]"

```



3\. Re-run the post-deployment verification (7.3).

4\. Announce the rollback and open an incident record (Section 11).



\*\*Never roll back using `:latest`.\*\* `latest` always points to the most recent push, which is the bad version.



\### 9.3 Database considerations



Rolling back the container does \*\*not\*\* roll back database schema or data changes. If the bad release ran a destructive or incompatible migration, restore from backup or snapshot (Section 13) instead of, or in addition to, container rollback.



\---



\## 10. Monitoring and Alerting



\*\*Current state:\*\* no monitoring or alerting is documented. This is the largest operational gap. Until alarms exist, outages are found by users.



\### 10.1 \[RECOMMENDED] Minimum alarm set (CloudWatch)



| Signal | Threshold (starting point) | Action |

|---|---|---|

| EC2 `StatusCheckFailed` | >= 1 for 2 min | Page |

| EC2 CPU | > 80% for 10 min | Notify |

| Memory and disk (via CloudWatch agent) | > 85% | Notify |

| SSM `PingStatus` not Online | 5 min | Notify |

| RDS CPUUtilization | > 80% for 10 min | Notify |

| RDS FreeStorageSpace | < 20% | Notify |

| RDS DatabaseConnections | near max | Notify |

| RDS FreeableMemory | low | Notify |

| ALB `HTTPCode\_Target\_5XX\_Count` (once ALB exists) | > threshold | Page |

| ALB `UnHealthyHostCount` | >= 1 | Page |

| Jenkins job failed | any | Notify |



Send alarms to an SNS topic with email / chat subscribers.



\### 10.2 \[RECOMMENDED] Application health endpoint



\*\*\[VERIFY]\*\* Whether Spring Boot Actuator is in `pom.xml`. If not, add `spring-boot-starter-actuator` and expose only `/actuator/health` (with liveness and readiness). This endpoint is what an ALB target group and container health checks should use.



Add a Docker health check to the Dockerfile or run command so `docker ps` shows `healthy` or `unhealthy`.



\### 10.3 Service level objectives (define and agree)



| SLI | Suggested initial SLO |

|---|---|

| Availability of API | 99.5% monthly (realistic for single instance) |

| p95 latency of search/booking endpoints | \*\*\[DEFINE]\*\* |

| Error rate (5xx) | < 1% |



\---



\## 11. Incident Response



\### 11.1 Severity



| Sev | Definition | Response target |

|---|---|---|

| SEV1 | Service fully down or data loss/corruption | Immediate, work until resolved |

| SEV2 | Major feature broken or heavy degradation | Within 30 min |

| SEV3 | Minor issue, workaround exists | Next business day |



\### 11.2 First 10 minutes (any incident)



1\. \*\*Confirm\*\* the problem and its scope (who is affected, since when).

2\. \*\*Check what changed\*\*: last Jenkins deploy time and build number. If an outage started right after a deploy, \*\*roll back\*\* (Section 9).

3\. \*\*Check the stack from the bottom up\*\* (below).

4\. \*\*Communicate\*\* status to stakeholders and keep a timeline.

5\. \*\*Mitigate\*\* before root-causing.



\### 11.3 Triage ladder



```text

1\. Is the EC2 running and SSM Online?        -> 11.4 A

2\. Is the container up?                      -> 11.4 B

3\. Is the app healthy (logs, port 8080)?     -> 11.4 C

4\. Can the app reach RDS?                    -> 11.4 D

5\. Is RDS itself healthy?                    -> 11.4 E

```



\### 11.4 Playbooks



\*\*A. Backend EC2 not reachable / SSM Offline\*\*



\- Check EC2 status checks in console. If impaired, reboot, then stop/start if reboot fails.

\- Check the instance profile still has `AmazonSSMManagedInstanceCore`.

\- Check the instance can reach SSM endpoints (NAT gateway healthy, or VPC endpoints `ssm`, `ssmmessages`, `ec2messages`).

\- The container uses `--restart unless-stopped`, so it should return automatically after a reboot \*\*provided the Docker service is enabled at boot\*\*: verify `systemctl is-enabled docker`.



\*\*B. Container not running or restarting repeatedly\*\*



```bash

docker ps -a --filter name=flightfinder-backend

docker logs --tail 200 flightfinder-backend

docker inspect -f '{{.State.ExitCode}} {{.State.OOMKilled}}' flightfinder-backend

```



\- `OOMKilled true` -> memory limit or instance too small. Check `free -m`, consider a larger instance or JVM memory flags.

\- Exit with stack trace -> usually config or DB problem (D). Roll back if just deployed.

\- Docker daemon down: `systemctl status docker`, `systemctl restart docker`.



\*\*C. App up but returning errors / slow\*\*



\- Read logs for exceptions; correlate with the time of first error.

\- Check CPU, memory, disk (8.4). A full disk breaks Docker and logging: prune images (8.5).

\- Check thread / connection pool exhaustion in logs (Hikari messages).



\*\*D. App cannot connect to database\*\*



Symptoms: `Communications link failure`, `Access denied`, `Unknown database`, timeouts.



\- Network: from backend EC2, `nc -zv <RDS\_ENDPOINT> 3306`. If it fails, check the RDS security group allows inbound 3306 from the backend's security group, and that route tables and NACLs allow it.

\- Credentials: confirm username/password/host/db name (`bookingdb`) in the app config match RDS. Check whether the password was rotated.

\- Connection limits: RDS `DatabaseConnections` at max -> check for leaks or restart the app.



\*\*E. RDS problem\*\*



\- Check RDS console status and events (failover, maintenance, storage full, reboot).

\- Storage full -> increase allocated storage (online operation), then investigate growth.

\- Instance unhealthy and Multi-AZ enabled -> failover may be automatic. If single-AZ, restore from snapshot (Section 13).



\*\*F. Pipeline failure (cannot deploy)\*\*



| Symptom | Likely cause / fix |

|---|---|

| `NoCredentials` in AWS CLI | Role detached from Jenkins EC2. Reattach `jenkins-ec2-role`. |

| `AccessDenied` on `ssm:\*` | Missing IAM permission (for example `ssm:DescribeInstanceInformation`). Add it. |

| `docker: not found` in SSM output | Docker not installed or not on PATH on backend EC2. |

| `permission denied` on `docker.sock` for a user | User not in `docker` group: `sudo usermod -aG docker <user>`, re-login. (Jenkins user already works.) |

| Docker push `denied` / `unauthorized` | Docker Hub token expired or revoked. Create a new token, update Jenkins `dockerhub-credentials`. |

| Docker Hub rate limit on pull | Authenticate pulls or use ECR (Section 15). |

| SSM command `Failed` | `get-command-invocation` and read `StandardErrorContent`. |

| Jenkins disk full | Prune Docker images and old workspaces/builds. |



\### 11.5 After the incident



Within 5 business days, write a blameless postmortem: timeline, impact, root cause, what worked, what did not, and action items with owners and dates. Update this runbook with anything learned.



\---



\## 12. Security



\### 12.1 Secrets management \*\*\[RECOMMENDED, high priority]\*\*



\- Do not store DB credentials in `application.properties` inside the image. Anyone with the image has the password.

\- Use AWS Secrets Manager or SSM Parameter Store (SecureString). Give the backend EC2 role read access to only that secret, and inject at start, for example:



```bash

DB\_PASS=$(aws ssm get-parameter --name /flightfinder/prod/db\_password \\

&#x20; --with-decryption --query Parameter.Value --output text --region ap-south-1)

docker run -d ... -e SPRING\_DATASOURCE\_PASSWORD="$DB\_PASS" ...

```



\- Never print secrets in Jenkins console output or SSM command parameters (SSM command text is stored and visible in history).



\### 12.2 Network



\- Backend SG inbound 8080 only from Jenkins SG / ALB SG, never `0.0.0.0/0`.

\- RDS SG inbound 3306 only from the backend SG.

\- No public IP on backend or RDS. RDS "Publicly accessible" = No.

\- \*\*\[VERIFY]\*\* Jenkins UI exposure: restrict port 8080/443 to known IPs or put it behind a VPN/ALB with TLS. Jenkins is the highest-value target in this setup because it can deploy to production.



\### 12.3 IAM



\- Least privilege on `jenkins-ec2-role` (Section 4.2).

\- Enable CloudTrail and keep it on. SSM commands are auditable there.

\- MFA for human IAM users and root account. No root usage.



\### 12.4 Supply chain



\- Pin base images by digest, rebuild regularly for security patches.

\- Scan images (Trivy or Docker Scout) in the pipeline and fail on critical CVEs.

\- Use a Docker Hub \*\*access token\*\*, never the account password (already the case).

\- Keep Jenkins and its plugins patched.



\### 12.5 Host hardening



\- Keep OS patched (SSM Patch Manager with a maintenance window).

\- IMDSv2 required on both EC2 instances.

\- Encrypted EBS volumes and encrypted RDS.



\---



\## 13. Backup and Disaster Recovery



\### 13.1 What needs protecting



| Asset | How it is protected today | Target |

|---|---|---|

| Source code | GitHub | Fine. Consider a mirror. |

| Docker images | Docker Hub tags | Retain at least the last 10 tags; consider ECR |

| Jenkins config / jobs | \*\*\[VERIFY]\*\*, probably only on the instance | Export job config (Jenkinsfile is already in repo). Snapshot Jenkins home EBS volume. |

| Database (bookingdb) | \*\*\[VERIFY]\*\* RDS automated backups and retention | See below |

| Infrastructure | Terraform (per your VPC/EC2/RDS project) | Remote state in S3 with locking |



\### 13.2 \[RECOMMENDED] RDS settings



\- Automated backups enabled, retention >= 7 days (14 to 35 preferred).

\- Multi-AZ enabled for production.

\- Deletion protection on. Final snapshot required on delete.

\- Take a manual snapshot before any risky change:



```bash

aws rds create-db-snapshot \\

&#x20; --db-instance-identifier <RDS\_ID> \\

&#x20; --db-snapshot-identifier pre-release-$(date +%Y%m%d-%H%M) \\

&#x20; --region ap-south-1

```



\### 13.3 Recovery objectives (set and test)



| Objective | Suggested starting target |

|---|---|

| RPO (acceptable data loss) | <= 5 minutes with RDS point-in-time restore |

| RTO (acceptable downtime) | <= 1 hour |



\### 13.4 Recovery procedures



\*\*Lost backend EC2:\*\* launch a replacement from Terraform in the private app subnet with the same instance profile and security group, confirm SSM Online, update the instance ID in the Jenkins pipeline (or switch to tag-based targeting), run the pipeline to deploy the latest good tag, verify (7.3).



\*\*Lost or corrupted database:\*\* restore to a new RDS instance from snapshot or point-in-time, point the app at the new endpoint, verify data, then cut over. Note the new endpoint changes connection config.



\*\*Lost Jenkins:\*\* rebuild from Terraform/AMI, reinstall Jenkins, reattach `jenkins-ec2-role`, add `dockerhub-credentials`, recreate the job from the repo's `Jenkinsfile`. Meanwhile, deploy manually via SSM (Section 9.2 command pattern).



\*\*Region outage:\*\* not covered today. Document an explicit decision whether multi-region is required.



\### 13.5 Test restores



Do a restore drill at least twice a year. A backup that has never been restored is unproven.



\---



\## 14. Quality Gates \*\*\[RECOMMENDED]\*\*



Today the pipeline can deploy code that has zero automated tests. Before treating this as production-grade:



\- Add unit and integration tests (Testcontainers MySQL is a good fit). Fail the build on test failure.

\- Add a post-deploy smoke test stage in the Jenkinsfile that calls the health endpoint and fails the pipeline if it does not return success.

\- Add automatic rollback on failed smoke test (snippet below).

\- Protect `main`: pull request required, at least one review, status checks required.



\### Example deploy script with health check and auto-rollback



```bash

\#!/bin/bash

set -u

NEW\_TAG="$1"

IMAGE="ajaydhadi95/flightfinder-backend"

NAME="flightfinder-backend"



PREV\_TAG=$(docker inspect -f '{{.Config.Image}}' "$NAME" 2>/dev/null | cut -d: -f2 || true)



docker pull "$IMAGE:$NEW\_TAG" || exit 1

docker stop "$NAME" 2>/dev/null || true

docker rm "$NAME" 2>/dev/null || true

docker run -d --name "$NAME" --restart unless-stopped -p 8080:8080 "$IMAGE:$NEW\_TAG"



for i in $(seq 1 30); do

&#x20; if curl -fs http://localhost:8080/actuator/health >/dev/null; then

&#x20;   echo "Healthy on $NEW\_TAG"; exit 0

&#x20; fi

&#x20; sleep 5

done



echo "Health check failed, rolling back to $PREV\_TAG"

docker logs --tail 100 "$NAME"

docker stop "$NAME"; docker rm "$NAME"

if \[ -n "$PREV\_TAG" ]; then

&#x20; docker run -d --name "$NAME" --restart unless-stopped -p 8080:8080 "$IMAGE:$PREV\_TAG"

fi

exit 1

```



(Requires the health endpoint from 10.2. Adjust the path if you use a different one.)



\---



\## 15. Production Hardening Roadmap



Ordered by value for effort.



| Priority | Improvement | Why |

|---|---|---|

| P0 | Move DB credentials to Secrets Manager / Parameter Store | Credentials must not live in the image |

| P0 | CloudWatch alarms + log shipping | Currently you learn about outages from users |

| P0 | Confirm RDS automated backups, Multi-AZ, deletion protection | Data loss protection |

| P0 | Restrict Jenkins exposure and scope IAM to specific instance ARN | Reduce blast radius |

| P1 | Health endpoint + post-deploy smoke test + auto-rollback | Safe deployments |

| P1 | Real automated tests in pipeline | Quality gate |

| P1 | Stop deploying/using `:latest` on the host. Always run an explicit tag | Reproducibility |

| P1 | Image vulnerability scanning in pipeline | Supply chain |

| P2 | Application Load Balancer in public subnets, backend stays private | Proper public API endpoint, TLS termination, health checks |

| P2 | HTTPS with ACM certificate and a Route 53 domain | Encrypted client traffic |

| P2 | Auto Scaling Group with 2 instances across both private app subnets (AZ-1, AZ-2) | Removes single point of failure, enables rolling deploys |

| P2 | Remote Terraform state (S3 + DynamoDB lock) | Safe IaC collaboration |

| P3 | Migrate image registry to ECR (private, IAM-auth, no Docker Hub rate limits) | Tighter security and reliability |

| P3 | Move to ECS/Fargate or EKS | Less host management, built-in rolling and health-based deploys |

| P3 | WAF in front of ALB | Protection against common web attacks |



\### Target architecture



```text

Internet

&#x20;  |

&#x20;  v

Route 53 -> ALB (public subnets, HTTPS, WAF)

&#x20;               |

&#x20;       +-------+-------+

&#x20;       |               |

&#x20;Backend EC2 (AZ-1)  Backend EC2 (AZ-2)   (private, Auto Scaling Group)

&#x20;       |               |

&#x20;       +-------+-------+

&#x20;               |

&#x20;       RDS MySQL Multi-AZ (private DB subnets)



Jenkins -> Docker Hub/ECR -> SSM / rolling update -> ASG

```



\---



\## 16. Change Management



| Change type | Approval | Notes |

|---|---|---|

| Application release via pipeline | PR review | Follow Section 7 |

| Infrastructure change (Terraform) | Review of `terraform plan` output | Never apply an unreviewed plan to production |

| IAM / security group change | Second person review | Record reason |

| Emergency fix | Verbal approval acceptable | Retroactive PR and note in incident log within 24h |



Freeze windows: \*\*\[DEFINE]\*\* (for example no non-emergency deploys on Fridays after 4 PM or during known peak booking periods).



\---



\## 17. On-Call and Escalation \*\*\[DEFINE]\*\*



| Role | Name | Contact |

|---|---|---|

| Primary on-call | TBD | TBD |

| Secondary | TBD | TBD |

| AWS account owner | TBD | TBD |

| Escalation (SEV1 not resolved in 30 min) | TBD | TBD |



\---



\## 18. Appendix



\### A. Key identifiers



| Item | Value |

|---|---|

| Region | `ap-south-1` |

| VPC CIDR | `10.0.0.0/16` |

| Backend instance ID | `i-04e08bcedc0870665` |

| Backend private IP | `10.0.11.171` |

| Docker image | `ajaydhadi95/flightfinder-backend` |

| Jenkins credential ID | `dockerhub-credentials` |

| Jenkins IAM role | `jenkins-ec2-role` |

| Container name | `flightfinder-backend` |

| Database | `bookingdb` on RDS MySQL `:3306` |

| Last documented deployed tag | `4` (update after every deploy) |

| RDS endpoint / identifier | \*\*\[FILL IN]\*\* |

| Backend / RDS / Jenkins security group IDs | \*\*\[FILL IN]\*\* |



\### B. Known issues history (from initial build)



| Issue | Resolution |

|---|---|

| Untracked files, nothing committed | `git add .` then commit |

| Duplicate `bin/` directory with `.class` files tracked | `git rm -r --cached bin`; added `bin/`, `target/`, `\*.class` to `.gitignore` |

| AWS CLI `NoCredentials` on Jenkins | Attached `jenkins-ec2-role` |

| Missing `ssm:DescribeInstanceInformation` | Added to the role |

| `docker: not found` on backend EC2 | Installed Docker (29.1.3) |

| Ubuntu user `docker ps` permission denied on Jenkins host | `sudo usermod -aG docker ubuntu`, re-login (Jenkins user was already fine) |



\### C. Quick command card



```bash

\# Is the backend instance reachable by SSM?

aws ssm describe-instance-information --region ap-south-1 \\

&#x20; --filters "Key=InstanceIds,Values=i-04e08bcedc0870665" \\

&#x20; --query "InstanceInformationList\[0].PingStatus"



\# Container + logs

aws ssm send-command --instance-ids i-04e08bcedc0870665 \\

&#x20; --document-name AWS-RunShellScript --region ap-south-1 \\

&#x20; --parameters 'commands=\["docker ps -a","docker logs --tail 100 flightfinder-backend"]'



\# Open a shell

aws ssm start-session --target i-04e08bcedc0870665 --region ap-south-1



\# Rollback to a tag: see Section 9.2

```



\### D. Revision history



| Date | Author | Change |

|---|---|---|

| 2026-10-06 | Ajay Dhadi | Initial production runbook, based on the CI/CD implementation notes |

