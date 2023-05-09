
def failedBuild = false

def slackResponse = slackSend(channel: "ci", message: "7.3 plugins - <"+currentBuild.absoluteUrl+"|Link>", color: "#00A8E1")
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
        RUDDER_VERSION = "7.2"
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
                            updateSlack(errors, running, slackResponse)
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
                                //notifier.notifyResult("shell-team")
                                slackSend(channel: slackResponse.threadId, message: "Check shell scripts on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("shell scripts")
                                updateSlack(errors, running, slackResponse)
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
                            updateSlack(errors, running, slackResponse)
                        }
                        sh script: './qa-test --python', label: 'python scripts lint'
                    }
                    post {
                        failure {
                            script {
                                errors.add("python scripts")
                                //notifier.notifyResult("shell-team")
                                slackSend(channel: slackResponse.threadId, message: "Check python scripts on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("python scripts")
                                updateSlack(errors, running, slackResponse)
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
                        script {
                            running.add("check typos")
                            updateSlack(errors, running, slackResponse)
                        }
                        sh script: './qa-test --typos', label: 'check typos'
                    }
                    post {
                        failure {
                            script {
                                errors.add("check typos")
                                //notifier.notifyResult("shell-team")
                                slackSend(channel: slackResponse.threadId, message: "Check typos on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("check typos")
                                updateSlack(errors, running, slackResponse)
                            }
                        }
                    }
                }
            }
        }
        stage('Tests plugins') {
            // Build disabled, test everything
            //when { changeRequest() }

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

                                script {
                                    running.add("Test - ${p}")
                                    updateSlack(errors, running, slackResponse)
                                    def success = ""
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                    dir("${p}") {
                                        // enough to run the mvn tests and package the plugin
                                        sh script: 'make', label: "build ${p} plugin"
                                    }
                                    script {
                                        success = "${p}"
                                    }
                                }
                                script {
                                    echo ("${success} - haha")
                                    if (success == "") {
                                        echo ("hoho")
                                        errors.add("${p}")
                                        failedBuild = true
                                        slackSend(channel: slackResponse.threadId, message: "Error on build of plugin ${p} - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                                    }

                                    echo ("hihi")
                                    running.remove("${p}")
                                    updateSlack(errors, running, slackResponse)
                                }
                            }

                        }
                    }
                    parallel parallelStages
                }
            }

        }
        stage('Publish plugins') {
            // only publish nightly on dev branches
            when {
                allOf { anyOf { branch 'master'; branch 'branches/rudder/*'; branch '*-next' };
                not { changeRequest() } }
                // Disabled
                expression { return false }
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
                                script {
                                    running.add("Publish - ${p}")
                                    updateSlack(errors, running, slackResponse)
                                    def success = false
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {

                                    dir("${p}") {
                                        sh script: 'make', label: "build ${p} plugin"
                                        archiveArtifacts artifacts: '**/*.rpkg', fingerprint: true, onlyIfSuccessful: false, allowEmptyArchive: true
                                        sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/add_to_repo -r -t rpkg -v ${env.RUDDER_VERSION}-nightly -d /home/publisher/tmp/${p}-${env.RUDDER_VERSION}", remoteDirectory: "${p}-${env.RUDDER_VERSION}", sourceFiles: '**/*.rpkg')], verbose:true)])
                                    }
                                    script {
                                        success = true
                                    }
                                }
                                script {
                                    if (!success) {
                                        errors.add("${p}")
                                        slackSend(channel: slackResponse.threadId, message: "Error on publication of plugin ${p} - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                                    }
                                    running.remove("${p}")
                                    updateSlack(errors, running, slackResponse)
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

def updateSlack(errors, running , slackResponse) {


def msg ="*7.3 plugins* - <"+currentBuild.absoluteUrl+"|Link>"

def color = "#00A8E1"

if (! errors.isEmpty()) {
    msg += "\n*Errors* :nono: ("+errors.size()+")\n• " + errors.join("\n• ")
    color = "#CC3421"
}
if (! running.isEmpty()) {
    msg += "\n*Running* :felisk: ("+running.size()+")\n• " + running.join("\n• ")
}

if (errors.isEmpty() && running.isEmpty()) {
    msg +=  "\n:yesyes: All plugins checked! :fiesta-parrot:"
	color = "good"
}
  slackSend(channel: slackResponse.channelId, message: msg, timestamp: slackResponse.ts, color: color)
}