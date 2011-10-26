CLASSPATH=lib/guava-libraries/guava.jar:lib/jsr305/jsr305.jar:lib/jsdk2.1/servlet.jar
TEST_CLASSPATH=$(CLASSPATH):lib/junit/junit.jar
JAVAC_FLAGS=-source 1.5 -target 1.5 -Xlint


default: javadoc runtests findbugs out/autoesc.jar war

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
out/genfiles.tstamp: src/main/com/google/autoesc/*.java.py
	@echo generating source files
	@mkdir -p out/genfiles
	@(for f in $^; do \
	  mkdir -p "out/genfiles/$$(dirname $$f)/"; \
	  python $$f > "out/genfiles/$$(dirname $$f)/$$(basename $$f .py)"; \
	done) \
	&& touch out/genfiles.tstamp \
	&& echo generated source files

classes: out/classes.tstamp
out/classes.tstamp: out/genfiles.tstamp src/main/com/google/autoesc/*.java
	@echo compiling classes
	@javac -g ${JAVAC_FLAGS} -classpath ${CLASSPATH} -d out \
	  {,out/genfiles/}src/main/com/google/autoesc/*.java \
	&& touch out/classes.tstamp \
	&& echo compiled classes

# Depends on all java files under tests.
tests: out/tests.tstamp out/com/google/autoesc/alltests
out/tests.tstamp: out out/classes.tstamp src/tests/com/google/autoesc/*.java
	@echo compiling tests
	@javac -g ${JAVAC_FLAGS} -classpath out:${TEST_CLASSPATH} -d out \
	  $$(echo $^ | tr ' ' '\n' | egrep '\.java$$') \
	&& touch out/tests.tstamp \
	&& echo compiled tests
out/com/google/autoesc/alltests: src/tests/com/google/autoesc/*Test.java
	@echo $^ | tr ' ' '\n' | perl -pe 's#^src/tests/|\.java$$##g; s#/#.#g;' > $@

runtests: tests
	@echo running tests
	@java -classpath out:src/tests:${TEST_CLASSPATH} junit.textui.TestRunner com.google.autoesc.AllTests

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
out/javadoc.tstamp: out/genfiles.tstamp src/main/com/google/autoesc/*.java
	@echo generating javadoc
	@mkdir -p out/javadoc
	@javadoc -locale en -d out/javadoc \
	  -classpath ${CLASSPATH} \
	  -use -splitIndex \
	  -windowtitle 'HTML Contextual Autoescaper' \
	  -doctitle 'HTML Contextual Autoescaper' \
	  -header '<a href="https://github.com/mikesamuel/html-contextual-autoescaper-java" target=_top>source repo</a>' \
	  -J-Xmx500m -nohelp -sourcetab 8 -docencoding UTF-8 -protected \
	  -encoding UTF-8 -author -version \
	  {,out/genfiles/}src/main/com/google/autoesc/*.java \
	&& touch out/javadoc.tstamp \
	&& echo javadoc generated

out/autoesc.jar: out/classes.tstamp
	@echo packing jar
	@pushd out; \
	find com -type f -name \*.class | \
	  egrep -v 'Tests?([^.]+)?\.class' | \
	  xargs jar cf autoesc.jar; \
	popd
	@echo packaged jar
