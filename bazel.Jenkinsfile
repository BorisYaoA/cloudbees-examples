@Library('sharedSPRJenkinsLib@v1.0') import com.intusurg.spr.jenkins.*

import hudson.plugins.git.*

properties([
    parameters([
        string(name: 'NODE_LABEL', defaultValue: 'spider-builder'),
        choice(name: 'cache_test_results', choices: ['yes', 'no'], defaultValue: 'yes', description: 'Cache test results in Bazel')
    ])
])

class Globals {

    String BITBUCKET_MIRROR = 'ssh://git@bitbucket-mir-scl01.corp.intusurg.com:7999/bitbucket/orion2/sysg8x.git'
    String BITBUCKET_MIRROR_AWS = 'ssh://git@bitbucket-mir-scl03.corp.intusurg.com:7999/bitbucket/orion2/sysg8x.git'
    String userEmail
    String sourceBranch
    String sourceSHA

    String pr_number
    String source_sha
    String target_sha
    String source_branch
    String target_branch

    String deployableTag

    String startHash
    String finalHash

    String changeName
    String changeURL
    String jobURL
    String repoURL
    String hostname
    String links
    String bazelWorkspace = ''
    String bazelTestPath = ''
    boolean buildBuddyEnabled = true
    boolean remoteExecutionEnabled = false
    boolean differEnabled = false
    boolean skipUnittests = false
    boolean cleanWorkspaceEnabled = false
    boolean deleteOtherWorkspaces = true
    String junitPattern = 'bazel-testlogs/**/test.xml'
    boolean selectDevBuildBuddy = true
    String extraStartupConfigs = ''
    String extraBuildConfigs = ''
    String fakeConfig = ' --config=fake'
    String cleanupScript = 'build/scripts/bazel/clean_disk_cache.py'
    String cleanupDir = '~/.cache/bazel/disk-cache-prod/'
    String CI_SLACK_CHANNEL = '#oryx-ci-status'
    /* Realtime Notify */
    boolean REALTIME_SLACK_NOTIFY = true
    /* CI Realtime Metrics Control */
    boolean realtimeCiMetrics = true
    /* CI Branch Exceptions not to stop previous running jobs on those branches */
    String branchExceptions = 'develop master PR_batch release_'

    String maxTestThreshold = '999'
    List<String> unittestTargets
    List<String> integrationTargets
    List<String> enormousTargets

    String workspace = '/home/orionci/jenkins/workspace/bazelNix_persistent'

    Map<String, String> humanTestStageNames = [
        '//_orion2/tests:ui_tests':  'Bazel Normal Mode UI Test',
        '//_orion2/tests:normal_mode_tests': 'Bazel Normal Mode Test',
        '//_orion2/tests:simulator_tests': 'Bazel Simulator Test',
        '//_orion2/tests:dvmt_tests': 'Bazel DVMT Test',
        '//_orion2/lib/download_app_lib:pytests': 'Bazel Download App Test',
        '//run:desktop_regression_test_minime': 'Bazel Desktop Regression Minime Tests',
        '//run:desktop_regression_test_ci': 'Bazel Desktop Regression Tests',
        '//run:cal_regression_test': 'Bazel Desktop Cal Regression Tests',
        '//run:desktop_regression_test_hybrid': 'Bazel Desktop Regression hybrid Tests',
        '//run:desktop_regression_test_minime_windows': 'Bazel Desktop Regression Minime Windows Tests',
        '//_orion2/lib/dry_dock:pytests': 'Bazel DryDock Tests',
    ]

    Map<String, String> shortStageNames = [
        '//_orion2/tests:ui_tests': 'ui',
        '//_orion2/lib/download_app_lib:pytests': 'download_app',
        '//_orion2/lib/dry_dock:pytests': 'dry_dock',
        '//_orion2/tests:normal_mode_tests': 'normal_mode',
    ]

    List<String> commonStages = ['ui', 'download_app', 'dry_dock', 'normal_mode', 'deployable', 'build',
                                 'cal_regression_test', 'desktop_regression_test_ci', 'dvmt_tests',
                                 'integration', 'unittest']

    Map<String, Boolean> stagesEnabled = [:]
    Map<String, Boolean> stagesQuarantined = [:]

    /* Allure Testops */
    String allureJobRunId
    public final String ALLURE_SERVER_ID = 'allure-prod'
    public final String ORION2_ALLURE_PROJECT_ID = '1'
    boolean allureEnabled = true

    // Maximum time any test stage can run
    public final Integer DEFAULT_TEST_STAGE_TIMEOUT = 3600 * 2 //seconds
}

