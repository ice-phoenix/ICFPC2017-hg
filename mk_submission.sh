#!/usr/bin/env bash

TARGET=$1
KEY="19c8b9ef-b2a3-4389-a7ba-d0f700f43db6"

mkdir -p $TARGET/target

cp install punter PACKAGES README $TARGET
cp -r src/ $TARGET

mvn clean package

cp target/*-jar-with-dependencies.jar $TARGET/target

tar zcf icfp-$KEY.tar.gz $TARGET

md5sum icfp-$KEY.tar.gz
