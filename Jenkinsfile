pipeline {

    agent any

    environment {
        IMAGE_NAME = 'ajaydhadi95/flightfinder-backend'
        IMAGE_TAG  = "${BUILD_NUMBER}"

        BACKEND_INSTANCE_ID = 'i-04e08bcedc0870665'
        AWS_REGION = 'ap-south-1'

        DOCKER_CREDENTIALS = 'dockerhub-credentials'
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/ajaydhadi95-gif/FlightFinder-Application_backend.git'
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
                sh '''
                    ./mvnw clean package -DskipTests
                '''
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build \
                      -t ${IMAGE_NAME}:${IMAGE_TAG} \
                      -t ${IMAGE_NAME}:latest \
                      .
                '''
            }
        }

        stage('Docker Login & Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: "${DOCKER_CREDENTIALS}",
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login \
                            -u "$DOCKER_USER" \
                            --password-stdin

                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                    '''
                }
            }
        }

        stage('Deploy to Backend via SSM') {
            steps {
                script {

                    def commandId = sh(
                        script: """
                            aws ssm send-command \
                              --instance-ids "${BACKEND_INSTANCE_ID}" \
                              --document-name "AWS-RunShellScript" \
                              --parameters 'commands=[
                                "docker pull ${IMAGE_NAME}:${IMAGE_TAG}",
                                "docker stop flightfinder-backend || true",
                                "docker rm flightfinder-backend || true",
                                "docker run -d --name flightfinder-backend --restart unless-stopped -p 8080:8080 ${IMAGE_NAME}:${IMAGE_TAG}"
                              ]' \
                              --region ${AWS_REGION} \
                              --query 'Command.CommandId' \
                              --output text
                        """,
                        returnStdout: true
                    ).trim()

                    echo "SSM Command ID: ${commandId}"

                    sleep 10

                    sh """
                        aws ssm get-command-invocation \
                          --command-id "${commandId}" \
                          --instance-id "${BACKEND_INSTANCE_ID}" \
                          --region "${AWS_REGION}"
                    """
                }
            }
        }
    }

    post {

        success {
            echo 'Backend deployment successful!'
        }

        failure {
            echo 'Backend deployment failed!'
        }
    }
}