String getBuildUserEmail() {
    BuildUserInfo userInfo = new BuildUserInfo()

    String email = env.CHANGE_AUTHOR_EMAIL

    if (!env.CHANGE_AUTHOR_EMAIL?.contains('@')) {
        email = 'svc.builder@intusurg.com'
    }

    // Although we could just leave it as an email for some builds
    // like develop, we have to use consistent shared library component
    wrap([$class: 'BuildUser']) {
        userInfo.getUserInfo(changeAuthorEmail: email,
                             buildUserId: env.BUILD_USER_ID,
                             buildUserFirstName: env.BUILD_USER_ID_FIRST_NAME,
                             buildUserLastName: env.BUILD_USER_ID_LAST_NAME)
    }
    return userInfo.userId
}

boolean skipDiff(String prTitle) {
   def command = prTitle =~ /\[\s*skip\s*diff\s*\]/
   return command.find()
}

def bazel_sh(String script, String label='Bazel Command', boolean returnStdout=false) {
    // When we launch anything bazel, it will spin up server
    // (unless the server is already running)
    // We have to tell Jenkins that it is OK for the bazel command to
    // spin up a demon, because Jenkins is pretty good at cleaning up the
    // environment agressively, using ProcessTreeKiller
    // https://wiki.jenkins.io/display/JENKINS/ProcessTreeKiller

    // If your build wants to leave a daemon running behind (which bazel does)
    // You have to set the environment variable JENKINS_NODE_COOKIE to a special value
    // which Jenkins's ProcessTreeKiller is looking for.
    // This will cause Jenkins to assume that your daemon is not spawned by the Jenkins build.
    // And the bazel server is not terminated.
    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
        return sh(script:
                  "/bin/bash ./build/scripts/direnv/start_dev_shell.sh ${script}",
                  label: label, returnStdout: returnStdout)
    }
}

def uploadTooling() {
    withCredentials([
        string(credentialsId: 'dev_nix_cache_access_key_id', variable: 'AWS_ACCESS_KEY_ID'),
        string(credentialsId: 'dev_nix_cache_secret_access_key', variable: 'AWS_SECRET_ACCESS_KEY'),
        file(credentialsId: 'nix_cache_private_key', variable: 'FILE')
    ]) {
        sh('''
        set -eu
        set -f
        export IFS=' '

        export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
        export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
        export AWS_DEFAULT_REGION=us-west-1

        nix copy --to "s3://isi-dev-nix-bin?secret-key=$FILE&multipart-upload=true&scheme=https&endpoint=isi-dev-nix-bin.s3.us-west-1.amazonaws.com&region=us-west-1" --all
        '''
        )
    }
}

def updateCheckoutData(def scmVars, Globals globals) {
    unsafeEcho("${scmVars}")

    if (scmVars.containsKey('GIT_URL')) {
        globals.repoURL = scmVars.GIT_URL
    } else if (scmVars.containsKey('GIT_URL_1')) {
        globals.repoURL = scmVars.GIT_URL_1
    } else if (scmVars.containsKey('GIT_URL_2')) {
        globals.repoURL = scmVars.GIT_URL_2
    }

    globals.jobURL = RUN_DISPLAY_URL

    globals.links = "[Jenkins](${globals.jobURL})"

    // Actual Bitbucket last commit (different from Jenkins merge commit)
    globals.sourceSHA = scmVars.GIT_COMMIT

    if (JOB_BASE_NAME.startsWith('PR-')) {
        // Bitbucket branch name, aka: feature/STI-6386-add-ci-metadata
        globals.sourceBranch = CHANGE_BRANCH

        // Bitbucket PR URL
        globals.changeURL = CHANGE_URL

        // Normally 'PR-39' used as String for URL link
        globals.changeName = JOB_BASE_NAME

        globals.links += ",[${globals.changeName}](${globals.changeURL})"

        globals.pr_number = JOB_BASE_NAME.substring(3)

        // Get source branch
        globals.source_branch = sh(script:"""/opt/isi/bin/query_bitbucket.sh --get source_branch --pr ${globals.pr_number}""", returnStdout: true).trim()

        // Determine if PR is to master. Abort if True
        globals.target_branch = sh(script:"""/opt/isi/bin/query_bitbucket.sh --get target_branch --pr ${globals.pr_number}""", returnStdout: true).trim()


        // Fetch the target branch in case the target branch is not develop
        if (globals.target_branch != "develop") {
            withCredentials([gitUsernamePassword(credentialsId: 'svc-builder-jenkins-controller-token')]) {
                sh "git fetch origin ${globals.target_branch}:${globals.target_branch} --force"
            }
            globals.startHash = sh(script: """git merge-base HEAD origin ${globals.target_branch}""", returnStdout: true).trim()
        } else {
            globals.startHash = sh(script: """git merge-base HEAD origin/${globals.target_branch}""", returnStdout: true).trim()
        }

        globals.finalHash = sh(script: """git rev-parse HEAD""", returnStdout: true).trim()

        // globals.source_sha = sh(script: """git merge-base HEAD origin/${globals.source_branch}""", returnStdout: true).trim()
        // globals.target_sha = sh(script: """git merge-base HEAD origin/${globals.target_branch}""", returnStdout: true).trim()
        globals.source_sha = globals.sourceSHA
        globals.target_sha = ''

    } else {
        // i.e. "develop" when building branches (not PRs)
        globals.sourceBranch = JOB_BASE_NAME
        globals.source_sha = sh(script: """git rev-parse HEAD""", returnStdout: true).trim()
        globals.target_sha = ''

        globals.startHash = scmVars.GIT_PREVIOUS_SUCCESSFUL_COMMIT
        globals.finalHash = scmVars.GIT_COMMIT

    }

    String source_sha_short = globals.source_sha.substring(0,8)
    String serialTimestamp = sh(script:"""git --git-dir ${WORKSPACE}/.git log -1 --pretty=format:%ct""", returnStdout: true).trim()
    String deployableBranch = scmVars.GIT_BRANCH.replace('-', '').toLowerCase()
    globals.deployableTag = "${serialTimestamp}-BZL_${deployableBranch}-${source_sha_short}"

}

