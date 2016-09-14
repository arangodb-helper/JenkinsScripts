#!groovy
// We need these modules:
//
// We need permisions for several string manipulation operations, like take()
def REGISTRY="192.168.0.1"
def REGISTRY_URL="https://${REGISTRY}/"
def DOCKER_CONTAINER="centosix"
def OS="Linux"
def RELEASE_OUT_DIR="/net/fileserver/"
def LOCAL_TAR_DIR="/jenkins/tmp/"
def branches = [:]
def failures = ""
def paralellJobNames = []
def ADMIN_ACCOUNT = "willi@arangodb.com"
def lastKnownGoodGitFile="${RELEASE_OUT_DIR}/${env.JOB_NAME}.githash"
def lastKnownGitRev=""
def currentGitRev=""

def fatalError = false

stage("cloning source") {
  node {
    if (fileExists(lastKnownGoodGitFile)) {
      lastKnownGitRev=readFile(lastKnownGoodGitFile)
    }
    sh "cat /etc/issue"
    git url: 'https://github.com/arangodb/arangodb.git', branch: 'devel'
    currentGitRev = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    print("GIT_AUTHOR_EMAIL: ${env} ${currentGitRev}")
  }
}

stage("building ArangoDB") { try {
  node {
    OUT_DIR = ""
    docker.withRegistry("https://192.168.0.1/", '') {
      def myBuildImage=docker.image("centosix/build")
      myBuildImage.pull()
      docker.image(myBuildImage.imageName()).inside('--volume /net/fileserver:/net/fileserver:rw') {
        sh "cat /etc/issue"

        sh 'pwd > workspace.loc'
        WORKSPACE = readFile('workspace.loc').trim()
        OUT_DIR = "${WORKSPACE}/out"

        sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --buildDir build-package --jemalloc --targetDir ${OUT_DIR} "
        //sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --package RPM --buildDir build-package --jemalloc --targetDir ${OUT_DIR} "
        OUT_FILE = "${OUT_DIR}/arangodb-${OS}.tar.gz"
        env.MD5SUM = readFile("${OUT_FILE}.md5")
        echo "copying result files: "
  
        def UPLOAD_SHELLSCRIPT="""
   set -x
   if test -f ${OUT_FILE}.md5; then
     remote_md5sum=`cat ${OUT_FILE}.md5`
   fi
   if test \"\${MD5SUM}\" != \"\${remote_md5sum}\"; then
        echo 'uploading file'
        cp ${OUT_FILE} ${RELEASE_OUT_DIR}
        echo \"\${MD5SUM}\" > ${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz.md5
   else
        echo 'file not changed - not uploading'
   fi
"""
        echo "${UPLOAD_SHELLSCRIPT}"
        lock(resource: 'uploadfiles', inversePrecedence: true) {
          sh "${UPLOAD_SHELLSCRIPT}"
        }
        sh "ls -l ${RELEASE_OUT_DIR}"
      }
    }
  }
} catch (err) {
    stage('Send Notification for build' ) {
      mail (to: ADMIN_ACCOUNT, 
            subject: "Job '${env.JOB_NAME   }' (${env.BUILD_NUMBER}) 'building ArangoDB' has had a FATAL error.", 
            body: err.getMessage());
      currentBuild.result = 'FAILURE'
      throw(err)
    }
}}

stage("running unittest") { try {
  echo "syntax error following: "
  echo "${barf}"
  def COPY_TARBAL_SHELL_SNIPPET= """
   if test ! -d ${LOCAL_TAR_DIR}; then
        mkdir -p ${LOCAL_TAR_DIR}
   else
      if test -f ${LOCAL_TAR_DIR}/arangodb-${OS}.tar.gz.md5; then
           local_md5sum=`cat ${LOCAL_TAR_DIR}/arangodb-${OS}.tar.gz.md5`
      fi
   fi
   if test \"\${MD5SUM}\" != \"\${local_md5sum}\"; then
        cp ${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz ${LOCAL_TAR_DIR}
        echo \"\${MD5SUM}\" > ${LOCAL_TAR_DIR}/arangodb-${OS}.tar.gz.md5
   fi
   pwd
   tar -xzf ${LOCAL_TAR_DIR}/arangodb-${OS}.tar.gz
"""

    //  'http_server.ssl_server.shell_client': ["",
    // "--cluster true --testBuckets 4/1 ",
    //                                    "--cluster true --testBuckets 4/2 ",
    //                                    "--cluster true --testBuckets 4/3 ",
    //                                    "--cluster true --testBuckets 4/4 "],
  def testCaseSets = [ 
    ["fail", 'fail', ""],
    //    ["fail", 'fail', ""],
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
      
      branches[testRunName] = {
        node {
          sh "cat /etc/issue"
          sh "pwd"
          dir("${testRunName}") {
            echo "${unitTests}: ${COPY_TARBAL_SHELL_SNIPPET}"
            docker.withRegistry("${REGISTRY_URL}", '') {
              def myRunImage = docker.image("${DOCKER_CONTAINER}/run")
              myRunImage.pull()
              docker.image(myRunImage.imageName()).inside('--volume /net/fileserver:/net/fileserver:rw') {
                sh "cat /etc/issue"
                sh "ls -l ${RELEASE_OUT_DIR}"
                lock(resource: 'uploadfiles', inversePrecedence: true) {
                  sh "${COPY_TARBAL_SHELL_SNIPPET}"
                }
                def EXECUTE_TEST="pwd; `pwd`/scripts/unittest ${unitTests} --skipNondeterministic true --skipTimeCritical true ${cmdLineArgs}"
                echo "${unitTests}: ${EXECUTE_TEST}"
                sh "${EXECUTE_TEST}"
                echo "${unitTests}: recording results"
                junit 'out/UNITTEST_RESULT_*.xml'
                failureOutput=readFile('out/testfailures.txt')
                if (failureOutput.size() > 5) {
                  failures = "${failures}\n${failureOutput}"
                }
              }
            }
          }
        }
      }
      n += 1
    }
  
  }
  echo branches.toString();
  
  parallel branches
} catch (err) {
    stage('Send Notification unittest' ) {
      mail (to: ADMIN_ACCOUNT,
            subject: "Job '${env.JOB_NAME   }' (${env.BUILD_NUMBER}) 'running unittest' has had a FATAL error.", 
            body: err.getMessage());
      currentBuild.result = 'FAILURE'
      throw(err)
    }
}}

stage("generating test report") {
  node {
    if (failures.size() > 5) {
      def gitRange = ""
      if (lastKnownGitRev.size() > 1) {
        gitRange = "${lastKnownGitRev}.."
      }
      gitRange = "${gitRange}${currentGitRev}"
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
}
