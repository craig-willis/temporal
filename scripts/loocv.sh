
col=$1
model=$2
for metric in map ndcg P_5 P_10 P_20 
do
#    ./run.sh edu.gslis.main.CrossValidation -input eval/$col-krovetz/rm -metric $metric -output loocv/$col.rm.$metric.out
#    ./run.sh edu.gslis.main.CrossValidation -input eval/$col-krovetz/trm -metric $metric -output loocv/$col.trm.$metric.out
#    ./run.sh edu.gslis.main.CrossValidation -input eval/$col-krovetz/trmll -metric $metric -output loocv/$col.trmll.$metric.out
#    ./run.sh edu.gslis.main.CrossValidation -input eval/$col-krovetz/dir -metric $metric -output loocv/$col.dir.$metric.out
    ./run.sh edu.gslis.main.CrossValidation -input eval/$col/$model -metric $metric -output loocv/$col.$model.$metric.out
done
