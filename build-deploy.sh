#!/usr/bin/env bash
mvn clean source:jar javadoc:jar verify gpg:sign install:install deploy:deploy
