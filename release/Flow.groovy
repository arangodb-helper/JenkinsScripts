stage("building packages") {
  echo "skip build is: ${SKIP_BUILD}"
  if (SKIP_BUILD == "false") {
    echo "Now starting to build:"
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
          // echo "building Unstable builds with several attempts"
          // EP_PARAMS=[ params['ENTERPRISE_URL'], '']
          // UNSTABLE_BUILDERS = ['ubuntutwelveofour', 'centosix', 'fedoratwentyfive']
          // finalSuccess = true
          // for (EP_PARAM in EP_PARAMS ) {
          //   for (BUILDER in UNSTABLE_BUILDERS) {
          //     def rc
          //     done=false
          //     count=0
          //     while (!done && count < 10) {
          //       rc = build( job: 'RELEASE__BuildPackages',
          //                   propagate: false,
          //                   parameters: [
          //                     string(name: 'ENTERPRISE_URL', value: EP_PARAM),
          //                     string(name: 'GITTAG', value: "v3.1.1"),
          //                     string(name: 'preferBuilder', value: BUILDER),
          //                     booleanParam(name: 'CLEAN_BUILDENV', value: false),
          //                     booleanParam(name: 'propagate', value:false)
          //                   ]
          //                 )
          //       echo "Completed Run ${count} from ${BUILDER} (${EP_PARAM}) with ${rc.result}!"
          //       done = rc.result == "SUCCESS"
          //       count = count + 1
          //     }
          //     finalSuccess = finalSuccess && (rc.result == "SUCCESS")
          //   }
          // }
          // if (!finalSuccess) {
          //   currentBuild.result = 'FAILURE'
          //   error "some builds failed even after 10 retries!"
          // }
          
          ///----------------------------------------------------------------------
          echo "building Linux Enterprise Release"
          build( job: 'RELEASE__BuildPackages',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: params['preferBuilder']),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
               )

          ///----------------------------------------------------------------------
          echo "building Linux Community Release"
          build( job: 'RELEASE__BuildPackages',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: ''),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: params['preferBuilder']),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
               )

        },
        ////////////////////////////////////////////////////////////////////////////////
        "macintosh": {
          echo "building MacOS X Enterprise Release"
          ///----------------------------------------------------------------------
          node("macos") {
            sh "rm -rf /Users/jenkins/net/fileserver/*"
          }
          ///----------------------------------------------------------------------
          build( job: 'RELEASE__BuildPackages',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'macos'),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
               )

          ///----------------------------------------------------------------------
          echo "building MacOS X Community Release"
          build( job: 'RELEASE__BuildPackages',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: ''),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'macos'),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
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
          build( job: 'RELEASE__BuildWindows',
                 parameters: [
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'windows'),
                   string(name: 'ENTERPRISE_URL_PARAM', value: "--enterprise ${params['ENTERPRISE_URL']}"),
                   string(name: 'JENKINSMASTER', value: params['JENKINSMASTER']),
                   booleanParam(name: 'FLUSH_OUTPUT', value: true),
                   booleanParam(name: 'UPLOAD_RESULTS', value: false),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
               )
                 
          ///----------------------------------------------------------------------
          echo "building Windows Community Release"
          build( job: 'RELEASE__BuildWindows',
                 parameters: [
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'windows'),
                   string(name: 'ENTERPRISE_URL_PARAM', value: ''),
                   string(name: 'JENKINSMASTER', value: params['JENKINSMASTER']),
                   booleanParam(name: 'FLUSH_OUTPUT', value: false),
                   booleanParam(name: 'UPLOAD_RESULTS', value: true),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
               )
        },
        ////////////////////////////////////////////////////////////////////////////////
        "documentation": {
          build( job: 'RELEASE__BuildDocumentation',
                 parameters: [
                   string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                   string(name: 'GITTAG', value: "v${params['GITTAG']}"),
                   string(name: 'preferBuilder', value: 'debianjessieDocu'),
                   string(name: 'FORCE_GITBRANCH', value:''),
                   booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                 ]
               )
        }
      ]
    )
  }
}


stage("create repositories") {
  if (SKIP_REPOBUILD == 'false') {
    node('master') {
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

stage("Build Travis CI") {
  node('master') {
    build(
      job: 'RELEASE__BuildTravisCI',
          parameters: [
            string(name: 'GITTAG', value: params['GITTAG']),
            string(name: 'DEBFILE', value: "${env.INTERMEDIATE_CO_DIR}/xUbuntu_12.04/amd64/arangodb3-${GITTAG}-*_amd64.deb"),
            booleanParam(name: 'UPDATE_LINK', value: true),
            booleanParam(name: 'DEBUG', value: false)
          ]
    )
  }
}

stage("Generating HTML output") {
  build(
    job: 'RELEASE__CreateDownloadSnippets',
        parameters: [
          string(name: 'GITTAG', value: params['GITTAG'])
        ]
  )
}

input("message": "Everything we did so far was private. Proceed to the publish step now?")

stage("publish packages") {
  node('master') {
    sh "${ARANGO_SCRIPT_DIR}/publish/stage2public.sh"
    sh "${ARANGO_SCRIPT_DIR}/publish/publish_documentation.sh"
    
  }
}


stage("updating other repos") {
  node('macos') {
    if (IS_RELEASE == 'true') {
      build( job: 'RELEASE__BuildHomebrew',
             parameters: [
               string(name: 'GITTAG', value: params['GITTAG']),
               booleanParam(name: 'DEBUG', value: false),
             ]
           )
    }
  }
  
  node('master') {
    if (IS_RELEASE == 'true') {
      build( job: 'RELEASE__BuildAMI',
             parameters: [
               string(name: 'GITTAG', value: params['GITTAG']),
               booleanParam(name: 'NEW_MAJOR_RELEASE', value: false),
               booleanParam(name: 'DEBUG', value: false)
             ]
           )
      build( job: 'RELEASE__UpdateGithubMaster',
             parameters: [
               string(name: 'GITTAG', value: params['GITTAG'])
             ]
           )
    }
    
    build( job: 'RELEASE__UpdateGithubUnstable',
           parameters: [
             string(name: 'GITTAG', value: params['GITTAG'])
           ]
         )

    if (SKIP_DOCKER_PUBLISH == 'false' && IS_RELEASE == 'true') {
      build( job: 'RELEASE__UpdateDockerResources',
             parameters: [
               string(name: 'GITTAG', value: params['GITTAG']),
               booleanParam(name: 'NEW_MAJOR_RELEASE', value: params['NEW_MAJOR_RELEASE']),
               booleanParam(name: 'UPDATE_MESOS_IMAGE', value: true),
               booleanParam(name: 'UPDATE_UNOFFICIAL_IMAGE', value: true),
               booleanParam(name: 'CREATE_DOCKER_LIBRARY_PULLREQ', value: true),
               booleanParam(name: 'CREATE_NEW_VERSION', value: true),
               booleanParam(name: 'DEBUG', value: false)
             ]
           )
    }

    
  }
}
