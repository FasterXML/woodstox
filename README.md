# Overview

The gold standard Stax XML API implementation. Now at Github.

## Status

[![Build Status](https://travis-ci.org/FasterXML/woodstox.svg)](https://travis-ci.org/FasterXML/woodstox)


# Get it!

## Maven

The most common way is to use Maven (or Ivy) to access it from Maven Central repository.
Coordinates for this are:

* Group id: `com.fasterxml.woodstox`
* Artifact id: `woodstox-core`

Note that Maven id has changed since Woodstox 4.x.

## Requirements

Woodstox 5 and above require Java 6 (JDK 1.6); as well as Stax API that is included in JDK.
The only other mandatory dependence is [Stax2 API](../../../stax2-api], extended API implemented by Woodstox
and some other Stax implementations (like [Aalto](../../../aalto-xml).

Optional dependency is [Multi-Schema Validator (MSV)](https://github.com/kohsuke/msv) that is needed if
using XML Schema or RelaxNG validation functionality


