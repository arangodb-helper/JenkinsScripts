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
ADMIN_ACCOUNT = "heiko@arangodb.com"
lastKnownGitRev = ""
lastKnownUISum = ""
currentGitRev = ""
currentUISum = ""
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
  //[ 'name': 'arangodb/documentation-builder', 'OS': "Linux", 'cluster': false, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/']
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'arangodb/documentation-builder',        'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc", 'cluster': false,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': '']
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

DOCKER_CONTAINER = CONTAINERS[0]
RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
// BUILD_DIR = DOCKER_CONTAINER['BUILD']
// TEST_ARGS = DOCKER_CONTAINER['testArgs']

OS = DOCKER_CONTAINER['OS']

lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"
lastKnownUISumFile="${RELEASE_OUT_DIR}${env.JOB_NAME}.uisum"

DIST_FILE = "${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz".replace("/cygdrive/c", "c:")
echo(DIST_FILE)

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

    def BUILDSCRIPT = """
      export PATH=/opt/arangodb/bin/:\$PATH
      git checkout devel
      git pull
      git remote set-url origin https://${ENTERPRISE_URL}@github.com/arangodb/arangodb.git
      git config --global user.email "admin@arangodb.com"
      git config --global user.name "ArangoDB Release Bot"
      mkdir -p build
      cd build
      cmake ..
      make frontend 
      cd ..
      set +e
      git add js/apps/system/_admin/aardvark/APP/frontend/src/*
      git add js/apps/system/_admin/aardvark/APP/frontend/build/*
      set -e

      echo "Changes detected. Setting up commit and pushing to devel branch."
      git commit -m "nightly frontend build"
      git push

      if [ \$? -ne 0 ]; then
          echo "Error. Something went wrong.."
          exit 1
      else
          echo "Done."
      fi
    """

    if (VERBOSE) {
      print(BUILDSCRIPT)
    }
    // run shell script
    sh BUILDSCRIPT
    sh "echo ${currentUISum} > ${lastKnownUISumFile}"
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
    def myBuildImage = docker.image("${buildEnvironment['name']}")
    echo "hello before docker ${RELEASE_OUT_DIR}"
    docker.image(myBuildImage.imageName()).inside("""\
 --volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw\
 --volume /jenkins:/mnt/:rw \
 --volume /jenkins/workspace:/home/jenkins/:rw \
 --volume /jenkins/workspace:/home/node/:rw \
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
                [[url: 'https://${ENTERPRISE_URL}@github.com/arangodb/arangodb.git']]])

      // follow deletion of upstream tags:
      sh "git fetch --prune origin +refs/tags/*:refs/tags/*"
      currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
      currentUISum = sh(returnStdout: true, script: 'find js/apps/system/_admin/aardvark/APP/frontend/ -path js/apps/system/_admin/aardvark/APP/frontend/build -prune -o -type f -exec md5sum {} \\; | sort -k 2 | md5sum | rev | cut -c 4- | rev').trim()
      if (fileExists(lastKnownGoodGitFile)) {
        lastKnownGitRev=readFile(lastKnownGoodGitFile)
      }
      if (fileExists(lastKnownUISumFile)) {
        lastKnownUISum=readFile(lastKnownUISumFile)
      } else {
        sh "echo ${currentUISum} > ${lastKnownUISumFile}"
      }
      print("Current UI MD5: ${currentUISum}");
      print("Last known UI MD5: ${lastKnownUISum}");
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
  print("Last    MD5 : " + lastKnownUISum.split("\n")[0]);
  print("Current MD5 : " + currentUISum)
  if (lastKnownUISum.split("\n")[0] != currentUISum) {
    print("Changes detected. Continuing with build")
    EPDIR=""
    if (ENTERPRISE_URL != "") {
      EPDIR="EP"
    }
    try {
      print(DOCKER_CONTAINER)
      setupEnvCompileSource(DOCKER_CONTAINER, true, ENTERPRISE_URL, EPDIR, DOCKER_CONTAINER['reliable'])
    } catch (err) {
      stage('Send Notification for build') {
        mail (to: ADMIN_ACCOUNT,
              subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
              body: err.getMessage());
        currentBuild.result = 'FAILURE'
      }
      throw(err)
    }
  } else {
    print("No changes detected. No need for build yet.")
  }
}
