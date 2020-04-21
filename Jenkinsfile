@Library('sqreen-pipeline-library')
import io.sqreen.pipeline.tools.*

def git = new Git(this)

node 'docker_build', {
    stage 'Checkout', {
        sh 'git config --local url."https://github.com/".insteadOf git@github.com'
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
