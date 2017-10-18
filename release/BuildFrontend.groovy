#!groovy
import groovy.json.*
DOCKER_HOST=params['DOCKER_HOST']

REGISTRY="192.168.0.1"
REGISTRY_URL="https://${REGISTRY}/"

DOCKER_CONTAINER=[:]
RELEASE_OUT_DIR="/net/fileserver/"
LOCAL_TAR_DIR="/mnt/workspace/tmp/"
TEST_ARGS = ""
branches = [:]
failures = ""
ADMIN_ACCOUNT = "release-bot@arangodb.com"
parallelJobNames = []
ADMIN_ACCOUNT = "willi@arangodb.com"
lastKnownGitRev = ""
currentGitRev = ""
WORKSPACE = ""
BUILT_FILE = ""
DIST_FILE = ""
BUILD_DIR = ""
fatalError = false
VERBOSE = true

testParams = [:]

//@NonCPS
//def printParams() {
//  env.getEnvironment().each { name, value -> println "Name: $name -> Value $value" }
//}
//printParams()

testPathPrefix = 'j'
//currentBuild.result = 'SUCCESS'
def CONTAINERS=[
  [ 'name': 'arangodb/documentation-builder', 'OS': "Linux", 'cluster': false, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/'],
]

GITRAW=33
if (params['GITTAG'] == 'devel') {
  GITBRANCH="dev"
}
else {
  def parts=params['GITTAG'].tokenize(".")
  if (parts.size() == 3) {
    GITBRANCH="${parts[0]}.${parts[1]}"
    GITRAW="${parts[0].drop(1)}${parts[1]}".toInteger()
  }
  else {
    GITBRANCH="dev"
  }
}


if (preferBuilder.size() > 0) {
  found=false
  for (int c  = 0; c < CONTAINERS.size(); c++) {
    if (CONTAINERS[c]['name'] == preferBuilder) {
      print("prefering: ${CONTAINERS[c]}")
      found=true
      DOCKER_CONTAINER = CONTAINERS[c]
      RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
      LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
      BUILD_DIR = DOCKER_CONTAINER['BUILD']
      TEST_ARGS = DOCKER_CONTAINER['testArgs']
    }
  }
  if (!found) {
    throw new Exception("didn't find the builder '${preferBuilder}' you specified!")
  }
} else {
  for (int c  = 0; c < CONTAINERS.size(); c++) {
    if (CONTAINERS[c]['buildType'] == 'docker') {
      DOCKER_CONTAINER = CONTAINERS[c]
      RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
      LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
      BUILD_DIR = DOCKER_CONTAINER['BUILD']
      TEST_ARGS = DOCKER_CONTAINER['testArgs']
    }
  }
}

OS = DOCKER_CONTAINER['OS']
DOCKER_CONTAINER['cluster'] = (RUN_CLUSTER_TESTS == "true")

lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"

DIST_FILE = "${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz".replace("/cygdrive/c", "c:")
echo(DIST_FILE)

////////////////////////////////////////////////////////////////////////////////
// Test tarball

def getReleaseOutDir(String enterpriseUrl, String jobname) {
  if (enterpriseUrl.size() > 10) {
    outDir = "${RELEASE_OUT_DIR}/EP/${jobname}"
  } else {
    outDir = "${RELEASE_OUT_DIR}/CO/${jobname}"
  }
  return outDir
}

def setDirectories(where, String localTarDir, String OS, String jobName, String MD5SUM, String distFile, String testRunName, String unitTests, String cmdLineArgs, String releaseOutDir, String EPDIR) {
  localTarball="${localTarDir}/arangodb-${OS}.tar.gz"
  where['localTarDir'] = localTarDir
  where['localTarball'] = localTarball
  where['localWSDir']="${localTarDir}/${jobName}"
  where['localExtractDir']="${where['localWSDir']}/x${EPDIR}/"
  where['MD5SUM'] = MD5SUM
  where['distFile'] = distFile

  where['testRunName'] = testRunName
  where['unitTests'] = unitTests
  where['cmdLineArgs'] = cmdLineArgs
  where['releaseOutDir'] = releaseOutDir
}

def copyExtractTarBall (where, String buildHost, String testWorkingDirectory) {
  print("${where['testRunName']}: copyExtractTarBall\n")
  extract_tarball = where['localTarball'].replace("c:", "/cygdrive/c")

  CMD = """
if test ! -d ${where['localTarDir']}; then
        mkdir -p ${where['localTarDir']}
fi
if test ! -d ${where['localWSDir']}; then
        mkdir -p ${where['localWSDir']}
fi
if test ! -d ${where['localExtractDir']}; then
        mkdir -p ${where['localExtractDir']}
fi
if test ! -d ${where['releaseOutDir']}; then
        mkdir -p ${where['releaseOutDir']}
fi
python /usr/bin/copyFileLockedIfNewer.py ${where['MD5SUM']} ${where['distFile']} ${where['localWSDir']} ${where['localTarball']} '${where['testRunName']}_${buildHost}' 'rm -rf ${where['localExtractDir']}; mkdir ${where['localExtractDir']}; cd ${where['localExtractDir']}; tar -xzf ${extract_tarball}'
"""

  if (VERBOSE) {
    print CMD
  }
  sh CMD
}

def setupTestArea(where, testWorkingDirectory) {
  if (VERBOSE) {
    print("${where['testRunName']}: setupTestArea\n")
    print(where)
  }

  def createDirectory = "mkdir -p ${testWorkingDirectory}/out/ "
  def cleanOutFiles = "rm -rf ${testWorkingDirectory}/out/*"
  def removeOldSymlinks = "cd ${testWorkingDirectory}/; find . -type l -exec rm -f {} \\;;"
  def createNewSymlinks = "ln -s ${where['localExtractDir']}/* ${testWorkingDirectory}/ || true"
  if (VERBOSE) {
    sh "cat /mnt/workspace/issue"
    print(cleanOutFiles)
    print(createDirectory)
    print(cleanOutFiles)
    print(removeOldSymlinks)
    print(createNewSymlinks)
  }
  sh cleanOutFiles
  sh createDirectory
  sh cleanOutFiles
  sh removeOldSymlinks
  sh createNewSymlinks
}

def runTests(where, testWorkingDirectory) {
  def EXTREME_VERBOSITY=""
  if (VERBOSE) {
    print("${where['testRunName']}: runTests")
    //EXTREME_VERBOSITY="--extremeVerbosity true"
  }

  def TESTWD = "${testWorkingDirectory}/out".replace("/cygdrive/c", "c:")
  def cdTestWD = ""
  if (OS == "CYGWIN_NT-10.0") {
    cdTestWD = "cd ${where['localExtractDir']}"
  }

  def RCFile = "${testWorkingDirectory}/out/rc"
  def SHELL = env.SHELL;
  if (SHELL == "") {
    SHELL="/bin/bash"
  }
  def EXECUTE_TEST="""pwd;
         ulimit -c unlimited
         cat /mnt/workspace/issue
         ${cdTestWD}
         export TMPDIR=${testWorkingDirectory}/out/tmp
         export SHELL=${SHELL}
         mkdir -p \${TMPDIR}
         echo 0 > ${RCFile}
         ls -l
         cd ${where['localExtractDir']}/
         bash -x ./scripts/unittest ${where['unitTests']} \
                --skipNondeterministic true \
                --skipTimeCritical true \
                --build . \
                --testOutput ${TESTWD} \
                ${EXTREME_VERBOSITY} \
                ${TEST_ARGS} \
                ${where['cmdLineArgs']} || \
         echo \$? > ${RCFile}"""

  if (VERBOSE) {
    echo "${where['testRunName']}: ${EXECUTE_TEST}"
  }
  wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
    sh EXECUTE_TEST
  }
  def shellRC = sh(returnStdout: true, script: "cat ${RCFile}").trim()
  if (shellRC != "0") {
    failures = "${failures};test ${where['testRunName']} exited with ${shellRC}"
  }
  if (VERBOSE) {
    echo "${where['testRunName']}: recording results [ ${TESTWD}/*.xml ]:"
    sh "ls -l out/UNITTEST_RESULT_*.xml"
  }

  junit keepLongStdio: true, testResults: "out/*.xml"
  print("The currentBuild.result is: ${currentBuild.result}")
  def crashy = readFile("${TESTWD}/UNITTEST_RESULT_CRASHED.json")
  def testFailuresFile = "${TESTWD}/testfailures.txt"
  def failureOutput = readFile(testFailuresFile)
  def res = 'SUCCESS'
  if (failureOutput.size() > 5) {
    echo "switching to UNSTABLE!"
    sh "cp ${testFailuresFile} ${where['releaseOutDir']}/results/${where['testRunName']}.txt"
    res = currentBuild.result = 'UNSTABLE'
  }
  else {
    def executiveSummary = readFile("${TESTWD}/UNITTEST_RESULT_EXECUTIVE_SUMMARY.json").trim()
    echo "executiveSummary: ${executiveSummary} - ${crashy}"

    if (crashy == "true") {
      res = currentBuild.result = 'FAILURE'
    }
  }
  return res
}

