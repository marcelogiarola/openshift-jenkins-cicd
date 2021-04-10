@Library('util')
import com.redhat.Util

def util, webhookPayload, appGitBranch, appGitUrl, environment, buildName = '', version = '', projectName = "", appName = "", projectHomePath = "", pomFilePath = "", defaultConfigurationDirPath = ""
def isAppBCExists = false, isAppDCExists = false, hasMavenProfile = false
def envvars
def isDebugEnabled, isBuildFromFile

pipeline {
    agent any
    stages {
        stage ('Print env vars') {
            steps {
                script {
                    echo "Environment variables:\n" +
                    "\tPROJECT_NAME: ${env.PROJECT_NAME}\n" +
                    "\tBUILD_NAME: ${env.BUILD_NAME}\n" +
                    "\tAPP_IMG_STREAM: ${env.APP_IMG_STREAM}\n" +
                    "\tAPP_BINARY_BUILD_PATH: ${env.APP_BINARY_BUILD_PATH}\n" +
                    "\tIS_FROM_FILE: ${env.IS_FROM_FILE}\n" +
                    "\tTEMPLATE_NAME: ${env.TEMPLATE_NAME}\n" +
                    "\tURL_LIVENESS: ${env.URL_LIVENESS}\n" +
                    "\tURL_READINESS: ${env.URL_READINESS}\n" +
                    "\tENV_VARS: ${env.ENV_VARS}\n" +
                    "\tPROJECT_HOME_PATH: ${env.PROJECT_HOME_PATH}\n" +
                    "\tMAVEN_PROFILE: ${env.MAVEN_PROFILE}\n"
                }
            }
        }
        stage ('Printing payload info') {
            steps {
                script {
                    try {
                        echo "Variables from shell: payload ${payload}"
                    } catch (MissingPropertyException e) {
                        echo "Webhook não configurado corretamente, ou pipeline iniciado manualmente pelo Openshift/Jenkins. Iniciar pipeline com push na branch requerida."
                    }
                }
            }
        }
        stage ('Parameters validation') {
            steps {
                script {
                    if ("".equals(env.BUILD_NAME)) {
                        echo "Parâmetro obrigatório. Informar o nome de build da aplicação."
                    }
                    if ("".equals(env.PROJECT_NAME)) {
                       echo "Parâmetro obrigatório. Informar o nome base do projeto."
                    }
                    if ("".equals(env.IS_FROM_FILE)) {
                        echo "Parâmetro obrigatório. Informar se o build será realizado a partir de um arquivo, caso 'true', ou a partir de um diretório, caso 'false'."
                    }
                    if ("".equals(env.TEMPLATE_NAME)) {
                        echo "Parâmetro obrigatório. Informar o template usado para a construção da aplicação."
                    }
                    if ("".equals(env.URL_LIVENESS)) {
                        echo "Parâmetro obrigatório. Informar a URL usada para a probe de liveness."
                    }
                    if ("".equals(env.URL_READINESS)) {
                        echo "Parâmetro obrigatório. Informar a URL usada para a probe de readiness."
                    }
                }
            }
        }
        stage ('Init Pipeline') {
            steps {
                script {
                    util = new com.redhat.Util();
                    util.enableDebug(true)
                    util.init();
                }
            }
        }
        stage ('Build') {
            steps {
                script {
                    echo "Clone example project from https://github.com/jpmaida/hello-world-spring-boot/"
                }
                git 'https://github.com/jpmaida/hello-world-spring-boot/'
                script {
                    echo "Example project cloned"
                }
                withMaven(maven: 'mvn3', mavenOpts: '-DskipTests', options: [artifactsPublisher()]) {
                    sh label: 'maven build', script: 'mvn clean package -f example/pom.xml'
                }
            }
        }
    }
}
