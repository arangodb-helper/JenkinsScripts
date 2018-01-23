def LinuxTargets
def DOCKER_HOST_2
def DOCKER_HOST="docker_host"
VERSION_MAJOR_MINOR=""
REPO_TL_DIR=""

reportChannel = '#release'

echo "${params['HAVE_MORE_BUILDERS']}"
if (HAVE_MORE_BUILDERS == "true") {
  echo "Have 2 docker hosts!"
  DOCKER_HOST_2='docker_host_2'
  LinuxTargets="LinuxEnterprise"
}
else {
  echo "building with one docker host"
  DOCKER_HOST_2='docker_host'
  LinuxTargets="linuxPackages"
}

if (params['GITTAG'] == 'devel') {
  TRAVIS_DEB_FILE="xUbuntu_12.04/amd64/arangodb3-3.3.devel-*_amd64.deb"
  VERSION_MAJOR_MINOR="3.3"
  REPO_TL_DIR="nightly"
  // if we build devel, we don't have any v's at all:
  GITTAG="devel"
  GIT_VERSION="devel"
  GIT_BRANCH="devel"
  reportChannel='#status-packaging'
}
else {
  TRAVIS_DEB_FILE="xUbuntu_12.04/amd64/arangodb3-${GITTAG}-*_amd64.deb"
  def parts=params['GITTAG'].tokenize(".")
  VERSION_MAJOR=parts[0]
  VERSION_MINOR=parts[1]
  VERSION_MAJOR_MINOR="${VERSION_MAJOR}.${VERSION_MINOR}"
  REPO_TL_DIR="arangodb${VERSION_MAJOR}${VERSION_MINOR}"
  // the GITTAG actualy matches the tag on the repo...
  GITTAG="v${params['GITTAG']}"
  // while this one is the human readable value:
  GIT_VERSION="${params['GITTAG']}"
  GIT_BRANCH="${parts[0]}.${parts[1]}"
  slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL}/job/${env.JOB_NAME} - Starting release build for ${GIT_VERSION}"
}


