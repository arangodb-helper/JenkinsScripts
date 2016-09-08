#!groovy
stage "cloning source"
node {
  sh "cat /etc/issue"
  sh "pwd"
  sh "cat /etc/docker/key.json"
  sh "ps -eaf"
  sh "find /etc/"
  sh "docker version"
  //  sh "/etc/init.d/docker restart"
  git url: 'https://github.com/arangodb/arangodb.git', branch: 'pipeline'
}
def REGISTRY="192.168.0.1"
def REGISTRY_URL="https://${REGISTRY}/"
def DOCKER_CONTAINER="${REGISTRY}/centosix"
def OS="Linux"
def RELEASE_OUT_DIR="/var/tmp/"
// OUT_DIR = "/home/jenkins/shared/out"

stage "building ArangoDB"
node {
  OUT_DIR = ""
  docker.withRegistry("${REGISTRY_URL}", '') {
    docker.image("${DOCKER_CONTAINER}/build").inside {
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
  sh "${UPLOAD_SHELLSCRIPT}"
   
}
stage "running unittest"

List<String> testCaseSets = [ 
  'boost.config.dump.importing.upgrade.authentication.authentication_parameters.arangobench',
  'shell_server.shell_server_aql',
  //  'http_server.ssl_server.shell_client',
  'arangosh'
]

def branches = [:]

def COPY_TARBAL_SHELL_SNIPPET= """
   if test -f /tmp/arangodb-${OS}.tar.gz.md5; then
           local_md5sum=`cat /tmp/arangodb-${OS}.tar.gz.md5`
   fi
   if test \"\${MD5SUM}\" != \"\${local_md5sum}\"; then
        cp ${RELEASE_OUT_DIR}/arangodb-${OS}.tar.gz /var/tmp/
        echo \"\${MD5SUM}\" > /tmp/arangodb-${OS}.tar.gz.md5
   fi
   pwd
   tar -xzf /var/tmp/arangodb-${OS}.tar.gz
"""
for (int i = 0; i < testCaseSets.size(); i++) {
  def unitTests = testCaseSets.get(i);
  branches["tcs_${i}"] = {
    node {
      dir("${unitTests}") {
        echo "${unitTests}: ${COPY_TARBAL_SHELL_SNIPPET}"
        sh "${COPY_TARBAL_SHELL_SNIPPET}"
        
        docker.withRegistry("${REGISTRY_URL}", '') {
          docker.image("${DOCKER_CONTAINER}/run").inside {

      
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

echo branches.toString();
parallel branches
