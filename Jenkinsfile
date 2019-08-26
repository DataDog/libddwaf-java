@Library('sqreen-pipeline-library')
import io.sqreen.pipeline.tools.*

def git = new Git(this)

node 'docker_build', {
    stage 'Checkout', {
        git.checkout()
    }

    stage 'Build Image', {
        sh 'build/jenkins_run.sh build_docker_image'
    }

    stage 'Build PowerWAF', {
        sh 'build/jenkins_run.sh build_powerwaf'
    }

    stage 'Build and test Java wrapper', {
        sh 'build/jenkins_run.sh test_java'
    }
}
