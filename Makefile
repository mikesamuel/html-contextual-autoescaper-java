CLASSPATH=lib/guava-libraries/guava.jar:lib/jsr305/jsr305.jar:lib/jsdk2.1/servlet.jar
TEST_CLASSPATH=$(CLASSPATH):lib/junit/junit.jar
JAVAC_FLAGS=-g -Xlint:all -encoding UTF-8 -source 1.7 -target 1.7 -J-Xss4m


default: javadoc runtests findbugs jars war

clean:
	rm -rf out

out:
	@mkdir -p out

# Packages content in out/war so that the appengine tool can be used thus:
#    PATH_TO_APPENGINE_SDK/bin/dev_appserver.sh out/war
#    PATH_TO_APPENGINE_SDK/appcfg.sh update out/war
war: out/war.tstamp

out/war.tstamp: out/war/WEB-INF classes
	@echo packing appengine testbed
	@touch out/war.stamp
	@mkdir -p out/war/WEB-INF/lib out/war/WEB-INF/classes
	@cp -r out/com out/war/WEB-INF/classes/com
	@cp $$(echo ${CLASSPATH} | tr ':' ' ') out/war/WEB-INF/lib
out/war/WEB-INF: war/WEB-INF
	@mkdir -p out/war
	@rm -rf out/war/WEB-INF
	@cp -r war/WEB-INF out/war/WEB-INF \
	&& echo packed appengine testbed
out/genfiles.tstamp: src/main/java/com/google/autoesc/*.java.py
	@echo generating source files
	@mkdir -p out/genfiles
	@(for f in $^; do \
	  mkdir -p "out/genfiles/$$(dirname $$f)/"; \
	  python $$f > "out/genfiles/$$(dirname $$f)/$$(basename $$f .py)"; \
	done) \
	&& touch out/genfiles.tstamp \
	&& echo generated source files

classes: out/classes.tstamp
out/classes.tstamp: out/genfiles.tstamp src/main/java/com/google/autoesc/*.java
	@echo compiling classes
	@javac ${JAVAC_FLAGS} -classpath ${CLASSPATH} -d out \
	  {,out/genfiles/}src/main/java/com/google/autoesc/*.java \
	&& touch out/classes.tstamp \
	&& echo compiled classes

# Depends on all java files under tests.
tests: out/tests.tstamp out/com/google/autoesc/alltests
out/tests.tstamp: out/classes.tstamp src/test/java/com/google/autoesc/*.java
	@echo compiling tests
	@javac ${JAVAC_FLAGS} -classpath out:${TEST_CLASSPATH} -d out \
	  $$(echo $^ | tr ' ' '\n' | egrep '\.java$$') \
	&& touch out/tests.tstamp \
	&& echo compiled tests
out/com/google/autoesc/alltests: src/test/java/com/google/autoesc/*Test.java
	@echo $^ | tr ' ' '\n' | perl -pe 's#^src/test/java/|\.java$$##g; s#/#.#g;' > $@

runtests: tests
	@echo running tests
	@java -classpath out:src/tests:${TEST_CLASSPATH} -enableassertions junit.textui.TestRunner com.google.autoesc.AllTests
	@echo running tests w/out extra jars
	@java -Dtest.nodeps=true -classpath out:lib/junit/junit.jar junit.textui.TestRunner com.google.autoesc.AllTests

# Profiles the benchmark.
profile: out/java.hprof.txt
out/java.hprof.txt: out/tests.tstamp
	java -cp ${TEST_CLASSPATH}:out -agentlib:hprof=cpu=times,format=a,file=out/java.hprof.txt,lineno=n,doe=y com.google.autoesc.BenchmarkEscapersTest < /dev/null

# Runs findbugs to identify problems.
findbugs: out/findbugs.txt
	@cat $^
out/findbugs.txt: out/tests.tstamp
	@echo finding bugs
	@find out/com -type d | \
	  xargs tools/findbugs-1.3.9/bin/findbugs \
	  -jvmArgs -Xmx1500m \
	  -textui -effort:max \
	  -auxclasspath ${TEST_CLASSPATH} > $@ \
	&& echo findbugs done

# Builds the documentation.
javadoc: out/javadoc.tstamp
out/javadoc.tstamp: out/genfiles.tstamp src/main/java/com/google/autoesc/*.java
	@echo generating javadoc
	@mkdir -p out/javadoc
	@javadoc -locale en -d out/javadoc \
	  -quiet \
	  -docencoding UTF-8 \
	  -charset UTF-8 \
	  -classpath "${CLASSPATH}" \
	  -use -splitIndex \
	  -windowtitle 'HTML Contextual Autoescaper' \
	  -doctitle 'HTML Contextual Autoescaper' \
	  -header '<a href="https://github.com/mikesamuel/html-contextual-autoescaper-java" target=_top>source repo</a>' \
	  -J-Xmx500m -nohelp -sourcetab 8 -docencoding UTF-8 -protected \
	  -encoding UTF-8 -author -version \
	  {,out/genfiles/}src/main/java/com/google/autoesc/*.java \
	&& touch out/javadoc.tstamp \
	&& echo javadoc generated

jars: out/autoesc.jar out/autoesc-src.jar
out/autoesc.jar: out/classes.tstamp
	@echo packing jar
	@cd out; \
	find com -type f -name \*.class | \
	  egrep -v 'Tests?([^.]+)?\.class' | \
	  xargs jar cf autoesc.jar \
	&& echo packaged jar
out/autoesc-src.jar: out/genfiles.tstamp src/main/java/com/google/autoesc/*.java
	@echo packing src jar
	@rm -rf $@ out/combined-src && \
	mkdir out/combined-src && \
	cp -R src/{main,test}/java/com out/genfiles/src/main/java/com out/combined-src/ && \
	find out/combined-src/ -name '.*' -exec rm '{}' \; && \
	jar cMf $@ -C out/combined-src com

versioned_jars: jars
	cp out/autoesc.jar out/autoesc-"$$(git tag | head -1)".jar
	cp out/autoesc-src.jar out/autoesc-src-"$$(git tag | head -1)".jar
