v.3.0.15
 * Add automatic jackson module registration

v.3.0.14
 * Add support for key-value-pairs set via fluent api
 * optimized import

v.3.0.13
 * fix: upgrade jackson-core to fix DoS vulnerability
 * fix: broken tests because of API change

v.3.0.12
 * fixed: 401 error occurs when  the password or username with characters such as @, # by @awol2005ex
 * fixed: Some time es bluk happened timestamp parse error， so that to set the format of @timestamp by @awol2005ex

v.3.0.11
 * Update version of Mockito to make tests compatible with JDK 17+. by @inigo in #24
 * Don't disconnect the URLConnection by @inigo in #25

v.3.0.10
 * fixed logback serialization vulnerability

v.3.0.9
 * fix bug of dead loop when get http 4xx error from Elasticsearch

v.3.0.8
 * added enum values for update & delete

v.3.0.7
 * configurable operation setting for op_type

v.3.0.6
 * logback 1.3.0 fixed #11
 * bulkrequest fixed

v3.0.5
 * NPE during bulk update fixed

v.3.0.4
 * fix vulnerables
 * added autoStackTraceLevel

v.3.0.2
 * Major dependency updates
 * Renamed project
 * fixed tests

v.3.0.1
 * second test fix

v.3.0.0
 * New fork
 * merged open PRs from abandoned origin project
