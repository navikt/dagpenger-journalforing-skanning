pipeline {
  agent any
  environment {
    APPLICATION_NAME = 'dagpenger-journalforing-skanning'
    ZONE = 'fss'
    NAMESPACE = 'default'
    VERSION = sh(script: './gradlew -q printVersion', returnStdout: true).trim()
  }

  stages {
    stage('Install dependencies') {
      steps {
        sh "./gradlew assemble"
      }
    }

    stage('Build') {
      steps {
        sh "./gradlew check"
      }

      post {
        always {
          publishHTML target: [
            allowMissing: true,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: 'build/reports/tests/test',
            reportFiles: 'index.html',
            reportName: 'Test coverage'
          ]

          junit 'build/test-results/test/*.xml'
        }
      }
    }

    stage('Publish') {
      steps {
        timeout(10) {
                input 'Keep going?'
        }

        withCredentials([usernamePassword(
          credentialsId: 'repo.adeo.no',
          usernameVariable: 'REPO_USERNAME',
          passwordVariable: 'REPO_PASSWORD'
        )]) {
            sh "docker login -u ${REPO_USERNAME} -p ${REPO_PASSWORD} repo.adeo.no:5443"
        }

        script {
          sh "./gradlew dockerPush${VERSION}"
        }
      }
    }

    stage("Publish service contract") {
      steps {
        withCredentials([usernamePassword(
          credentialsId: 'repo.adeo.no',
          usernameVariable: 'REPO_USERNAME',
          passwordVariable: 'REPO_PASSWORD'
        )]) {
          sh "curl -vvv --user ${REPO_USERNAME}:${REPO_PASSWORD} --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/${APPLICATION_NAME}/${VERSION}/nais.yaml"
        }
      }
    }

    stage('Deploy to non-production') {
      steps {
        script {
          response = naisDeploy.createNaisAutodeployment(env.APPLICATION_NAME, env.VERSION,"q1",env.ZONE ,env.NAMESPACE, "")
        }
      }
    }
  }
}