@Library('slack-notification')
import org.gradiant.jenkins.slack.SlackNotifier

pipeline {
    agent none

    stages {
        stage('shell') {
            agent { label 'script' }
            steps {
                sh script: './qa-test --shell', label: 'shell scripts lint'
            }
            post {
               always {
                    // linters results
                    recordIssues enabledForFailure: true, failOnError: true, sourceCodeEncoding: 'UTF-8',
                                    tool: checkStyle(pattern: '.shellcheck/*.log', reportEncoding: 'UTF-8', name: 'Shell scripts')
                    script {
                        new SlackNotifier().notifyResult("shell-team")
                    }
                }
            }
        }

        stage('python') {
            agent { label 'script' }
            steps {
                sh script: './qa-test --python', label: 'python scripts lint'
            }
            post {
                always {
                    script {
                       new SlackNotifier().notifyResult("shell-team")
                    }
                }
            }
        }

        stage('typos') {
            agent { label 'script' }
            steps {
                sh script: './qa-test --typos', label: 'check typos'
            }
            post {
                always {
                    script {
                        new SlackNotifier().notifyResult("shell-team")
                    }
                }
            }
        }

        stage('scripts') {
            agent { label 'script' }
            steps {
                sh script: './qa-test --scripts', label: 'packaging scripts must exit on error'
            }
            post {
                always {
                    script {
                        new SlackNotifier().notifyResult("shell-team")
                    }
                }
            }
        }
    }
}
