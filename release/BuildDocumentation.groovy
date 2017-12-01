#!groovy
import groovy.json.*
DOCKER_HOST=params['DOCKER_HOST']

REGISTRY="192.168.0.1"
REGISTRY_URL="https://${REGISTRY}/"

DOCKER_CONTAINER=[:]
RELEASE_OUT_DIR="/net/fileserver/"
LOCAL_TAR_DIR="/mnt/workspace/tmp/"
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
  [ 'buildType': 'native', 'testType': 'native', 'name': 'windows',             'packageFormat': 'NSIS',   'OS': "CYGWIN_NT-10.0", 'buildArgs': "--msvc",     'cluster': false, 'LOCALFS': 'c:/mnt/workspace/tmp/', 'FS': '/var/tmp/r', 'reliable': true, 'BUILD': "../../${testPathPrefix}_", 'CBUILD': "../../${testPathPrefix}c_", 'SYMSRV': '/cygdrive/e/symsrv/', 'testArgs': "--ruby c:/tools/ruby23/bin/ruby.exe --rspec c:/tools/ruby23/bin/rspec"],

  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'arangodb/documentation-builder',        'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--rpath --jemalloc", 'cluster': false,  'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/mnt/data/fileserver/', 'reliable': true, 'BUILD': '', 'CBUILD':'', 'SYMSRV':'', 'testArgs': ''],
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
    if (!buildUnittestTarball) {
      outDir = getReleaseOutDir(enterpriseUrl, envName)
    }
    print(buildEnv)
    if (params['FORCE_GITBRANCH'] != "") {
      sh """echo "${params['FORCE_GITBRANCH']}" > VERSION"""
    }
    def BUILDSCRIPT ="""
    id
    echo ~
    ARANGODB_VERSION_MAJOR=`grep 'set(ARANGODB_VERSION_MAJOR' CMakeLists.txt | sed 's;.*\"\\(.*\\)\".*;\\1;'`
    ARANGODB_VERSION_MINOR=`grep 'set(ARANGODB_VERSION_MINOR' CMakeLists.txt | sed 's;.*\"\\(.*\\)\".*;\\1;'`
    ARANGODB_VERSION_REVISION=`grep 'set(ARANGODB_VERSION_REVISION' CMakeLists.txt | sed 's;.*\"\\(.*\\)\".*;\\1;'`

    ls -l /usr/local/ /usr/local/nodeshit/
    rm -f ~/.gitbook
    rm -f ~/.npm

    ln -s /usr/local/nodeshit/gitbook ~/.gitbook
    ln -s /usr/local/nodeshit/npm ~/.npm

    ls -l ~/.gitbook/

    /usr/local/bin/gitbook ls
    echo blarg
    INSTALLED_GITBOOK_VERSION=\$(/usr/local/bin/gitbook ls |grep '*'|sed \"s;.*\\* ;;\")
    if test -z \"\${INSTALLED_GITBOOK_VERSION}\"; then
        echo \"your container doesn't come with a preloaded version of gitbook, please update it.\"
        exit 1
    fi
    export GITBOOK_ARGS=(\"--gitbook\" \"\${INSTALLED_GITBOOK_VERSION}\")

    if test \"\${ARANGODB_VERSION_REVISION}\" = \"devel\"; then
        export NODE_MODULES_DIR=\"/tmp/devel/node_modules\"
    else
        export NODE_MODULES_DIR=\"/tmp/\${ARANGODB_VERSION_MAJOR}.\${ARANGODB_VERSION_MINOR}/node_modules\"
    fi
    if test -d \"\${NODE_MODULES_DIR}\" ; then 
      echo 'building documentation: '
      # cd Documentation/Books; make build-dist-books OUTPUT_DIR=${outDir} NODE_MODULES_DIR=\${NODE_MODULES_DIR}
      cd Documentation/Books; bash -x -e ./build.sh build-dist-books --outputDir ${outDir} --nodeModulesDir \${NODE_MODULES_DIR}
    else
      echo 'building documentation: '
      cd Documentation/Books; make build-dist-books OUTPUT_DIR=${outDir}
    fi
"""
    print(buildEnv)
    wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm']) {
      retry(5) {
        sh BUILDSCRIPT
      }
    }
    
    if (params['FORCE_GITBRANCH'] != "") {
      sh "git checkout VERSION"
    }
    if (VERBOSE) {
      sh "ls -l ${outDir}"
    }
  } catch (err) {
    stage('Send Notification for failed build' ) {
      gitCommitter = sh(returnStdout: true, script: 'git --no-pager show -s --format="%ae"')

      mail (to: gitCommitter,
            subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB Documentation' failed to run.", 
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
      //docker.withRegistry(REGISTRY_URL, '') {
        def myBuildImage = docker.image("${buildEnvironment['name']}")
        // myBuildImage.pull()
        echo "hello before docker ${RELEASE_OUT_DIR}"
        docker.image(myBuildImage.imageName()).inside("""\
 --volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw\
 --volume /jenkins:/mnt/:rw \
 --volume /jenkins/workspace:/home/jenkins/:rw \
 --volume /jenkins/workspace:/home/node/:rw \
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
      //}
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
    print(DOCKER_CONTAINER)
    setupEnvCompileSource(DOCKER_CONTAINER, false, ENTERPRISE_URL, EPDIR, DOCKER_CONTAINER['reliable'])
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

stage("generating Documentation Build report") {
  if (DOCKER_CONTAINER['buildType'] == 'docker') {
    nodeName = 'docker'
  } else {
    nodeName = DOCKER_CONTAINER['name']
  }
  node(nodeName) {
    def releaseOutDir = getReleaseOutDir(ENTERPRISE_URL, env.JOB_NAME)
    
    def failures = failures + "\n" +  sh(returnStdout: true, script: "cat ${releaseOutDir}/results/* || echo ''").trim()

    if (failures.size() > 5) {

      print(failures)

      def gitRange = ""
      if (lastKnownGitRev.size() > 1) {
        gitRange = "${lastKnownGitRev}.."
      }
      gitRange = "${gitRange}${currentGitRev}"
      if (VERBOSE) {
        print(gitRange)
      }
      def gitcmd = 'git --no-pager show -s --format="%ae>" ${gitRange} |sort -u |sed -e :a -e \'$!N;s/\\n//;ta\' -e \'s;>;, ;g\' -e \'s;, $;;\''
      if (VERBOSE) {
        print(gitcmd)
      }
      gitCommitters = sh(returnStdout: true, script: gitcmd)
      if (VERBOSE) {
        echo gitCommitters
      }
      def subject = ""
      if (fatalError) {
        subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has failed MISERABLY! "
      }
      else {
        subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has failed"
      }

      if (REPORT_TO == "slack") {
        // Accusing: ${gitCommitters} \n
        slackMsg = """Branch '${GITTAG}' on '${preferBuilder}' failed: (${env.BUILD_URL}) \n  ```${failures}```"""
        slackSend channel: '#status-packaging', color: '#FF0000', message: slackMsg

      }
      else if (REPORT_TO == "mail") {
        print("sending messages to ${gitCommitters}")
        mail (to: gitCommitters,
              subject: subject,
              body: "the failed testcases gave this output: ${failures}\nPlease go to ${env.BUILD_URL}.");
      }
      else { // report to upstream process via artifact
        msg = """${GITTAG} on ${preferBuilder} failed (${env.BUILD_URL}) \n Accusing: ${gitCommitters} \n ${failures}"""
        jstr = new JsonBuilder(['status':false, 'messge': msg]).toPrettyString()
        writeFile file: REPORT_TO, text: jstr
        archive REPORT_TO
      }
    }
    else {
      sh "echo ${currentGitRev} > ${lastKnownGoodGitFile}";

      if (REPORT_TO == "slack") {
	// no success messages for now. slackSend channel: '#status-packaging', color: '#00FF00', message: "${GITTAG} on ${preferBuilder} succeeded (${env.BUILD_URL}) OK"
      }
      else if (REPORT_TO == "mail") {
      }
      else { // report to upstream process via artifact
        jstr = new JsonBuilder(['status':false, 'messge': "success"]).toPrettyString()
        writeFile file: REPORT_TO, text: jstr
      }
    }
  }
}
