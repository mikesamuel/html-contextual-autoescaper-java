CLASSPATH=lib/guava-libraries/guava.jar:lib/jsr305/jsr305.jar
TEST_CLASSPATH=$(CLASSPATH):lib/junit/junit.jar
JAVAC_FLAGS=-source 1.5 -target 1.5 -Xlint


default: javadoc runtests findbugs out/autoesc.jar

clean:
	rm -rf out

out:
	@mkdir -p out

classes: out/classes.tstamp
out/classes.tstamp: out src/main/com/google/autoesc/*.java
	@javac -g ${JAVAC_FLAGS} -classpath ${CLASSPATH} -d out \
	  $$(echo $^ | tr ' ' '\n' | egrep '\.java$$')
	@touch out/classes.tstamp

# Depends on all java files under tests.
tests: out/tests.tstamp out/com/google/autoesc/alltests
out/tests.tstamp: out out/classes.tstamp src/tests/com/google/autoesc/*.java
	@javac -g ${JAVAC_FLAGS} -classpath out:${TEST_CLASSPATH} -d out \
	  $$(echo $^ | tr ' ' '\n' | egrep '\.java$$')
	@touch out/tests.tstamp
out/com/google/autoesc/alltests: src/tests/com/google/autoesc/*Test.java
	@echo $^ | tr ' ' '\n' | perl -pe 's#^src/tests/|\.java$$##g; s#/#.#g;' > $@

runtests: tests
	@java -classpath out:src/tests:${TEST_CLASSPATH} junit.textui.TestRunner com.google.autoesc.AllTests

# Runs findbugs to identify problems.
findbugs: out/findbugs.txt
	@cat $^
out/findbugs.txt: out/tests.tstamp
	@find out/com -type d | \
	  xargs tools/findbugs-1.3.9/bin/findbugs \
	  -jvmArgs -Xmx1500m \
	  -textui -effort:max \
	  -auxclasspath ${TEST_CLASSPATH} > $@

# Builds the documentation.
javadoc: out/javadoc.tstamp
out/javadoc.tstamp: src/main/com/google/autoesc/*.java
	@mkdir -p out/javadoc
	@javadoc -locale en -d out/javadoc \
	  -classpath ${CLASSPATH} \
	  -use -splitIndex \
	  -windowtitle 'HTML Contextual Autoescaper' \
	  -doctitle 'HTML Contextual Autoescaper' \
	  -header '<a href="https://github.com/mikesamuel/html-contextual-autoescaper-java" target=_top>source repo</a>' \
	  -J-Xmx500m -nohelp -sourcetab 8 -docencoding UTF-8 -protected \
	  -encoding UTF-8 -author -version $^ \
	&& touch out/javadoc.tstamp

out/autoesc.jar: out/classes.tstamp
	@pushd out; \
	find com -type f -name \*.class | \
	  egrep -v 'Tests?([^.]+)?\.class' | \
	  xargs jar cf autoesc.jar; \
	popd
