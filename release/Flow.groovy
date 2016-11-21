stage("building packages") {
  if (params['SKIP_BUILD'] == "true") {
    parallel(
      [
        ////////////////////////////////////////////////////////////////////////////////
        "sourceTarballs": {
          node("master") {
            sh "export GITTAG=${params['GITTAG']}; ${ARANGO_SCRIPT_DIR}/source/build.sh"

          }
        },
        ////////////////////////////////////////////////////////////////////////////////
        "linuxPackages": {
          ///----------------------------------------------------------------------
          echo "building Linux Enterprise Release"
          build( job: 'ArangoDB_Release',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: params['preferBuilder']),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )

          ///----------------------------------------------------------------------
          echo "building Linux Community Release"
          build( job: 'ArangoDB_Release',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: ''),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: params['preferBuilder']),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )
                 
          ///----------------------------------------------------------------------
          echo "building Unstable builds with several attempts"
          EP_PARAMS=[ params['ENTERPRISE_URL'], '']
          UNSTABLE_BUILDERS = ['ubuntutwelveofour']
          for (EP_PARAM in EP_PARAMS ) {
            for (BUILDER in UNSTABLE_BUILDERS) {
              done=false
              count=0
              while (!done && count < 10) {
                rc = build( job: 'ArangoDB_Release',
                            propagate: false,
                            parameters: [
                              string(name: 'ENTERPRISE_URL', value: EP_PARAM),
                              string(name: 'GITTAG', value: "v3.1.1"),
                              string(name: 'preferBuilder', value: BUILDER),
                              booleanParam(name: 'CLEAN_BUILDENV', value: false),
                              booleanParam(name: 'propagate', value:false)]

                          )
                echo "Completed Run ${count} from ${BUILDER} with rc.result!"
                done = rc.result == "SUCCESS"
                count = count + 1
              }
            }
          }
        },
        ////////////////////////////////////////////////////////////////////////////////
        "macintosh": {
          echo "building MacOS X Enterprise Release"
          ///----------------------------------------------------------------------
          node("macos") {
            sh "rm -rf /Users/jenkins/net/fileserver/*"
          }
          ///----------------------------------------------------------------------
          build( job: 'ArangoDB_Release',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'macos'),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )

          ///----------------------------------------------------------------------
          echo "building MacOS X Community Release"
          build( job: 'ArangoDB_Release',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: ''),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'macos'),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )

          ///----------------------------------------------------------------------
          echo "uploading dmg's"
          node("macos") {
            sh "scp -r /Users/jenkins/net/fileserver/* ${env.JENKINSMASTER}:${env.INTERMEDIATE_DIR}"
          }
        },
        ////////////////////////////////////////////////////////////////////////////////
        "Windows": {
          // Windows doesn't like if we compile multiple times at once...
          ///----------------------------------------------------------------------
          echo "building Windows Enterprise Release"
          build( job: 'WindowsRelease',
                 parameters: [
                   string(name: 'ENTERPRISE_URL_PARAM', value: "--enterprise ${params['ENTERPRISE_URL']}"),
                   string(name: 'JENKINSMASTER', value: params['JENKINSMASTER']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'windows'),
                   booleanParam(name: 'FLUSH_OUTPUT', value: true),
                   booleanParam(name: 'UPLOAD_RESULTS', value: false),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )
                 
          ///----------------------------------------------------------------------
          echo "building Windows Community Release"
          build( job: 'RELEASE__BuildWindows',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: ''),
                   string(name: 'JENKINSMASTER', value: params['JENKINSMASTER']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'windows'),
                   booleanParam(name: 'FLUSH_OUTPUT', value: false),
                   booleanParam(name: 'UPLOAD_RESULTS', value: true),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )
        },
        ////////////////////////////////////////////////////////////////////////////////
        "documentation": {
          build( job: 'RELEASE__BuildWindows',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'debianjessieDocu'),
                   string(name: 'FORCE_GITBRANCH', value:''),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])]
               )
        }
      ]
    )
  }
}


stage("create repositories") {
  node('master') {
    if (SKIP_REPOBUILD == 'false') {
      sh """
${ARANGO_SCRIPT_DIR}/publish/copyFiles.sh \
        ${env.INTERMEDIATE_DIR} \
        ${env.INTERMEDIATE_EP_DIR} \
        ${env.INTERMEDIATE_CO_DIR} \
        ${GITTAG} \
        ${env.ENTERPRISE_SECRET}/arangodb31 \
        repositories/arangodb31
"""
    }
    
  }
}

stage("Generating HTML output") {
  node('master') {
    sh """
${ARANGO_SCRIPT_DIR}/html/create_index.sh enterprise ${env.ENTERPRISE_SECRET} ${env.INTERMEDIATE_EP_DIR}
${ARANGO_SCRIPT_DIR}/html/create_index.sh community repositories ${env.INTERMEDIATE_CO_DIR}
${ARANGO_SCRIPT_DIR}/publish/builddoc2stage.sh
"""
  }
}

stage("publish packages") {
  node('master') {
    sh "${ARANGO_SCRIPT_DIR}/publish/stage2public.sh"
    sh "${ARANGO_SCRIPT_DIR}/publish/publish_documentation.sh"
    
  }
}

stage("updating other repos") {

  // sh "export GITTAG=${GITTAG}; ${ARANGO_SCRIPT_DIR}/macosx/update_homebrew.sh"

  node('master') {
    if (SKIP_DOCKER_PUBLISH == 'false') {
      build( job: 'RELEASE__UpdateDockerResources',
             parameters: [
               string(name: 'GITTAG', value: params['GITTAG']),
               booleanParam(name: 'NEW_MAJOR_RELEASE', value: params['NEW_MAJOR_RELEASE']),
               booleanParam(name: 'CREATE_NEW_VERSION', value: true),
               booleanParam(name: 'CREATE_DOCKER_LIBRARY_PULLREQ', value: true),
               booleanParam(name: 'UPDATE_UNOFFICIAL_IMAGE', value: true),
               booleanParam(name: 'UPDATE_MESOS_IMAGE', value: true)]
           )
    }
  }
}
