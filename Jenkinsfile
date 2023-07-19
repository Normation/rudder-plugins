
def failedBuild = false
def version = "7.2"

def changeUrl = env.CHANGE_URL

def slackResponse = slackSend(channel: "ci", message: "${version} next plugins - build - <"+currentBuild.absoluteUrl+"|Link>", color: "#00A8E1")
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
        RUDDER_VERSION = "${version}"
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
                                //notifier.notifyResult("shell-team")
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
                                //notifier.notifyResult("shell-team")
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
                            additionalBuildArgs  '--build-arg VERSION=1.0'
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
                                //notifier.notifyResult("shell-team")
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
                    def stageSuccess = [:]
                    PLUGINS = sh (
                        script: 'make plugins-list',
                        returnStdout: true
                    ).trim().split(' ')
                    PLUGINS.each { p ->
                        parallelStages[p] = {
                            stage("test ${p}") {

                                script {
                                    running.add("Test - ${p}")
                                    updateSlack(errors, running, slackResponse, version, changeUrl)
                                    stageSuccess.put(p,false)
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                    dir("${p}") {
                                        // enough to run the mvn tests and package the plugin
                                        sh script: 'make', label: "build ${p} plugin"
                                    }
                                    script {
                                        stageSuccess.put(p,true)
                                    }
                                }
                                script {
                                    if (! stageSuccess[p]) {
                                        errors.add("Test - ${p}")
                                        failedBuild = true
                                        slackSend(channel: slackResponse.threadId, message: "Error on build of plugin ${p} - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                                    }
                                    running.remove("Test - ${p}")
                                    updateSlack(errors, running, slackResponse, version, changeUrl)
                                }
                            }

                        }
                    }
                    parallel parallelStages
                }
            }

        }

        stage('Publish plugins commons') {

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
                            sh script: 'make'
                            sh script: '$MVN_CMD --update-snapshots clean package deploy', label: "common deploy"
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
                            sh script: 'make'
                            sh script: '$MVN_CMD --update-snapshots clean package deploy', label: "private common deploy"
                        }
                    }
                }

                post {
                    always {
                        archiveArtifacts artifacts: 'webapp/sources/rudder/rudder-web/target/*.war'
                    }
                    failure {
                        script {
                            failedBuild = true
                            errors.add("Publish - common plugin")
                            //notifier.notifyResult("scala-team")
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
                    def stageSuccess = [:]
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
                                    updateSlack(errors, running, slackResponse, version, changeUrl)
                                    stageSuccess.put(p,false)
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {

                                    dir("${p}") {
                                        sh script: 'make', label: "build ${p} plugin"
                                        archiveArtifacts artifacts: '**/*.rpkg', fingerprint: true, onlyIfSuccessful: false, allowEmptyArchive: true
                                        sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/add_to_repo -r -t rpkg -v ${env.RUDDER_VERSION}-nightly -d /home/publisher/tmp/${p}-${env.RUDDER_VERSION}", remoteDirectory: "${p}-${env.RUDDER_VERSION}", sourceFiles: '**/*.rpkg')], verbose:true)])
                                    }
                                    script {
                                        stageSuccess.put(p,true)
                                    }
                                }
                                script {
                                    if (! stageSuccess[p]) {
                                        errors.add("Publish - ${p}")
                                        failedBuild = true
                                        slackSend(channel: slackResponse.threadId, message: "Error on publication of plugin ${p} - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                                    }
                                    running.remove("Publish - ${p}")
                                    updateSlack(errors, running, slackResponse, version, changeUrl)
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

def updateSlack(errors, running, slackResponse, version, changeUrl) {

def msg ="*${version} - next plugins - build* - <"+currentBuild.absoluteUrl+"|Link>"

if (changeUrl != null) {
  msg ="*${version} PR - next plugins - build* - <"+currentBuild.absoluteUrl+"|Link> - <"+changeUrl+"|Pull request>"
}

def color = "#00A8E1"

if (! errors.isEmpty()) {
    msg += "\n*Errors* :x: ("+errors.size()+")\n  • " + errors.join("\n  • ")
    color = "#CC3421"
}
if (! running.isEmpty()) {
    msg += "\n*Running* :arrow_right: ("+running.size()+")\n  • " + running.join("\n  • ")
}

if (errors.isEmpty() && running.isEmpty()) {
    msg +=  " => All plugin built! :white_check_mark:"
	color = "good"
}
  slackSend(channel: slackResponse.channelId, message: msg, timestamp: slackResponse.ts, color: color)
}
