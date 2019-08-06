pipeline{
    agent any

    stages {

        stage("Build") {
            steps {
                sh "./gradlew clean build -x test"
                archiveArtifacts artifacts: '**/build/libs/*', excludes: null
            }
        }

        stage("Test") {
            steps {
                sh "./gradlew test"
            }

            post {
                always {
                    junit '**/build/test-results/**/*.xml'
                }
            }
        }

        stage("Integration Tests") {
            steps {
                sh "./gradlew integrationTests --info"
            }
            post {
                always {
                    junit 'integration-tests/build/test-results/**/*.xml'
                    sh "./gradlew :integration-tests:composeDown"
                }
            }
        }


        stage('Publish Jars & Amps') {
            environment {
                SONATYPE_CREDENTIALS = credentials('sonatype')
                GPGPASSPHRASE = credentials('gpgpassphrase')
            }
            steps {
                script {
                    sh "./gradlew publish -Ppublish_username=${SONATYPE_CREDENTIALS_USR} -Ppublish_password=${SONATYPE_CREDENTIALS_PSW} -PkeyId=DF8285F0 -Ppassword=${GPGPASSPHRASE} -PsecretKeyRingFile=/var/jenkins_home/secring.gpg"
                }
            }
        }

        stage("Publish Docker Image") {
            when {
                anyOf {
                    branch "master*"
                    branch "release*"
                }
            }
            steps {
                sh "./gradlew pushDockerImage"
            }
        }
    }
}