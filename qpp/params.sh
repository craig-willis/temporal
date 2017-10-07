#!/bin/bash


if [ -z "$1" ]; then
   echo "./params.sh <col> <metric>"
   exit 1;
fi
col=$1

if [ -z "$2" ]; then
   echo "./params.sh <col> <metric>"
   exit 1;
fi
metric=$2


# Dir
zgrep "^$metric\s" ../eval/full/$col/dir/mu\=* | grep -v all | sed 's/^.*dir\///g' | sed "s/\.eval.gz:$metric//g" | sed 's/\s\s*/,/g' | sed 's/mu=//g' > $col.dir.mu.$metric.out

# KDE
zgrep "^$metric\s" ../eval/full/$col/kde/mu\=* | grep -v all | grep "mu=500.0:alpha=" | sed 's/^.*kde\///g' | sed "s/\.eval.gz:$metric//g" | sed 's/\s\s*/,/g' | sed 's/mu=500.0:alpha=//g' > $col.kde.alpha.$metric.out

zgrep "^$metric\s" ../eval/full/$col/tsm/mu\=* | grep -v all | grep "mu=500.0:lambda=" | sed 's/^.*tsm\///g' | sed "s/\.eval.gz:$metric//g" | sed 's/\s\s*/,/g' | sed 's/mu=500.0:lambda=//g' > $col.tsm.lambda.$metric.out

# RM3
zgrep "^$metric\s" ../eval/full/$col/rm3/mu\=500.0\:lambda\=0.*:fbDocs\=75.0:fbTerms\=50.0* | grep -v all  | sed 's/^.*rm3\///' | sed "s/\.eval:$metric//g"  | sed "s/mu=500.0:lambda=//g" | sed "s/:fbDocs=75.0:fbTerms=50.0"//g | sed "s/\s\s*/,/g" > $col.rm3.lambda.$metric.out

# qacf2
zgrep "^$metric\s" ../eval/full/$col/qacf2/mu\=* | grep -v all | sed 's/^.*qacf2\///g' | sed "s/:lag=2.0\.eval.gz:$metric//g" | sed 's/\s\s*/,/g' | sed 's/mu=//g' > $col.qacf2.mu.$metric.out

