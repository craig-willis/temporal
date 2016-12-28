
data <- read.table("tmp.out", header=F);
t.test(data$V1, data$V2, paired=T, alternative="less");
t.test(data$V1, data$V3, paired=T, alternative="less");