def runThisTest(which, buildEnvironment)
{
  def rc=false
  print('runThisTest')
  def testWorkingDirectory;
  def where = testParams[which]
  if (buildEnvironment['testType'] == 'docker') {
    print("in")
    node('docker') {
      sh 'pwd > workspace.loc'
      def WORKSPACE = readFile('workspace.loc').trim()
      testWorkingDirectory="${WORKSPACE}/${where['testRunName']}"
      if (VERBOSE) {
        print("hello ${which}: ${where['testRunName']} ${where} RUNNING in ${WORKSPACE}")
      }
      dir("${where['testRunName']}") {
        if (VERBOSE) {
          echo "Hi, I'm [${where['testRunName']}] - ${where['unitTests']}"
        }
        // docker.withRegistry(REGISTRY_URL, '') {
          def myRunImage = docker.image("${buildEnvironment['name']}/run")
          // myRunImage.pull()
          docker.image(myRunImage.imageName()).inside("--volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw --volume /jenkins:/mnt/:rw --volume /var/lib/systemd/coredump:/var/lib/systemd/coredump:rw") {
            def buildHost=sh(returnStdout: true, script: "cat /mnt/workspace/issue").trim()
            buildHost = buildHost[32..35]
            if (VERBOSE) {
              sh "cat /etc/issue"
              sh "pwd"
              echo "${env} ${buildHost}"
            }
            // GO!
            copyExtractTarBall(where, buildHost, testWorkingDirectory)
            setupTestArea(where, testWorkingDirectory)
            rc = runTests(where, testWorkingDirectory)
          }
          //}
      }
    }
  }
  else {
    print("else")
    print(buildEnvironment)
    node(buildEnvironment['name']){
      print("on node")
      sh 'pwd > workspace.loc'
      def WORKSPACE = readFile('workspace.loc').trim()
      print("setting workspace")
      testWorkingDirectory="${WORKSPACE}/${where['testRunName']}"
      print("done")
      if (VERBOSE) {
        print("hello ${which}: ${where['testRunName']} ${where} RUNNING in ${WORKSPACE}")
      }
      dir("${where['testRunName']}") {
        print("on directory")
        if (VERBOSE) {
          echo "Hi, I'm [${where['testRunName']}] - ${where['unitTests']}"
        }
        def buildHost=buildEnvironment['name']
        // GO!
        copyExtractTarBall(where, buildHost, testWorkingDirectory)
        setupTestArea(where, testWorkingDirectory)
        rc = runTests(where, testWorkingDirectory)
      }
    }
  }
}

