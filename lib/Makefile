GET=curl -OJs

define g
	@echo Fetching $@...
	@$(GET) 
endef

JGLOBUS=myproxy-2.1.0.jar gss-2.1.0.jar ssl-proxies-2.1.0.jar \
	jsse-2.1.0.jar

.PHONY: all clean distclean

# Add libraries here to cause them to be fetched.
all: netty-all-5.0.0.Alpha1.jar jargon-core-3.3.1.1.jar \
	junit-4.12-beta-1.jar $(JGLOBUS) jsch-0.1.51.jar \
	jsoup-1.8.1.jar dropbox-core-sdk-1.7.7.jar javax.mail-1.5.2.jar

# Add libraries below to specify their download location and dependencies.
netty-all-5.0.0.Alpha1.jar: commons-logging-1.2.jar log4j-1.2.17.jar
	$g https://search.maven.org/remotecontent?filepath=io/netty/netty-all/5.0.0.Alpha1/netty-all-5.0.0.Alpha1.jar
commons-logging-1.2.jar:
	$g https://search.maven.org/remotecontent?filepath=commons-logging/commons-logging/1.2/commons-logging-1.2.jar
jargon-core-3.3.1.1.jar:
	$g https://ci-dev.renci.org/nexus/content/groups/public/org/irods/jargon/jargon-core/3.3.1.1/jargon-core-3.3.1.1.jar
junit-4.12-beta-1.jar: hamcrest-core-1.3.jar
	$g https://search.maven.org/remotecontent?filepath=junit/junit/4.12-beta-1/junit-4.12-beta-1.jar
hamcrest-core-1.3.jar:
	$g https://search.maven.org/remotecontent?filepath=org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
log4j-1.2.17.jar:
	$g https://search.maven.org/remotecontent?filepath=log4j/log4j/1.2.17/log4j-1.2.17.jar
jsch-0.1.51.jar:
	$g https://search.maven.org/remotecontent?filepath=com/jcraft/jsch/0.1.51/jsch-0.1.51.jar
jsoup-1.8.1.jar:
	$g https://search.maven.org/remotecontent?filepath=org/jsoup/jsoup/1.8.1/jsoup-1.8.1.jar
javax.mail-1.5.2.jar:
	$g https://maven.java.net/content/repositories/releases/com/sun/mail/javax.mail/1.5.2/javax.mail-1.5.2.jar

# Dropbox dependencies.
dropbox-core-sdk-1.7.7.jar: jackson-core-2.5.0-rc1.jar
	$g https://search.maven.org/remotecontent?filepath=com/dropbox/core/dropbox-core-sdk/1.7.7/dropbox-core-sdk-1.7.7.jar
jackson-core-2.5.0-rc1.jar:
	$g https://search.maven.org/remotecontent?filepath=com/fasterxml/jackson/core/jackson-core/2.5.0-rc1/jackson-core-2.5.0-rc1.jar

# JGlobus dependencies.
gss-2.1.0.jar:
	$g https://search.maven.org/remotecontent?filepath=org/jglobus/gss/2.1.0/gss-2.1.0.jar
myproxy-2.1.0.jar:
	$g https://search.maven.org/remotecontent?filepath=org/jglobus/myproxy/2.1.0/myproxy-2.1.0.jar
ssl-proxies-2.1.0.jar: bcprov-jdk15on-1.50.jar
	$g https://search.maven.org/remotecontent?filepath=org/jglobus/ssl-proxies/2.1.0/ssl-proxies-2.1.0.jar
jsse-2.1.0.jar:
	$g https://search.maven.org/remotecontent?filepath=org/jglobus/jsse/2.1.0/jsse-2.1.0.jar

bcprov-jdk15on-1.50.jar:
	$g https://search.maven.org/remotecontent?filepath=org/bouncycastle/bcprov-jdk15on/1.50/bcprov-jdk15on-1.50.jar

clean:
	@echo Do not use target clean, use distclean instead.
	@exit 1
distclean:
	@echo Cleaning dependencies...
	@$(RM) *.jar
