# Jenkins Plugin for Jira Server, Data Center and Cloud

This plugin integrates Jenkins with [Jira](https://www.atlassian.com/jira) on Server, Data Center and Cloud deployments.

The integration offers support:

* An **Inaccessible** Jenkins site, meaning the Jenkins instance is not accessible by the Jira app. Either because of a firewall or other
  network limitations.
* An **Accessible** Jenkins site, meaning the Jenkins instance is accessible by the Jira app for data synchronization.
* An **Accessible through Tunneling** Jenkins site, meaning the Jenkins instance is not accessible directly by the Jira app, but can be 
  made accessible through a secure tunnel. (Only available in the Jira Cloud app) 

> :warning: This plugin does need
> the [Jenkins Integration for Jira](https://marketplace.atlassian.com/apps/1211376/jenkins-integration-for-jira?tab=overview&hosting=cloud)
> app to be installed in for Jira instance which is not a free app. Go to **Apps > Manage Apps** in Jira, search for "Jenkins
> Integration for Jira" and install the app. After the app is installed it will help you integrate your Jenkins server with Jira.

## Prerequisites

- You're an administrator of your Jira instance/site and can install, update and delete apps.
- You're an administrator of your Jenkins server and can install, update and delete plugins.
- Your team as part of there development or deployment practices add issue keys to jobs and/or builds in Jenkins.
  See [Linking builds to issue](https://docs.marvelution.com/jenkins-for-jira/cloud/data-synchronization/#linking-builds-to-issues) where the app
  searches for issue keys.

## Getting Started

Refer to the getting started [guide](https://docs.marvelution.com/jenkins-for-jira/cloud/) or [video](https://youtu.be/KxlVIJlQ4To) to integrate your
Jenkins server with Jira.

## Support

Read all about the integration between Jenkins and Jira in the [Marvelution docs](https://docs.marvelution.com/jenkins-for-jira/).

If you are having trouble with this plugin, please reach out to [Marvelution support](https://getsupport.marvelution.com/).

## Contributing

Feel free to raise issues and questions via the [GitHub issue tracker](https://github.com/jenkinsci/jira-integration-plugin/issues).

Pull requests are always welcome!

## Differentiators

There are multiple [Jira](https://plugins.jenkins.io/ui/search?query=jira) plugins available for Jenkins and there is also the
[official](https://marketplace.atlassian.com/apps/1227791/jenkins-for-jira-official?hosting=cloud&tab=overview) Jira plugin developed by
Atlassian.

See below on how this plugin differentiates with the [Jira](https://plugins.jenkins.io/jira/) and the
[Atlassian Jira Software Cloud](https://plugins.jenkins.io/atlassian-jira-software-cloud/) plugins.

### [Jira](https://plugins.jenkins.io/jira/)

Pros

- Free.
- Supports Jira Server, Data Center and Cloud.
- Supports automation features to create/update issues, generate release notes.
- Requires a single plugin in Jenkins

Cons

- Takes a user account to integrate.
- Back links from Jira issues to Jenkins builds is done using issue comments.

### [Atlassian Jira Software Cloud](https://plugins.jenkins.io/atlassian-jira-software-cloud/)

Pros

- Free.
- Integration is App based, no need for a user account to integrate.
- Back links from Jira issue to Jenkins builds is done using the development information panel.
- Automation is available through Jira Automation rules.

Cons

- Only supports Jira Cloud.
- Only supports Jenkins Multi branch pipeline jobs.
- The integration needs to be configured manually in Jenkins by copy and pasting the secret and webhook, no (semi)automated method exists.
- Automation rules are loosely coupled to Jenkins data. You need to copy&past Jenkins data like job name, to configure rules.
- Requires a plugin in Jenkins and an app in Jira.

### [Jira Integration](https://marketplace.atlassian.com/apps/1211376/jenkins-integration-for-jira?tab=overview&hosting=cloud)

Pros

- Integration is App based, no need for a user account to integrate.
- Supports Jira Server, Data Center and Cloud.
- Supports all Jenkins job types.
- Supports limiting the integration to selected projects.
- The app builds an issue-to-build index available in multiple panels when viewing an issue in Jira
- App provides a rules engine for to take action in Jira based on job and build data from Jenkins where the rules are configured by
  selecting jobs and builds instead on copying identifiers.
- On Jira Cloud the app also provides automation through the Jira Automation rules just like the Atlassian Jira Software Cloud plugin.
- Next to the custom integration, the app also integrates using the same APIs and the Atlassian Jira Software Cloud plugin.
- Even inaccessible Jenkins instances are supported for by-directional integration.
- Integration in Jenkins can be done automatically, manually and by using Configuration as Code.
- If installed on Jira Cloud, you can now also include Compass using the new [Jenkins for Compass](https://marketplace.atlassian.com/apps/1234756/jenkins-for-compass?tab=overview&hosting=cloud) app.

Cons

- Paid app.
- Requires a plugin in Jenkins and an app in Jira

Fun fact, Atlassian reached out to Marvelution when they first wanted to develop there own Jenkins integration plugin.
