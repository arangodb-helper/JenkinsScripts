#!groovy
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
lastKnownGitRev = ""
currentGitRev = ""
WORKSPACE = ""
BUILT_FILE = ""
DIST_FILE = ""
BUILD_DIR = ""
fatalError = false
VERBOSE = true
MAINTAINERMODE = "false"
testParams = [:]
testPathPrefix = 'r'
def CONTAINERS=[
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centosix',            'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc --rpmDistro centos", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': false, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centoseven',          'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc --rpmDistro centos", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'fedoratwentyfive',   'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc --rpmDistro centos", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'opensusefortytwo',    'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc --gcc6 --rpmDistro SUSE13", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'debianjessie',        'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntutwelveofour',   'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': false, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntufourteenofour', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntusixteenofour',  'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc --snap", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],

  //  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntusixteenarmhfxc', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--xcArm /usr/bin/arm-linux-gnueabihf --noopt", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':''],
  // compiler to old ;-)  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntufourteenarmhfxc', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--xcArm /usr/bin/arm-linux-gnueabihf --noopt", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':''],
  // [ 'buildType': 'docker', 'testType': 'docker', 'name': 'debianjessiearmhfxc', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--xcArm /usr/bin/arm-linux-gnueabihf --noopt", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],

  [ 'buildType': 'native', 'testType': 'native', 'name': 'macos',               'packageFormat': 'Bundle', 'OS': "Darwin",  'buildArgs': "--clang --staticOpenSSL",    'cluster': false, 'LOCALFS': '/Users/jenkins/mnt/workspace/tmp/', 'FS': '/Users/jenkins/net/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'windows',             'packageFormat': 'NSIS',   'OS': "CYGWIN_NT-10.0", 'buildArgs': "--msvc",     'cluster': false, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/var/tmp/r', 'reliable': true, 'BUILD': "../../${testPathPrefix}_", 'CBUILD': "../${testPathPrefix}c_", 'SYMSRV': '/cygdrive/e/symsrv/', 'testArgs': "--ruby c:/tools/ruby23/bin/ruby.exe --rspec c:/tools/ruby23/bin/rspec"]
]

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

lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"

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

