@Library('sqreen-pipeline-library')
import io.sqreen.pipeline.tools.*

def git = new Git(this)

node 'docker_build', {
    stage 'Checkout', {
        git.checkout()
    }

    stage 'Build Image', {
        sh 'ci/jenkins_run.sh build_docker_image'
    }

    stage 'Build PowerWAF', {
        sh 'ci/jenkins_run.sh build_powerwaf'
    }

    stage 'Build and test Java wrapper', {
        sh 'ci/jenkins_run.sh test_java'
    }
}