//================================================================================
stage("building packages") {
  if (SKIP_BUILD == "false") {
    try {
      echo "Now starting to build:"
      parallel(
        [

          ////////////////////////////////////////////////////////////////////////////////
          "sourceTarballs": {
            node("master") {
              sh "export GITTAG=${GIT_VERSION}; ${ARANGO_SCRIPT_DIR}/source/build.sh"

            }
          },
          ////////////////////////////////////////////////////////////////////////////////
          "LinuxTargets": {
            ///----------------------------------------------------------------------
            echo "building Linux Enterprise Release"
            build( job: 'RELEASE__BuildPackages',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'preferBuilder', value: params['preferBuilder']),
                     string(name: 'DOCKER_HOST', value: DOCKER_HOST),
                     string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                   ]
                 )

            ///----------------------------------------------------------------------
            if (HAVE_MORE_BUILDERS == "false") {
              echo "building Linux Community Release"
              build( job: 'RELEASE__BuildPackages',
                     parameters: [
                       string(name: 'ENTERPRISE_URL', value: ''),
                       string(name: 'GITTAG', value: "${GITTAG}"),
                       string(name: 'preferBuilder', value: params['preferBuilder']),
                       string(name: 'DOCKER_HOST', value: DOCKER_HOST),
                       string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                       booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                     ]
                   )
            }

          },
          ////////////////////////////////////////////////////////////////////////////////
          "linuxCommunityPackages": {
            ///----------------------------------------------------------------------
            if (HAVE_MORE_BUILDERS == "true") {
              echo "building Linux Community Release"
              node(DOCKER_HOST_2) {
                build( job: 'RELEASE__BuildPackages',
                       parameters: [
                         string(name: 'ENTERPRISE_URL', value: ''),
                         string(name: 'GITTAG', value: "${GITTAG}"),
                         string(name: 'preferBuilder', value: params['preferBuilder']),
                         string(name: 'DOCKER_HOST', value: DOCKER_HOST_2),
                         string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                         booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                       ]
                     )
                echo "uploading to master"
                sh """
retries=0
while test \"\${retries}\" -lt 10; do
    set +e
    rsync -ua  --timeout=600 ${INTERMEDIATE_DIR}/CO ${JENKINSMASTER}:${INTERMEDIATE_DIR} && exit 0
    retries=\$(( \${retries} + 1 ))
done
"""
              }
            }

          },
          ////////////////////////////////////////////////////////////////////////////////
          "macintosh": {
            echo "building MacOS X Enterprise Release"
            ///----------------------------------------------------------------------
            node("macos") {
              sh "mkdir -p /Users/jenkins/net/fileserver/; rm -rf /Users/jenkins/net/fileserver/*"
            }
            ///----------------------------------------------------------------------
            build( job: 'RELEASE__BuildPackages',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'preferBuilder', value: 'macos'),
                     string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                   ]
                 )

            ///----------------------------------------------------------------------
            echo "building MacOS X Community Release"
            build( job: 'RELEASE__BuildPackages',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: ''),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'preferBuilder', value: 'macos'),
                     string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            ///----------------------------------------------------------------------
            echo "codesigning dmg's"
            node("macos") {
              retry(5) {
                sleep 5
                sh 'codesign --force --deep -s W7UC4UQXPV --sign "Developer ID Application: ArangoDB GmbH (W7UC4UQXPV)" /Users/jenkins/net/fileserver/CO/macos/*.dmg'
                sleep 5
                sh 'codesign --force --deep -s W7UC4UQXPV --sign "Developer ID Application: ArangoDB GmbH (W7UC4UQXPV)" /Users/jenkins/net/fileserver/EP/macos/*.dmg'
              }
            }
            ///----------------------------------------------------------------------
            echo "uploading dmg's"
            node("macos") {
              sh "scp -r /Users/jenkins/net/fileserver/* ${env.JENKINSMASTER}:${env.INTERMEDIATE_DIR}"
            }
            ///----------------------------------------------------------------------
            echo "running MacOS X Community Release Single unittests"
            build( job: 'RELEASE__BuildTest',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: ''),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     string(name: 'preferBuilder', value: 'macos'),
                     string(name: 'REPORT_TO', value: "slack"),
                     booleanParam(name: 'SKIP_BUILD', value: false),
                     booleanParam(name: 'RUN_CLUSTER_TESTS', value: false),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                     booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            echo "running MacOS X Community Release Cluster unittests"
            build( job: 'RELEASE__BuildTest',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: ''),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     string(name: 'preferBuilder', value: 'macos'),
                     string(name: 'REPORT_TO', value: "slack"),
                     booleanParam(name: 'SKIP_BUILD', value: true), // second run - no need to recomile!
                     booleanParam(name: 'RUN_CLUSTER_TESTS', value: true),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                     booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            echo "running MacOS X Release Enterprise Single unittests"
            build( job: 'RELEASE__BuildTest',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     string(name: 'preferBuilder', value: 'macos'),
                     string(name: 'REPORT_TO', value: "slack"),
                     booleanParam(name: 'SKIP_BUILD', value: false),
                     booleanParam(name: 'RUN_CLUSTER_TESTS', value: false),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                     booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            echo "running MacOS X Release Enterprise Cluster unittests"
            build( job: 'RELEASE__BuildTest',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     string(name: 'preferBuilder', value: 'macos'),
                     string(name: 'REPORT_TO', value: "slack"),
                     booleanParam(name: 'SKIP_BUILD', value: true), // second run - no need to recomile!
                     booleanParam(name: 'RUN_CLUSTER_TESTS', value: true),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                     booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                   ]
                 )
          },
          ////////////////////////////////////////////////////////////////////////////////
          "Windows": {

            node('windows') {
              sh "rm -rf /var/tmp/r/; mkdir -p /var/tmp/r/"
            }
            // Windows doesn't like if we compile multiple times at once...
            ///----------------------------------------------------------------------
            echo "building Windows Enterprise Release"
            build( job: 'RELEASE__BuildPackages',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'preferBuilder', value: 'windows'),
                     string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                   ]
                 )

            ///----------------------------------------------------------------------
            echo "building Windows Community Release"
            build( job: 'RELEASE__BuildPackages',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: ''),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'preferBuilder', value: 'windows'),
                     string(name: 'REPO_TL_DIR', value: REPO_TL_DIR),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            node('windows') {
              sh """
retries=0
while test \"\${retries}\" -lt 10; do
    set +e
    /usr/bin/rsync -ua --timeout=600 --progress /var/tmp/r/*  ${JENKINSMASTER}:/mnt/data/fileserver/ && exit 0
    retries=\$(( \${retries} + 1 ))
done
"""
              sh """
retries=0
while test \"\${retries}\" -lt 10; do
    set +e
    /usr/bin/rsync -ua --timeout=600 --progress  /cygdrive/e/symsrv_${REPO_TL_DIR} ${JENKINSMASTER}:${INTERMEDIATE_CO_DIR} && exit 0
    retries=\$(( \${retries} + 1 ))
done
"""

            }

            ///----------------------------------------------------------------------

            echo "running Windows Community Release Single unittests"
            build( job: 'RELEASE__BuildTest',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: ''),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     string(name: 'preferBuilder', value: 'windows'),
                     string(name: 'REPORT_TO', value: "slack"),
                     booleanParam(name: 'SKIP_BUILD', value: false),
                     booleanParam(name: 'RUN_CLUSTER_TESTS', value: false),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                     booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            /*
              echo "running Windows Community Release Cluster unittests"
              build( job: 'RELEASE__BuildTest',
              parameters: [
              string(name: 'ENTERPRISE_URL', value: ''),
              string(name: 'GITTAG', value: "${GITTAG}"),
              string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
              string(name: 'preferBuilder', value: 'windows'),
              string(name: 'REPORT_TO', value: "slack"),
              booleanParam(name: 'SKIP_BUILD', value: true), // second run - no need to recomile!
              booleanParam(name: 'RUN_CLUSTER_TESTS', value: true),
              booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
              booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
              ]
              )
            */
            echo "running Windows Release Enterprise Single unittests"
            build( job: 'RELEASE__BuildTest',
                   parameters: [
                     string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                     string(name: 'GITTAG', value: "${GITTAG}"),
                     string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     string(name: 'preferBuilder', value: 'windows'),
                     string(name: 'REPORT_TO', value: "slack"),
                     booleanParam(name: 'SKIP_BUILD', value: false),
                     booleanParam(name: 'RUN_CLUSTER_TESTS', value: false),
                     booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                     booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                   ]
                 )
            /*
              echo "running Windows Release Enterprise Cluster unittests"
              build( job: 'RELEASE__BuildTest',
              parameters: [
              string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
              string(name: 'GITTAG', value: "${GITTAG}"),
              string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
              string(name: 'preferBuilder', value: 'windows'),
              string(name: 'REPORT_TO', value: "slack"),
              booleanParam(name: 'SKIP_BUILD', value: true), // second run - no need to recomile!
              booleanParam(name: 'RUN_CLUSTER_TESTS', value: true),
              booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
              booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
              ]
              )
            */
            ///----------------------------------------------------------------------          
            echo "testing Windows Community Release NSIS Installer"
            build( job: 'RELEASE__TestWindowsInstaller',
                   parameters: [
                     string(name: 'FULL_VERSION', value: "${GIT_VERSION}"),
                     string(name: 'PACKAGE_BASE', value: "/var/tmp/r/CO/windows/ArangoDB3-"),
                     string(name: 'COMMUNITY_ENTERPRISE', value: "CO")
                   ]
                 )

            echo "testing Windows Enterprise Release NSIS Installer"
            build( job: 'RELEASE__TestWindowsInstaller',
                   parameters: [
                     string(name: 'FULL_VERSION', value: "${GIT_VERSION}"),
                     string(name: 'PACKAGE_BASE', value: "/var/tmp/r/EP/windows/ArangoDB3e-"),
                     string(name: 'COMMUNITY_ENTERPRISE', value: "EP")
                   ]
                 )

          },
          ////////////////////////////////////////////////////////////////////////////////
          "documentation": {
            try {
              echo "trying: "
              build( job: 'RELEASE__BuildDocumentation',
                     parameters: [
                       string(name: 'ENTERPRISE_URL', value: params['ENTERPRISE_URL']),
                       string(name: 'DOCKER_HOST', value: DOCKER_HOST),
                       string(name: 'GITTAG', value: "${GITTAG}"),
                       string(name: 'preferBuilder', value: 'arangodb/documentation-builder'),
                       string(name: 'FORCE_GITBRANCH', value:''),
                       string(name: 'REPORT_TO', value: "slack"),
                       string(name: 'GIT_BRANCH', value: "${GIT_BRANCH}"),
                       booleanParam(name: 'CLEAN_BUILDENV', value: params['CLEAN_BUILDENV']),
                       booleanParam(name: 'CLEAN_CMAKE_STATE', value: params['CLEAN_BUILDENV'])
                     ]
                   )
            } catch (Exception err) {
              def channel = reportChannel
              if (GIT_VERSION == 'devel') {
		      //channel = '#devel'
              }
              echo "failed: ${err}"
              slackSend channel: channel, color: '#ff0000', message: "${JENKINS_URL} Building documentation for ${GITTAG} ${REPO_TL_DIR} failed - ${err}"
            }
          }
        ]
      )
    }
    catch (hudson.AbortException ex) {
        print "Silently ignoring abort. bye."
        throw ex
    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException ex) {
        print "Silently ignoring abort. bye."
        throw ex
    } catch (Exception err) {
      slackSend channel: reportChannel, color: '#ff0000', message: "${JENKINS_URL} - Building ${GITTAG} ${REPO_TL_DIR} failed - ${err}"
      throw err;
    }
    if (GIT_VERSION != 'devel') {
      slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL}/job/${env.JOB_NAME} - Building ArangoDB ${GIT_VERSION} finished and available via 'Stage 1' - continuing to the repository building."
    }
  }
  else {
    echo "Compile step deactivated"
  }
}
//================================================================================
stage("create repositories") {
  if (SKIP_REPOBUILD == 'false') {
    build(
      job: 'RELEASE__BuildRepositories',
          parameters: [
            string(name: 'GITTAG', value: GIT_VERSION),
            string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
            booleanParam(name: 'DEBUG', value: false)
          ]
    )
  }
  else {
    echo "Create Repositories step deactivated"
  }
}


