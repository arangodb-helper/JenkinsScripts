#!groovy
stage("cloning source") {
  def testCaseSets = [ 
    ['config.upgrade.authentication.authentication_parameters.arangobench', ""],
    ['dump.importing', "", "--cluster true"],
    ['shell_server', "",
     "--cluster true --testBuckets 4/1 ",
     "--cluster true --testBuckets 4/2 ",
     "--cluster true --testBuckets 4/3 ",
     "--cluster true --testBuckets 4/4 "],
    ['shell_server_aql', "",
     "--cluster true --testBuckets 4/1 ",
     "--cluster true --testBuckets 4/2 ",
     "--cluster true --testBuckets 4/3 ",
     "--cluster true --testBuckets 4/4 "],
    ['arangosh', "",
     "--cluster true --testBuckets 4/1 ",
     "--cluster true --testBuckets 4/2 ",
     "--cluster true --testBuckets 4/3 ",
     "--cluster true --testBuckets 4/4 "],
  ]

  print("getting keyset\n")
  m = testCaseSets.size()
  print("size: ${m}\n")
  int n = 0;
  for (int i = 0; i < m; i++) {
    print("in loop\n")
    def unitTestSet = testCaseSets.getAt(i);
    o = unitTestSet.size()
    def unitTestName = unitTestSet.getAt(0);
    print("generating short name:\n")
    def shortName = unitTestName.take(12)
    print("generated short name: ${shortName}\n")
    for (int j = 1; j < o; j ++ ) {
      
      print("unitTestName: ${unitTestName}\n")
      def thisTestParam = unitTestSet.getAt(j)
      print("thisTestParam: ${unitTestName} ${thisTestParam}\n")
    }
  }
  node {
    sh "cat /etc/issue"
    git url: 'https://github.com/arangodb/arangodb.git', branch: 'devel'
  }
}
def REGISTRY="192.168.0.1"
def REGISTRY_URL="https://${REGISTRY}/"
def DOCKER_CONTAINER="centosix"
def OS="Linux"
def RELEASE_OUT_DIR="/net/fileserver/"
def LOCAL_TAR_DIR="/jenkins/tmp/"
def branches = [:]

//stage("building ArangoDB") {
//  node {
//    OUT_DIR = ""
//    docker.withRegistry("https://192.168.0.1/", '') {
//      def myBuildImage=docker.image("centosix/build")
//      myBuildImage.pull()
//      docker.image(myBuildImage.imageName()).inside('--volume /net/fileserver:/net/fileserver:rw') {
//        sh "cat /etc/issue"
//
//        sh 'pwd > workspace.loc'
//        WORKSPACE = readFile('workspace.loc').trim()
//        OUT_DIR = "${WORKSPACE}/out"
//
//        sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --buildDir build-package --jemalloc --targetDir ${OUT_DIR} "
//        //sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --package RPM --buildDir build-package --jemalloc --targetDir ${OUT_DIR} "
//        OUT_FILE = "${OUT_DIR}/arangodb-${OS}.tar.gz"
//        env.MD5SUM = readFile("${OUT_FILE}.md5")
//        echo "copying result files: "
//  
//        def UPLOAD_SHELLSCRIPT="""
//   set -x
//   if test -f ${OUT_FILE}.md5; then
//     remote_md5sum=`cat ${OUT_FILE}.md5`
//   fi
//   if test \"\${MD5SUM}\" != \"\${remote_md5sum}\"; then
//        echo 'uploading file'
//        cp ${OUT_FILE} ${RELEASE_OUT_DIR}
//        echo \"\${MD5SUM}\" > ${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz.md5
//   else
//        echo 'file not changed - not uploading'
//   fi
//"""
//        echo "${UPLOAD_SHELLSCRIPT}"
//        lock(resource: 'uploadfiles', inversePrecedence: true) {
//          sh "${UPLOAD_SHELLSCRIPT}"
//        }
//        sh "ls -l ${RELEASE_OUT_DIR}"
//      }
//    }
//  }
//}

stage("running unittest") {

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
    'config.upgrade.authentication.authentication_parameters.arangobench': [ ""],
                       'dump.importing': ["", "--cluster true"],
                       'shell_server': ["",
                                        "--cluster true --testBuckets 4/1 ",
                                        "--cluster true --testBuckets 4/2 ",
                                        "--cluster true --testBuckets 4/3 ",
                                        "--cluster true --testBuckets 4/4 "],
                       'shell_server_aql': ["",
                                        "--cluster true --testBuckets 4/1 ",
                                        "--cluster true --testBuckets 4/2 ",
                                        "--cluster true --testBuckets 4/3 ",
                                        "--cluster true --testBuckets 4/4 "],
                       'arangosh': ["",
                                        "--cluster true --testBuckets 4/1 ",
                                        "--cluster true --testBuckets 4/2 ",
                                        "--cluster true --testBuckets 4/3 ",
                                        "--cluster true --testBuckets 4/4 "],
  ]

  print("getting keyset")
  testCaseNames = testCaseSets.keySet()
  print("done keyset is: ${testCaseNames}")
  m = testCaseNames.size()
  print("size: ${m}")
  int n = 0;
  for (int i = 0; i < m; i++) {
    print("in loop")
    def unitTestName = testCaseNames.getAt(i);
    print("unitTestName: ${unitTestName}")
    def thisTest = testCaseSets[unitTestName]
    print("generating short name:")
    def shortName = unitTestName.take(12)
    print("generated short name: ${shortName}")
    
//    for (int j = 0; j < thisTest.size(); j++) {
//      def cmdLineArgs = thisTest.get(j)
//      echo "${cmdLineArgs} - ${j}"
//      
//      branches["${shortName}_${n}"] = {
//        node {
//          sh "cat /etc/issue"
//          sh "pwd"
//          dir("${unitTests}") {
//            echo "${unitTests}: ${COPY_TARBAL_SHELL_SNIPPET}"
//            docker.withRegistry("${REGISTRY_URL}", '') {
//              def myRunImage = docker.image("${DOCKER_CONTAINER}/run")
//              myRunImage.pull()
//              docker.image(myRunImage.imageName()).inside('--volume /net/fileserver:/net/fileserver:rw') {
//                sh "cat /etc/issue"
//                sh "ls -l ${RELEASE_OUT_DIR}"
//                lock(resource: 'uploadfiles', inversePrecedence: true) {
//                  sh "${COPY_TARBAL_SHELL_SNIPPET}"
//                }
//                def EXECUTE_TEST="pwd; `pwd`/scripts/unittest ${unitTests} --skipNondeterministic true --skipTimeCritical true ${cmdLineArgs}"
//                echo "${unitTests}: ${EXECUTE_TEST}"
//                sh "${EXECUTE_TEST}"
//                echo "${unitTests}: recording results"
//                junit 'out/UNITTEST_RESULT_*.xml'
//              }
//            }
//          }
//        }
//      }
//    n += 1
//    }
  
  }
  //echo branches.toString();
//  parallel branches
}
