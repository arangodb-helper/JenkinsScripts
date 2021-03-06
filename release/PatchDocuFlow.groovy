VERSION_MAJOR_MINOR=""
REPO_TL_DIR=""

PRETEND_GITVERSION_BUILD = params['PRETEND_GITVERSION']
hilightUsers = "@here"

matchDoc = /^documentation\/.*/
matchFeature = /^feature[0-9.-]*\/.*/
matchBugFix = /^bug-fix[0-9.-]*\/.*/
browseHint = ""
DIRECTORY=""
if (params['GITTAG'] == 'devel') {
    VERSION_MAJOR_MINOR="3.4"
    REPO_TL_DIR="nightly"
    // if we build devel, we don't have any v's at all:
    GITTAG="devel"
    GIT_BRANCH="devel"
    
    if (params['PRETEND_GITVERSION'] == 'devel') {
        PRETEND_GITVERSION_BUILD = ""
    }
    hilightUsers = ""
    browseHint = "access it at http://docs.arangodb.com/devel/"
}
else if (params['GITTAG'] ==~ matchDoc) {
    VERSION_MAJOR_MINOR="3.4"
    REPO_TL_DIR="nightly/${params['GITTAG']}"
    // if we build devel, we don't have any v's at all:
    GITTAG=params['GITTAG']
    GIT_BRANCH=params['GITTAG']
    DIRECTORY="review/${GITTAG}"
    if (params['PRETEND_GITVERSION'] == 'devel') {
        PRETEND_GITVERSION_BUILD = ""
    }
    hilightUsers = ""
    browseHint = "access it at http://docs.arangodb.com/review/${GITTAG}"
}
else if (params['GITTAG'] ==~ matchFeature) {
    VERSION_MAJOR_MINOR="3.4"
    REPO_TL_DIR="nightly/${params['GITTAG']}"
    // if we build devel, we don't have any v's at all:
    GITTAG=params['GITTAG']
    GIT_BRANCH=params['GITTAG']
    DIRECTORY="review/${GITTAG}"
    
    if (params['PRETEND_GITVERSION'] == 'devel') {
        PRETEND_GITVERSION_BUILD = ""
    }
    hilightUsers = ""
    browseHint = "access it at http://docs.arangodb.com/review/${GITTAG}"
}
else if (params['GITTAG'] ==~ matchBugFix) {
    VERSION_MAJOR_MINOR="3.4"
    REPO_TL_DIR="review/${params['GITTAG']}"
    // if we build devel, we don't have any v's at all:
    GITTAG=params['GITTAG']
    GIT_BRANCH=params['GITTAG']
    DIRECTORY="devel/${GITTAG}"
    
    if (params['PRETEND_GITVERSION'] == 'devel') {
        PRETEND_GITVERSION_BUILD = ""
    }
    hilightUsers = ""
    browseHint = "access it at http://docs.arangodb.com/review/${GITTAG}"
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
    browseHint = "access it at http://docs.arangodb.com/${VERSION_MAJOR_MINOR}"
}

//================================================================================
try {
    stage("building documentation") {
        build( job: 'RELEASE__BuildDocumentation',
              parameters: [
                string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                string(name: 'DOCKER_HOST', value: "docker_host"),
                string(name: 'GITTAG', value: "${GITTAG}"),
                string(name: 'preferBuilder', value: 'arangodb/documentation-builder'),
                string(name: 'FORCE_GITBRANCH', value: "${PRETEND_GITVERSION_BUILD}"),
                string(name: 'REPORT_TO', value: "slack"),
                string(name: 'GIT_BRANCH', value: "${GIT_BRANCH}"),
                booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
            ]
        )
    }
} catch (Exception err) {
    slackSend channel: "#documentation", color: '#ff0000', message: "${JENKINS_URL} - Building ${GITTAG} ${REPO_TL_DIR} documentation failed - ${err}"
    throw err;
}

//================================================================================
stage("create repositories") {
    node('master') {
        echo "syncing docu into packages"
        sh """
export REPO_TL_DIR=${REPO_TL_DIR};
export GITTAG="${PRETEND_GITVERSION}"
${ARANGO_SCRIPT_DIR}/publish/copyDocumentation.sh      \
                ${INTERMEDIATE_DIR}                    \
                ${INTERMEDIATE_EP_DIR}/${REPO_TL_DIR}  \
                ${INTERMEDIATE_CO_DIR}/${REPO_TL_DIR}  \
                ${PRETEND_GITVERSION}                  \
                ${ENTERPRISE_SECRET}/${REPO_TL_DIR}    \
                repositories/${REPO_TL_DIR}
"""
    }
}


def getUserId() {
    def user
    node {
        wrap([$class: 'BuildUser']) {
            user = env.BUILD_USER_ID
        }
    }
    return user
}

stage("publish documentation") {
    node('master') {
        sh """
export GITTAG="${PRETEND_GITVERSION}"
export REPO_TL_DIR=${REPO_TL_DIR};
export DIRECTORY=${DIRECTORY};
${ARANGO_SCRIPT_DIR}/publish/publish_documentation.sh
"""
        def userId = getUserId()
        
        slackSend channel: '#documentation', color: '#00ff00', message: "${hilightUsers} - ${userId} published a patched release ${GITTAG} on behalf of ${PRETEND_GITVERSION} - ${browseHint}"

    }
}
