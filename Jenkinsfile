#!groovy
// We need these modules:
//
// We need permisions for several string manipulation operations, like take()
REGISTRY="192.168.0.1"
REGISTRY_URL="https://${REGISTRY}/"

DOCKER_CONTAINER=[:]
RELEASE_OUT_DIR="/net/fileserver/"
LOCAL_TAR_DIR="/mnt/workspace/tmp/"
branches = [:]
failures = ""
parallelJobNames = []
ADMIN_ACCOUNT = "willi@arangodb.com"
lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"
lastKnownGitRev = ""
currentGitRev = ""
WORKSPACE = ""
BUILT_FILE = ""
DIST_FILE = ""
fatalError = false
VERBOSE = true
ENTERPRISE_URL=""// TODO from param
testParams = [:]
//def preferBuilder="ubuntusixteenofour"
def preferBuilder="macos"
def CONTAINERS=[
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centosix',            'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'centoseven',          'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'opensusethirteen',    'packageFormat': 'RPM',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'debianjessie',        'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntufourteenofour', 'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'docker', 'testType': 'docker', 'name': 'ubuntusixteenofour',  'packageFormat': 'DEB',    'OS': "Linux",   'buildArgs': "--jemalloc", 'cluster': true, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'windows',             'packageFormat': 'NSIS',   'OS': "Windows", 'buildArgs': "--msvc",     'cluster': false, 'LOCALFS': '/mnt/workspace/tmp/', 'FS': '/net/fileserver/'],
  [ 'buildType': 'native', 'testType': 'native', 'name': 'macos',               'packageFormat': 'Bundle', 'OS': "Darwin",  'buildArgs': "--clang",    'cluster': false, 'LOCALFS': '/Users/jenkins/mnt/workspace/tmp/', 'FS': '/Users/jenkins/net/fileserver/'],
]

for (int c  = 0; c < CONTAINERS.size(); c++) {
  if (CONTAINERS[c]['name'] == preferBuilder) {
      DOCKER_CONTAINER = CONTAINERS[c]
      RELEASE_OUT_DIR = DOCKER_CONTAINER['FS']
      LOCAL_TAR_DIR = DOCKER_CONTAINER['LOCALFS']
  }
}

OS = DOCKER_CONTAINER['OS']

def getReleaseOutDir(String enterpriseUrl, String jobname) {
  if (enterpriseUrl.size() > 10) {
    outDir = "${RELEASE_OUT_DIR}/EP/${jobname}"
  } else {
    outDir = "${RELEASE_OUT_DIR}/${jobname}"
  }
  return outDir
}

def setDirectories(where, String localTarDir, String OS, String jobName, String MD5SUM, String distFile, String testRunName, String unitTests, String cmdLineArgs, String releaseOutDir) {
  localTarball="${localTarDir}/arangodb-${OS}.tar.gz"
  where['localTarDir'] = localTarDir
  where['localTarball'] = localTarball
  where['localWSDir']="${localTarDir}/${jobName}"
  where['localExtractDir']=where['localWSDir'] + "/x/"
  where['MD5SUM'] = MD5SUM
  where['distFile'] = distFile

  where['testRunName'] = testRunName
  where['unitTests'] = unitTests
  where['cmdLineArgs'] = cmdLineArgs
  where['releaseOutDir'] = releaseOutDir
}

