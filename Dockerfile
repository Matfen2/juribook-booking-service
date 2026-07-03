# Dockerfile pour le microservice booking de JuriBook
# Étape 1 : Build avec Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 1) D'abord les fichiers de configuration Maven, pour télécharger les dépendances (cachées dans Docker) avant de copier le code source :
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2) Ensuite le code source, pour que les changements dans src ne réinitialisent pas le cache des dépendances :
COPY src ./src
RUN mvn clean package -DskipTests -B

# Étape 2 : Runtime avec JRE
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copier le JAR généré depuis l'étape de build
COPY --from=build /app/target/*.jar app.jar

# Exposer le port de l'application
EXPOSE 8083

# Commande pour démarrer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]