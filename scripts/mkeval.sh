#for file in `find output/ap-krovetz/title/trm/ -type f`;  do     basename=`basename $file .out`;     echo $basename;     trec_eval -c -q -m all_trec qrels/qrels.ap.51-150 $file > eval/ap-krovetz/trm/$basename.eval; done
#for file in `find output/tweets2011-krovetz/title/rm/ -type f`;  do     basename=`basename $file .out`;     echo $basename;     trec_eval -c -q -m all_trec qrels/qrels.microblog2011 $file > eval/tweets2011-krovetz/rm/$basename.eval; done

#for file in `find output/tweets2011-krovetz/title/trmll/ -type f -size +0`;  do     basename=`basename $file .out`;     echo $basename;     trec_eval -c -q -m all_trec qrels/qrels.microblog2011 $file > eval/tweets2011-krovetz/trmll/$basename.eval; done

model=brm
#col=ap-krovetz
#qrels=qrels.ap.51-150
col=ft-krovetz
qrels=qrels.ft.301-400
#col=latimes-krovetz
#qrels=qrels.latimes.301-400
#col=tweets2011-krovetz
#qrels=qrels.microblog2011
#col=blog06
#qrels=qrels.blog06

for file in `find output/$col/title/$model/ -type f -size +0`;  
do     
    basename=`basename $file .out`;     
    echo $basename;     
    mkdir -p eval/$col/$model
    trec_eval -c -q -m all_trec qrels/$qrels $file > eval/$col/$model/$basename.eval; 
done