String getBuildMetadata(Globals globals, String hostname) {
    // Provide proper BuildBuddy metadata
    return """--build_metadata=BUILDBUDDY_LINKS=\"${globals.links}\" \
              --build_metadata=REPO_URL=${globals.repoURL} \
              --build_metadata=BRANCH_NAME=${globals.sourceBranch} \
              --build_metadata=COMMIT_SHA=${globals.sourceSHA} \
              --build_metadata=HOST=${hostname} \
              --build_metadata=USER=${globals.userEmail}"""
}

def mirrorCheckout(Globals globals) {
    def scmVars

    String hostname = getHostname()
    String referenceRepoLock = getNodeFileLockName(hostname,
                                                   '/home/orionci/gitcache')

    echo("Waiting for ${referenceRepoLock} to checkout")
    lock(resource: referenceRepoLock) {
        echo("starting scm.userRemoteConfigs: ${scm.userRemoteConfigs}")

        // Create reference repo if it does not exist yet
        if (!fileExists('/home/orionci/gitcache')) {
            sh(script: """
                cd /home/orionci
                git clone --mirror ${globals.BITBUCKET_MIRROR} gitcache
            """, label: 'Cloning reference repo')

            sh(script: '''
                cd /home/orionci/gitcache
                git fetch --all --prune
                git lfs fetch --recent
            ''', label: 'Updating reference repo wih LFS')
        }

        try {
            scmVars = checkout([
                $class: 'GitSCM',
                doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
                userRemoteConfigs: scm.userRemoteConfigs,
                branches: scm.branches,
                extensions: scm.extensions,
                browser: scm.browser,
                gitTool: 'builder-git'
            ])
        } catch (Exception e) {
            echo 'Exception occurred on primary server: ' + e.toString()

            def newUserRemoteConfigs = scm.userRemoteConfigs.collect {
                new UserRemoteConfig(globals.BITBUCKET_MIRROR, it.name, it.refspec, it.credentialsId)
            }

            echo("new scm.userRemoteConfigs: ${newUserRemoteConfigs}")
            scmVars = checkout([
                $class: 'GitSCM',
                doGenerateSubmoduleConfigurations: scm.doGenerateSubmoduleConfigurations,
                userRemoteConfigs: newUserRemoteConfigs,
                branches: scm.branches,
                extensions: scm.extensions,
                browser: scm.browser,
                gitTool: 'builder-git'
            ])

        }
    }

    return scmVars
}

