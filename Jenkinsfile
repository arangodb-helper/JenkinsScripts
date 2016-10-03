#!groovy
// We need these modules:
//
// We need permisions for several string manipulation operations, like take()
REGISTRY="192.168.0.1"
REGISTRY_URL="https://${REGISTRY}/"
DOCKER_CONTAINER="ubuntusixteenofour"
OS="Linux"
RELEASE_OUT_DIR="/net/fileserver/"
LOCAL_TAR_DIR="/mnt/workspace/tmp/"
branches = [:]
failures = ""
paralellJobNames = []
ADMIN_ACCOUNT = "willi@arangodb.com"
lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"
lastKnownGitRev = ""
currentGitRev = ""
WORKSPACE = ""
BUILT_FILE = ""
DIST_FILE = ""
fatalError = false

def setDirectories(where, String localTarDir, String OS, String jobName, String MD5SUM, String distFile, String WD, String testRunName, String unitTests, String cmdLineArgs) {
   localTarball="${localTarDir}/arangodb-${OS}.tar.gz"
   where['localTarDir'] = localTarDir
   where['localTarball'] = localTarball
   where['localWSDir']="${localTarDir}/${jobName}"
   where['localExtractDir']=where['localWSDir'] + "/x/"
   where['MD5SUM'] = MD5SUM
   where['distFile'] = distFile
        
   where['testWorkingDirectory'] = "${WD}/${testRunName}"
   where['testRunName'] = testRunName
   where['unitTests'] = unitTests
   where['cmdLineArgs'] = cmdLineArgs
}


