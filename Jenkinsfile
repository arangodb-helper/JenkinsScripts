#!groovy
stage("cloning source") {
  node {
    sh "cat /etc/issue"
    sh "pwd"
    sh "mount"
    git url: 'https://github.com/arangodb/arangodb.git', branch: 'pipeline'
  }
}
def REGISTRY="192.168.0.1"
def REGISTRY_URL="https://${REGISTRY}/"
def DOCKER_CONTAINER="centosix"
def OS="Linux"
def RELEASE_OUT_DIR="/net/fileserver/"
def LOCAL_TAR_DIR="/jenkins/tmp/"

// OUT_DIR = "/home/jenkins/shared/out"

stage("building ArangoDB") {
  node {
    OUT_DIR = ""
    docker.withRegistry("https://192.168.0.1/", '') {
      def myBuildImage=docker.image("centosix/build")
      myBuildImage.pull()
      docker.image(myBuildImage.imageName()).inside() {
        sh "mount"
        sh "cat /etc/issue"

        sh 'pwd > workspace.loc'
        WORKSPACE = readFile('workspace.loc').trim()
        OUT_DIR = "${WORKSPACE}/out"
        sh "./Installation/Jenkins/build.sh standard  --rpath --parallel 5 --package RPM --buildDir build-package --jemalloc --targetDir ${OUT_DIR} "
      }
    }
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
  }
}
stage("running unittest") {

  List<String> testCaseSets = [ 
    'boost.config.dump.importing.upgrade.authentication.authentication_parameters.arangobench',
    'shell_server.shell_server_aql',
    //  'http_server.ssl_server.shell_client',
    'arangosh'
  ]

  def branches = [:]

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
  for (int i = 0; i < testCaseSets.size(); i++) {
    def unitTests = testCaseSets.get(i);
    branches["tcs_${i}"] = {
      node {
        dir("${unitTests}") {
          echo "${unitTests}: ${COPY_TARBAL_SHELL_SNIPPET}"
          sh "${COPY_TARBAL_SHELL_SNIPPET}"
        
          docker.withRegistry("${REGISTRY_URL}", '') {
            def myRunImage = docker.image("${DOCKER_CONTAINER}/run")
            myRunImage.pull()
            docker.image(myRunImage).inside() {

      
              def EXECUTE_TEST="pwd; `pwd`/scripts/unittest ${unitTests} --skipNondeterministic true --skipTimeCritical true"
              echo "${unitTests}: ${EXECUTE_TEST}"
              sh "${EXECUTE_TEST}"
              echo "${unitTests}: recording results"
              step([$class: 'JUnitResultArchiver', testResults: 'out/UNITTEST_RESULT_*.xml'])
            }
          }
        }
      }
    }
  
  }
}
echo branches.toString();
parallel branches
