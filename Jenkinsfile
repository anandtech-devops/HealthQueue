
pipeline {
    agent any

    environment {
        AWS_REGION = 'ap-south-1'
        ECR_REGISTRY = '166637874911.dkr.ecr.ap-south-1.amazonaws.com'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'master',
                    url: 'https://github.com/anandtech-devops/HealthQueue.git'
            }
        }

        stage('Build and Test Backend') {
            steps {
                dir('backend') {
                    sh 'mvn clean package'
                }
            }
        }

        stage('Login to ECR') {
            steps {
                sh '''
                    aws ecr get-login-password --region $AWS_REGION |
                    docker login --username AWS --password-stdin $ECR_REGISTRY
                '''
            }
        }

        stage('Build and Push Backend') {
            steps {
                sh '''
                    docker build -t $ECR_REGISTRY/healthqueue-backend:latest ./backend
                    docker push $ECR_REGISTRY/healthqueue-backend:latest
                '''
            }
        }

        stage('Build and Push Frontend') {
            steps {
                sh '''
                    docker build -t $ECR_REGISTRY/healthqueue-frontend:latest ./frontend
                    docker push $ECR_REGISTRY/healthqueue-frontend:latest
                '''
            }
        }
    }
}
