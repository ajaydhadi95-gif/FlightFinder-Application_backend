pipeline {
    agent any

    environment {
        AWS_REGION          = 'ap-south-1'
        EKS_CLUSTER_NAME    = 'devops-eks'
        K8S_NAMESPACE       = 'default'

        DEPLOYMENT_NAME     = 'backend'
        CANARY_NAME         = 'backend-canary'
        CONTAINER_NAME      = 'backend'

        DOCKER_IMAGE        = 'ajaydhadi95/booking_backend'
        DOCKER_CREDENTIALS  = 'dockerhub-credentials'

        DB_USERNAME = credentials('rds-db-username')
        DB_PASSWORD = credentials('rds-db-password')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Maven Build & Test') {
            steps {
                sh '''
                    set -eu

                    chmod +x mvnw
                    ./mvnw clean test
                '''
            }
        }

        stage('Package JAR') {
            steps {
                sh '''
                    set -eu

                    ./mvnw package -DskipTests
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    set -eu

                    echo "===== Building Docker Image ====="

                    docker build \
                      -t ${DOCKER_IMAGE}:${BUILD_NUMBER} \
                      -t ${DOCKER_IMAGE}:latest .
                '''
            }
        }

        stage('Docker Hub Login & Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKER_CREDENTIALS}",
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        set -eu

                        echo "$DOCKER_PASSWORD" | docker login \
                          --username "$DOCKER_USER" \
                          --password-stdin

                        docker push ${DOCKER_IMAGE}:${BUILD_NUMBER}
                        docker push ${DOCKER_IMAGE}:latest

                        docker logout
                    '''
                }
            }
        }

        stage('Connect to EKS') {
            steps {
                sh '''
                    set -eu

                    echo "===== Updating EKS Kubeconfig ====="

                    aws eks update-kubeconfig \
                      --region "$AWS_REGION" \
                      --name "$EKS_CLUSTER_NAME"

                    echo "===== EKS Nodes ====="
                    kubectl get nodes
                '''
            }
        }

        stage('Configure Database Secret') {
            steps {
                sh '''
                    set +x
                    set -eu

                    echo "===== Configuring Database Secret ====="

                    kubectl create secret generic backend-db-secret \
                      --namespace "$K8S_NAMESPACE" \
                      --from-literal=username="$DB_USERNAME" \
                      --from-literal=password="$DB_PASSWORD" \
                      --dry-run=client -o yaml | kubectl apply -f -

                    echo "Database Secret configured."
                '''
            }
        }

        stage('Apply Kubernetes Resources') {
            steps {
                sh '''
                    set -eu

                    echo "===== Applying Backend Resources ====="

                    kubectl apply -f k8s/backend-deployment.yaml

                    echo "===== Current Deployments ====="
                    kubectl get deployments -n "$K8S_NAMESPACE"
                '''
            }
        }

        stage('Deploy Canary Backend') {
            steps {
                sh '''
                    set -eu

                    echo "===== Updating Canary Backend ====="

                    kubectl set image \
                      deployment/${CANARY_NAME} \
                      ${CONTAINER_NAME}=${DOCKER_IMAGE}:${BUILD_NUMBER} \
                      -n ${K8S_NAMESPACE}

                    echo "===== Waiting for Canary Rollout ====="

                    kubectl rollout status \
                      deployment/${CANARY_NAME} \
                      -n ${K8S_NAMESPACE} \
                      --timeout=300s

                    echo "===== Canary Pods ====="

                    kubectl get pods \
                      -n ${K8S_NAMESPACE} \
                      -l 'app=backend,version=canary' \
                      -o wide
                '''
            }
        }

        stage('Verify Canary') {
            steps {
                sh '''
                    set -eu

                    echo "===== Canary Service ====="

                    kubectl get service backend-canary-service \
                      -n ${K8S_NAMESPACE}

                    echo "===== Canary Service Endpoints ====="

                    kubectl get endpoints backend-canary-service \
                      -n ${K8S_NAMESPACE}

                    echo "===== Canary Image ====="

                    kubectl get deployment ${CANARY_NAME} \
                      -n ${K8S_NAMESPACE} \
                      -o 'custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image'

                    echo "===== Canary Pods Ready ====="

                    kubectl wait \
                      --for=condition=Ready \
                      pod \
                      -l 'app=backend,version=canary' \
                      -n ${K8S_NAMESPACE} \
                      --timeout=180s

                    echo "Canary verification completed."
                '''
            }
        }

        stage('Deploy Stable Backend') {
            steps {
                sh '''
                    set -eu

                    echo "===== Updating Stable Backend ====="

                    kubectl set image \
                      deployment/${DEPLOYMENT_NAME} \
                      ${CONTAINER_NAME}=${DOCKER_IMAGE}:${BUILD_NUMBER} \
                      -n ${K8S_NAMESPACE}

                    echo "===== Waiting for Stable Rollout ====="

                    kubectl rollout status \
                      deployment/${DEPLOYMENT_NAME} \
                      -n ${K8S_NAMESPACE} \
                      --timeout=300s
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    set -eu

                    echo "===== Stable and Canary Deployments ====="

                    kubectl get deployments \
                      ${DEPLOYMENT_NAME} ${CANARY_NAME} \
                      -n ${K8S_NAMESPACE}

                    echo "===== Backend Pods ====="

                    kubectl get pods \
                      -n ${K8S_NAMESPACE} \
                      -l 'app=backend' \
                      -L version -o wide

                    echo "===== Stable Backend Service ====="

                    kubectl get service backend-service \
                      -n ${K8S_NAMESPACE}

                    echo "===== Canary Backend Service ====="

                    kubectl get service backend-canary-service \
                      -n ${K8S_NAMESPACE}

                    echo "===== Stable Service Endpoints ====="

                    kubectl get endpoints backend-service \
                      -n ${K8S_NAMESPACE}

                    echo "===== Canary Service Endpoints ====="

                    kubectl get endpoints backend-canary-service \
                      -n ${K8S_NAMESPACE}

                    echo "===== Deployed Images ====="

                    kubectl get deployments \
                      ${DEPLOYMENT_NAME} ${CANARY_NAME} \
                      -n ${K8S_NAMESPACE} \
                      -o 'custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image'

                    echo "===== Final Deployment Verification ====="

                    kubectl rollout status \
                      deployment/${DEPLOYMENT_NAME} \
                      -n ${K8S_NAMESPACE} \
                      --timeout=180s

                    kubectl rollout status \
                      deployment/${CANARY_NAME} \
                      -n ${K8S_NAMESPACE} \
                      --timeout=180s

                    echo "Stable and Canary deployments verified."
                '''
            }
        }
    }

    post {
        success {
            echo 'Stable and Canary Backend Deployment Successful!'

            sh '''
                docker image prune -f
            '''

            cleanWs()
        }

        failure {
            echo 'Deployment Failed! Check the failed stage logs.'

            cleanWs()
        }

        always {
            echo 'Jenkins pipeline execution finished.'
        }
    }
}