def buildTestStage(nodeLabel, stageName, globals, closure, tests, combineTests = false, dryDockTest = true, ui = false) {
    return {
        def safeTestClosure = { quarantined, steps ->
            if (quarantined) {
                // Run test steps, and allow failure within the stage.
                // Also, if the failure does occur, set stage status to be failureStageStatus
                // but do not fail the build (intended for quarantine only)
                // Maximum duration and maxDurationUnit (SECONDS, MINUTES) determine the maximum
                // duration for the stage

                String failureStageStatus = 'UNSTABLE'
                Integer maxDuration = 7200
                String maxDurationUnit = 'SECONDS'

                Exception caughtException = null
                timeout(time: maxDuration, unit: maxDurationUnit) {
                    catchError(buildResult: 'SUCCESS', stageResult: failureStageStatus, catchInterruptions: true) {
                        try {
                            steps()
                        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
                            error "Caught timeout ${e.toString()} exception"
                        } catch (Throwable e) {
                            error "Caught ${e.toString()}"
                            caughtException = e
                        }
                    }

                    if (caughtException) {
                        error caughtException.message
                    }
                }
            } else {
                steps()
            }
        }

        closure(nodeLabel) {
            stage(stageName) {
                String hostname = getHostname()
                stage(hostname) {
                    echo "Running on ${hostname} in ${WORKSPACE}"
                }
                stage("Checkout ${stageName}") {
                    isiExecute(globals.realtimeCiMetrics) {
                        mirrorCheckout(globals)
                    }
                }

                List<String> bazelTestTargets

                if (combineTests) {
                    // Unite all tests into one stage
                    bazelTestTargets = [tests.join(' ')]
                } else {
                    // Each test will execute in separate stag
                    bazelTestTargets = tests.clone()
                }

                String buildStageName = "${stageName} Build"

                String metadata = getBuildMetadata(globals, hostname)
                stage(buildStageName) {
                    String buildTargets = tests.join(' ')

                    bazel_sh("""bazel ${globals.extraStartupConfigs} build ${buildTargets} \
                                                      --config=ci ${globals.extraBuildConfigs} \
                                                      ${metadata}""",
                             "> bazel build ${buildTargets}")

                }

                // DryDock stop has to happen right before the test (not before build)
                if (dryDockTest) {
                    isiExecute(globals.realtimeCiMetrics) {
                        stage('DryDock Stop') {
                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE', catchInterruptions: true) {
                                bazel_sh('bazel run drydock stop', 'Stopping any previous DryDock sessions')
                            }

                            catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE', catchInterruptions: true) {
                                sh '/opt/isi/bin/orion_docker_restart'
                            }
                        }
                    }
                }

                bazelTestTargets.each { testTarget ->
                    String humanStageName
                    if (combineTests) {
                        humanStageName = 'Unittest'
                    } else {
                        humanStageName = globals.humanTestStageNames[testTarget] ?: testTarget
                    }

                    stage(humanStageName) {
                        //TODO: re-add realtime metrics here once we resolve ConcurrentModificationException
                        // OleksiyP 21/02/2023
                        isiExecute(false) {
                            sh(script: """
                                    set +e
                                    find -L bazel-testlogs -name test.xml -delete -type f -printf . | wc -c
                                    true
                                    """, label: "Cleaning test logs")

                            String startTestStamp = sh(script:'mktemp start_of_test.XXXXXX', returnStdout: true).trim()

                            if (dryDockTest) {
                                sh(script: '/opt/isi/bin/orion-sudo drop_caches_and_defrag',
                                   label: "Drop cache and compact mem before dry dock tests")
                            }

                            if (ui) {
                                sh(script: """
                                       echo "executing x11 cleanup script"
                                       /opt/isi/bin/orion_x11 stop
                                       /opt/isi/bin/orion_x11 start x11.log
                                       export DISPLAY=:0
                                       """, label: 'Start X11')
                            }

                            withAllureUploadISI(
                                enabled: globals.allureEnabled,
                                serverId: globals.ALLURE_SERVER_ID,
                                projectId: globals.ORION2_ALLURE_PROJECT_ID,
                                allureJobRunId: globals.allureJobRunId,
                                env: ['BUILD_TOOL=bazel'],
                                timeout: globals.DEFAULT_TEST_STAGE_TIMEOUT,
                                results: [[path: globals.bazelTestPath]]
                            ) {

                                boolean stageQuarantined = globals.stagesQuarantined[stageName]
                                safeTestClosure(stageQuarantined) {
                                    bazel_sh("""bazel ${globals.extraStartupConfigs} test ${testTarget} \
                                                      --test_output=streamed \
                                                      --cache_test_results=${params.cache_test_results} \
                                                      --test_tag_filters=-broken \
                                                      --config=ci ${globals.extraBuildConfigs} \
                                                      ${metadata}""",
                                             "> bazel test ${testTarget}")
                                }
                                sh(script: """
                                         set +e
                                         find -L bazel-testlogs -name test.xml -type f ! -newer ${startTestStamp} -delete -printf . | wc -c
                                         true
                                         """, label: "Cleaning up test logs dated prior to ${startTestStamp} modification")
                            }

                            boolean stageQuarantined = globals.stagesQuarantined[stageName]

                            String buildResult = stageQuarantined ? 'SUCCESS' : 'UNSTABLE'
                            unsafeEcho("stageName ${stageName} quarantined: ${stageQuarantined}, build will be marked as: ${buildResult}")
                            catchError(buildResult: buildResult, stageResult: 'UNSTABLE', catchInterruptions: true) {
                                if (stageQuarantined) {
                                    xunit (
                                        thresholds: [
                                            failed(
                                                failureNewThreshold: globals.maxTestThreshold,
                                                failureThreshold: globals.maxTestThreshold,
                                                unstableNewThreshold: globals.maxTestThreshold,
                                                unstableThreshold: globals.maxTestThreshold
                                            )
                                        ],
                                        tools: [
                                            JUnit(pattern: globals.junitPattern,
                                                  skipNoTestFiles: true,
                                                  stopProcessingIfError: false,
                                                  deleteOutputFiles: false)
                                        ]
                                    )
                                } else {
                                    junit(testResults: globals.junitPattern, allowEmptyResults: true)
                                }
                                archiveArtifacts(allowEmptyArchive: true, artifacts: globals.junitPattern)
                            }

                            if(ui) {
                                sh(script: """
                                   /opt/isi/bin/orion_x11 stop
                                   /opt/isi/bin/orion-sudo clean_xauthority
                                """, label: "Stop X11")
                            }
                        }
                    }
                }
                String cleanupStageName = "${stageName} cleanup"
                stage(cleanupStageName) {
                    cleanup(globals)
                    echo "cleanup for ${stageName} complete"
                }
            }
        }
    }
}

