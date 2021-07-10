# Overview

The gold standard Stax XML API (`javax.xml.stream`) implementation.
Now at Github.

For longer overview, check out:

* [Java XML: Woodstox Introduction](https://www.studytrails.com/2016/09/12/java-xml-woodstox-introduction/) by StudyTrails

## Status

| Type | Status |
| ---- | ------ |
| Build (CI) |  [![Build Status](https://travis-ci.org/FasterXML/woodstox.svg)](https://travis-ci.org/FasterXML/woodstox) |
| Artifact |  [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.woodstox/woodstox-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fasterxml.woodstox/woodstox-core/) |
| OSS Sponsorship | [![Tidelift](https://tidelift.com/badges/package/maven/com.fasterxml.woodstox:woodstox-core)](https://tidelift.com/subscription/pkg/maven-com-fasterxml-woodstox-woodstox-core?utm_source=maven-com-fasterxml-woodstox-woodstox-core&utm_medium=referral&utm_campaign=readme) |
| Javadocs | [![Javadoc](https://javadoc.io/badge/com.fasterxml.woodstox/woodstox-core.svg)](http://www.javadoc.io/doc/com.fasterxml.woodstox/woodstox-core)
| CodeQ (LGTM.com) | [![LGTM alerts](https://img.shields.io/lgtm/alerts/g/FasterXML/woodstox.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/FasterXML/woodstox/alerts/) [![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/FasterXML/woodstox.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/FasterXML/woodstox/context:java) |

# Get it!

## Maven

The most common way is to use Maven (or Ivy) to access it from Maven Central repository.
Coordinates for this are:

* Group id: `com.fasterxml.woodstox`
* Artifact id: `woodstox-core`
* Latest published version: 6.2.6 (2021-04-19)

Note that Maven id has changed since Woodstox 4.x but API is still compatible (despite nominal major version upgrade -- major version upgrades in this case were only due to package coordinate changes)

## Requirements

Woodstox 5 and above require Java 6 (JDK 1.6); as well as Stax API that is included in JDK.
The only other mandatory dependency is [Stax2 API](../../../stax2-api), extended API implemented
by Woodstox and some other Stax implementations (like [Aalto](../../../aalto-xml).

Optional dependency is [Multi-Schema Validator (MSV)](https://github.com/kohsuke/msv) that is needed if
using XML Schema or RelaxNG validation functionality

## License

Woodstox 4.x and above licensed under [Apache 2](http://www.apache.org/licenses/LICENSE-2.0.txt) license.

## Documentation etc

### Configuration

Most configuration is handled using standard Stax mechanism, property access via

* `XMLInputFactory.setProperty(propertyName, value)` for configuring XML reading aspects
* `XMLOutputFactory.setProperty(propertyName, value)` for configuring XML writing aspects

Names of properties available, including standard Stax 1.x ones, are documented in a series of blog posts:

* [Stax 1.x](https://medium.com/@cowtowncoder/configuring-woodstox-xml-parser-basic-stax-properties-39bdf88c18ec) standard configuration properties
* [Stax2 extension](https://medium.com/@cowtowncoder/configuring-woodstox-xml-parser-stax2-properties-c80ef5a32ef1) configuration properties
* [Woodstox-specific](https://medium.com/@cowtowncoder/configuring-woodstox-xml-parser-woodstox-specific-properties-1ce5030a5173) configuration properties

## Support

### Community support

Woodstox is supported by the community via the mailing list:
[woodstox-user](https://groups.google.com/forum/#!forum/woodstox-user)

### Enterprise support

Available as part of the Tidelift Subscription.

The maintainers of `woodstox` and thousands of other packages are working with Tidelift to deliver commercial support and maintenance for the open source dependencies you use to build your applications. Save time, reduce risk, and improve code health, while paying the maintainers of the exact dependencies you use. [Learn more.](https://tidelift.com/subscription/pkg/maven-com-fasterxml-woodstox-woodstox-core?utm_source=maven-com-fasterxml-woodstox-woodstox-core&utm_medium=referral&utm_campaign=enterprise&utm_term=repo)

## Contributing

For simple bug reports and fixes, and feature requests, please simply use projects
[Issue Tracker](../../issues), with exception of security-related issues for which
we recommend filing a
[Tidelift security contact](https://tidelift.com/security) (NOTE: you do NOT have to be
a subscriber to do this).

## Other

* Check out [project Wiki](../../wiki) for javadocs

