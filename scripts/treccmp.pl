#!/usr/bin/perl


my $qrels = $ARGV[0];
my $f1 = $ARGV[1];
my $f2 = $ARGV[2];
my $quiet = $ARGV[3];
my $format = $ARGV[4];
my $metric = $ARGV[3];

if ($metric eq "") { $metric = "map"; }
print "$metric\n";

open(F1, "trec_eval -q -c $qrels $f1 |");
open(F2, "trec_eval -q -c $qrels $f2 |");


my %res1;
while(<F1>) 
{
   chomp();
   my ($met, $query, $score) = split /\s+/, $_;
   $res1{$query}{$met} = $score;
}

my %res2;
while(<F2>) 
{
   chomp();
   my ($met, $query, $score) = split /\s+/, $_;
   $res2{$query}{$met} = $score;
}

open(TMP, "> tmp.out");
my $total = 0;
if ($quiet eq "v") {
    print "query\t$metric 1\t$metric 2\tdiff\tsign\tmax\n";
}
my $pos = 0;
my $neg = 0;
my $sigpos = 0;
my $signeg = 0;
my $i=0;
for $query (sort {$a <=> $b} keys %res1) {
     if ($query eq "all") { next; }
     my $score1 = $res1{$query}{$metric};
     my $score2 = $res2{$query}{$metric};
     my $max = $score1;
     if ($score2 > $score1) {
        $max = $score2; 
     }
     $total += $max;
     my $score2 = $res2{$query}{$metric};
     my $diff = 0;
     if ($score2 ne "" && $score1 ne "") {
         $diff = sprintf("%.4f", $score2 - $score1);
     }
     my $star = "o";
     if ( $diff > 0) { $star="+"; $pos++;}
     if ( $diff < 0) { $star="-"; $neg++;}
     if ( $diff > 0.02) { $star="++"; $sigpos++;}
     if ( $diff < -0.02) { $star="--"; $signeg++;}
     if ($quiet eq "v") {
         print "\t$query\t$score1\t$score2\t$diff\t$star\t$max\n";
     }
     if ($query ne "all" && $score1 ne "" && $score2 ne "") {
        print TMP "$score1\t$score2\t$max\n";
        $i++;
     }
}

my $max = sprintf("%.4f", $total/$i);
$score1 = $res1{"all"}{$metric};
$score2 = $res2{"all"}{$metric};
if ($quiet ne "v") {
    print "\tall\t" . $score1 . "\t" . $score2 . "\t" . sprintf("%.4f", ($score2 - $score1)) . "\t\t$max\t\t";
print "pos=$pos\tneg=$neg\tspos=$sigpos\tsneg=$signeg, ";
} else {
    print "\tall\t" . $score1 . "\t" . $score2 . "\t" . sprintf("%.4f", ($score2 - $score1)) . "\t\t$max\n";
    print "\tMax = $max, pos=$pos, neg=$neg, spos=$sigpos, sneg=$signeg\n";
}

close(TMP);

open(R, "Rscript ttest.R | ");
while (<R>) {
  if ($_ =~ /p-value/) {
     if ($quiet ne "v") {
        my @fields = split /=/, $_ ; #t = -1.6525, df = 98, p-value = 0.05082
        print $fields[3];
     } else {
        print "\t" . $_;
     }
  }
}
close(R);
