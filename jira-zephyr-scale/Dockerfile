# Use a base image
FROM amazoncorretto:17

# Create a non-root user
ARG USER=knowhowuser
ARG UID=1000
ARG GID=1000

RUN ln -sf /bin/bash /bin/sh \
    && yum install -y shadow-utils \
    && groupadd -g $GID $USER \
    && useradd -u $UID -g $GID -m -s /bin/bash $USER \
    && yum clean all -y

# Set environment variables for volumes
ENV APP_DIR="/app" \
    PROPERTIES_DIR="/app/properties" \
    keytoolalias="myknowhow" \
    certhostpath="/app/certs/" \
    keystorefile="/usr/lib/jvm/java-17-amazon-corretto/lib/security/cacerts" \
    JAVA_OPTS=""

# Set the working directory
WORKDIR $APP_DIR

# Create the volumes
VOLUME $PROPERTIES_DIR

# Set the JAR file variables
ARG ZEPHYR_JAR_FILE=target/zephyr-processor-exec.jar

# Set the properties file names
ARG ZEPHYR_PROPERTIES_FILE_NAME=zephyr.properties

# Copy JAR files
ADD ${ZEPHYR_JAR_FILE} $APP_DIR/zephyr.jar

# Expose ports
EXPOSE 50001

# Set the ownership of the working directory to the non-root user
RUN chown -R $USER:$USER $APP_DIR \
    && chmod 766 $keystorefile

# Switch to the non-root user
USER $USER:$GID

# Command to run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar zephyr.jar --spring.config.location=classpath:/BOOT-INF/classes/application.properties --spring.config.additional-location=optional:file:$PROPERTIES_DIR/$ZEPHYR_PROPERTIES_FILE_NAME"]