def buildFullTree(nodeLabel, stageName, globals, closure) {
    return {
        closure(nodeLabel) {
            stage(stageName) {
                String hostname = getHostname()
                stage(hostname) {
                    echo "Running on ${hostname} in ${WORKSPACE}"
                }
                stage("Checkout ${stageName}") {
                    isiExecute(globals.realtimeCiMetrics) {
                        mirrorCheckout(globals)
                    }
                }
                stage('Full Build') {
                	String metadata = getBuildMetadata(globals, hostname)
                    isiExecute(globals.realtimeCiMetrics) {
                        bazel_sh("""bazel ${globals.extraStartupConfigs} build //... \
                                      --config=ci \
                                      ${globals.extraBuildConfigs} \
                                      ${metadata}""",
                                 '> bazel build //...')
                    }

                    isiExecute(globals.realtimeCiMetrics) {
                        bazel_sh("""bazel ${globals.extraStartupConfigs} build \
                                      //_orion2/lib/download_app_lib:remote_software_update \
                                      --platforms=//build/bazel/platforms:isi.platform.linux_aarch64 \
                                      --//:image_library=//_orion2/images/vision_cart/vdcn_yocto/image_lib:image_lib \
                                      --config=ci \
                                      ${globals.extraBuildConfigs} \
                                      ${metadata}""",
                                 '> bazel build //_orion2/lib/download_app_lib:remote_software_update --platforms=//build/bazel/platforms:isi.platform.linux_aarch64 --//:image_library=//_orion2/images/vision_cart/vdcn_yocto/image_lib:image_lib')
                    }

                }
                stage('Upload Tooling') {
                    isiExecute(globals.realtimeCiMetrics) {
                        uploadTooling()
                    }
                }
                String cleanupStageName = "${stageName} cleanup"
                stage(cleanupStageName) {
                    cleanup(globals)
                    echo "cleanup for ${stageName} complete"
                }
            }
        }
    }
}

def buildDeployable(nodeLabel, stageName, globals, closure) {
    return {
        closure(nodeLabel) {
            stage(stageName) {
                String hostname = getHostname()
                stage(hostname) {
                    echo "Running on ${hostname} in ${WORKSPACE}"
                }
                stage("Checkout ${stageName}") {
                    isiExecute(globals.realtimeCiMetrics) {
                        mirrorCheckout(globals)
                    }
                }
                stage('Generate Metadata') {
                    isiExecute(globals.realtimeCiMetrics) {
                        bazel_sh('ox metadata', '> ox metadata')
                    }
                }
                stage ('Upload deployable') {
                    isiExecute(globals.realtimeCiMetrics) {
                        String storage = "/mnt/sw-store"
                        String storageLocation = "${storage}/deployables"
                        String storageTransfer = "${storage}/transfer"

                        String destinationFile = "${storageLocation}/${globals.deployableTag}"
                        if(fileExists(destinationFile)) {
                            echo "Skip save to storage. Deployable for this build already on server."
                        } else {
                            lock(resource: destinationFile) {
                                String transferPath = "${storageTransfer}/${globals.deployableTag}"
                                lock(resource: transferPath) {
                                    sh("rm -rf ${transferPath}")
                                    sh("mkdir -p ${transferPath}")
                                    String deployableBuildConfigs = globals.extraBuildConfigs.minus(globals.fakeConfig)

                                    String metadata = getBuildMetadata(globals, hostname)

                                    bazel_sh("""bazel ${globals.extraStartupConfigs} \
                                                run \
                                                --config=ci \
                                                ${deployableBuildConfigs} \
                                                ${metadata} \
                                                //build/bazel/deployable copy ${transferPath}""",
                                             "> bazel run //build/bazel/deployable copy ${transferPath}")

                                    sh("cp build/output/commit_history.gz build/output/whoami.json ${transferPath}")
                                    sh("mv ${transferPath} ${storageLocation}")
                                }
                            }
                        }
                    }
                }
                String cleanupStageName = "${stageName} cleanup"
                stage(cleanupStageName) {
                    cleanup(globals)
                    echo "cleanup for ${stageName} complete"
                }
            }
        }
    }
}


