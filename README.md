# How to configure a basic CICD with OpenShift - Jenkins - Nexus - SonarQube - Github

## Software versions tested

| Software | Versions |
| --- | --- |
| OpenShift | 4.6 |
| Jenkins | 2.235.5 |
| SonarQube | 7.9.1 |

## Deploying Jenkins, Nexus and SonarQube

- Create a new project to keep Jenkins, Nexus and SonarQube

    ```sh
    $ oc new-project cicd-tools --description 'CICD tools project' --display-name 'CICD Tools'
    ```

- Go to the cicd-tools project

    ```sh
    $ oc project cicd-tools
    ```

- Deploy Jenkins

    - The template used has default values for every exposed parameter and can be used without any configuration. If you need to change any default value, include `-P PARAMETER_NAME=PARAMETER_NEW_VALUE` on the `oc process` command.

    - The template exposed parameters and default values can be checked with this command

        ```bash
        $ oc process -n openshift --parameters jenkins-persistent
        ```

    ```bash
    $ oc process -n openshift jenkins-persistent \
          -p MEMORY_LIMET=2Gi  \
          -p VOLUME_CAPACITY=2Gi \
          | oc create -f -
    ```

- Deploy Nexus

    `#TODO Not working yet`

- Deploy SonarQube

    See [this reference](https://github.com/marcelogiarola/image-sonar#openshift "GitHub public repository marcelogiarola/imagem-sonar") and deploy SonarQube on the same **cicd-tools** project

## Jenkins setup

### Integrate Jenkins with AD

`#TODO`

### Install Jenkins Plugins

1. Go to (*Jenkins menu structure*) **Jenkins** > **Manage Jenkins** > **Manage Plugins**
    1. Click on **Available** tab
        1. Fill in the search field with **maven**
        1. Check the **Pipeline Maven Integration Plugin**
        1. Fill in the search field with **openshift**
        1. Check the **OpenShift Pipeline Jenkins Plugin**
        1. Click on **Download now and install after restart** button (Jenkins will restart)

### Install Util Pipeline Library

See [this reference](https://github.com/marcelogiarola/jenkins-util-library#utility-feature-library-for-jenkins-pipelines "GitHub public repository marcelogiarola/jenkins-util-library")

## Nexus setup

`#TODO`

## SonarQube setup

`#TODO`

## Setup of a new application

### Application Git repository

`#TODO`

### Application Openshift Environments (DEV, HML and PRD)

- Create one namespace for each environment and create an imagestream for the appropriated base image on each namespace

    ```bash
    $ oc new-project <namespace base name>-dev --description '<development environment namespace`s description>' --display-name '<development environment namespace`s display name>'
    $ oc project <namespace base name>-dev
    $ oc import-image redhat-openjdk-18/openjdk18-openshift --from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift:1.8 --confirm -n myproject

    $ oc new-project <namespace base name>-hml --description '<homologation environment namespace`s description>' --display-name '<homologation environment namespace`s display name>'
    $ oc project <namespace base name>-hml
    $ oc import-image redhat-openjdk-18/openjdk18-openshift --from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift:1.8 --confirm -n myproject

    $ oc new-project <namespace base name> --description '<production environment namespace`s description>' --display-name '< environment namespace`s display name>'
    $ oc project <namespace base name>
    $ oc import-image redhat-openjdk-18/openjdk18-openshift --from=registry.access.redhat.com/redhat-openjdk-18/openjdk18-openshift:1.8 --confirm -n myproject
    ```   

### Application Jenkins Pipeline

1. On Jenkins home page, click on **New Item**
    1. Fill in the **item name** on the selected text field
    1. Click on **Pipeline**
    1. Check **This project is parameterized** and for each of the following parameters click **Add Parameter**, select the appropriated type and fill in the corresponding fields
        1. **Project Name**
            - Type - **String Parameter**
            - Name - **PROJECT_NAME**
            - Default Value - The **namespace base name** you used at **Application Openshift Environments (DEV, HML and PRD)** above
            - Description - Anything
            - Check **Trim the string**
        1. **Build Name**
            - Type - **String Parameter**
            - Name - **BUILD_NAME**
            - Default Value - **application name**
            - Description - Anything
            - Check **Trim the string**
