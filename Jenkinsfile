/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  useContainerAgent: true, // Set to `false` if you need to use Docker for containerized tests
  useArtifactCachingProxy: false,
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'windows', jdk: 17],
])