def cleanup(Globals globals) {
    if(fileExists(globals.cleanupScript)) {
        sh(script: "python3 ${globals.cleanupScript} --dir ${globals.cleanupDir}",
           label: 'Cleaning bazel cache')
    }

    if (globals.cleanWorkspaceEnabled) {
        if (globals.bazelWorkspace) {
            sh(script: "chmod --recursive +w ${globals.bazelWorkspace}",
               label: "removing bazel workspace (${globals.bazelWorkspace})")

            sh(script: "rm -rf ${globals.bazelWorkspace}",
               label: "removing bazel workspace (${globals.bazelWorkspace})")
        }
    }

    if (globals.deleteOtherWorkspaces) {
        if (globals.bazelWorkspace) {
            sh(script: "find ~/.cache/bazel/_bazel_orionci -mindepth 1 -maxdepth 1 -type d -not -name cache -not -name install -not -wholename ${globals.bazelWorkspace} -print -exec chmod -R +rw {} \\; -exec rm -rf {} \\;",
               label: "deleting all workspaces except for ${globals.bazelWorkspace}")
        }
    }
}

def orchestratePipeline(globals) {
    // Dancing Mario party starts
    slackSendBuildInProgress(channel: globals.CI_SLACK_CHANNEL)

    // Run the pipeline steps inside the custom node closure
    def nodeClosure = { label, steps ->
        node(label) {
            String wsLock = getNodeFileLockName(env.NODE_NAME, globals.workspace)
            lock(wsLock) {
                ws(globals.workspace) {
                    // Define any consistent settings here
                    withEnv([
                        'NIX_REMOTE=daemon',
                        "PATH=/home/orionci/.nix-profile/bin:/nix/var/nix/profiles/system/bin:/nix/var/nix/profiles/default/bin:${env.PATH}",
                        'DEV_SHELL_NO_SPLASH=1',
                        'DIRENV_LOG_FORMAT=',
                        'GIT_CONFIG_NOSYSTEM=1']) {
                        ansiColor('xterm') {
                            steps()
                        }
                    }
                }
            }
        }
    }

    // This is the start of the scripted pipeline
    nodeClosure(params.NODE_LABEL) {
        String hostname = getHostname()
        stage(hostname) {
            echo "Checkout on ${hostname} in ${WORKSPACE}"
        }
        stage('Checkout') {
            isiExecute(globals.realtimeCiMetrics) {
                def jobBranch = env.JOB_NAME.split('/')
                stopPreviousBuildingJobs(jobBranch[0]/*job Name*/, jobBranch[1]/*branch*/, globals.branchExceptions)

                globals.userEmail = getBuildUserEmail()
                updateCheckoutData(mirrorCheckout(globals), globals)
            }
        }
        stage('Configuring Build') {
            isiExecute(globals.realtimeCiMetrics) {
                withLaunchDarkly {
                    globals.buildBuddyEnabled = boolVariation('fflag.ci.pipeline.buildbuddy.enabled', globals.buildBuddyEnabled)
                    unsafeEcho("retrieved BuildBuddyEnabled: ${globals.buildBuddyEnabled}")

                    globals.remoteExecutionEnabled = boolVariation('fflag.ci.pipeline.remote_exec.enabled', globals.remoteExecutionEnabled)
                    unsafeEcho("retrieved remoteExecutionEnabled: ${globals.remoteExecutionEnabled}")

                    globals.differEnabled = boolVariation('fflag.ci.pipeline.differ.enabled', globals.differEnabled)
                    unsafeEcho("retrieved differEnabled: ${globals.differEnabled}")

                    globals.cleanWorkspaceEnabled = boolVariation('fflag.ci.pipeline.clean_workspace.enabled', globals.cleanWorkspaceEnabled)
                    unsafeEcho("retrieved cleanWorkspaceEnabled: ${globals.cleanWorkspaceEnabled}")

                    globals.deleteOtherWorkspaces = boolVariation('fflag.ci.pipeline.delete_other_workspaces.enabled', globals.deleteOtherWorkspaces)
                    unsafeEcho("retrieved deleteOtherWorkspaces: ${globals.deleteOtherWorkspaces}")

                    globals.selectDevBuildBuddy = boolVariation('fflag.ci.pipeline.select-dev-buildbuddy', globals.selectDevBuildBuddy)
                    unsafeEcho("retrieved selectDevBuildBuddy: ${globals.selectDevBuildBuddy}")

                    globals.commonStages.each { stage ->
                        globals.stagesEnabled[stage] = boolVariation("fflag.ci.pipeline.stage.${stage}.enabled", true)
                        globals.stagesQuarantined[stage] = boolVariation("fflag.ci.pipeline.stage.${stage}.quarantined", false)
                        unsafeEcho("stage ${stage} -> enabled: ${globals.stagesEnabled[stage]}, quarantined: ${globals.stagesQuarantined[stage]}")
                    }

                }

                globals.extraStartupConfigs = ""

                globals.extraBuildConfigs += globals.fakeConfig

                if (!globals.buildBuddyEnabled) {
                    globals.extraBuildConfigs += ' --config=noremote'
                }

                if (globals.remoteExecutionEnabled) {
                    globals.extraBuildConfigs += ' --config=rbe'
                }

                if (globals.selectDevBuildBuddy) {
                    globals.cleanupDir = '~/.cache/bazel/disk-cache/'
                    // double quotes for substitution
                    globals.extraBuildConfigs += " --disk_cache=${globals.cleanupDir}"
                    globals.extraBuildConfigs += ' --bes_results_url=https://buildbuddy-dev.data.corp.intusurg.com/invocation/'
                    globals.extraBuildConfigs += ' --bes_backend=grpcs://buildbuddy-dev-grpc.data.corp.intusurg.com'
                    globals.extraBuildConfigs += ' --remote_cache=grpcs://buildbuddy-dev-grpc.data.corp.intusurg.com'
                    globals.extraBuildConfigs += ' --remote_header=x-buildbuddy-api-key=5RJmydkEuRLQKN4Q4gqg'
                }

                globals.bazelWorkspace = bazel_sh("""bazel ${globals.extraStartupConfigs} info output_base \
                                                              --config=ci \
                                                              ${globals.extraBuildConfigs}""",
                                                  'Fetching bazel output base',
                                                  true).trim()

                globals.bazelTestPath = bazel_sh("""bazel ${globals.extraStartupConfigs} info bazel-testlogs \
                                                              --config=ci \
                                                              ${globals.extraBuildConfigs}""",
                                                 'Fetching bazel test logs path',
                                                 true).trim()
            }
        }
        stage ('Linting') {
            isiExecute(globals.realtimeCiMetrics) {

                bazel_sh("""bazel ${globals.extraStartupConfigs} run \
                            lint \
                            --config=ci \
                            ${globals.extraBuildConfigs}""",
                         '> bazel run lint')
            }
        }
        stage ('Collecting Test Targets') {
            isiExecute(globals.realtimeCiMetrics) {

                if (JOB_BASE_NAME.startsWith('PR-') && !skipDiff(env.CHANGE_TITLE) && globals.differEnabled) {
                    unsafeEcho("collecting set of test targets between ${globals.startHash}..${globals.finalHash} range")
                    String bazel  = sh(script: '/bin/bash ./build/scripts/direnv/start_dev_shell.sh which bazel',
                                       label: 'Fetching bazel executable',
                                       returnStdout: true).trim()

                    bazel_sh("""bazel-differ get-targets -y ${globals.extraStartupConfigs} -w ${WORKSPACE} -b ${bazel} -s ${globals.startHash} -f ${globals.finalHash} -q 'kind(".*_test",set({{.Targets}}))' -o test_targets.txt""", 'Collecting test targets')
                } else {
                    bazel_sh("""bazel ${globals.extraStartupConfigs} \
                                    query "kind('.*_test', //...)" | sed '/\\/\\/_\\(apollo\\|prometheus\\)\\/lib\\/download_app_lib:pytests/d' > test_targets.txt""",
                             'Collecting all test targets')
                }

                sh(script: 'cat test_targets.txt',
                   label: 'Test target list')

                bazel_sh("""bazel ${globals.extraStartupConfigs} \
                                query "attr(size, 'small|medium|large', set(\$(<test_targets.txt))) except attr('tags', 'broken|manual|drydock-tests', set(\$(<test_targets.txt))) except attr('flaky', 1, set(\$(<test_targets.txt)))" > unittest_targets.txt
                               """, 'Collecting unittests')

                String unittestList = readFile("unittest_targets.txt")
                globals.unittestTargets = unittestList.readLines()

                if (globals.unittestTargets) {
                    unsafeEcho("Planned unittest tests: ${globals.unittestTargets.join(',')}")
                } else {
                    unsafeEcho('No unittest targets changed, unittest stage will be skipped')
                }

                bazel_sh("""bazel ${globals.extraStartupConfigs} \
                                query "attr(size, 'small|medium|large', set(\$(<test_targets.txt))) intersect attr(tags, 'drydock-tests', set(\$(<test_targets.txt))) except attr('tags', 'broken', set(\$(<test_targets.txt))) except attr('flaky', 1, set(\$(<test_targets.txt)))" > integration_targets.txt
                               """, 'Collecting integration tests')

                String integrationList = readFile("integration_targets.txt")
                globals.integrationTargets = integrationList.readLines()

                if (globals.integrationTargets) {
                    unsafeEcho("Planned integration tests: ${globals.integrationTargets.join(',')}")
                } else {
                    unsafeEcho('No integration targets changed, integration stage will be skipped')
                }

                bazel_sh("""bazel ${globals.extraStartupConfigs} \
                                query "attr(size, 'enormous', set(\$(<test_targets.txt))) intersect attr('tags', 'drydock-tests', set(\$(<test_targets.txt))) except attr('tags', 'broken', set(\$(<test_targets.txt))) except attr('flaky', 1, set(\$(<test_targets.txt)))" > enormous_targets.txt""",
                         'Collecting normal mode tests')

                String enormousList = readFile("enormous_targets.txt")
                globals.enormousTargets = enormousList.readLines()

                if (globals.enormousTargets) {
                    unsafeEcho("Planned enormous tests: ${globals.enormousTargets.join(',')}")
                } else {
                    unsafeEcho('No enormous targets changed, enormous stage will be skipped')
                }
            }
        }
        stage('DryDock Build') {
            isiExecute(globals.realtimeCiMetrics) {
                sh('printenv')
                String metadata = getBuildMetadata(globals, hostname)
                bazel_sh("""bazel ${globals.extraStartupConfigs} build  //_orion2/images/dry_dock_subsys/dry_dock_normal:dry_dock \
                            --config=ci \
                            ${globals.extraBuildConfigs} \
                            ${metadata}""",
                         '> bazel build //_orion2/images/dry_dock_subsys/dry_dock_normal:dry_dock')
            }
        }
        stage('Upload DryDock Tooling') {
            isiExecute(globals.realtimeCiMetrics) {
                uploadTooling()
            }
        }
        stage('Cleanup') {
            cleanup(globals)
        }
        stage('Allure Launch') {
            withAllureLaunchISI(enabled: globals.allureEnabled,
                                serverId: globals.ALLURE_SERVER_ID,
                                projectId: globals.ORION2_ALLURE_PROJECT_ID,
                                tags: "${BRANCH_NAME}") {
                if (env.ALLURE_JOB_RUN_ID) {
                    globals.allureJobRunId = env.ALLURE_JOB_RUN_ID
                    unsafeEcho("Created allureJobRunId=${globals.allureJobRunId}")
                }
            }
        }
    }

    stage('Run Distributed Build') {
        def testMap = [
            'unittest': globals.unittestTargets,
            'integration': globals.integrationTargets,
        ]

        // Enormous tests run in parallel
        globals.enormousTargets.each { testTarget ->
            String defaultShortName = testTarget.split(':')[1]
            String parallelStage = globals.shortStageNames[testTarget] ?: defaultShortName
            testMap[parallelStage] = [ testTarget ]
        }

        def ptests = [:]
        ptests.failFast = false
        testMap.each { stageName, testTargets ->
            if (testTargets) {
                boolean combineTests = (stageName == 'unittest')
                boolean dryDockTest = (stageName != 'unittest')
                boolean ui = (stageName == 'ui')
                if (globals.stagesEnabled[stageName]) {
                    ptests[stageName] = buildTestStage(params.NODE_LABEL, stageName, globals,
                                                       nodeClosure, testTargets, combineTests, dryDockTest, ui)
                }
            }
        }

        String deployableStage = 'deployable'
        if (globals.stagesEnabled[deployableStage]) {
            ptests[deployableStage] = buildDeployable(params.NODE_LABEL, deployableStage, globals, nodeClosure)
        }

        String buildStage = 'build'
        if (globals.stagesEnabled[buildStage]) {
            ptests[buildStage] = buildFullTree(params.NODE_LABEL, buildStage, globals, nodeClosure)
        }

        parallel ptests

    }
    stage ('Wrapup') {
        echo 'wrap'
    }
}


def executePipeline() {
    Globals globals = new Globals()

    withSlackSendBuildStatus(channel: globals.CI_SLACK_CHANNEL,
                             realTime: globals.REALTIME_SLACK_NOTIFY,
                             postCondition: true,
                             targetSha: globals.target_sha,
                             sourceSha: globals.source_sha,
                             collectMetrics: globals.realtimeCiMetrics) {
        orchestratePipeline(globals)
    }
}

executePipeline()