def copyExtractTarBall (where, String buildHost) {
  print("${where['testRunName']}: copyExtractTarBall\n")
  
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
python /usr/bin/copyFileLockedIfNewer.py ${where['MD5SUM']} ${where['distFile']} ${where['localWSDir']} ${where['localTarball']} '${where['testRunName']}_${buildHost}' 'rm -rf ${where['localExtractDir']}; mkdir ${where['localExtractDir']}; cd ${where['localExtractDir']}; tar -xzf ${where['localTarball']}'
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
  def createNewSymlinks = "ln -s ${where['localExtractDir']}/* ${testWorkingDirectory}/"
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
  if (VERBOSE) {
    print("${where['testRunName']}: runTests")
  }

  def RCFile = "${testWorkingDirectory}/out/rc"
  def EXECUTE_TEST="""pwd;
         cat /mnt/workspace/issue
         export TMPDIR=${testWorkingDirectory}/out/tmp
         mkdir -p \${TMPDIR}
         echo 0 > ${RCFile}
         ls -l
         bash -x ${testWorkingDirectory}/scripts/unittest ${where['unitTests']} \
                --skipNondeterministic true \
                --skipTimeCritical true \
                ${where['cmdLineArgs']} || \
         echo \$? > ${RCFile}"""
  if (VERBOSE) {
    echo "${where['testRunName']}: ${EXECUTE_TEST}"
  }
  sh EXECUTE_TEST
  def shellRC = sh(returnStdout: true, script: "cat ${RCFile}").trim()
  if (shellRC != "0") {
    echo "SHELL EXITED WITH FAILURE: ${shellRC}xxx"
    failures = "${failures}\n\n test ${where['testRunName']} exited with ${shellRC}"
    currentBuild.result = 'FAILURE'
  }
  if (VERBOSE) {
    echo "${where['testRunName']}: recording results [ ${testWorkingDirectory}/out/UNITTEST_RESULT_*.xml ]:"
    sh "ls -l ${testWorkingDirectory}/out/UNITTEST_RESULT_*.xml"
  }
  junit "out/UNITTEST_RESULT_*.xml"
  //step([$class: 'JUnitResultArchiver', testResults: 'out/UNITTEST_RESULT_*.xml'])
  print("The currentBuild.result is: ${currentBuild.result}")
  def testFailuresFile = "${testWorkingDirectory}/out/testfailures.txt"
  def failureOutput = readFile(testFailuresFile)
  if (failureOutput.size() > 5) {
    echo "FAILING NOW!"
    sh "cp ${testFailuresFile} ${where['releaseOutDir']}/results/${where['testRunName']}.txt"
    failures = "${failureOutput}"
    currentBuild.result = 'FAILURE'
  }
  else {
    def executiveSummary = readFile("${testWorkingDirectory}/out/UNITTEST_RESULT_EXECUTIVE_SUMMARY.json").trim()
    echo "executiveSummary: ${executiveSummary}"
    if (executiveSummary == "true") {
      currentBuild.result = 'SUCCESS'
    } else {
      currentBuild.result = 'UNSTABLE'
    }
  }
}

