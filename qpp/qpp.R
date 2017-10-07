library(dplyr)
library(MASS)

args = commandArgs(trailingOnly=TRUE)

topics <- args[1]
col <- args[2]
model <- args[3]
param <- args[4]
metric <- args[5]

cat(sprintf("topics <- \"%s\"\n", topics))
cat(sprintf("col <- \"%s\"\n", col))
cat(sprintf("model <- \"%s\"\n", model))
cat(sprintf("param <- \"%s\"\n", param))
cat(sprintf("metric <- \"%s\"\n", metric))


cat(sprintf("\n\nPerformance predictors for %s\n", metric))
loocv <- read.table(paste("../loocv/", col, ".", topics, ".", model, ".",  metric, ".out", sep=""), header=F)
colnames(loocv) <- c("query", "params", "metric.model")

# Read file containing metric for each level of parameter
paramvals <- read.csv(paste(col, ".", model, ".", param, ".", metric, ".out", sep=""), header=F)
colnames(paramvals) <- c("param", "query", "metric")

#paramvals <- paramvals %>% group_by(query) %>% top_n(1, metric)
paramvals <- paramvals %>% group_by(query) %>% filter(metric == max(metric)) %>%  filter(1:n() == 1)

#print(paramvals)

# Read qpp
qpp <- read.csv(paste(col,".qpp.out", sep=""), header=T)

# Merge predictors with LOOCV output
qpp.loocv <- merge(loocv, qpp, by=c("query"))

# Merge predictors with response
qpp <- merge(paramvals, qpp, by=c("query"))


cor_analysis <- function(names) {
    cor.metric.pearson <- data.frame(predictor=character(), estimate=numeric(), pvalue=numeric(), indicator=character(), stringsAsFactors=F)
    #cor.metric.spearman <- data.frame(predictor=character(), estimate=numeric(), pvalue=numeric(), stringsAsFactors=F)
    #cor.param.pearson <- data.frame(predictor=character(), estimate=numeric(), pvalue=numeric(), stringsAsFactors=F)
    #cor.param.spearman <- data.frame(predictor=character(), estimate=numeric(), pvalue=numeric(), stringsAsFactors=F)
    #for (name in colnames(qpp.loocv)) {
    for (name in names) {
       if ( name != "query" && name != "metric" && name != "metric.model" && name != "param" && name != "params") {
           p.metric <- cor.test(qpp.loocv$metric, qpp.loocv[,c(name)], method="pearson", alternative="two.sided")
    #       s.metric <- cor.test(qpp.loocv$metric, qpp.loocv[,c(name)], method="spearman", alternative="two.sided")
    #       p.param <- cor.test(qpp$param, qpp[,c(name)], method="pearson", alternative="two.sided")
    #       s.param <- cor.test(qpp$param, qpp[,c(name)], method="spearman", alternative="two.sided")
		   ind <- ""
		   if (p.metric$p.value < 0.001) {
			   ind <- "\\Uparrow"
		   }
	       else if (p.metric$p.value < 0.01) {
			   ind <- "\\uparrow"
		   }
	       else if (p.metric$p.value < 0.05) {
		   }
	       #if (p.metric$p.value < 0.05) {
	          cor.metric.pearson[nrow(cor.metric.pearson) + 1,]  <- c(name, round(p.metric$estimate, 6), round(p.metric$p.value, 6), ind)
           cat(sprintf("& %s &  $%0.4f%s$ \n", name, p.metric$estimate, ind))
	       #}
	#       if (s.metric$p.value < 0.05) {
	#          cor.metric.spearman[nrow(cor.metric.spearman) + 1,]  <- c(name, round(s.metric$estimate, 6), round(s.metric$p.value, 6))
	#       }
    #	   if (p.param$p.value < 0.05) {
    #	      cor.param.pearson[nrow(cor.param.pearson) + 1,]  <- c(name, p.param$estimate, p.param$p.value)
    #	   }
    #	   if (s.param$p.value < 0.05) {
    #	      cor.param.spearman[nrow(cor.param.spearman) + 1,]  <- c(name, s.param$estimate, s.param$p.value)
    #	   }
       }
    }
    #warnings()
    options(digits=4)
    #cor.metric.pearson <- cor.metric.pearson[order(as.numeric(cor.metric.pearson$pvalue)),]
    #cor.metric.spearman <- cor.metric.spearman[order(as.numeric(cor.metric.spearman$pvalue)),]
    #cor.param.pearson <- cor.param.pearson[order(as.numeric(cor.param.pearson$pvalue)),]
    #cor.param.spearman <- cor.param.spearman[order(as.numeric(cor.param.spearman$pvalue)),]

    #cat(sprintf("\n\nCorrelation table: predictors to LOOCV metric (%s) - pearson\n", metric))
	

    #cat(sprintf("\n\nCorrelation table: predictors to LOOCV metric (%s) - spearman\n", metric))
    #cor.metric.spearman

    #cat(sprintf("\n\nCorrelation table: predictors to parameter (%s)- pearson\n", param))
    #cor.param.pearson
    #cat(sprintf("\n\nCorrelation table: predictors to parameter (%s)- spearman\n", param))
    #cor.param.spearman
}