def copyExtractTarBall (where) {
  print("copyExtractTarBall\n")
  
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
python /usr/bin/copyFileLockedIfNewer.py ${where['MD5SUM']} ${where['distFile']} ${where['localWSDir']} ${where['localTarball']} 'rm -rf ${where['localExtractDir']}; mkdir ${where['localExtractDir']}; cd ${where['localExtractDir']}; tar -xzf ${where['localTarball']}'
"""

  print CMD
  sh CMD
}

def setupTestArea(where) {
  print("setupTestArea\n")
  sh "rm -rf ${where['testWorkingDirectory']}/out/*"
  sh "find -type l -exec rm -f {} \\; ; ln -s ${where['localExtractDir']}/* ${where['testWorkingDirectory']}/"
}
def Boolean runTests(where) {
  print("runTests")
  def EXECUTE_TEST="""pwd;
         export TMPDIR=${where['testWorkingDirectory']}/out/tmp
         mkdir -p \${TMPDIR}
         echo 0 > ${where['testWorkingDirectory']}/out/rc
         ${where['testWorkingDirectory']}/scripts/unittest ${where['unitTests']} \
                --skipNondeterministic true \
                --skipTimeCritical true \
                ${where['cmdLineArgs']} || \
         echo \$? > ${where['testWorkingDirectory']}/out/rc"""
  echo "${where['unitTests']}: ${EXECUTE_TEST}"
  sh EXECUTE_TEST
  shellRC = readFile("${where['testWorkingDirectory']}/out/rc").trim()
  if (shellRC != "0") {
    echo "SHELL EXITED WITH FAILURE: ${shellRC}xxx"
    failures = "${failures}\n\n test ${where['testRunName']} exited with ${shellRC}"
    currentBuild.result = 'FAILURE'
  }
  echo "${where['unitTests']}: recording results [ ${where['testWorkingDirectory']}/out/UNITTEST_RESULT_*.xml ]:"
  sh "ls -l ${where['testWorkingDirectory']}/out/UNITTEST_RESULT_*.xml"
  junit "${where['testWorkingDirectory']}/out/UNITTEST_RESULT_*.xml"
  failureOutput=readFile("${where['testWorkingDirectory']}/out/testfailures.txt")
  if (failureOutput.size() > 5) {
    failures = "${failureOutput}"
    return false;
  }
  return true;
}

def runThisTest(where, REGISTRY_URL) {
  node {
      sh 'pwd > workspace.loc'
      WORKSPACE = readFile('workspace.loc').trim()
      sh "pwd"
      dir("${where['testRunName']}") {
          echo "${where['unitTests']}"
          echo "${env}"
          docker.withRegistry(REGISTRY_URL, '') {
              def myRunImage = docker.image("${DOCKER_CONTAINER}/run")
              myRunImage.pull()
              docker.image(myRunImage.imageName()).inside('--volume /mnt/data/fileserver:/net/fileserver:rw --volume /jenkins:/mnt/:rw') {
                  sh "cat /etc/issue"
                  sh "cat /mnt/workspace/issue"
                  

                echo "${env}"
                  copyExtractTarBall(where)
                  setupTestArea(where)
                  runTests(where)

              }
          }
      }
  }
}

echo "bla"
stage("cloning source")
  node {
    sh "mount"
    sh "pwd"
    sh "ls -l /jenkins/workspace"
    sh "cat /etc/issue /jenkins/workspace/issue"
    def someString="1234567890"
    echo someString.take(5)
    
    if (fileExists(lastKnownGoodGitFile)) {
      lastKnownGitRev=readFile(lastKnownGoodGitFile)
    }
    git url: 'https://github.com/arangodb/arangodb.git', branch: 'devel'
    currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
  }

stage("building ArangoDB")
try {
  node {
    OUT_DIR = ""
    docker.withRegistry(REGISTRY_URL, '') {
      def myBuildImage=docker.image("${DOCKER_CONTAINER}/build")
      myBuildImage.pull()
      docker.image(myBuildImage.imageName()).inside('--volume /mnt/data/fileserver:/net/fileserver:rw --volume /jenkins:/mnt/:rw ') {
        sh "mount"
        sh "pwd"
        sh "cat /etc/issue /mnt/workspace/issue"

        sh 'pwd > workspace.loc'
        WORKSPACE = readFile('workspace.loc').trim()
        OUT_DIR = "${WORKSPACE}/out"

        try {
          sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --buildDir build-package-${DOCKER_CONTAINER} --jemalloc --targetDir ${OUT_DIR} "
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
        //sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --package RPM --buildDir build-package --jemalloc --targetDir ${OUT_DIR} "
        BUILT_FILE = "${OUT_DIR}/arangodb-${OS}.tar.gz"
        DIST_FILE = "${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz"
        MD5SUM = readFile("${BUILT_FILE}.md5").trim()
        echo "copying result files: '${MD5SUM}' '${BUILT_FILE}' '${DIST_FILE}.lock' '${DIST_FILE}'"

        sh "python /usr/bin/copyFileLockedIfNewer.py ${MD5SUM} ${BUILT_FILE} ${DIST_FILE}.lock ${DIST_FILE} "

        sh "ls -l ${RELEASE_OUT_DIR}"
      }
    }
  }
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
  def testCaseSets = [ 
    //  ["fail", 'fail', ""],
    //    ["fail", 'fail', ""],
    ['ssl_server', 'ssl_server', ""], // FC: don''t need this with clusters.
    ['http_server', 'http_server', "",
     "--cluster true --testBuckets 4/1 ",
     "--cluster true --testBuckets 4/2 ",
     "--cluster true --testBuckets 4/3 ",
     "--cluster true --testBuckets 4/4 "],
//    ["shell_client", 'shell_client', "",
//     "--cluster true --testBuckets 4/1 ",
//     "--cluster true --testBuckets 4/2 ",
//     "--cluster true --testBuckets 4/3 ",
//     "--cluster true --testBuckets 4/4 "],
//    ["shell_server_aql", 'shell_server_aql', "",
//     "--cluster true --testBuckets 4/1 ",
//     "--cluster true --testBuckets 4/2 ",
//     "--cluster true --testBuckets 4/3 ",
//     "--cluster true --testBuckets 4/4 "],
//    ["overal", 'config.upgrade.authentication.authentication_parameters.arangobench', ""],
//    ["dump_import", 'dump.importing', "", "--cluster true"],
//    ["shell_server", 'shell_server', "",
//     "--cluster true --testBuckets 4/1 ",
//     "--cluster true --testBuckets 4/2 ",
//     "--cluster true --testBuckets 4/3 ",
//     "--cluster true --testBuckets 4/4 "],
//    ["arangosh", 'arangosh', "",
//     "--cluster true --testBuckets 4/1 ",
//     "--cluster true --testBuckets 4/2 ",
//     "--cluster true --testBuckets 4/3 ",
//     "--cluster true --testBuckets 4/4 "],
  ]

  print("getting keyset\n")
  m = testCaseSets.size()
  int n = 0;
  def params = [:]
  for (int i = 0; i < m; i++) {
    def unitTestSet = testCaseSets.getAt(i);
    o = unitTestSet.size()
    def unitTests = unitTestSet.getAt(1);
    def shortName = unitTestSet.getAt(0);
    for (int j = 2; j < o; j ++ ) {
      def cmdLineArgs = unitTestSet.getAt(j)
      echo " ${shortName} ${cmdLineArgs} -  ${j}"
      testRunName = "${shortName}_${j}_${n}"
      paralellJobNames[n]=testRunName
      params[testRunName] = [:]
      setDirectories(params[testRunName], LOCAL_TAR_DIR, OS, env.JOB_NAME, MD5SUM, DIST_FILE, WORKSPACE, testRunName, unitTests, cmdLineArgs)
      
      branches[testRunName] = runThisTest(params[testRunName], REGISTRY_URL)
      n += 1
    }
  }
  echo branches.toString();
  
  parallel branches
} catch (err) {
  stage('Send Notification unittest' )
  mail (to: ADMIN_ACCOUNT,
        subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) 'running unittest' has had a FATAL error.", 
        body: err.getMessage());
  currentBuild.result = 'FAILURE'
  throw(err)
}

stage("generating test report")
  node {
    if (failures.size() > 5) {
      def gitRange = ""
      if (lastKnownGitRev.size() > 1) {
        gitRange = "${lastKnownGitRev}.."
      }
      gitRange = "${gitRange}${currentGitRev}"
      print(gitRange)
      def gitcmd = 'git --no-pager show -s --format="%ae>" ${gitRange} |sort -u |sed -e :a -e \'$!N;s/\\n//;ta\' -e \'s;>;, ;g\' -e \'s;, $;;\''
      print(gitcmd)
      gitCommitters = sh(returnStdout: true, script: gitcmd)
      echo gitCommitters
      
      def subject = ""
      if (fatalError) {
        subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has failed MISERABLY! "
      }
      else {
        subject = "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has failed"
      }
      
      mail (to: gitCommitters,
            subject: subject,
            body: "the failed testcases gave this output: ${failures}\nPlease go to ${env.BUILD_URL}.");
    }
    else {
      writeFile(lastKnownGoodGitFile, currentGitRev);
    }
  }

