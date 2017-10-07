setwd("/Users/willis8/dev/uiucGSLIS/temporal/qpp")

library(dplyr)
library(glmnet)

topics <- "full"
col <- "ap"
model <- "kde"
param <- "alpha"
metric <- "map"


loocv <- read.table(paste("../loocv/", col, ".", topics, ".", model, ".",  metric, ".out", sep=""), header=F)
colnames(loocv) <- c("query", "params", "metric.model")
paramvals <- read.csv(paste(col, ".", model, ".", param, ".", metric, ".out", sep=""), header=F)
colnames(paramvals) <- c("param", "query", "metric")
paramvals <- paramvals %>% group_by(query) %>% filter(metric == max(metric)) %>%  filter(1:n() == 1)
qpp <- read.csv(paste(col,".qpp.out", sep=""), header=T)
qpp.loocv <- merge(loocv, qpp, by=c("query"))
qpp <- merge(paramvals, qpp, by=c("query"))


qpp.loocv.baseline <- qpp.loocv[,c("metric.model", "query", "params", "maxSCQ", "clarity", "deviation", "avgSCQ", "drift")]
x.baseline <- as.matrix(qpp.loocv.baseline[,-c(1,2,3)])
y.baseline <- qpp.loocv$metric.model
cv_lasso(x.baseline,y.baseline)
x <- x.baseline
y <- y.baseline

x <- as.matrix(qpp.loocv[,-c(1,2,3)])
y <- qpp.loocv$metric.model
cv_lasso(x,y)
mypred <- pred

# Predicting the parameter. 
x <- as.matrix(qpp[,-c(1,2,3)])
y <- qpp$param
cv_lasso(x,y)
cv_glm(qpp,0.3)
cv_lr_lasso(qpp)

pred.bin <- qpp
k <- 0.5
pred.bin[pred.bin$param > k,]$param <- 1
pred.bin[pred.bin$param <= k,]$param <- 0
lr.null <- glm("param ~ 1", pred.bin, family="binomial")

for (feature in colnames(qpp)) {
  if (feature != "query" && feature != "metric" && feature != "param") {
    lr.mod <- glm(paste("param ~", feature), pred.bin, family="binomial")
    pr <- lrtest(lr.mod, lr.null)$"Pr(>Chisq)"[2]
    if (pr < 0.05) {
      cat (sprintf("%s %f\n", feature, pr))
    }
  }
}


cv_lasso <- function(x, y) {
  set.seed(1)
  train <- sample(seq(nrow(x)), nrow(x)*(2/3), replace=F) # Training sample
  cv.lasso <- cv.glmnet(x[train,],y[train],alpha=1)
  coef <- data.frame(as.matrix(coef(cv.lasso)))
  coef[coef$X1 > 0,]
  plot(cv.lasso)
  pred <- predict(cv.lasso, x[-train,])         # Predict data
  
  mse <- sqrt(apply((y[-train]-pred)^2,2,mean)) # Get MSE
  lam.best <- cv.lasso$lambda[order(mse)[1]]
  lam.best
  min(mse)
  
  #lasso.tr <- glmnet(x=x[train,], y=y[train])   # Train lasso 
  #pred <- predict(lasso.tr, x[-train,])         # Predict data
  #mse <- sqrt(apply((y[-train]-pred)^2,2,mean)) # Get MSE
  #min(mse)
  #plot(log(lasso.tr$lambda), mse, type="b", xlab="Log(lambda)")
  #lam.best <- lasso.tr$lambda[order(mse)[1]]
  #lam.best
  #coef(lasso.tr, s=lam.best)
  #min(mse)
}

cv_glm <- function(pred, fixed) {
  pred.bin <- qpp
  k <- 0.5
  pred.bin[pred.bin$param > k,]$param <- 1
  pred.bin[pred.bin$param <= k,]$param <- 0
  lr.null <- glm("param ~ 1", pred.bin, family="binomial")
  queries <- qpp$query
  z <- double(length(queries))
  i <- 1
  # maxIDF, maxICTF, maxCACF, minCCCF, avgQCCF, maxQACF, maxQDPS
  for (query in queries) {
      q <- which(queries == query)
      lr.mod <- glm("param ~ ", pred.bin[-q,], family="binomial")
      summary(lr.mod)
      pred <- predict(lr.mod, pred.bin[q,-c(1:2)],type="response")
      z[i] <- pred
      i <- i+1
  }
  mean.1 <- mean(abs(qpp$param-fixed))
  mean.2 <- mean(abs(qpp$param-z))
  cat (sprintf("%f %f\n", mean.1, mean.2))
}


cv_lr_lasso <- function(pred) {
  pred.bin <- qpp
  k <- 0.5
  pred.bin[pred.bin$param > k,]$param <- 1
  pred.bin[pred.bin$param <= k,]$param <- 0
  
  y.bin = pred.bin$param
  x <- pred.bin[,-c(1,2,3)]
  
  queries <- qpp$query
  z <- double(length(queries))
  i <- 1
  # maxIDF, maxICTF, maxCACF, minCCCF, avgQCCF, maxQACF, maxQDPS
  for (query in queries) {
    q <- which(queries == query)
    cv.lasso <- cv.glmnet(as.matrix(x[-q,]), y.bin[-q], alpha=0, family="binomial")
    coef <- data.frame(as.matrix(coef(cv.lasso)))
    cat(sprintf("%s %s\n", query, paste(coef[abs(coef$X1) > 0.01,])))
    pred <- predict(cv.lasso, as.matrix(x[q,]),type="response")
    
    z[i] <- pred
    i <- i+1
  }
  mean.1 <- mean(abs(qpp$param-0.1))
  mean.2 <- mean(abs(qpp$param-z))
  cat (sprintf("%f %f\n", mean.1, mean.2))
}


#fit.lasso <- glmnet(x, y, family="gaussian", alpha=1)
#plot(fit.lasso, xvar="lambda")
#cv.lasso <- cv.glmnet(x,y,alpha=1)
#plot(cv.lasso)
#coef(cv.lasso) # Coefficients for best model
#plot(fit.lasso, xvar="dev", label=TRUE) # Plot percentage of deviance explained