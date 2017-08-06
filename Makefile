APPNAME=Stork
VERSION=3.0a

PROJECT=stork

CMDS=info ls q raw rm server status submit user cred
JARFILE=$(LIB)/$(PROJECT)-$(VERSION).jar

CLASSPATH=$(call classpathify,$(LIBJARS)):$(BUILD)
DBGFLAG=-g
JMEM=-J-Xmx512m
JFLAGS=$(DBGFLAG) $(JMEM) -classpath $(CLASSPATH)
JCFLAGS=$(JFLAGS) $(DBGFLAG) -sourcepath $(PROJECT) -nowarn

# Commands
JAVA=java
JC=javac
JAR=jar
JAVADOC=javadoc
LN=ln

# Directories
BIN=bin
BUILD=build
DOC=doc
LIB=lib

# Installation locations.
INSTALLDIR=/usr/share/$(PROJECT)
BINDIR=/usr/local/bin

.PHONY: all install classes clean discover fetchdeps pkglist \
	$(PROJECT)_cmds doc help buildtest install
.SUFFIXES: .java .class

# Recursive wildcard function from jgc.org.
rwildcard=$(foreach d,$(wildcard $1/*),$(call rwildcard,$d,$2) \
	$(filter $(subst *,%,$2),$d))
rdirs=$1 $(patsubst %/.,%,$(wildcard $(addsuffix /.,$(call rwildcard,$1,*))))

# Used to join space-delimited lists with a string.
empty:=
space:=$(empty) $(empty)
classpathify=$(subst $(space) ,:,$1)

JAVASRCS=$(call rwildcard,$(PROJECT),*.java)
JAVASRCS:=$(patsubst %/package-info.java,,$(JAVASRCS))
CLASSES=$(JAVASRCS:%.java=$(BUILD)/%.class)
#CLASSNAMES=$(subst /,.,$(JAVASRCS:%.java=%))
LIBJARS=$(call rwildcard,lib,*.jar)

BUILDLIST= # Generated by "build/%.class" rule.
JC_CMD=    # Set only if we need to compile something.

# Legacy underscore-named bins.
LEGACY_CMDS=$(patsubst %,bin/$(PROJECT)_%,$(CMDS))

all: $(JARFILE) $(LEGACY_CMDS) | $(BUILD)

$(BUILD):
	@mkdir -p $(BUILD)

$(JARFILE): classes $(BUILD)/build_tag
	@echo Generating $(JARFILE)...
	@$(JAR) $(JMEM) cf $(JARFILE) -C $(BUILD) .

# Find changed classes and fill BUILDLIST.
discover: $(CLASSES)

# If the source has changed, add the class to BUILDLIST.
$(BUILD)/%.class: %.java | $(BUILD)
	@echo Including for build: $<
	$(eval BUILDLIST += $<)
	$(eval JC_CMD=$(JC) $(JFLAGS) -d $(BUILD))

# Build everything in BUILDLIST.
classes: fetchdeps discover $(BUILDLIST)
	@echo Building $(words $(BUILDLIST)) files...
	@$(JC_CMD) $(BUILDLIST)

bin/$(PROJECT)_%: bin/$(PROJECT)
	@$(LN) -s -f $(PROJECT) $@

fetchdeps:
	@$(MAKE) -j4 --no-print-directory -C lib

$(BUILD)/build_tag: | $(BUILD)
	@echo Generating build tag...
	@echo appname=$(APPNAME) > $(BUILD)/build_tag
	@echo version=$(VERSION) >> $(BUILD)/build_tag
	@echo buildtime=$(shell date) >> $(BUILD)/build_tag

pkglist:
	@echo $(subst /,.,$(call rdirs,$(PROJECT)))

doc: $(JAVASRCS)
	@$(JAVADOC) -classpath $(CLASSPATH) -d $(DOC) \
	  -link http://docs.oracle.com/javase/7/docs/api \
	  -sourcepath $(PROJECT) $(JAVASRCS)

test: all
	@echo Running tests...
	$(JAVA) -classpath $(CLASSPATH) org.junit.runner.JUnitCore $(PROJECT).test.Tests
	@echo Testing complete.

clean:
	@echo Cleaning project build files...
	@$(RM) -r $(BUILD) $(LIB)/$(PROJECT)-*.jar $(BIN)/$(PROJECT)_*

distclean: clean
	@$(MAKE) --no-print-directory -C lib distclean

install: all
	@echo Installing to $(INSTALLDIR)...
	@install -d $(INSTALLDIR)/{bin,etc,lib}
	@install -D -t $(INSTALLDIR)/etc $(PROJECT).conf
	@install -D -t $(INSTALLDIR)/bin bin/$(PROJECT)
	@$(LN) -s -f $(INSTALLDIR)/bin/$(PROJECT) $(BINDIR)/$(PROJECT)
	@install -D -t $(BINDIR) bin/$(PROJECT)_*
	@install -D -t $(INSTALLDIR)/lib lib/*.jar

uninstall:
	@echo Uninstalling $(INSTALLDIR)...
	@$(RM) -r $(INSTALLDIR)
	@$(RM) $(BINDIR)/$(PROJECT) $(BINDIR)/$(PROJECT)_*

buildtest: distclean
	$(MAKE) help
	$(MAKE)
	$(MAKE) test
	$(MAKE) clean
	$(MAKE) distclean

help:
	@echo 'Possible targets:'
	@echo
	@echo '  all         Build everything. This is the default target.'
	@echo '  buildtest   Test the whole build system.'
	@echo '  clean       Clean up after a build.'
	@echo '  discover    Find changed sources.'
	@echo '  distclean   Clean after build and also clean dependencies.'
	@echo '  doc         Build documentation.'
	@echo '  fetchdeps   Fetch external libraries.'
	@echo '  help        Display this help information.'
	@echo '  install     Install $(APPNAME) to this system.'
	@echo '  pkglist     List all Java packages in the project.'
	@echo '  test        Run test cases.'
	@echo '  uninstall   Remove $(APPNAME) from this system.'
	@echo