# Jenkins Plugin for Jira Server, Data Center and Cloud

This plugin integrates Jenkins with [Jira Software](https://www.atlassian.com/software/jira) 
and [Jira Service Management](https://www.atlassian.com/software/jira/service-management/features/service-desk) on Server, Data Center 
and Cloud deployments.

The integration offers support for public (non-firewalled) and private (behind a firewall) Jenkins servers. In this case public means 
the Jira app can access it. It does not need to be public to the entire human race.

> :warning: This plugin does need the Jenkins Integration for Jira app to be installed in for Jira instance. Go to **Apps > Manage Apps**
> in Jira, search for "Jenkins Integration for Jira" and install the app. After the app is installed it will help you integrate your 
> Jenkins server with Jira.
     
## Prerequisites

- You're an administrator of your Jira instance/site and can install, update and delete apps.
- You're an administrator of your Jenkins server and can install, update and delete plugins.
- Your team as part of there development or deployment practices add issue keys to jobs and/or builds in Jenkins.
  See [Linking builds to issue](https://docs.marvelution.com/jji/cloud/data-synchronization/#linking-builds-to-issues) where the app 
  searches for issue keys. 

## Getting Started

Refer to the getting started [guide](https://docs.marvelution.com/jji/cloud/) or [video](https://youtu.be/KxlVIJlQ4To) to integrate your 
Jenkins server with Jira. 

## Support

Read all about the integration between Jenkins and Jira in the [Marvelution docs](https://docs.marvelution.com/jji/).

If you are having trouble with this plugin, please reach out to [Marvelution support](https://getsupport.marvelution.com/).

## Contributing

Feel free to raise issues and questions via the [GitHub issue tracker](https://github.com/jenkinsci/jira-integration-plugin/issues).

Pull requests are always welcome!