////////////////////////////////////////////////////////////////////////////////
// Build source
def compileSource(buildEnv, Boolean buildUnittestTarball, String enterpriseUrl, String outDir, String envName, Boolean Reliable) {
  try {
    // sh "cmake --version"
    def EP=""
    def XEP=""
    if (enterpriseUrl.size() > 10) {
      EP="--enterprise https://${ENTERPRISE_URL}@github.com/arangodb/enterprise"
      XEP="EP"
      if (GITRAW >= 33) {
        EP="${EP} --downloadSyncer ${ENTERPRISE_URL}"
      }
    }
    if (!buildUnittestTarball) {
      outDir = getReleaseOutDir(enterpriseUrl, envName)
    }
    print(buildEnv)
    def buildDir = ""
    if (buildEnv['BUILD'].size() == 0) {
      buildDir = "build-${XEP}package-${buildEnv['name']}-${GITBRANCH}"
    }
    else {
      buildDir = "${buildEnv['BUILD']}${XEP}-${GITBRANCH}"
    }
    def cBuildDir = ""
    if (buildEnv['CBUILD'].size() != 0) {
      cBuildDir = "--clientBuildDir ${buildEnv['CBUILD']}${XEP}"
    }

    def buildMode = "standard"
    if (MAINTAINERMODE == "true") {
      buildMode = "maintainer"
    }

    // def BUILDSCRIPT = "./Installation/Jenkins/build.sh ${buildMode} --parallel ${PARALLEL_BUILD} --buildDir ${buildDir} ${cBuildDir} ${EP} --targetDir ${outDir} ${buildEnv['buildArgs']}"
    def BUILDSCRIPT = "cd build; cmake ..; make frontend_clean; make frontend"; 

    if (VERBOSE) {
      print(BUILDSCRIPT)
    }
    // run shell script
    sh BUILDSCRIPT
  } catch (err) {
    stage('Send Notification for failed build' ) {
      gitCommitter = sh(returnStdout: true, script: 'git --no-pager show -s --format="%ae"')

      mail (to: gitCommitter,
            subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' failed to compile.", 
            body: err.getMessage());
      currentBuild.result = 'FAILURE'
      throw(err)
    }
  }
}

