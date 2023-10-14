generate_release_notes() {
  local resourceName=$1
  local version=$2
  local releaseName=$3
  local releaseBody=$(find_resource_variable "$resourceName" releaseBody)
  local releasedAt=$(find_resource_variable "$resourceName" releasedAt)

  local releaseNotesFile="docs/content/releases/jenkins/${version}.md"

  cat <<EOF > $releaseNotesFile
---
title: "${version}"
date: ${releasedAt}
draft: false
layout: none
outputs:
- note

dl: new-dl
version: "${releaseName}"

supports:
  server: true
  cloud: true
---

${releaseBody}

EOF

  git add $releaseNotesFile
}

create_pull_request() {
  local resourceName=$1
  local sourceBranch=$2

  local scmName=$(find_resource_variable "$resourceName" masterName)
  local repositoryUrl=$(find_resource_variable "$resourceName" gitRepoRepositoryUrl)
  local pullRequestUrl=$(add_provider_basic_auth_credentials_to_url "${repositoryUrl}/pullrequests")

  echo "Creating PR using ${pullRequestUrl}"
  curl "${pullRequestUrl}" --request POST --header 'Content-Type: application/json' --data "{
        \"title\": \"Release Notes for Jenkins plugin version ${releaseName}\",
        \"source\": {
            \"branch\": {
                \"name\": \"new_jenkins_release\"
            }
        }
    }"
}
