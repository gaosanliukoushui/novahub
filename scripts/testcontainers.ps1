param(
    [string]$DockerHost = "tcp://localhost:2375",
    [string]$Test = "CoreFlowIntegrationTest"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

Write-Host "Using DOCKER_HOST=$DockerHost"
Write-Host "Make sure Docker Desktop has enabled: Expose daemon on tcp://localhost:2375 without TLS"

$env:DOCKER_HOST = $DockerHost
$env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = ""

mvn -pl nova-web -am -DskipTests package

mvn -pl nova-web `
    "-Ddocker.host=$DockerHost" `
    "-Dtest=$Test" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    test
