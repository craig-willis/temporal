./run.sh edu.gslis.qpp.RunQPP  \
    -index /data0/willis8/indexes/ap.temporal \
    -topics ./topics/topics.ap.51-200.indri \
    -startTime 571647000 \
    -endTime 631169940 \
    -interval 86400 \
    -tsindex /data0/willis8/tsindex/ap.tsindex.csv.gz  \
    -output qpp/ap.qpp.out

./run.sh edu.gslis.qpp.RunQPP  \
    -index /data0/willis8/indexes/ft.temporal \
    -topics ./topics/topics.ft.301-450.indri \
    -startTime 694332000 \
    -endTime 788918400 \
    -interval 86400 \
    -tsindex /data0/willis8/tsindex/ft.tsindex.csv.gz \
    -output qpp/ft.qpp.out

./run.sh edu.gslis.qpp.RunQPP  \
    -index /data0/willis8/indexes/latimes.temporal \
    -topics ./topics/topics.latimes.301-450.indri \
    -startTime 599637600 \
    -endTime 662688000 \
    -interval 86400 \
    -tsindex /data0/willis8/tsindex/latimes.tsindex.csv.gz \
    -output qpp/latimes.qpp.out

./run.sh edu.gslis.qpp.RunQPP  \
    -index /data0/willis8/indexes/tweets2011.temporal \
    -topics ./topics/topics.microblog.indri \
    -startTime 1295740800 \
    -endTime 1297209600 \
    -interval 3600 \
    -tsindex /data0/willis8/tsindex/tweets2011.tsindex.csv.gz \
    -output qpp/tweets2011.qpp.out

./run.sh edu.gslis.qpp.RunQPP  \
    -index /data0/willis8/indexes/wsj.temporal \
    -topics ./topics/topics.wsj.51-200.indri \
    -startTime 533779200 \
    -endTime 701395200 \
    -interval 86400 \
    -tsindex /data0/willis8/tsindex/wsj.tsindex.csv.gz \
    -output qpp/wsj.qpp.out