if (GIT_VERSION != 'devel') {
  slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL}/job/${env.JOB_NAME} - Building ArangoDB ${GIT_VERSION} repository building finished and available via 'Stage 2', starting silent upload and testing packages."
}
parallel(
  [
    "Uploading Packages silently": {
      if (GIT_VERSION != 'devel') {
        node('master') {
          sh "export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/stage2public.sh false true"
        }
      }
    },
    "Snippets And Test": {
      stage("Generating HTML snippets & test it with the packages") {
        build(
          job: 'RELEASE__CreateDownloadSnippets',
              parameters: [
                string(name: 'GITTAG', value: GIT_VERSION),
                string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}")
              ]
        )
        node("master") {
          sh "export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/publish_snippets.sh dev"
        }
        if (SKIP_INSTALL_TEST == "false") {
          build(
            job: 'RELEASE__TestPackages',
                parameters: [
                  string(name: 'preferBuilder', value: ''),
                  string(name: 'GITTAG', value: GIT_VERSION),
                  string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                  booleanParam(name: 'DEBUG', value: false),
                  booleanParam(name: 'testLiveDownloads', value: false)
                ]
          )
        }
        else {
          echo "Install Test deactivated"
        }
      }
    }
    ]
)

////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////
if (GIT_VERSION != 'devel') {
  slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL}/job/${env.JOB_NAME} - Private part of release '${GIT_VERSION}' process finished - Hit Continue to publish"
  input("message": "Everything we did so far was private. DC/OS checked? Proceed to the publish step now?")
  slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL} - '${GIT_VERSION}' - Continuing publish stage 1"
  echo "Continuing publish stage 1"
}
else {
  echo "building devel version without user trigger"
}
////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////