cor_analysis(c("clarity", "maxSCQ", "drift", "qfbdiv_a", "avgQCCF", "avgQDPS", "avgTKL", "avgQDP", "avgQACF"))
cor_analysis(c("scs", "avgIDF", "avgICTF",  "avgCCCF", "avgCACF", "avgCDPS", "avgCDP"))
quit()

#lm <- lm(param ~ drift + varSCQ + deviation, qpp)
#lm <- step(lm, direction="forward")
#summary(lm)


reg_analysis <- function(formula) {
    cat (sprintf("Regression analysis of %s\n", formula))
    mod <- lm(as.formula(formula), qpp)
    sum <-summary(mod)
    bc <- boxcox(as.formula(formula), data=qpp,  plotit=F)

    # -2 : y^(-2); -1 : y^(-1); -0.5 : y^(-1/2); 0 : log(y); 0.5 : y^(1/2); 1 : y; 2 : y^2
    #    cat(sprintf("Coefficients:  %0.4f %0.4f p=%0.4f, R^2=%0.4f \n", lm$coefficients[1,1], lm$coefficients[2,1], lm$coefficients[2,4], lm$adj.r.squared))
    bc.val <- bc$x[which(bc$y == max(bc$y))]
    cat(sprintf("\tBoxcox: %0.2f\n", bc.val))

    len <- length(residuals(mod))
    var <- summary(lm(abs(residuals(mod)) ~ fitted(mod)))
    cat(sprintf("\tConstant variance R^2: %0.4f\n", var$adj.r.squared))
    res <- summary(lm(residuals(mod)[-1] ~ residuals(mod)[-len]))
    cat(sprintf("\tResidual correlation R^2: %0.4f\n", res$adj.r.squared))
    f <- summary(mod)$fstatistic
    p <- pf(f[1],f[2],f[3],lower.tail=F)
    cat(sprintf("\tModel R^2: %0.4f\n", summary(mod)$r.squared))
    cat(sprintf("\tModel p-value %0.4f\n", p))
}


cat(sprintf("\n\nStepwise regression (%s): -- skipping\n\n", metric))
#lm <- lm(metric ~ . - query - param, qpp)
#lm <- step(lm, direction="both")
#summary(lm)

cat(sprintf("\n\nSingle-predictor models for metric (%s):\n\n", metric))

if (nrow(qpp[qpp$metric == 0,]) > 0) {
   qpp[qpp$metric == 0,]$metric <- 0.000001
}

for (name in colnames(qpp)) {
    #if ( name != "query" && name != "metric" && name != "param" && name != "avgCDPS" && name != "minCDPS" && name != "maxCDPS" && name != "varCDPS") {
    if ( name != "query" && name != "metric" && name != "param") {
      lm <- lm(paste("metric ~ ", name), qpp)
      f <- summary(lm)$fstatistic
      # http://www.gettinggeneticsdone.com/2011/01/rstats-function-for-extracting-f-test-p.html
      if (! is.null(f)) {
          p <- pf(f[1],f[2],f[3],lower.tail=F)
          if (p < 0.01) {
              reg_analysis(paste("metric ~ ", name))
#              print (summary(lm))
          }
      } else {
          cat(sprintf("%s is null\n", name))
      }
    }
}

cat(sprintf("\n\nSingle-predictor models for parameter (%s):\n\n", param))
for (name in colnames(qpp)) {
    #if ( name != "query" && name != "metric" && name != "param" && name != "avgCDPS" && name != "minCDPS" && name != "maxCDPS" && name != "varCDPS") {
    if ( name != "query" && name != "metric" && name != "param") {
      lm <- lm(paste("param ~ ", name), qpp)
      f <- summary(lm)$fstatistic
      # http://www.gettinggeneticsdone.com/2011/01/rstats-function-for-extracting-f-test-p.html
      if (! is.null(f)) {
          p <- pf(f[1],f[2],f[3],lower.tail=F)
          if (p < 0.01) {
              reg_analysis(paste("param ~ ", name))
#              print (summary(lm))
          }
      } else {
          cat(sprintf("%s is null\n", name))
      }
    }
}

#lm <- lm(param ~ . - query -metric, qpp)
#lm <- step(lm, direction="both")
#summary(lm)

cat("\n\nTwo-predictor models:\n\n")
reg_analysis("metric ~ avgQCCF + clarity ")
reg_analysis("param ~ avgQCCF + clarity ")


#cat("\n\nPredicting for held-out query:\n\n")
#for (query in qpp$query) {
#  lm <- lm(param ~ varSCQ + deviation, qpp[qpp$query != query,])
#  new <- data.frame(qpp[qpp$query == query,])
#  cat(sprintf("\t%s %0.1f\n", query, round(predict.lm(lm, new), 1)))
#  #print (paste(query, "," , lm$coefficients))
#}
