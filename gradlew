#!/bin/sh

##############################################################################
# Gradle start up script for POSIX
##############################################################################

# Attempt to set APP_HOME
APP_HOME=$( cd "${0%/*}" && pwd )

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# Increase the maximum file descriptors if we can
MAX_FD="maximum"

# Collect arguments
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Execute Gradle
exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
