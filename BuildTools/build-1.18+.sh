#!/bin/sh
echo "Checking BuildTools 1.18+ cache"

#Local Maven Repo
MAVEN_DIR="$HOME/.m2"

#CraftBukkit in local repo
SPIGOT="${MAVEN_DIR}/repository/org/spigotmc/spigot"

#Versions
array=("1.18.2" "1.18.1" "1.19")

#Download BuildTools jar
curl -s -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

for i in "${array[@]}"
do
  VERSION_DIR="${SPIGOT}/${i}-R0.1-SNAPSHOT"

    if [ -d "$VERSION_DIR" ]; then
      echo "Spigot version ${i} is cached in ${VERSION_DIR}!"
      echo "Checking for latest commit! "
      java -jar BuildTools.jar --rev ${i} --compile-if-changed --remapped  > /dev/null 2>&1
      echo "Finished Check."
    else
      echo "Spigot version ${i} isn't cached!"
      echo "Running BuildTools!"
      java -jar BuildTools.jar --rev ${i} --remapped > /dev/null 2>&1
      echo "Compiled Spigot ${i} in ${VERSION_DIR}"
    fi
done
