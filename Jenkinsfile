```groovy
pipeline {
    agent any

    environment {
        AWS_REGION       = 'ap-south-1'
        EKS_CLUSTER_NAME = 'devops-eks'
        K8S_NAMESPACE    = 'default'
        DEPLOYMENT_NAME  = 'backend'
        CONTAINER_NAME   = 'backend'

        DOCKER_IMAGE     = 'ajaydhadi95/booking_backend'
        DOCKER_CREDENTIALS = 'dockerhub-credentials'
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
                    chmod +x mvnw
                    ./mvnw clean test
                '''
            }
        }

        stage('Package JAR') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
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
                        set -eu

                        echo "$DOCKER_PASSWORD" | docker login \
                          -u "$DOCKER_USER" \
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

                    aws eks update-kubeconfig \
                      --region "$AWS_REGION" \
                      --name "$EKS_CLUSTER_NAME"

                    kubectl get nodes
                    kubectl get secret backend-db-secret \
                      -n "$K8S_NAMESPACE"
                '''
            }
        }

        stage('Deploy Backend to EKS') {
            steps {
                sh '''
                    set -eu

                    kubectl apply -f k8s/backend-deployment.yaml

                    kubectl set image \
                      deployment/${DEPLOYMENT_NAME} \
                      ${CONTAINER_NAME}=${DOCKER_IMAGE}:${BUILD_NUMBER} \
                      -n ${K8S_NAMESPACE}

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
                    echo "===== Backend Deployment ====="
                    kubectl get deployment backend -n default

                    echo "===== Backend Pods ====="
                    kubectl get pods -l app=backend -n default

                    echo "===== Backend Service ====="
                    kubectl get svc backend-service -n default
                '''
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Backend deployed to AWS EKS!'
        }

        failure {
            echo 'FAILED: Check the Jenkins stage logs.'
        }

        always {
            echo 'Backend CI/CD pipeline finished.'
        }
    }
}
```