////////////////////////////////////////////////////////////////////////////////
// Build source
def compileSource(buildEnv, Boolean buildUnittestTarball, String enterpriseUrl, String outDir, String envName, Boolean Reliable) {
  try {
    // sh "cmake --version"
    def EP=""
    def XEP=""
    if (enterpriseUrl.size() > 10) {
      EP="--enterprise ${ENTERPRISE_URL}"
      XEP="EP"
    }
    if (!buildUnittestTarball) {
      outDir = getReleaseOutDir(enterpriseUrl, envName)
    }
    print(buildEnv)
    def buildDir = ""
    if (buildEnv['BUILD'].size() == 0) {
      buildDir = "build-${XEP}package-${buildEnv['name']}"
    }
    else {
      buildDir = "${buildEnv['BUILD']}${XEP}"
    }
    def cBuildDir = ""
    if (buildEnv['CBUILD'].size() != 0) {
      cBuildDir = "--clientBuildDir ${buildEnv['CBUILD']}${XEP}"
    }

    def buildMode = "standard"
    if (MAINTAINERMODE == "true") {
      buildMode = "maintainer"
    }

    def BUILDSCRIPT = "./Installation/Jenkins/build.sh ${buildMode} --parallel ${PARALLEL_BUILD} --buildDir ${buildDir} ${cBuildDir} ${EP} --targetDir ${outDir} ${buildEnv['buildArgs']}"

    if (! buildUnittestTarball) {
      BUILDSCRIPT="${BUILDSCRIPT} --package ${buildEnv['packageFormat']} --downloadStarter"
    }
    if (buildEnv['SYMSRV'].size() != 0) {
      BUILDSCRIPT="${BUILDSCRIPT} --symsrv ${buildEnv['SYMSRV']} "
    }
    if (CLEAN_BUILDENV == "true") {
      BUILDSCRIPT="rm -rf ${buildDir} ; ${BUILDSCRIPT}"
    }
    else {
      BUILDSCRIPT="set; rm -rf ${buildDir}/CMakeFiles ${buildDir}/CMakeCache.txt ${buildDir}/CMakeCPackOptions.cmake ${buildDir}/cmake_install.cmake ${buildDir}/CPackConfig.cmake ${buildDir}/CPackSourceConfig.cmake ;${BUILDSCRIPT}"
    }
    if (!Reliable) {
      BUILDSCRIPT="""nohup bash -c "${BUILDSCRIPT}" > nohup.out 2>&1 & PID=\$!; echo \$PID > pid; tail -f nohup.out & wait \$PID; kill %2 ||true"""
      try {
        if (VERBOSE) {
          print(BUILDSCRIPT)
        }
        sh BUILDSCRIPT
      }
      catch (err) {
        RUNNING_PID=readFile("pid").trim()
        def stillRunning=true
        while (stillRunning) {
          def processStat=""
          try{
            scripT="cat /proc/${RUNNING_PID}/stat 2>/dev/null"
            echo "script: ${scripT}"
            processStat = sh(returnStdout: true, script: scripT).trim()
          }
          catch (x){}
          stillRunning=(processStat != "")
          sleep 5
        }
        sh "tail -n 100 nohup.out"
      }
    }
    else {
      // we expect this docker to run stable, so we don't fuck aroundwith nohup
      if (VERBOSE) {
        print(BUILDSCRIPT)
      }
      sh BUILDSCRIPT
    }
    if (buildUnittestTarball) {
      sh "mkdir -p ${RELEASE_OUT_DIR}"

      BUILT_FILE = "${outDir}/arangodb-${OS}.tar.gz".replace("/cygdrive/c", "c:")
      DIST_FILE = "${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz".replace("/cygdrive/c", "c:")
      echo(DIST_FILE)
      echo(BUILT_FILE)
      MD5SUM = readFile("${BUILT_FILE}.md5").trim()

      if (VERBOSE) {
        echo "copying result files: '${MD5SUM}' '${BUILT_FILE}' '${DIST_FILE}.lock' '${DIST_FILE}'"
      }

      sh "python /usr/bin/copyFileLockedIfNewer.py ${MD5SUM} ${BUILT_FILE} ${DIST_FILE}.lock ${DIST_FILE} ${env.JOB_NAME}"
    }
    else {
      // TODO: release?
    }
    if (VERBOSE) {
      sh "ls -l ${RELEASE_OUT_DIR}"
    }
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

def setupEnvCompileSource(buildEnvironment, Boolean buildUnittestTarball, String enterpriseUrl, String EPDIR, Boolean Reliable){
  def outDir = ""
  print(buildEnvironment)
  if (buildEnvironment['buildType'] == 'docker') {
    node(DOCKER_HOST) {
      sh "set"
      docker.withRegistry(REGISTRY_URL, '') {
        def myBuildImage = docker.image("${buildEnvironment['name']}/build")
        myBuildImage.pull()
        echo "hello before docker ${RELEASE_OUT_DIR}"
        docker.image(myBuildImage.imageName()).inside("""\
 --volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw\
 --volume /jenkins:/mnt/:rw \
""") {
          //           docker.image(myBuildImage.imageName()).withRun("""\
          //  -u 1000:1000 \
          // --volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw \
          // --volume /jenkins:/mnt/:rw \
          // --volume /var/lib/jenkins/workspace/ArangoDB_Release:/var/lib/jenkins/workspace/ArangoDB_Release:rw \
          // """) { c->
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
  else {
    print("building native")
    node(buildEnvironment['name']){
      print "else:"
      echo "building on ${buildEnvironment['name']}"
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
                  [$class: 'CheckoutOption', timeout: 30],
                  [$class: 'CloneOption', timeout: 30]
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
  if (DOCKER_CONTAINER['buildType'] == 'docker') {
    node(DOCKER_HOST) {
      CloneSource(true)
    }
  }
  else {
    node(DOCKER_CONTAINER['name']) {
      CloneSource(false)
    }
  }
}

stage("building ArangoDB") {
  EPDIR=""
  if (ENTERPRISE_URL != "") {
    EPDIR="EP"
  }
  try {
    if (preferBuilder.size() > 0) {
      print(DOCKER_CONTAINER)
      setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL, EPDIR, DOCKER_CONTAINER['reliable'])
    }
    else {
      for (int c  = 0; c < CONTAINERS.size(); c++) {
        if (CONTAINERS[c]['buildType'] == 'docker') {
          DOCKER_CONTAINER = CONTAINERS[c]
          RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
          LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
          OS = DOCKER_CONTAINER['OS']
          print(DOCKER_CONTAINER)
          setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL, EPDIR, DOCKER_CONTAINER['reliable'])
        }
      }
    }
  } catch (err) {
    stage('Send Notification for build' ) {
      // no slack now. slackSend channel: '#status-dev', color: '#439FE0', message: err.getMessage()
        /*
      msgBody = 
      if (msgBody.size() > 0) {
        mail (to: ADMIN_ACCOUNT, 
              subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
              body: msgBody);
        currentBuild.result = 'FAILURE'
        throw(err)
      }
        */
    }
  }
}

/*
  stage("generating release build report") {
  node {
      
  def subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) is finished"
      
  mail (to: 'release-bot@arangodb.com',
  subject: subject,
  body: "we successfully compiled ${GITTAG} \nfind the results at ${env.BUILD_URL}.");
  }

  }
*/
