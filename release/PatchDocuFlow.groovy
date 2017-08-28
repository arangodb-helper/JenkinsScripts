VERSION_MAJOR_MINOR=""
REPO_TL_DIR=""

if (params['GITTAG'] == 'devel') {
  VERSION_MAJOR_MINOR="3.2"
  REPO_TL_DIR="nightly"
  // if we build devel, we don't have any v's at all:
  GITTAG="devel"
  GIT_BRANCH="devel"
}
else {
  def parts=params['PRETEND_GITVERSION'].tokenize(".")
  VERSION_MAJOR=parts[0]
  VERSION_MINOR=parts[1]
  VERSION_MAJOR_MINOR="${VERSION_MAJOR}.${VERSION_MINOR}"
  REPO_TL_DIR="arangodb${VERSION_MAJOR}${VERSION_MINOR}"
  // the GITTAG actualy matches the tag on the repo...
  GITTAG="${params['GITTAG']}"
  // while this one is the human readable value:
  GIT_BRANCH="${parts[0]}.${parts[1]}"
}
//================================================================================
stage("building documentation") {
  build( job: 'RELEASE__BuildDocumentation',
         parameters: [
           string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
           string(name: 'DOCKER_HOST', value: "docker"),
           string(name: 'GITTAG', value: "${GITTAG}"),
           string(name: 'preferBuilder', value: 'arangodb/documentation-builder'),
           string(name: 'FORCE_GITBRANCH', value: params['PRETEND_GITVERSION']),
           string(name: 'REPORT_TO', value: "slack"),
           string(name: 'GIT_BRANCH', value: "${GIT_BRANCH}"),
           booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
           booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
         ]
       )
}

//================================================================================
stage("create repositories") {
  node('master') {
    echo "syncing docu into packages"
    sh """
export GITTAG="${PRETEND_GITVERSION}"
${ARANGO_SCRIPT_DIR}/publish/copyDocumentation.sh \
                ${INTERMEDIATE_DIR}                    \
                /mnt/data/localstage/enterprise/${REPO_TL_DIR}  \
                /mnt/data/localstage/community/${REPO_TL_DIR}   \
                ${PRETEND_GITVERSION}                           \
                ${ENTERPRISE_SECRET}/${REPO_TL_DIR}             \
                repositories/${REPO_TL_DIR}
"""
  }
}


stage("publish documentation") {
  node('master') {
    sh """
export GITTAG="${PRETEND_GITVERSION}"
export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/publish_documentation.sh
"""
  }
}
