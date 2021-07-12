#!/bin/sh
echo "Checking BuildTools 1.17+ cache"

#Local Maven Repo
MAVEN_DIR="$HOME/.m2"

#CraftBukkit in local repo
CRAFTBUKKIT="${MAVEN_DIR}/repository/org/bukkit/craftbukkit"

#Versions
array=("1.17")

#Download BuildTools jar
curl -s -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar

for i in "${array[@]}"
do
  VERSION_DIR="${CRAFTBUKKIT}/${i}-R0.1-SNAPSHOT"

    if [ -d "$VERSION_DIR" ]; then
      echo "CraftBukkit version ${i} is cached!"
      echo "Checking for latest commit! "
      java -jar BuildTools.jar --rev ${i} --compile craftbukkit --compile-if-changed --remapped  > /dev/null 2>&1
      echo "Finished Check."
    else
      echo "CraftBukkit version ${i} isn't cached!"
      echo "Running BuildTools!"
      java -jar BuildTools.jar --rev ${i} --compile craftbukkit --remapped > /dev/null 2>&1
      echo "Compiled CraftBukkit ${i}"
    fi
done
