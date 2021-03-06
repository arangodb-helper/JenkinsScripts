

stage("addCustomer") {
  node('master') {
    echo "${env.BUILD_USER} ${env.BUILD_USER_FIRST_NAME} ${env.BUILD_USER_ID} "
    sh "${ARANGO_SCRIPT_DIR}/enterprise/AddCustomer.sh '${CUSTOMER_NAME}' '${env.BUILD_USER_ID}'"
  }
}

//stage("publish customer to docker registry") {
//  node('master') {
//    build( job: 'RELEASE__UpdateDockerResources',
//           parameters: [
//             string(name: 'GITTAG', value: params['GITTAG']),
//             booleanParam(name: 'NEW_MAJOR_RELEASE', value: false),
//             booleanParam(name: 'UPDATE_MESOS_IMAGE', value: false),
//             booleanParam(name: 'UPDATE_UNOFFICIAL_IMAGE', value: false),
//             booleanParam(name: 'CREATE_DOCKER_LIBRARY_PULLREQ', value: false),
//             booleanParam(name: 'CREATE_NEW_VERSION', value: false),
//             booleanParam(name: 'DEBUG', value: false)
//           ]
//         )
//  }
//}