def runThisTest(which, buildEnvironment, testWorkingDirectory)
{
    print('runThisTest')
    
  def where = testParams[which]
    if (buildEnvironment['testType'] == 'docker') {
	print("in")
    node {
      sh 'pwd > workspace.loc'
      def WORKSPACE = readFile('workspace.loc').trim()
	def testWorkingDirectory="${WD}/${where['testRunName']}"
      if (VERBOSE) {
        print("hello ${which}: ${where['testRunName']} ${where} RUNNING in ${WORKSPACE}")
      }
      dir("${where['testRunName']}") {
        if (VERBOSE) {
          echo "Hi, I'm [${where['testRunName']}] - ${where['unitTests']}"
        }
        docker.withRegistry(REGISTRY_URL, '') {
          def myRunImage = docker.image("${buildEnvironment['name']}/run")
          myRunImage.pull()
          docker.image(myRunImage.imageName()).inside('--volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw --volume /jenkins:/mnt/:rw --volume /var/lib/systemd/coredump:/var/lib/systemd/coredump:rw') {
            def buildHost=sh(returnStdout: true, script: "cat /mnt/workspace/issue").trim()
            buildHost = buildHost[-40..-1]
            if (VERBOSE) {
              sh "cat /etc/issue"
              sh "pwd"
              echo "${env} ${buildHost}"
            }
              copyExtractTarBall(where, buildHost, testWorkingDirectory)
              setupTestArea(where, testWorkingDirectory)
              runTests(where, testWorkingDirectory)

          }
        }
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
	    def testWorkingDirectory="${WD}/${where['testRunName']}"
	    print("done")
	    if (VERBOSE) {
		print("hello ${which}: ${mywhere['testRunName']} ${mywhere} RUNNING in ${WORKSPACE}")
	    }
	    dir("${mywhere['testRunName']}") {
		print("on directory")
		if (VERBOSE) {
		    echo "Hi, I'm [${mywhere['testRunName']}] - ${mywhere['unitTests']}"
		}
		def buildHost=buildEnvironment['name']
		copyExtractTarBall(mywhere, buildHost, testWorkingDirectory)
		setupTestArea(mywhere, testWorkingDirectory)
		runTests(mywhere, testWorkingDirectory)
	    }
	}
  }
}

def compileSource(buildEnv, Boolean buildUnittestTarball, String enterpriseUrl, String outDir) {
  try {
    def EP=""
    def XEP=""
    if (enterpriseUrl.size() > 10) {
      EP="--enterprise ${ENTERPRISE_URL}"
      XEP="EP"
    }
    if (!buildUnittestTarball) {
      outDir = getReleaseOutDir(enterpriseUrl, ${env.JOB_NAME})
    }
      print(buildEnv)
    def BUILDSCRIPT = "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --buildDir build-${XEP}package-${buildEnv['name']} ${EP} --targetDir ${outDir} ${buildEnv['buildArgs']}"
    if (! buildUnittestTarball) {
      BUILDSCRIPT="${BUILDSCRIPT} --package ${buildEnv['packageFormat']} "
    }
    if (VERBOSE) {
      print(BUILDSCRIPT)
    }
    
    sh BUILDSCRIPT
    
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

def setupEnvCompileSource(buildEnvironment, Boolean buildUnittestTarball, String enterpriseUrl) {
    def outDir = ""
    print("before")
  if (buildEnvironment['compileType'] == 'docker') {
    node {
      docker.withRegistry(REGISTRY_URL, '') {
        def myBuildImage = docker.image("${buildEnvironment['name']}/build")
        myBuildImage.pull()
        docker.image(myBuildImage.imageName()).inside('--volume /mnt/data/fileserver:${RELEASE_OUT_DIR}:rw --volume /jenkins:/mnt/:rw ') {
          if (VERBOSE) {
            sh "mount"
            sh "pwd"
          }
          if (VERBOSE) {
            sh "cat /etc/issue /mnt/workspace/issue"
          }
        
          sh 'pwd > workspace.loc'
          WORKSPACE = readFile('workspace.loc').trim()
          outDir = "${WORKSPACE}/out"
          compileSource(buildEnvironment, buildUnittestTarball, enterpriseUrl, outDir)
        }
      }
    }
  }
    else {
	print("else")
    node(buildEnvironment['name']){
      echo "building on ${buildEnvironment['name']}"
      sh 'pwd > workspace.loc'
      WORKSPACE = readFile('workspace.loc').trim()
      outDir = "${WORKSPACE}/out"
      compileSource(buildEnvironment, buildUnittestTarball, enterpriseUrl, outDir)
    }
  }
}

stage("cloning source")
if (DOCKER_CONTAINER['buildType'] == 'docker') {
  node {
    if (VERBOSE) {
      sh "mount"
      sh "pwd"
      sh "ls -l /jenkins/workspace"
      sh "cat /etc/issue /jenkins/workspace/issue"
    }
    if (fileExists(lastKnownGoodGitFile)) {
      lastKnownGitRev=readFile(lastKnownGoodGitFile)
    }
    git url: 'https://github.com/arangodb/arangodb.git', branch: 'devel'
    currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
  }
}
else {
  node(DOCKER_CONTAINER['name']) {
    if (VERBOSE) {
      sh "mount"
      sh "pwd"
      sh "uname -a"
    }
    if (fileExists(lastKnownGoodGitFile)) {
      lastKnownGitRev=readFile(lastKnownGoodGitFile)
    }
    git url: 'https://github.com/arangodb/arangodb.git', branch: 'devel'
    currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
  }
}
  
stage("building ArangoDB")
try {
    print(DOCKER_CONTAINER)
  setupEnvCompileSource(DOCKER_CONTAINER, true, ENTERPRISE_URL)
} catch (err) {
  stage('Send Notification for build' )
  mail (to: ADMIN_ACCOUNT, 
        subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
        body: err.getMessage());
  currentBuild.result = 'FAILURE'
  throw(err)
}

stage("running unittest")
try {
    def testCaseSets = [:]
    if (DOCKER_CONTAINER['cluster']) {
	testCaseSets = [ 
	    //  ["fail", 'fail', ""],
	    //    ["fail", 'fail', ""],
	    ['http_server', 'http_server', "",
	     "--cluster true --testBuckets 4/1 ",
	     "--cluster true --testBuckets 4/2 ",
	     "--cluster true --testBuckets 4/3 ",
	     "--cluster true --testBuckets 4/4 "],
	    ["shell_client", 'shell_client', "",
	     "--cluster true --testBuckets 4/1 ",
	     "--cluster true --testBuckets 4/2 ",
	     "--cluster true --testBuckets 4/3 ",
	     "--cluster true --testBuckets 4/4 "],
	    ["shell_server_aql", 'shell_server_aql', "",
	     "--cluster true --testBuckets 4/1 ",
	     "--cluster true --testBuckets 4/2 ",
	     "--cluster true --testBuckets 4/3 ",
	     "--cluster true --testBuckets 4/4 "],
	    ["dump_import", 'dump.importing', "", "--cluster true"],
	    ["shell_server", 'shell_server', "",
	     "--cluster true --testBuckets 4/1 ",
	     "--cluster true --testBuckets 4/2 ",
	     "--cluster true --testBuckets 4/3 ",
	     "--cluster true --testBuckets 4/4 "],
	    ['ssl_server', 'ssl_server', ""], // FC: don''t need this with clusters.
	    ["overal", 'config.upgrade.authentication.authentication_parameters.arangobench', ""],
	    ["arangosh", 'arangosh', ""],
	]
    } else {
	testCaseSets = [ 
	    //  ["fail", 'fail', ""],
	    //    ["fail", 'fail', ""],
	    ['http_server', 'http_server', ""],
	    ["shell_client", 'shell_client', ""],
	    ["shell_server_aql", 'shell_server_aql', ""],
//	    ["dump_import", 'dump.importing', ""],
//	    ["shell_server", 'shell_server', ""],
//	    ['ssl_server', 'ssl_server', ""], // FC: don''t need this with clusters.
//	    ["overal", 'config.upgrade.authentication.authentication_parameters.arangobench', ""],
//	    ["arangosh", 'arangosh', ""],
	]
    }
    print("getting keyset\n")
  def releaseOutDir = getReleaseOutDir(ENTERPRISE_URL, env.JOB_NAME)
    if (DOCKER_CONTAINER['buildType'] == 'docker') {
	node {
	    sh "mkdir -p ${releaseOutDir}/results/ ; rm -f ${releaseOutDir}/results/*;"
	}
    } else {
	node(DOCKER_CONTAINER['name']) {
	    sh "mkdir -p ${releaseOutDir}/results/ ; rm -f ${releaseOutDir}/results/*;"
	}
    }
  m = testCaseSets.size()
  int n = 0;
  for (int i = 0; i < m; i++) {
    def unitTestSet = testCaseSets.getAt(i);
    o = unitTestSet.size()
    def unitTests = unitTestSet.getAt(1);
    def shortName = unitTestSet.getAt(0);
    for (int j = 2; j < o; j ++ ) {
      def cmdLineArgs = unitTestSet.getAt(j)
      echo " ${shortName} ${cmdLineArgs} -  ${j}"
      testRunName = "${shortName}_${j}_${n}"
      parallelJobNames[n]=testRunName
      testParams[testRunName] = [:]
      setDirectories(testParams[testRunName], LOCAL_TAR_DIR, DOCKER_CONTAINER['OS'], env.JOB_NAME, MD5SUM, DIST_FILE, testRunName, unitTests, cmdLineArgs, releaseOutDir)
      n += 1
    }
  }
  print("setting up paralel branches")
  for (int i = 0; i < parallelJobNames.size(); i++) {
    def thisTestRunName = parallelJobNames[i]
    branches[thisTestRunName] = {
      runThisTest(thisTestRunName, DOCKER_CONTAINER)
    }
  }
  echo branches.toString();
  
  parallel branches
  print("The currentBuild.result is now after the parallel: ${currentBuild.result}")

} catch (err) {
  stage('Send Notification unittest')
  mail (to: ADMIN_ACCOUNT,
        subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'running unittest' has had a FATAL error.", 
        body: "error message: " + err.getMessage());
  currentBuild.result = 'FAILURE'
  throw(err)
}

stage("generating test report")
node {
  def releaseOutDir = getReleaseOutDir(ENTERPRISE_URL, env.JOB_NAME)
  def failures = failures +  sh(returnStdout: true, script: "cat ${releaseOutDir}/results/* || echo ''").trim()

  if (failures.size() > 5) {
    
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

    print("sending messages to ${gitCommitters}")
    mail (to: gitCommitters,
          subject: subject,
          body: "the failed testcases gave this output: ${failures}\nPlease go to ${env.BUILD_URL}.");
  }
  else {
    sh "echo ${currentGitRev} > ${lastKnownGoodGitFile}";
  }
}

