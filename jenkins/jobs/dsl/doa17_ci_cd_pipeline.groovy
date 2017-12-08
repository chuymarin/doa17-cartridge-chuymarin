// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def applicationRepository = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/doa17-application"

// Views
def doa17CICDPipeline = buildPipelineView(projectFolderName + "/DOA17_CI_CD_Pipeline")

// Jobs DOA17_CI_CD_Pipeline
def doa17CodeBuild = freeStyleJob(projectFolderName + "/DOA17_Code_Build")
def doa17CodeDeployDevelopment = freeStyleJob(projectFolderName + "/DOA17_Code_Deploy_Development")
def doa17CodeDeployProduction = freeStyleJob(projectFolderName + "/DOA17_Code_Deploy_Production")

// DOA17_CI_CD_Pipeline
doa17CICDPipeline.with{
    title('DOA17 CI CD Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/DOA17_Code_Build")
    showPipelineDefinitionHeader()
    alwaysAllowManualTrigger()
    refreshFrequency(5)
}

// Job DOA17_Code_Build
doa17CodeBuild.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
  }
  parameters{
    stringParam("AWS_REGION",'us-east-1',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("S3_BUCKET",'',"Name of your S3 Bucket from Environment Stack")
  }
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
  scm{
    git{
      remote{
        url(applicationRepository)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  steps {
    shell('''
set +x

export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Building Application Code"
aws codebuild start-build --project-name ${ENVIRONMENT_NAME}-project

echo "[INFO] Getting Code Build eTAG"
BUILD_ETAG=$(aws s3api head-object --bucket ${S3_BUCKET} --key WebAppOutputArtifact.zip)
echo "BUILD_ETAG=$BUILD_ETAG" >> properties_file.txt

set -x'''.stripMargin()
    )
  }
  publishers{
    archiveArtifacts("**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/DOA17_Code_Deploy_Development"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          currentBuild()
          propertiesFile('properties_file.txt')
        }
      }
    }
  }
}

// Job DOA17_Code_Deploy_Development
doa17CodeDeployDevelopment.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
  }
  parameters{
    stringParam("AWS_REGION",'',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("S3_BUCKET",'',"Name of your S3 Bucket from Environment Stack")
    stringParam("BUILD_ETAG",'',"Application Build eTAG")
  }
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
  steps {
    copyArtifacts(projectFolderName + "/DOA17_Code_Build") {
        buildSelector {
          latestSuccessful(true)
      }
    }
    shell('''
set +x

export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Deploying Application to ${ENVIRONMENT_NAME}-DevWebApp"
aws deploy create-deployment --application-name ${ENVIRONMENT_NAME}-WebApp --deployment-group-name ${ENVIRONMENT_NAME}-DevWebApp --description "Applicacion Build ${BUILD_NUMBER}" --s3-location bucket=${S3_BUCKET},key=WebAppOutputArtifact.zip,bundleType=zip,eTag=${BUILD_ETAG}

set -x'''.stripMargin()
    )
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/DOA17_Code_Deploy_Production"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          currentBuild()
        }
      }
    }
  }
}

// Job DOA17_Code_Deploy_Production
doa17CodeDeployProduction.with{
  description("Job Description")
  environmentVariables {
    env('WORKSPACE_NAME', workspaceFolderName)
    env('PROJECT_NAME', projectFolderName)
  }
  parameters{
    stringParam("AWS_REGION",'',"Default AWS Region")
    stringParam("ENVIRONMENT_NAME",'',"Name of your Environment")
    stringParam("S3_BUCKET",'',"Name of your S3 Bucket from Environment Stack")
    stringParam("BUILD_ETAG",'',"Application Build eTAG")
  }
  wrappers {
    preBuildCleanup()
    maskPasswords()
  }
  label("docker")
  steps {
    copyArtifacts(projectFolderName + "/DOA17_Code_Build") {
        buildSelector {
          latestSuccessful(true)
      }
    }
    shell('''
set +x

export AWS_DEFAULT_REGION=$AWS_REGION
echo "[INFO] Default region is set to $AWS_DEFAULT_REGION"

echo "[INFO] Deploying Application to ${ENVIRONMENT_NAME}-ProdWebApp"
aws deploy create-deployment --application-name ${ENVIRONMENT_NAME}-WebApp --deployment-group-name ${ENVIRONMENT_NAME}-ProdWebApp --description "Applicacion Build ${BUILD_NUMBER}" --s3-location bucket=${S3_BUCKET},key=WebAppOutputArtifact.zip,bundleType=zip,eTag=${BUILD_ETAG}


set -x'''.stripMargin()
    )
  }
}