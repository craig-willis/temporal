#!/usr/bin/env Rscript

args = commandArgs(trailingOnly=TRUE)
setwd("/home/willis8/temporal/loocv")

col <- args[1]
from <- args[2]
to <- args[3]

for (metric in c("map", "ndcg", "P_5", "P_10", "P_20")) {
    print (metric)
    fromFile <- paste(col, from, metric, "out", sep=".")
    toFile <- paste(col, to, metric, "out", sep=".")

    fromData <- read.table(fromFile, header=F)
    toData <- read.table(toFile, header=F)
    fromData <- fromData[order(fromData$V1),]
    toData <- toData[order(toData$V1),]
    print(paste("mean 1", mean(fromData$V3)))
    print(paste("mean 2",mean(toData$V3)))
    t <- t.test(fromData$V3, toData$V3, paired=T, alternative="less")
    print(paste("p-value", t$p.value))
}
