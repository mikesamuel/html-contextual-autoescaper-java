#!/bin/bash

export SOURCE_DIR="$1"
export OUTPUT_DIR="$2"

echo SOURCE_DIR="$SOURCE_DIR"
echo OUTPUT_DIR="$OUTPUT_DIR"

find "$SOURCE_DIR" -name \*.java.py | perl -e '
use File::Path qw/make_path/;

my $outputDir = $ARGV[0];

while (<STDIN>) {
  chomp;

  my $infile = "$_";

  my $outfile = "$_";
  $outfile =~ s|^.*src/main/java/||;
  $outfile =~ s|\.java\.py$|.java|;
  $outfile = "$outputDir/$outfile";

  my $outdir = $outfile;
  $outdir =~ s|[^/]*.$||;
  make_path $outdir;

  print "Generating\n\t$infile\nto\n\t$outfile\n";

  open (GEN_JAVA, qq`python "$infile" |`) or die "$!";
  open (JAVA_OUT, ">$outfile") or die "$!";
  while (<GEN_JAVA>) {
    print JAVA_OUT "$_";
  }
  close(JAVA_OUT);
  close(GEN_JAVA);
}' "$OUTPUT_DIR"
