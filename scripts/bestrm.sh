
./run.sh edu.gslis.main.GetBestRMTerms -index=/data0/willis8/indexes/tweets2011.temporal.krovetz -topics topics/topics.microblog2011.krovetz.indri -qrels qrels/qrels.microblog2011 -numFbDocs 50 -numFbTerms 10 -metric map -output best/rm1.tweets2011.map.out > rm1.tweets2011.map.log &


./run.sh edu.gslis.main.GetBestRMTerms -index=/data0/willis8/indexes/ap.temporal.krovetz -topics topics/topics.ap.51-150.krovetz.indri -qrels qrels/qrels.ap.51-150 -numFbDocs 50 -numFbTerms 10 -metric map -output best/rm1.ap.map.out  > rm1.ap.map.log &

./run.sh edu.gslis.main.GetBestRMTerms -index=/data0/willis8/indexes/latimes.temporal.krovetz -topics topics/topics.latimes.301-400.krovetz.indri -qrels qrels/qrels.latimes.301-400 -numFbDocs 50 -numFbTerms 10 -metric map -output best/rm1.latimes.map.out  > rm1.latimes.map.log &

#./run.sh edu.gslis.main.GetBestRMTerms -index=/data0/willis8/indexes/blog06.temporal -topics topics/topics.blog06.indri -qrels qrels/qrels.blog06 -numFbDocs 50 -numFbTerms 20 -output x > best/blog06.out

#./run.sh edu.gslis.main.GetBestRMTerms -index=/data0/willis8/indexes/blog06.temporal -topics topics/topics.blog06.indri -qrels qrels/qrels.blog06 -numFbDocs 50 -numFbTerms 20 -output x > best/blog06.out


#./run.sh edu.gslis.main.GetBestRMTerms -index=/data0/willis8/indexes/ft.temporal.krovetz -topics topics/topics.ft.301-400.krovetz.indri -qrels qrels/qrels.ft.301-400 -numFbDocs 50 -numFbTerms 20 -output x > best/ft.out
