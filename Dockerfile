FROM openjdk:8u131
ADD target/scala-2.12/web-crawler.jar web-crawler.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "web-crawler.jar"]
