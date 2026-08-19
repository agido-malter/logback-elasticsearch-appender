# Security Policy

## Supported Versions

Security fixes are provided for the latest released version of
`com.agido:logback-elasticsearch-appender`.

Older versions are not actively supported. Before reporting a vulnerability,
please verify whether it is reproducible with the latest release.

## Reporting a Vulnerability

Please do not report suspected security vulnerabilities through a public
GitHub issue.

Use GitHub's private vulnerability reporting feature:

https://github.com/agido-malter/logback-elasticsearch-appender/security/advisories/new

Include the following information where possible:

- affected appender version
- Java and Logback versions
- Elasticsearch or OpenSearch version, if relevant
- description of the impact
- steps or a minimal example to reproduce the issue
- known workarounds or suggested remediation
- whether the issue has been publicly disclosed

Remove credentials, tokens, personal data, and production log contents from
all examples.

Reports will be assessed based on their impact and reproducibility. We may ask
for additional information before confirming a vulnerability. Please allow
time for investigation and coordinated disclosure before publishing details.

## Scope

Examples of issues that may be considered security vulnerabilities include:

- disclosure of configured credentials or authentication data
- incorrect authentication or request-signing behavior
- vulnerabilities triggered by crafted log events or server responses
- unintended transmission of log data
- exploitable vulnerabilities in bundled dependencies

The following are generally outside the scope of this project:

- Elasticsearch or OpenSearch cluster configuration and hardening
- index templates, shard counts, replica counts, and lifecycle policies
- application-specific logging of sensitive information
- vulnerabilities that only affect unsupported versions
- reports containing only an automated scanner result without a demonstrated
  impact on this library

## Security Recommendations

- Use TLS when sending logs across an untrusted network.
- Grant the logging application only the Elasticsearch permissions required
  for writing its log events.
- Do not store credentials directly in repository-managed Logback
  configuration files.
- Review which application data is included in log messages, MDC values,
  structured arguments, and stack traces.
