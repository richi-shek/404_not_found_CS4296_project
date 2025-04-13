FROM openjdk:8-jdk

WORKDIR /build
RUN apt-get update && apt-get install -y git && wget https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar && java -jar BuildTools.jar --rev 1.16.5

WORKDIR /server
RUN echo "eula=true" > eula.txt
COPY server.properties .
COPY --from=0 /build/spigot-1.16.5.jar .

CMD ["java", "-jar", "spigot-1.16.5.jar", "nogui"]