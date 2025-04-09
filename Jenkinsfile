
def failedBuild = false
def minor_version = "8.1"
def version = "${minor_version}"
def changeUrl = env.CHANGE_URL
def slackResponse = null
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
                            label 'generic-docker'
                            filename 'ci/shellcheck.Dockerfile'
                            args '-u 0:0'
                        }
                    }
                    steps {
                        script {
                            running.add("shell scripts")
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
                                slackResponse = updateSlack(errors, running, slackResponse, version, changeUrl)
                                slackSend(channel: slackResponse.threadId, message: "Check shell scripts on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("shell scripts")
                            }
                        }
                    }
                }
                stage('python') {
                    agent {
                        dockerfile {
                            label 'generic-docker'
                            filename 'ci/pylint.Dockerfile'
                            args '-u 0:0'
                        }
                    }
                    steps {
                        script {
                            running.add("python scripts")
                        }
                        sh script: './qa-test --python', label: 'python scripts lint'
                    }
                    post {
                        failure {
                            script {
                                errors.add("python scripts")
                                slackResponse = updateSlack(errors, running, slackResponse, version, changeUrl)
                                slackSend(channel: slackResponse.threadId, message: "Check python scripts on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("python scripts")
                            }
                        }
                    }
                }
                stage('typos') {
                    agent {
                        dockerfile {
                            label 'generic-docker'
                            filename 'ci/typos.Dockerfile'
                            additionalBuildArgs  '--build-arg VERSION=1.24.5'
                            args '-u 0:0'
                        }
                    }
                    steps {
                        script {
                            running.add("check typos")
                        }
                        sh script: './qa-test --typos', label: 'check typos'
                    }
                    post {
                        failure {
                            script {
                                errors.add("check typos")
                                slackResponse = updateSlack(errors, running, slackResponse, version, changeUrl)
                                slackSend(channel: slackResponse.threadId, message: "Check typos on all plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                            }
                        }
                        cleanup {
                            script {
                                running.remove("check typos")
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
                    label 'generic-docker'
                    filename 'ci/plugins.Dockerfile'
                    additionalBuildArgs "--build-arg USER_ID=${env.JENKINS_UID}"
                    // set same timezone as some tests rely on it
                    // and share maven cache
                    args '-u 0:0 -v /etc/timezone:/etc/timezone:ro -v /srv/cache/elm:/root/.elm -v /srv/cache/maven:/root/.m2'
                }
            }
            steps {

                script {
                    running.add("Publish - common plugin")
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
                        slackResponse = updateSlack(errors, running, slackResponse, version, changeUrl)
                        slackSend(channel: slackResponse.threadId, message: "Error while publishing webapp - <${currentBuild.absoluteUrl}|Link>", color: "#CC3421")
                    }
                }
                cleanup {
                    script {
                        running.remove("Publish - common plugin")
                    }
                }
            }
        }

        stage('Build plugins') {
            // only publish nightly on dev branches
            when { anyOf { branch 'master'; branch 'branches/rudder/*'; branch '*-next' }; }

            agent {
                dockerfile {
                    label 'generic-docker'
                    filename 'ci/plugins.Dockerfile'
                    // set same timezone as some tests rely on it
                    // and share maven cache
                    args '-u 0:0 -v /etc/timezone:/etc/timezone:ro -v /srv/cache/elm:/root/.elm -v /srv/cache/maven:/root/.m2'
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
                            stage("Build ${p}") {
                                script {
                                    running.add("Build - ${p}")
                                    stageSuccess.put(p,false)
                                }
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {

                                    script {
                                        dir("${p}") {
                                          withMaven(globalMavenSettingsConfig: "1bfa2e1a-afda-4cb4-8568-236c44b94dbf",
                                            // don't archive jars
                                            options: [artifactsPublisher(disabled: true)]
                                          ) {
                                            sh script: "export PATH=$MVN_CMD_DIR:$PATH && make ${(p == "cis" || p == "openscap")  ? "" : "licensed"}", label: "build ${p} plugin"
                                            if (changeRequest()) {
                                                archiveArtifacts artifacts: '**/*.rpkg', fingerprint: true, onlyIfSuccessful: false, allowEmptyArchive: true
                                                sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/add_to_repo -r -t rpkg -v ${env.RUDDER_VERSION}-nightly -d /home/publisher/tmp/${p}-${env.RUDDER_VERSION}", remoteDirectory: "${p}-${env.RUDDER_VERSION}", sourceFiles: '**/*.rpkg')], verbose:true)])
                                            }
                                         }
                                        }
                                        stageSuccess.put(p,true)
                                    }
                                }
                                script {
                                    if (! stageSuccess[p]) {
                                        errors.add("Build - ${p}")
                                        failedBuild = true
                                        slackResponse = updateSlack(errors, running, slackResponse, version, changeUrl)
                                        slackSend(channel: slackResponse.threadId, message: "Error on plugin ${p} build - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                                    }
                                    running.remove("Build - ${p}")
                                }
                            }
                    }
                }

            }
       }

        stage("Publish to repository") {
            when { not { changeRequest() } }
            agent any
            steps {
                script {
                    running.add("Publish - plugins")
                }
                sshPublisher(publishers: [sshPublisherDesc(configName: 'publisher-01', transfers: [sshTransfer(execCommand: "/usr/local/bin/publish -v \"${RUDDER_VERSION}\" -t plugins -u -m nightly")], verbose:true)])
            }
            post {
                failure {
                    script {
                        errors.add("Publish - plugins")
                        slackResponse = updateSlack(errors, running, slackResponse, version, changeUrl)
                        slackSend(channel: slackResponse.threadId, message: "Publishing plugins failed - <${currentBuild.absoluteUrl}console|Console>", color: "#CC3421")
                    }
                }
                cleanup {
                    script {
                        running.remove("Publish - plugins")
                    }
                }
            }
        }
        stage('End') {
            steps {
                script {
                    updateSlack(errors, running, slackResponse, version, changeUrl)
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
  def msg ="*${version} - plugins* - <"+currentBuild.absoluteUrl+"|Link>"

  if (changeUrl == null) {

      def fixed = currentBuild.resultIsBetterOrEqualTo("SUCCESS") && currentBuild.previousBuild.resultIsWorseOrEqualTo("UNSTABLE")
      if (errors.isEmpty() && running.isEmpty() && fixed) {
        msg +=  " => All plugins built! :white_check_mark:"
        def color = "good"
        slackSend(channel: "ci", message: msg, color: color)
      }


      if (! errors.isEmpty()) {
          msg += "\n*Errors* :x: ("+errors.size()+")\n  • " + errors.join("\n  • ")
          def color = "#CC3421"
          if (slackResponse == null) {
            slackResponse = slackSend(channel: "ci", message: msg, color: color)
          }
          slackSend(channel: slackResponse.channelId, message: msg, timestamp: slackResponse.ts, color: color)
      }
      return slackResponse
  }
}
