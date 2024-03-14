
def failedBuild = false
def minor_version = "8.1"
def version = "${minor_version}"
def changeUrl = env.CHANGE_URL
def slackResponse = ""
def blueUrl = "${env.JOB_DISPLAY_URL}"
if (changeUrl == null) {
  slackResponse = slackSend(channel: "ci", message: "${version} plugins - build - <"+currentBuild.absoluteUrl+"|Link> - <"+blueUrl+"|Blue>", color: "#00A8E1")
}
def job = ""
def errors = []
def running = []


pipeline {
    agent none

    triggers {
        cron('@midnight')
    }

    environment {
        // TODO: automate
        RUDDER_VERSION = "${minor_version}"
        // we want it everywhere for plugins
        MAVEN_ARGS = "--update-snapshots"
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
                        script {
                            running.add("shell scripts")
                            updateSlack(errors, running, slackResponse, version, changeUrl)
                        }
                        sh script: './qa-test --shell', label: 'shell scripts lint'
                        sh script: './qa-test --scripts', label: 'shell postinst lint'
                    }
                    post {
                        always {
                            // linters results
                            recordIssues enabledForFailure: true, failOnError: true, sourceCodeEncoding: 'UTF-8',
                                         tool: checkStyle(pattern: '.shellcheck/*.log', reportEncoding: 'UTF-8', name: 'Shell scripts')
                        }

                        failure {
                            script {
                                errors.add("shell scripts")
                                slackSend(channel: slackResponse.threadId, message: "Check shell scripts on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("shell scripts")
                                updateSlack(errors, running, slackResponse, version, changeUrl)
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
                        script {
                            running.add("python scripts")
                            updateSlack(errors, running, slackResponse, version, changeUrl)
                        }
                        sh script: './qa-test --python', label: 'python scripts lint'
                    }
                    post {
                        failure {
                            script {
                                errors.add("python scripts")
                                slackSend(channel: slackResponse.threadId, message: "Check python scripts on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("python scripts")
                                updateSlack(errors, running, slackResponse, version, changeUrl)
                            }
                        }
                    }
                }
                stage('typos') {
                    agent {
                        dockerfile {
                            filename 'ci/typos.Dockerfile'
                            additionalBuildArgs  '--build-arg VERSION=1.16.5'
                        }
                    }
                    steps {
                        script {
                            running.add("check typos")
                            updateSlack(errors, running, slackResponse, version, changeUrl)
                        }
                        sh script: './qa-test --typos', label: 'check typos'
                    }
                    post {
                        failure {
                            script {
                                errors.add("check typos")
                                slackSend(channel: slackResponse.threadId, message: "Check typos on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("check typos")
                                updateSlack(errors, running, slackResponse, version, changeUrl)
                            }
                        }
                    }
                }
            }
        }

        stage('Publish plugins commons') {

            // only publish nightly on dev branches
            when { anyOf { branch 'master'; branch 'branches/rudder/*'; branch '*-next' } }

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
                    running.add("Publish - common plugin")
                    updateSlack(errors, running, slackResponse, version, changeUrl)
                }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    dir('plugins-common') {
                        withMaven(globalMavenSettingsConfig: "1bfa2e1a-afda-4cb4-8568-236c44b94dbf",
                            // don't archive jars
                            options: [artifactsPublisher(disabled: true)]
                        ) {
                            // we need to use $MVN_COMMAND to get the settings file path
                            sh script: 'make generate-pom'
                            sh script: '$MVN_CMD --update-snapshots clean install package deploy', label: "common deploy"
                        }
                    }
                }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    dir('plugins-common-private') {
                        withMaven(globalMavenSettingsConfig: "1bfa2e1a-afda-4cb4-8568-236c44b94dbf",
                            // don't archive jars
                            options: [artifactsPublisher(disabled: true)]
                        ) {
                            // we need to use $MVN_COMMAND to get the settings file path
                            sh script: 'make generate-pom'
                            sh script: '$MVN_CMD --update-snapshots install package deploy', label: "private common deploy"
                        }
                    }
                }
            }
            post {
                failure {
                    script {
                        failedBuild = true
                        errors.add("Publish - common plugin")
                        slackSend(channel: slackResponse.threadId, message: "Error while publishing webapp - <${currentBuild.absoluteUrl}|Link>", color: "#CC3421")
                    }
                }
                cleanup {
                    script {
                        running.remove("Publish - common plugin")
                        updateSlack(errors, running, slackResponse, version, changeUrl)
                    }
                }
            }
        }

        stage('Build plugins') {
            // only publish nightly on dev branches
            when { anyOf { branch 'master'; branch 'branches/rudder/*'; branch '*-next' }; }

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
                    def stageSuccess = [:]
                    def parallelStages = [:]
                    PLUGINS = sh (
                        script: 'make plugins-list',
                        returnStdout: true
                    ).trim().split(' ')
                    PLUGINS.each { p ->
                        parallelStages[p] = {
                            stage("Build ${p}") {
                                script {
                                    running.add("Build - ${p}")
                                    updateSlack(errors, running, slackResponse, version, changeUrl)
                                    stageSuccess.put(p,false)
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {

                                    dir("${p}") {

                                        withMaven(globalMavenSettingsConfig: "1bfa2e1a-afda-4cb4-8568-236c44b94dbf",
                                          // don't archive jars
                                          options: [artifactsPublisher(disabled: true)]
                                        ) {
                                            sh script: 'export PATH=$MVN_CMD_DIR:$PATH && make licensed', label: "build ${p} plugin"
                                            if (changeRequest()) {
                                                archiveArtifacts artifacts: '**/*.rpkg', fingerprint: true, onlyIfSuccessful: false, allowEmptyArchive: true
                                                sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/add_to_repo -r -t rpkg -v ${env.RUDDER_VERSION}-nightly -d /home/publisher/tmp/${p}-${env.RUDDER_VERSION}", remoteDirectory: "${p}-${env.RUDDER_VERSION}", sourceFiles: '**/*.rpkg')], verbose:true)])
                                            }
                                        }
                                    }
                                    script {
                                        stageSuccess.put(p,true)
                                    }
                                }
                                script {
                                    if (! stageSuccess[p]) {
                                        errors.add("Build - ${p}")
                                        failedBuild = true
                                        slackSend(channel: slackResponse.threadId, message: "Error on plugin ${p} build - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                                    }
                                    running.remove("Build - ${p}")
                                    updateSlack(errors, running, slackResponse, version, changeUrl)
                                }
                            }
                        }
                    }
                    parallel parallelStages
                }

            }
       }

        stage("Publish to repository") {
            when { not { changeRequest() } }
            agent any
            steps {
                script {
                    running.add("Publish - plugins")
                    updateSlack(errors, running, slackResponse, version, changeUrl)
                }
                sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/publish -v \"${RUDDER_VERSION}\" -t plugins -u -m nightly")], verbose:true)])
            }   
            post {
                failure {
                    script {
                        errors.add("Publish - plugins")
                        slackSend(channel: slackResponse.threadId, message: "Check typos on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                    }
                }
                cleanup {
                    script {
                        running.remove("Publish - plugins")
                        updateSlack(errors, running, slackResponse, version, changeUrl)
                    }
                }
            }
        }
        stage('End') {
            steps {
                script {
                    if (failedBuild) {
                        error 'End of build'
                    } else {
                        echo 'End of build'
                    }
                }
            }
        }
    }
}

def updateSlack(errors, running, slackResponse, version, changeUrl) {

  if (changeUrl == null) {

    def msg ="*${version} - plugins - build* - <"+currentBuild.absoluteUrl+"|Link>"

    def color = "#00A8E1"

    if (! errors.isEmpty()) {
        msg += "\n*Errors* :x: ("+errors.size()+")\n  • " + errors.join("\n  • ")
        color = "#CC3421"
    }
    if (! running.isEmpty()) {
        msg += "\n*Running* :arrow_right: ("+running.size()+")\n  • " + running.join("\n  • ")
    }

    if (errors.isEmpty() && running.isEmpty()) {
        msg +=  " => All plugins built! :white_check_mark:"
        color = "good"
    }

    slackSend(channel: slackResponse.channelId, message: msg, timestamp: slackResponse.ts, color: color)
  }
}
