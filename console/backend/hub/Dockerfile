FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /backend

COPY pom.xml ./
COPY commons/pom.xml commons/pom.xml
COPY hub/pom.xml hub/pom.xml
COPY toolkit/pom.xml toolkit/pom.xml

COPY . .
RUN mvn -DskipTests package -pl hub -am

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && \
    apt-get install -y locales tzdata && \
    locale-gen zh_CN.UTF-8 && \
    ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai
ENV LANG=zh_CN.UTF-8
ENV LANGUAGE=zh_CN:zh
ENV LC_ALL=zh_CN.UTF-8

COPY --from=build /backend/hub/target/hub-server.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