def setupEnvCompileSource(buildEnvironment, Boolean buildUnittestTarball, String enterpriseUrl, String EPDIR, Boolean Reliable) {
  def outDir = ""
  print(buildEnvironment)
  node(DOCKER_HOST) {
    sh "set"
    docker.withRegistry(REGISTRY_URL, '') {
      def myBuildImage = docker.image("${buildEnvironment['name']}/build")
      // myBuildImage.pull()
      echo "hello before docker ${RELEASE_OUT_DIR}"
      docker.image(myBuildImage.imageName()).inside("""\
--volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw\
--volume /jenkins:/mnt/:rw \
""") {
        echo "hello from docker"
        if (VERBOSE && Reliable) {
          sh "mount"
          sh "pwd"
          sh "cat /etc/issue /mnt/workspace/issue /etc/passwd"
        }

        sh 'pwd > workspace.loc'
        WORKSPACE = readFile('workspace.loc').trim()
        outDir = "${WORKSPACE}/out${EPDIR}"
        compileSource(buildEnvironment, buildUnittestTarball, enterpriseUrl, outDir, buildEnvironment['name'], Reliable)
      }
    }
  }
}

def CloneSource(inDocker){
      if (VERBOSE) {
        sh "pwd"
        if (inDocker) {
          sh "cat /etc/issue /jenkins/workspace/issue"
        }
        else {
          sh "uname -a"
        }
      }

      sh "rm -f 3rdParty/rocksdb/rocksdb/util/build_version.cc"
      checkout([$class: 'GitSCM',
                branches: [[name: "${GITTAG}"]],
                /*
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'SubmoduleOption',
                              disableSubmodules: false,
                              parentCredentials: false,
                              recursiveSubmodules: true,
                              reference: '',
                              trackingSubmodules: false]],
                submoduleCfg: [],
                */
                extensions: [
                  [$class: 'CheckoutOption', timeout: 20],
                  [$class: 'CloneOption', timeout: 20]
                ],
                userRemoteConfigs:
                [[url: 'https://github.com/arangodb/arangodb.git']]])

      // follow deletion of upstream tags:
      sh "git fetch --prune origin +refs/tags/*:refs/tags/*"
      currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
      if (fileExists(lastKnownGoodGitFile)) {
        lastKnownGitRev=readFile(lastKnownGoodGitFile)
      }
      currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
      print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
}

stage("cloning source") {
  print(DOCKER_CONTAINER)
  node(DOCKER_HOST) {
    CloneSource(true)
  }
}

stage("building ArangoDB") {
  EPDIR=""
  if (ENTERPRISE_URL != "") {
    EPDIR="EP"
  }
  try {
    print(DOCKER_CONTAINER)
    setupEnvCompileSource(DOCKER_CONTAINER, true, ENTERPRISE_URL, EPDIR, DOCKER_CONTAINER['reliable'])
  } catch (err) {
    stage('Send Notification for build' ) {
      mail (to: ADMIN_ACCOUNT,
            subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
            body: err.getMessage());
      currentBuild.result = 'FAILURE'
    }
    throw(err)
  }
}

stage("checking push") {

}
