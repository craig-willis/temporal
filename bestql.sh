./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/tweets2011.temporal.krovetz -topics topics/topics.microblog2011.krovetz.indri -qrels qrels/qrels.microblog2011  -metric ndcg > best/ql.tweets2011.ndcg.out
./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/tweets2011.temporal.krovetz -topics topics/topics.microblog2011.krovetz.indri -qrels qrels/qrels.microblog2011  -metric ap > best/ql.tweets2011.ap.out
./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/tweets2011.temporal.krovetz -topics topics/topics.microblog2011.krovetz.indri -qrels qrels/qrels.microblog2011  -metric p_20 > best/ql.tweets2011.p20.out

#./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/ap.temporal.krovetz -topics topics/topics.ap.51-150.krovetz.indri -qrels qrels/qrels.ap.51-150  -metric ap > best/ql.ap.map.out
#./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/ap.temporal.krovetz -topics topics/topics.ap.51-150.krovetz.indri -qrels qrels/qrels.ap.51-150  -metric ndcg > best/ql.ap.ndcg.out
#./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/ap.temporal.krovetz -topics topics/topics.ap.51-150.krovetz.indri -qrels qrels/qrels.ap.51-150  -metric p_20 > best/ql.ap.p20.out

#./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/ft.temporal.krovetz -topics topics/topics.ft.301-400.krovetz.indri -qrels qrels/qrels.ft.301-400  > best/ql.ft.out
#./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/latimes.temporal.krovetz -topics topics/topics.latimes.301-400.krovetz.indri -qrels qrels/qrels.latimes.301-400  > best/ql.latimes.out
#./run.sh edu.gslis.main.GetBestQLWeights -index /data0/willis8/indexes/blog06.temporal -topics topics/topics.blog06.indri -qrels qrels/qrels.blog06  > best/ql.blog06.out
