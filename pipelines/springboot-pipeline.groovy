@Library('util')
import com.redhat.Util

def util, artifactId, artifactPath, webhookPayload, appGitBranch, appGitUrl, environment, buildName = '', version = '', projectName = "", appName = "", projectHomePath = "", pomFilePath = "", defaultConfigurationDirPath = ""
def isAppBCExists = false, isAppDCExists = false, hasMavenProfile = false
def envvars
def isDebugEnabled, isBuildFromFile = false

pipeline {
    agent {
        label 'maven' 
    }
    options {
        timeout(time: 20, unit: 'MINUTES') 
    }
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
						error("Parâmetro obrigatório. Informar o nome de build da aplicação.")
					}
					if ("".equals(env.PROJECT_NAME)) {
					   error("Parâmetro obrigatório. Informar o nome base do projeto.")
					}
					if ("".equals(env.IS_FROM_FILE)) {
						error("Parâmetro obrigatório. Informar se o build será realizado a partir de um arquivo, caso 'true', ou a partir de um diretório, caso 'false'.")
					}
					if ("".equals(env.TEMPLATE_NAME)) {
						error("Parâmetro obrigatório. Informar o template usado para a construção da aplicação.")
					}
					if ("".equals(env.URL_LIVENESS)) {
						error("Parâmetro obrigatório. Informar a URL usada para a probe de liveness.")
					}
					if ("".equals(env.URL_READINESS)) {
						error("Parâmetro obrigatório. Informar a URL usada para a probe de readiness.")
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
					webhookPayload = readJSON text: env.payload
					appGitBranch = webhookPayload.ref
					appGitBranch = appGitBranch.replace("refs/heads/", "")
					appGitUrl = webhookPayload.project.git_http_url

					echo "Branch: ${appGitBranch}"
					echo "Git url: ${appGitUrl}"

					buildName = env.BUILD_NAME
					appName = buildName

					if (appGitBranch.equals("develop")){
						echo "Iniciando pipeline para a branch development"
						environment = 'dev'
						projectName = "${env.PROJECT_NAME}" + '-' + environment
					} else if (appGitBranch ==~ /release.*/ ){
						echo "Iniciando pipeline para a branch ${appGitBranch}"
						environment = 'hml'
						projectName = "${env.PROJECT_NAME}" + '-' + environment
					} else if (appGitBranch ==~ /hotfix.*/ ){
						echo "Iniciando pipeline para a branch ${appGitBranch}"
						environment = 'hotfix'
						projectName = "${env.PROJECT_NAME}-hotfix"
					/*} else if (appGitBranch.equals("master") ){
						echo "Iniciando pipeline para a branch ${appGitBranch}"
						environment = 'prd'
						projectName = "${env.PROJECT_NAME}"*/
					} else {
						currentBuild.result = 'ABORTED'
						error("Branch não reconhecida: ${appGitBranch}. Finalizando pipeline.")					
					}

					echo "Pipeline inicializado pelo commit realizado:\n " +
						"\tAutor: ${webhookPayload.user_name}\n " +
						"\tBranch: ${appGitBranch}\n" +
						"\tRepositório url: ${appGitUrl}"
					
					openshift.withCluster() {
						openshift.withProject(projectName) {
							isAppBCExists = openshift.selector("bc", buildName).exists()
							isAppDCExists = openshift.selector("dc", appName).exists()
						}
					}
					echo "Aplicação já existe? ${isAppBCExists}"

					isBuildFromFile = env.IS_FROM_FILE == 'true'
					echo "Build a partir de artefato? ${isBuildFromFile}"
					if("".equals(env.ENV_VARS)){
						envvars = util.stringToMap("[]")
					} else {
						envvars = util.stringToMap(env.ENV_VARS)
					}

					if("".equals(env.PROJECT_HOME_PATH)){
						projectHomePath = "."
					} else {
						projectHomePath = env.PROJECT_HOME_PATH
					}

					pomFilePath = "${projectHomePath}/pom.xml"
					defaultConfigurationDirPath = "${projectHomePath}/configurations/openshift/"

					if("".equals(env.MAVEN_PROFILE)){
						hasMavenProfile = false
					} else {
						hasMavenProfile = true
					}
				}
			}
		}
		stage ('Ambiente DES'){
			when {
				expression { return "dev".equals(environment) }
			}
			steps{
				script {
					echo "Deploy sera realizado no ambiente de Desenvolvimento."
				}
			}	
		}
		stage ('Ambiente HOM'){
			when {
				expression { return "hml".equals(environment) }
			}
			steps{
				script {
					echo "Deploy sera realizado no ambiente de Homologação."
				}
			}	
		}
		stage ('Ambiente HOTFIX'){
			when {
				expression { return "hotfix".equals(environment) }
			}
			steps{
				script {
					echo "Deploy sera realizado no ambiente de Hotfix."
				}
			}	
		}
		stage ('Build') {
			steps {
				script {
					echo "Clone GIT URL: ${appGitUrl} da branch: ${appGitBranch}"
				}
				// git branch: appGitBranch, url: appGitUrl
				git branch: 'master', url: appGitUrl
				script {
					echo "Clone realizado com sucesso"
					//def pom = readMavenPom file: 'ocp/cep-web/pom.xml'
					def pom = readMavenPom file: "${pomFilePath}"
					version = pom.version
					echo "Definido versão para uma aplicação maven. Versão: ${version}"
					artifactId = pom.build.finalName
					echo "Definido artifactId para uma aplicação maven. ArtifactId: ${artifactId}"
					packaging = pom.packaging
					echo "Definido packaging para uma aplicação maven. Packaging: ${packaging}"
					artifactPath = "${projectHomePath}/target/${artifactId}.${packaging}"
					echo "Definido artifactPath para uma aplicação maven. ArtifactPath: ${artifactPath}"
					sh "locale -a"
					//withMaven(){
						//sh "mvn clean install -DskipTests=true -P release -f ocp/cep-web/pom.xml"
						if(hasMavenProfile){
							sh "mvn clean install -DskipTests=true -P ${pomFilePath} -f ${pomFilePath}"
						} else {
							sh "mvn clean install -DskipTests=true -f ${pomFilePath}"
						}
					//}
				}
			}
		}
		stage('Code Analysis') {
			steps {
				script {
					try{
						def sonarqubeUrl = "https://sonarqube-custom:9000"
						//withMaven(){
							//sh "mvn sonar:sonar -Dsonar.host.url=${sonarqubeUrl} -DskipTests=true -Dsonar.scm.provider=git -P release -f ${pomFilePath}"
							if(hasMavenProfile){
								sh "mvn sonar:sonar -Dsonar.login=4f66a62af0699291699f42a43733328e11f29500 -Dsonar.host.url=${sonarqubeUrl} -Dsonar.scm.provider=git -P ${pomFilePath} -f ${pomFilePath}"
							} else {
								sh "mvn sonar:sonar -Dsonar.login=4f66a62af0699291699f42a43733328e11f29500 -Dsonar.host.url=${sonarqubeUrl} -Dsonar.scm.provider=git -f ${pomFilePath}"
							}
						//}
					} catch(Exception e) {
						echo "Erro ao chamar o Sonar. - " + e.getMessage()
					}
				}
			}
		}
		stage('Archive') {
			steps {
				script {
					//withMaven(){
						echo "mvn deploy -DskipTests=true -f ${pomFilePath}"
					//}
				}
			}
		}
		stage('Config Files') {
		    steps {
		        script {
					def files = findFiles(glob: "${defaultConfigurationDirPath}")
					echo("Files: ${files}")
					for(file in files){
						def path = file.path
						def yamlFile = readYaml(file: path)
						def kind = yamlFile.kind
						def name = yamlFile.metadata.name
						
						util.createOrReplace(projectName, kind, name, path, appName)
					}
		        }
		    }
		}
		stage('Build Creation') {
			when {
				expression {
					openshift.withCluster() {
						openshift.withProject(projectName) {
							echo "BuildConfig da aplicação existe? ${isAppBCExists}"
							return !isAppBCExists
						}
					} 
				}
			}
			steps {
				script {
					util.createBuild(
						projectName,
						buildName,
						env.APP_IMG_STREAM,
						artifactPath,
						isBuildFromFile)
				}
			}
		}
		stage('Build Start') {
			when {
				expression {
					return (isAppBCExists)
				}
			}
			steps {
				script {
					util.buildApp(
						projectName
						, buildName
						, artifactPath
						, isBuildFromFile)
				}
			}
		}
		stage('Image tag'){
			when {
				expression { return "hml".equals(environment) }
			}
			steps{
				script{
					openshift.withCluster(){
						openshift.withProject(projectName){
							openshift.tag(projectName+"/"+appName+":latest", projectName+"/"+appName+":${version}")
						}
					}
				}
			}
		}
		stage('Application Creation') {
			when {
				expression { return !isAppDCExists }
			}
			steps {
				script {
				    templateName = ""
				    if(env.TEMPLATE_NAME?.trim())
				        templateName = env.TEMPLATE_NAME.trim()
					util.newApp(
						projectName
						, environment
						, appName
						, templateName
						, env.URL_READINESS
						, env.URL_LIVENESS
						, 'latest'
						, 'apps.ocp.sefaz.ma.gov.br' )
				}
			}
		}
		stage("Waiting for deployment") {
			steps {
				script {
					util.verifyDeployment(projectName, appName)
				}
			}
		}
	}
}
