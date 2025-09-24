FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /backend

COPY pom.xml ./
COPY commons/pom.xml commons/pom.xml
COPY hub/pom.xml hub/pom.xml
COPY toolkit/pom.xml toolkit/pom.xml

COPY . .
RUN mvn -DskipTests package -pl hub -am -Dfinal.name=hub

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/"$TZ" /etc/localtime && echo "$TZ" > /etc/timezone

ENV LANG=zh_CN.UTF-8
ENV LANGUAGE=zh_CN:zh
ENV LC_ALL=zh_CN.UTF-8

COPY --from=build /backend/hub/target/hub.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
