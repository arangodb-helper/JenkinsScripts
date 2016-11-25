#!groovy
REGISTRY="192.168.0.1"
REGISTRY_URL="https://${REGISTRY}/"

DOCKER_CONTAINER=[:]
RELEASE_OUT_DIR="/net/fileserver/"
LOCAL_TAR_DIR="/mnt/workspace/tmp/"
branches = [:]
failures = ""
ADMIN_ACCOUNT = "release-bot@arangodb.com"
lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"
lastKnownGitRev = ""
currentGitRev = ""
WORKSPACE = ""
BUILT_FILE = ""
DIST_FILE = ""
fatalError = false
VERBOSE = true
testParams = [:]
def CONTAINERS=[
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centosix',            'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc --rpmDistro centos", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': false],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centoseven',          'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc --rpmDistro centos", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'fedoratwentythree',   'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc --rpmDistro centos", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': false],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'opensusethirteen',    'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc --rpmDistro SUSE13", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'debianjessie',        'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntutwelveofour',   'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': false],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntufourteenofour', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntusixteenofour',  'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc --snap", 'cluster': true,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'windows',             'packageFormat': 'NSIS',   'OS': "Windows", 'buildArgs': "--msvc",     'cluster': false, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'macos',               'packageFormat': 'Bundle', 'OS': "Darwin",  'buildArgs': "--clang --staticOpenSSL",    'cluster': false, 'LOCALFS': '/Users/jenkins/mnt/workspace/tmp/', 'FS': '/Users/jenkins/net/fileserver/', 'reliable': true],
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
    }
  }
}

OS = DOCKER_CONTAINER['OS']


def getReleaseOutDir(String enterpriseUrl, String jobname) {
  if (enterpriseUrl.size() > 10) {
    outDir = "${RELEASE_OUT_DIR}/EP/${jobname}"
  } else {
    outDir = "${RELEASE_OUT_DIR}/CO/${jobname}"
  }
  return outDir
}

def compileSource(buildEnv, Boolean buildUnittestTarball, String enterpriseUrl, String outDir, String envName, Boolean Reliable) {
  try {
    sh "cmake --version"
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
    def buildDir = "build-${XEP}package-${buildEnv['name']}"
    def BUILDSCRIPT = "./Installation/Jenkins/build.sh standard  --rpath --parallel 16 --buildDir ${buildDir} ${EP} --targetDir ${outDir} ${buildEnv['buildArgs']}"
    if (! buildUnittestTarball) {
      BUILDSCRIPT="${BUILDSCRIPT} --package ${buildEnv['packageFormat']} "
    }
    if (CLEAN_BUILDENV == "true") {

      sh "rm -rf ${buildDir}"
    }
    if (!Reliable) {
      BUILDSCRIPT="nohup ${BUILDSCRIPT} 2>&1 > nohup.out & echo \$! > pid; tail -f nohup.out; wait"
      try {
        if (VERBOSE) {
          print(BUILDSCRIPT)
        }
        sh BUILDSCRIPT
      }
      catch (err) {
        input("message": "blarg")
        RUNNING_PID=readFile("pid").trim()
        def stillRunning=true
        while (stillRunning) {
          def processStat=""
          try{
            scripT="cat /proc/${RUNNING_PID}/stat"
            echo "script: ${scripT}"
            processStat = sh(returnStdout: true, script: scripT)
          }
          catch (x){}
          stillRunning=(processStat != "")
          sleep 1
        }
        sh "tail nohup.out"
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
      BUILT_FILE = "${outDir}/arangodb-${OS}.tar.gz"
      DIST_FILE = "${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz"
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

def setupEnvCompileSource(buildEnvironment, Boolean buildUnittestTarball, String enterpriseUrl, Boolean Reliable) {
  def outDir = ""
  print(buildEnvironment)
  if (buildEnvironment['buildType'] == 'docker') {
    node('docker') {
      sh "set"
      docker.withRegistry(REGISTRY_URL, '') {
        def myBuildImage = docker.image("${buildEnvironment['name']}/build")
        myBuildImage.pull()
        echo "hello before docker ${RELEASE_OUT_DIR}"
        echo """hello before docker ${RELEASE_OUT_DIR}"""
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
          if (VERBOSE) {
            sh "mount"
            sh "pwd"
            sh "cat /etc/issue /mnt/workspace/issue /etc/passwd"
            
          }
        
          sh 'pwd > workspace.loc'
          WORKSPACE = readFile('workspace.loc').trim()
          echo "hi"
          echo "${WORKSPACE}/out"
          outDir = "${WORKSPACE}/out"
          echo "checking out: "
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
      outDir = "${WORKSPACE}/out"
      compileSource(buildEnvironment, buildUnittestTarball, enterpriseUrl, outDir, buildEnvironment['name'], Reliable)
    }
  }
}


stage("cloning source") {
  if (DOCKER_CONTAINER['buildType'] == 'docker') {
    node {
      if (VERBOSE) {
        sh "pwd"
        sh "cat /etc/issue /jenkins/workspace/issue"
      }
      if (fileExists(lastKnownGoodGitFile)) {
        lastKnownGitRev=readFile(lastKnownGoodGitFile)
      }
      git url: 'https://github.com/arangodb/arangodb.git', tag: "${GITTAG}"
      currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
      sh "git checkout ${GITTAG}"
      print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
    }
  }
  else {
    node(DOCKER_CONTAINER['name']) {
      if (VERBOSE) {
        sh "pwd"
        sh "uname -a"
      }
      if (fileExists(lastKnownGoodGitFile)) {
        lastKnownGitRev=readFile(lastKnownGoodGitFile)
      }
      git url: 'https://github.com/arangodb/arangodb.git', tag: "${GITTAG}"
      currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
      sh "git checkout ${GITTAG}"
      print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
    }
  }
}

stage("building ArangoDB") {
  try {
    if (preferBuilder.size() > 0) {
      print(DOCKER_CONTAINER)
      setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL, DOCKER_CONTAINER['reliable'])
    }
    else {
      for (int c  = 0; c < CONTAINERS.size(); c++) {
        if (CONTAINERS[c]['buildType'] == 'docker' && CONTAINERS[c]['reliable'] == true) {
          DOCKER_CONTAINER = CONTAINERS[c]
          RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
          LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
          OS = DOCKER_CONTAINER['OS']
          print(DOCKER_CONTAINER)
          setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL, DOCKER_CONTAINER['reliable'])
        }
      }
    }
  } catch (err) {
    stage('Send Notification for build' ) {
      mail (to: ADMIN_ACCOUNT, 
            subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
            body: err.getMessage());
      currentBuild.result = 'FAILURE'
      throw(err)
    }
  }
}

stage("generating release build report") {
  node {
      
    def subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) is finished"
      
    mail (to: 'release-bot@arangodb.com',
          subject: subject,
          body: "we successfully compiled ${GITTAG} \nfind the results at ${env.BUILD_URL}.");
  }

}
