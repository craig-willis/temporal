./run.sh edu.gslis.main.GetFeaturesRM -index=/data0/willis8/indexes/tweets2011.temporal.krovetz -topics topics/topics.microblog2011.krovetz.indri -startTime 1295740800 -endTime 1297209600 -interval 3600 -numFbDocs 50 -numFbTerms 10 > features/rm1.tweets2011.out

 ./run.sh edu.gslis.main.GetFeaturesRM -index=/data0/willis8/indexes/latimes.temporal.krovetz -topics topics/topics.latimes.301-400.krovetz.indri -startTime 599637600 -endTime 662688000 -interval 86400 -numFbDocs 50 -numFbTerms 10 > features/rm1.latimes.out


./run.sh edu.gslis.main.GetFeaturesRM -index=/data0/willis8/indexes/ap.temporal.krovetz -topics topics/topics.ap.51-150.krovetz.indri -startTime 571647000 -endTime 631169940 -interval 86400 -numFbDocs 50 -numFbTerms 10 > features/rm1.ap.out

# ./run.sh edu.gslis.main.GetFeaturesRM -index=/data0/willis8/indexes/ft.temporal.krovetz -topics topics/topics.ft.301-400.krovetz.indri -startTime 662688000 -endTime 788918400 -interval 86400 > features/rm1.ft.out

# ./run.sh edu.gslis.main.GetFeaturesRM -index=/data0/willis8/indexes/blog06.temporal -topics topics/topics.blog06.indri -startTime 1133827200 -endTime 1140566400 -interval 3600 > features/rm1.blog06.out