stage("upload packages") {
  node('master') {
    if (GIT_VERSION != 'devel') {
      sh "export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/stage2public.sh true false"
    } else {
      sh "export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/stage2public.sh false false"
    }
  }
}

stage("updating other repos") {
  if (SKIP_UPDATE_OTHER == "false") {
    parallel(
      [
        ////////////////////////////////////////////////////////////////////////////////
        "homeBrew": {
          node('macos') {
            if ((IS_RELEASE == 'true') && (PUBLISH_HOMEBREW == 'true')) {
              build( job: 'RELEASE__BuildHomebrew',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION),
                       booleanParam(name: 'DEBUG', value: false),
                     ]
                   )
            }
            else {
              echo "publish homebrew deactivated"
            }
          }
        },
        /*
        "AMI": {
          node('master') {
            if (IS_RELEASE == 'true') {
              retry(5) {
                build( job: 'RELEASE__BuildAMI',
                       parameters: [
                         string(name: 'GITTAG', value: GIT_VERSION),
                         string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                         booleanParam(name: 'NEW_MAJOR_RELEASE', value: false),
                         booleanParam(name: 'DEBUG', value: false)
                       ]
                     )
              }
            }
            else {
              echo "publish ami deactivated"
            }
          }
        },
        */
        /*
        "SNAPPY": {
          if (GITTAG != "devel") {
            node('master') {
              build( job: 'RELEASE__PublishSnap',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION),
                       string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                     ]
                   )
            }
          }
          else {
            echo "publish snappy deactivated"
          }
        },
        */
        "Docker": {
          node('master') {
            echo "(${SKIP_DOCKER_PUBLISH} == 'false' && ${IS_RELEASE} == 'true') ${GITTAG}"
            if (SKIP_DOCKER_PUBLISH == 'false' && IS_RELEASE == 'true') {
              build( job: 'RELEASE__UpdateDockerResources',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION),
                       string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                       booleanParam(name: 'NEW_MAJOR_RELEASE', value: params['NEW_MAJOR_RELEASE']),
                       booleanParam(name: 'UPDATE_MESOS_IMAGE', value: true),
                       booleanParam(name: 'UPDATE_UNOFFICIAL_IMAGE', value: true),
                       booleanParam(name: 'CREATE_DOCKER_LIBRARY_PULLREQ', value: true),
                       booleanParam(name: 'CREATE_NEW_VERSION', value: true),
                       booleanParam(name: 'DEBUG', value: false)
                     ]
                   )
            }
            else if (GITTAG == "devel") {
              build( job: 'RELEASE__UpdateDockerResources',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION),
                       string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                       booleanParam(name: 'NEW_MAJOR_RELEASE', value: false),
                       booleanParam(name: 'UPDATE_MESOS_IMAGE', value: false),
                       booleanParam(name: 'UPDATE_UNOFFICIAL_IMAGE', value: true),
                       booleanParam(name: 'CREATE_DOCKER_LIBRARY_PULLREQ', value: false),
                       booleanParam(name: 'CREATE_NEW_VERSION', value: false),
                       booleanParam(name: 'DEBUG', value: false)
                     ]
                   )
            }
            else if (SKIP_DOCKER_PUBLISH == 'false' && IS_RELEASE == 'false') {
              build( job: 'RELEASE__UpdateDockerResources',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION),
                       string(name: 'REPO_TL_DIR', value: "${REPO_TL_DIR}"),
                       booleanParam(name: 'NEW_MAJOR_RELEASE', value: false),
                       booleanParam(name: 'UPDATE_MESOS_IMAGE', value: false),
                       booleanParam(name: 'UPDATE_UNOFFICIAL_IMAGE', value: true),
                       booleanParam(name: 'CREATE_DOCKER_LIBRARY_PULLREQ', value: false),
                       booleanParam(name: 'CREATE_NEW_VERSION', value: true),
                       booleanParam(name: 'DEBUG', value: false)
                     ]
                   )
            }
            else {
              echo "publish docker deactivated"
            }
          }
        },
        "Update Github Master": {
          if (IS_RELEASE == 'true') {
            node("master") {
              build( job: 'RELEASE__UpdateGithubMaster',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION)
                     ]
                   )
            }
          }
          else {
            echo "update github master deactivated"
          }
        },
        "Update Github Unstable": {
          node("master") {
            if (GITTAG != "devel") {
              build( job: 'RELEASE__UpdateGithubUnstable',
                     parameters: [
                       string(name: 'GITTAG', value: GIT_VERSION)
                     ]
                   )
            }
            else {
              echo "update github unstable deactivated"
            }
          }
        }
      ])
  }
}

stage("publish website") {
  if (GIT_VERSION != 'devel') {
    slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL}/job/${env.JOB_NAME} - Invisible parts have been published - Hit Continue to publish websites"
    input("message": "Invisible parts have been published - Hit Continue to publish websites!")
    slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL} - '${GIT_VERSION}' - Continuing publish stage 2"
    echo "Continuing publish stage 2"
  }
  node('master') {
    if (GIT_VERSION != 'devel') {
      sh "export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/publish_snippets.sh live"
    }
    sh "export REPO_TL_DIR=${REPO_TL_DIR}; ${ARANGO_SCRIPT_DIR}/publish/publish_documentation.sh"
    sh "echo '${GIT_VERSION}' > ${env.INTERMEDIATE_CO_DIR}VERSION"
  }
  if (GIT_VERSION != 'devel') {
    slackSend channel: reportChannel, color: '#00ff00', message: "${JENKINS_URL}/job/${env.JOB_NAME} - Finished publishing of ArangoDB ${GIT_VERSION} - bye."
  }
}
