@Library('slack-notification')
import org.gradiant.jenkins.slack.SlackNotifier

pipeline {
    agent none

    triggers {
        cron('@midnight')
    }

    environment {
        // TODO: automate
        RUDDER_VERSION = "7.1"
    }

    stages {
        stage('Base tests') {
            parallel {
                stage('shell') {
                    agent {
                        dockerfile {
                            filename 'ci/shellcheck.Dockerfile'
                        }
                    }
                    steps {
                        sh script: './qa-test --shell', label: 'shell scripts lint'
                        sh script: './qa-test --scripts', label: 'shell postinst lint'
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
                    agent {
                        dockerfile {
                            filename 'ci/pylint.Dockerfile'
                        }
                    }
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
                    agent {
                        dockerfile {
                            filename 'ci/typos.Dockerfile'
                            additionalBuildArgs  '--build-arg VERSION=1.0'
                        }
                    }
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
            }
        }
        stage('Tests plugins') {
            when { changeRequest() }

            agent {
                dockerfile {
                    filename 'ci/plugins.Dockerfile'
                    additionalBuildArgs "--build-arg USER_ID=${env.JENKINS_UID}"
                    // set same timezone as some tests rely on it
                    // and share maven cache
                    args '-v /etc/timezone:/etc/timezone:ro -v /srv/cache/elm:/home/jenkins/.elm -v /srv/cache/maven:/home/jenkins/.m2'
                }
            }
            steps {
                script {
                    def parallelStages = [:]
                    PLUGINS = sh (
                        script: 'make plugins-list',
                        returnStdout: true
                    ).trim().split(' ')
                    PLUGINS.each { p ->
                        parallelStages[p] = {
                            stage("test ${p}") {
                                dir("${p}") {
                                    // enough to run the mvn tests and package the plugin
                                    sh script: 'MVN_PARAMS="-U" make', label: "build ${p} plugin"
                                }
                            }
                        }
                    }
                    parallel parallelStages
                }
            }
            post {
                always {
                    script {
                        new SlackNotifier().notifyResult("scala-team")
                    }
                }
            }
        }
        stage('Publish plugins') {
            // only publish nightly on dev branches
            when {
                allOf { anyOf { branch 'master'; branch 'branches/rudder/*'; branch '*-next' };
                not { changeRequest() } }
            }

            agent {
                dockerfile {
                    filename 'ci/plugins.Dockerfile'
                    additionalBuildArgs "--build-arg USER_ID=${env.JENKINS_UID}"
                    // set same timezone as some tests rely on it
                    // and share maven cache
                    args '-v /etc/timezone:/etc/timezone:ro -v /srv/cache/elm:/home/jenkins/.elm -v /srv/cache/maven:/home/jenkins/.m2'
                }
            }
            steps {
                script {
                    def parallelStages = [:]
                    PLUGINS = sh (
                        script: 'make plugins-list',
                        returnStdout: true
                    ).trim().split(' ')
                    PLUGINS.each { p ->
                        parallelStages[p] = {
                            stage("publish ${p}") {
                                dir("${p}") {
                                    sh script: 'make', label: "build ${p} plugin"
                                    archiveArtifacts artifacts: '**/*.rpkg', fingerprint: true, onlyIfSuccessful: false, allowEmptyArchive: true
                                    sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/add_to_repo -r -t rpkg -v ${env.RUDDER_VERSION}-nightly -d /home/publisher/tmp/${p}-${env.RUDDER_VERSION}", remoteDirectory: "${p}-${env.RUDDER_VERSION}", sourceFiles: '**/*.rpkg')], verbose:true)])
                                }
                            }
                        }
                    }
                    parallel parallelStages
                    stage("Publish to repository") {
                        sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/publish -v \"${RUDDER_VERSION}\" -t plugins -u -m nightly")], verbose:true)])
                    }
                }
            }
            post {
                always {
                    script {
                        new SlackNotifier().notifyResult("scala-team")
                    }
                }
            }
        }
    }
}
